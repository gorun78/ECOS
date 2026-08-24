package com.chinacreator.gzcm.engine.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

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

    // R1.3: SecuritySandboxService 位于 security-engine-impl（编译期不可见），
    // 通过 ApplicationContext + 反射调用，不可用时降级为放行（但记录警告日志）
    @Autowired(required = false)
    private ApplicationContext applicationContext;

    /** R1.3: 高危指令正则 — DELETE/DROP/TRUNCATE/ALTER TABLE/EXEC/RM/MV/批量UPDATE/GRANT/REVOKE */
    private static final Pattern HIGH_RISK_PATTERN = Pattern.compile(
            "(?i).*(DELETE|DROP|TRUNCATE|ALTER\\s+TABLE|EXEC|RM\\s|MV\\s|UPDATE\\s+.*WHERE|GRANT|REVOKE).*");

    @Autowired(required = false)
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 使用 @Lazy 打破 AgentDelegationService → AgentLoopService → ToolExecutorService 循环依赖
    @org.springframework.context.annotation.Lazy
    @Autowired(required = false)
    private AgentDelegationService agentDelegationService;

    /** 工具注册中心 — 优先走注册表，未命中时回退到 DB/fallback 逻辑 */
    @org.springframework.context.annotation.Lazy
    @Autowired
    private ToolRegistry toolRegistry;

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

        // ── R1.3: 高危指令沙盒审查 ──
        ToolResult sandboxBlock = sandboxReview(toolName, arguments, callId, startNs);
        if (sandboxBlock != null) {
            return sandboxBlock;
        }

        // 优先走 ToolRegistry（统一工具管理）
        if (toolRegistry != null && toolRegistry.has(toolName)) {
            return toolRegistry.execute(toolName, arguments);
        }

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

    // ── R1.3 高危指令沙盒审查 ────────────────────────────────────────────

    /**
     * R1.3: 工具执行前沙盒审查。
     * <ol>
     *   <li>查询当前用户的 td_user_security_profile.sandbox_mandatory</li>
     *   <li>sandbox_mandatory=true 时，检查指令是否高危（正则匹配）</li>
     *   <li>高危指令 → 调 SecuritySandboxService 审查 → 高风险 → 拒绝 + 写审计日志</li>
     *   <li>非高危或 sandbox_mandatory=false → 放行（返回 null）</li>
     * </ol>
     *
     * @return 拒绝执行的 ToolResult，或 null 表示放行
     */
    private ToolResult sandboxReview(String toolName, Map<String, Object> arguments,
                                     String callId, long startNs) {
        try {
            String userId = getCurrentUserId();
            if (userId == null || jdbcTemplate == null) {
                return null; // 无用户上下文或无DB → 放行
            }

            // 1. 查询 sandbox_mandatory
            Boolean sandboxMandatory = querySandboxMandatory(userId);
            if (sandboxMandatory == null || !sandboxMandatory) {
                return null; // 未强制沙盒 → 放行
            }

            // 2. 拼接待审查的指令文本（工具名 + 参数，尤其 sql/command 字段）
            String instruction = buildInstructionText(toolName, arguments);
            if (instruction == null || instruction.isBlank()) {
                return null;
            }

            // 3. 检查是否高危
            if (!HIGH_RISK_PATTERN.matcher(instruction).matches()) {
                return null; // 非高危 → 放行
            }

            // 4. 高危指令 → 调 SecuritySandboxService 审查
            Boolean highRisk = reviewBySandboxService(instruction, userId);
            if (highRisk == null) {
                // SecuritySandboxService 不可用 → 降级放行，但记录警告日志
                log.warn("R1.3 SecuritySandboxService 不可用，高危指令降级放行: tool={}, user={}, instruction={}",
                        toolName, userId, truncate(instruction, 200));
                return null;
            }

            if (highRisk) {
                // 高风险 → 拒绝执行 + 写审计日志
                log.warn("R1.3 沙盒拦截高危指令: tool={}, user={}, instruction={}",
                        toolName, userId, truncate(instruction, 200));
                writeSandboxBlockAudit(userId, toolName, instruction);
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                return ToolResult.fail(callId, toolName,
                        "高危指令被沙盒拦截（sandbox_block）：指令包含敏感操作，需管理员审批", elapsedMs);
            }

            // 审查通过 → 放行
            return null;
        } catch (Exception e) {
            log.warn("R1.3 沙盒审查异常，降级放行: tool={}, error={}", toolName, e.getMessage());
            return null;
        }
    }

    /**
     * R1.3: 从 SecurityContext 获取当前用户 ID。
     */
    private String getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                Object principal = auth.getPrincipal();
                if (principal instanceof String s) return s;
                if (principal != null) return principal.toString();
            }
        } catch (Exception e) {
            log.debug("R1.3 获取当前用户失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * R1.3: 查询用户的 sandbox_mandatory 配置。
     *
     * @return true=强制沙盒, false=不强制, null=查询失败/无配置
     */
    private Boolean querySandboxMandatory(String userId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT sandbox_mandatory FROM td_user_security_profile " +
                    "WHERE scope_type = 'user' AND user_id = ? LIMIT 1",
                    userId);
            if (rows.isEmpty() || rows.get(0).get("sandbox_mandatory") == null) {
                return false;
            }
            return ((Boolean) rows.get(0).get("sandbox_mandatory"));
        } catch (Exception e) {
            log.debug("R1.3 查询 sandbox_mandatory 失败 user={}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * R1.3: 拼接待审查的指令文本（工具名 + 参数中的 sql/command/query 字段）。
     */
    private String buildInstructionText(String toolName, Map<String, Object> arguments) {
        StringBuilder sb = new StringBuilder(toolName != null ? toolName : "");
        if (arguments != null) {
            for (String key : new String[]{"sql", "command", "query", "cmd", "statement", "script"}) {
                Object val = arguments.get(key);
                if (val != null) {
                    sb.append(' ').append(val);
                }
            }
            // 若无显式指令字段，拼接全部参数值
            if (sb.length() <= (toolName != null ? toolName.length() : 0)) {
                for (Object val : arguments.values()) {
                    if (val != null) sb.append(' ').append(val);
                }
            }
        }
        return sb.toString();
    }

    /**
     * R1.3: 调用 SecuritySandboxService 审查指令风险。
     * <p>SecuritySandboxService 位于 security-engine-impl（编译期不可见），
     * 通过 ApplicationContext 查找 bean 后反射调用 evaluateFilter，
     * 返回 allowed=false 即视为高风险。
     *
     * @return true=高风险(拒绝), false=低风险(放行), null=服务不可用(降级放行)
     */
    @SuppressWarnings("unchecked")
    private Boolean reviewBySandboxService(String instruction, String userId) {
        if (applicationContext == null) {
            return null;
        }
        try {
            Object sandboxService = null;
            Map<String, ?> beans = applicationContext.getBeansOfType(Object.class);
            for (Map.Entry<String, ?> entry : beans.entrySet()) {
                if (entry.getValue().getClass().getName().endsWith("SecuritySandboxService")) {
                    sandboxService = entry.getValue();
                    break;
                }
            }
            if (sandboxService == null) {
                return null; // 服务不可用 → 降级
            }
            // 调用 evaluateFilter(String expression, Map rowData, String userRole)
            // expression 传入高危指令标记，userRole 传 userId，让沙盒策略评估是否允许
            Method evalMethod = sandboxService.getClass().getMethod(
                    "evaluateFilter", String.class, Map.class, String.class);
            Map<String, Object> rowData = Map.of("instruction", instruction, "classification", "RESTRICTED");
            Object result = evalMethod.invoke(sandboxService, "command contains blocked", rowData, userId);
            if (result instanceof Map<?, ?> m) {
                Object allowed = m.get("allowed");
                if (allowed instanceof Boolean b) {
                    return !b; // allowed=false → 高风险
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("R1.3 SecuritySandboxService 审查降级: {}", e.getMessage());
            return null;
        }
    }

    /**
     * R1.3: 写入沙盒拦截审计日志到 ecos_audit_log。
     */
    private void writeSandboxBlockAudit(String userId, String toolName, String instruction) {
        if (jdbcTemplate == null) return;
        try {
            String changesJson = String.format(
                    "{\"userId\":\"%s\",\"toolName\":\"%s\",\"instruction\":\"%s\",\"action\":\"sandbox_block\",\"result\":\"FAILURE\"}",
                    userId != null ? userId : "",
                    toolName != null ? toolName : "",
                    escapeJson(truncate(instruction, 1000)));
            jdbcTemplate.update(
                    "INSERT INTO ecos_audit_log (username, operation, entity_type, entity_id, changes, ip_address, created_at, category) " +
                    "VALUES (?, ?, ?, ?, CAST(? AS jsonb), NULL, NOW(), ?)",
                    userId,
                    "SANDBOX_BLOCK",
                    "TOOL_EXECUTION",
                    toolName,
                    changesJson,
                    "security");
        } catch (Exception e) {
            log.error("R1.3 沙盒拦截审计日志写入失败: {}", e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "...[truncated]" : s;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
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
