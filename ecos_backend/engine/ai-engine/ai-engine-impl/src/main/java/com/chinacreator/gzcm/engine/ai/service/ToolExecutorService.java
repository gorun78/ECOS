package com.chinacreator.gzcm.engine.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * 工具执行器 — 从 ecos_tool_definition 表加载工具 schema
 * 支持三种执行模式：
 * <ul>
 *   <li>SQL: 注入 JdbcTemplate 执行预定义查询</li>
 *   <li>REST: 用 RestTemplate 调内部微服务 API</li>
 *   <li>BUILTIN: Java 内置函数（如 getCurrentTime, calculateRisk）</li>
 * </ul>
 */
@Service
public class ToolExecutorService {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutorService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_RESULT_LENGTH = 2000;

    private JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;
    private final Map<String, ToolDefinitionEntry> fallbackTools = new ConcurrentHashMap<>();

    @Autowired(required = false)
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 使用 @Lazy 打破 AgentDelegationService → AgentLoopService → ToolExecutorService 循环依赖
    @org.springframework.context.annotation.Lazy
    @Autowired(required = false)
    private AgentDelegationService agentDelegationService;

    public ToolExecutorService() {
        this.restTemplate = new RestTemplate();
        initFallbackTools();
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * 执行工具调用（通过 ToolCall 对象）。
     * 供 {@link AgentLoopService} 调用。
     */
    public ToolResult execute(ToolCall call) {
        return execute(call.getName(), call.getArguments(), call.getId());
    }

    /**
     * 执行工具调用。
     *
     * @param toolName  工具名称/code
     * @param arguments 调用参数
     * @return 工具执行结果
     */
    public ToolResult execute(String toolName, Map<String, Object> arguments) {
        return execute(toolName, arguments, null);
    }

    private ToolResult execute(String toolName, Map<String, Object> arguments, String callId) {
        long startNs = System.nanoTime();

        try {
            ToolDefinitionEntry def = loadToolDefinition(toolName);
            if (def == null) {
                return buildError(callId, toolName, "工具未找到: " + toolName, startNs);
            }

            String result = executeWithTimeout(def, arguments);
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

            // 结果截断（AgentLoopService 也会截断，但这里做第一层防御）
            if (result != null && result.length() > MAX_RESULT_LENGTH) {
                result = result.substring(0, MAX_RESULT_LENGTH) + "...[truncated]";
            }

            return buildSuccess(callId, toolName, result, elapsedMs);
        } catch (TimeoutException e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            return buildError(callId, toolName, "工具执行超时 (" + TIMEOUT_SECONDS + "s)", elapsedMs);
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            log.error("Tool execution failed: tool={}, error={}", toolName, e.getMessage(), e);
            return buildError(callId, toolName, "工具执行失败: " + e.getMessage(), elapsedMs);
        }
    }

    // ── Tool Loading ──────────────────────────────────────────────────────

    /**
     * 加载工具定义：优先从数据库，回退到内存 fallback。
     */
    ToolDefinitionEntry loadToolDefinition(String toolName) {
        ToolDefinitionEntry def = loadFromDatabase(toolName);
        if (def != null) {
            return def;
        }
        return fallbackTools.get(toolName);
    }

    private ToolDefinitionEntry loadFromDatabase(String toolName) {
        if (jdbcTemplate == null) {
            log.debug("JdbcTemplate not available, skipping DB load for tool: {}", toolName);
            return null;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT code, name, description, tool_type, endpoint_url, http_method, schema_json " +
                "FROM ecos_ai.ecos_tool_definition WHERE code = ? AND status = 'ACTIVE'",
                toolName
            );
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> row = rows.get(0);
            ToolDefinitionEntry entry = new ToolDefinitionEntry();
            entry.code = (String) row.get("code");
            entry.name = (String) row.get("name");
            entry.description = (String) row.get("description");
            String toolType = (String) row.get("tool_type");
            entry.toolType = toolType != null ? toolType.toUpperCase() : "API";
            entry.endpointUrl = (String) row.get("endpoint_url");
            entry.httpMethod = (String) row.get("http_method");
            entry.schemaJson = row.get("schema_json");
            return entry;
        } catch (Exception e) {
            log.warn("Failed to load tool '{}' from database: {}", toolName, e.getMessage());
            return null;
        }
    }

    // ── Execution Dispatch ────────────────────────────────────────────────

    private String executeWithTimeout(ToolDefinitionEntry def, Map<String, Object> arguments)
            throws TimeoutException, Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() -> dispatch(def, arguments));
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("工具执行被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new RuntimeException(cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private String dispatch(ToolDefinitionEntry def, Map<String, Object> arguments) throws Exception {
        String type = def.toolType;
        if (type == null) {
            return executeRest(def, arguments);
        }
        switch (type) {
            case "SQL":     return executeSql(def, arguments);
            case "REST":
            case "API":     return executeRest(def, arguments);
            case "BUILTIN": return executeBuiltin(def.code, arguments);
            default:        return executeRest(def, arguments);
        }
    }

    // ── SQL Execution ─────────────────────────────────────────────────────

    private String executeSql(ToolDefinitionEntry def, Map<String, Object> arguments) throws Exception {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate 不可用，无法执行 SQL 工具");
        }

        Object schemaObj = def.schemaJson;
        String sql = extractSqlFromSchema(schemaObj, arguments);

        if (sql == null || sql.isBlank()) {
            sql = (String) arguments.get("sql");
        }
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 工具缺少 SQL 语句定义");
        }

        List<Object> paramList = extractParamsFromSchema(schemaObj, arguments);

        log.debug("Executing SQL: {} with params: {}", sql, paramList);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, paramList.toArray());

        return objectMapper.writeValueAsString(Map.of(
            "rows", rows,
            "count", rows.size()
        ));
    }

    // ── REST Execution ────────────────────────────────────────────────────

    private String executeRest(ToolDefinitionEntry def, Map<String, Object> arguments) throws Exception {
        String url = def.endpointUrl;
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("REST 工具缺少 endpoint_url");
        }

        // 替换 URL 中的路径参数 {paramName}
        url = resolveUrlParams(url, arguments);

        String httpMethod = def.httpMethod;
        if (httpMethod == null) {
            httpMethod = "POST";
        }
        HttpMethod method = HttpMethod.valueOf(httpMethod.toUpperCase());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<?> entity;
        if (method == HttpMethod.GET || method == HttpMethod.DELETE) {
            entity = new HttpEntity<>(headers);
        } else {
            entity = new HttpEntity<>(arguments, headers);
        }

        log.debug("Executing REST: {} {}", method, url);
        ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);

        String body = response.getBody();
        return body != null ? body : "";
    }

    // ── BUILTIN Execution ─────────────────────────────────────────────────

    private String executeBuiltin(String code, Map<String, Object> arguments) throws Exception {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("BUILTIN 工具缺少 code");
        }

        switch (code) {
            case "getCurrentTime": return executeGetCurrentTime();
            case "calculateRisk":  return executeCalculateRisk(arguments);
            case "delegate_to_agent": return executeDelegate(arguments);
            default:
                throw new IllegalArgumentException("未知内置工具: " + code);
        }
    }

    private String executeGetCurrentTime() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", System.currentTimeMillis());
        result.put("iso", Instant.now().toString());
        result.put("formatted", DateTimeFormatter.ISO_ZONED_DATE_TIME.format(ZonedDateTime.now()));
        return objectMapper.writeValueAsString(result);
    }

    private String executeCalculateRisk(Map<String, Object> arguments) throws Exception {
        double probability = getDoubleParam(arguments, "probability", 0.5);
        double impact = getDoubleParam(arguments, "impact", 0.5);
        String severity = getStringParam(arguments, "severity", "medium");

        double riskScore = probability * impact;
        String riskLevel;
        if (riskScore >= 0.7) {
            riskLevel = "HIGH";
        } else if (riskScore >= 0.4) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "LOW";
        }

        // 严重程度加权
        double severityMultiplier;
        switch (severity.toLowerCase()) {
            case "critical": severityMultiplier = 1.5; break;
            case "high":     severityMultiplier = 1.2; break;
            case "medium":   severityMultiplier = 1.0; break;
            case "low":      severityMultiplier = 0.8; break;
            default:         severityMultiplier = 1.0; break;
        }
        double weightedScore = Math.min(1.0, riskScore * severityMultiplier);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("probability", probability);
        result.put("impact", impact);
        result.put("rawRiskScore", Math.round(riskScore * 100.0) / 100.0);
        result.put("severity", severity);
        result.put("severityMultiplier", severityMultiplier);
        result.put("weightedRiskScore", Math.round(weightedScore * 100.0) / 100.0);
        result.put("riskLevel", riskLevel);

        return objectMapper.writeValueAsString(result);
    }

    /**
     * 执行子 Agent 委托 — 委托给 {@link AgentDelegationService}。
     * <p>
     * 这是 delegate_to_agent 内置工具的执行入口。
     * 因为需要返回 ToolResult（而非纯 String），不走 dispatch 路径，
     * 直接在外部 execute 方法中通过 ToolResult 返回。
     * </p>
     */
    private String executeDelegate(Map<String, Object> arguments) throws Exception {
        if (agentDelegationService == null) {
            throw new IllegalStateException("AgentDelegationService 不可用，无法执行子Agent委托");
        }
        // delegate 返回 ToolResult，但 executeBuiltin 要求返回 String
        // 将 ToolResult 序列化为 JSON 返回
        ToolResult delegateResult = agentDelegationService.delegate(arguments);
        Map<String, Object> serialized = new LinkedHashMap<>();
        serialized.put("success", delegateResult.isSuccess());
        serialized.put("content", delegateResult.getContent());
        serialized.put("error", delegateResult.getError());
        serialized.put("elapsedMs", delegateResult.getElapsedMs());
        return objectMapper.writeValueAsString(serialized);
    }

    // ── Fallback Tool Definitions ─────────────────────────────────────────

    private void initFallbackTools() {
        registerFallback("getCurrentTime", "获取当前时间", "BUILTIN", null, null);
        registerFallback("calculateRisk", "风险评估计算", "BUILTIN", null, null);
        registerFallback("knowledge-search", "知识库搜索", "API",
            "/api/v1/ecos/knowledge/search", "GET");
        registerFallback("object-query", "对象查询", "API",
            "/api/v1/ecos/objects/{entityCode}", "GET");
        registerFallback("graph-query", "知识图谱查询", "API",
            "/api/v1/ecos/knowledge/graph", "GET");
        registerFallback("ontology-explore", "本体浏览", "API",
            "/api/ontology", "GET");
        registerFallback("delegate_to_agent", "子Agent动态委托", "BUILTIN", null, null);
    }

    private void registerFallback(String code, String name, String toolType,
                                   String endpointUrl, String httpMethod) {
        ToolDefinitionEntry entry = new ToolDefinitionEntry();
        entry.code = code;
        entry.name = name;
        entry.toolType = toolType;
        entry.endpointUrl = endpointUrl;
        entry.httpMethod = httpMethod;
        fallbackTools.put(code, entry);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String resolveUrlParams(String url, Map<String, Object> arguments) {
        if (url.contains("{") && arguments != null) {
            for (Map.Entry<String, Object> e : arguments.entrySet()) {
                url = url.replace("{" + e.getKey() + "}",
                    e.getValue() != null ? e.getValue().toString() : "");
            }
        }
        return url;
    }

    @SuppressWarnings("unchecked")
    private String extractSqlFromSchema(Object schemaObj, Map<String, Object> arguments) {
        if (schemaObj == null) return null;
        try {
            Map<String, Object> schemaMap;
            if (schemaObj instanceof String s) {
                schemaMap = objectMapper.readValue(s, Map.class);
            } else if (schemaObj instanceof Map) {
                schemaMap = (Map<String, Object>) schemaObj;
            } else {
                return null;
            }

            // 直接 sql 字段
            Object sql = schemaMap.get("sql");
            if (sql instanceof String s && !s.isBlank()) {
                return s;
            }

            // 从参数列表构建占位符 SQL（表名 + 参数拼 WHERE）
            List<Map<String, Object>> parameters = (List<Map<String, Object>>) schemaMap.get("parameters");
            Object tableObj = schemaMap.get("table");
            if (tableObj instanceof String table && parameters != null) {
                StringBuilder sb = new StringBuilder("SELECT * FROM ").append(table).append(" WHERE 1=1");
                for (Map<String, Object> param : parameters) {
                    String name = (String) param.get("name");
                    if (arguments.containsKey(name)) {
                        sb.append(" AND ").append(name).append(" = ?");
                    }
                }
                sb.append(" LIMIT 100");
                return sb.toString();
            }
        } catch (Exception e) {
            // schema 本身可能就是 SQL 字符串
            if (schemaObj instanceof String s) return s;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Object> extractParamsFromSchema(Object schemaObj, Map<String, Object> arguments) {
        List<Object> params = new ArrayList<>();
        try {
            Map<String, Object> schemaMap;
            if (schemaObj instanceof String s) {
                schemaMap = objectMapper.readValue(s, Map.class);
            } else if (schemaObj instanceof Map) {
                schemaMap = (Map<String, Object>) schemaObj;
            } else {
                return params;
            }
            List<Map<String, Object>> parameters = (List<Map<String, Object>>) schemaMap.get("parameters");
            if (parameters != null) {
                for (Map<String, Object> param : parameters) {
                    String name = (String) param.get("name");
                    if (arguments.containsKey(name)) {
                        params.add(arguments.get(name));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return params;
    }

    private double getDoubleParam(Map<String, Object> args, String key, double defaultValue) {
        if (args == null) return defaultValue;
        Object val = args.get(key);
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) {
            try { return Double.parseDouble(s); }
            catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private String getStringParam(Map<String, Object> args, String key, String defaultValue) {
        if (args == null) return defaultValue;
        Object val = args.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    // ── Result Factory ────────────────────────────────────────────────────

    private ToolResult buildSuccess(String callId, String toolName, String content, long elapsedMs) {
        return ToolResult.ok(callId, toolName, content, elapsedMs);
    }

    private ToolResult buildError(String callId, String toolName, String errorMessage, long startNs) {
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        return ToolResult.fail(callId, toolName, errorMessage, elapsedMs);
    }

    // ── Inner Types ───────────────────────────────────────────────────────

    /** 工具执行结果 */
    public static class ToolResult {
        private String toolCallId;
        private String toolName;
        private String content;
        private String error;
        private long elapsedMs;
        private boolean success;

        public ToolResult() {}

        public static ToolResult ok(String callId, String toolName, String content, long elapsedMs) {
            ToolResult r = new ToolResult();
            r.toolCallId = callId;
            r.toolName = toolName;
            r.content = content;
            r.elapsedMs = elapsedMs;
            r.success = true;
            return r;
        }

        public static ToolResult fail(String callId, String toolName, String error, long elapsedMs) {
            ToolResult r = new ToolResult();
            r.toolCallId = callId;
            r.toolName = toolName;
            r.error = error;
            r.content = "ERROR: " + error;
            r.elapsedMs = elapsedMs;
            r.success = false;
            return r;
        }

        public String getToolCallId() { return toolCallId; }
        public void setToolCallId(String v) { this.toolCallId = v; }
        public String getToolName() { return toolName; }
        public void setToolName(String v) { this.toolName = v; }
        public String getContent() { return content; }
        public void setContent(String v) { this.content = v; }
        public String getError() { return error; }
        public void setError(String v) { this.error = v; }
        public long getElapsedMs() { return elapsedMs; }
        public void setElapsedMs(long v) { this.elapsedMs = v; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean v) { this.success = v; }

        @Override
        public String toString() {
            return success ? "ToolResult[ok, " + toolName + "]"
                : "ToolResult[fail, " + toolName + ": " + error + "]";
        }
    }

    static class ToolDefinitionEntry {
        String code;
        String name;
        String description;
        String toolType;
        String endpointUrl;
        String httpMethod;
        Object schemaJson;
    }
}
