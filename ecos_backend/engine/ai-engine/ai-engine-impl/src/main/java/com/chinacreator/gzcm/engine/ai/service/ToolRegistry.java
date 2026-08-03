package com.chinacreator.gzcm.engine.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工具注册中心 — 统一管理工具 Schema 和执行器。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>注册/查询工具 Schema（用于生成 function-calling 描述给 LLM）</li>
 *   <li>绑定执行器函数并统一执行入口</li>
 *   <li>内置 3 个通用工具：{@code query_db}, {@code invoke_rest}, {@code delegate_to_agent}</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * toolRegistry.register(schema, args -> { ... });
 * ToolExecutorService.ToolResult result = toolRegistry.execute("query_db", args);
 * }</pre>
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, ToolSchema> schemas = new ConcurrentHashMap<>();
    private final Map<String, Function<Map<String, Object>, ToolExecutorService.ToolResult>> executors =
            new ConcurrentHashMap<>();

    private JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;

    @org.springframework.context.annotation.Lazy
    @Autowired(required = false)
    private AgentDelegationService agentDelegationService;

    @Autowired(required = false)
    private FileToolService fileToolService;

    @Autowired(required = false)
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ToolRegistry() {
        this.restTemplate = new RestTemplate();
        initBuiltinTools();
    }

    // ─── Public API ────────────────────────────────────────────────────

    /**
     * 注册一个工具（Schema + 执行器）。
     *
     * @param schema   工具 Schema 定义
     * @param executor 执行器函数，入参 args，返回 ToolResult
     */
    public void register(ToolSchema schema,
                         Function<Map<String, Object>, ToolExecutorService.ToolResult> executor) {
        if (schema == null || schema.getName() == null) {
            throw new IllegalArgumentException("ToolSchema and its name must not be null");
        }
        schemas.put(schema.getName(), schema);
        if (executor != null) {
            executors.put(schema.getName(), executor);
        }
        log.info("[ToolRegistry] Registered tool: {}", schema.getName());
    }

    /**
     * 获取单个工具 Schema。
     */
    public ToolSchema get(String name) {
        return schemas.get(name);
    }

    /**
     * 列出所有已注册工具 Schema。
     */
    public List<ToolSchema> listAll() {
        return new ArrayList<>(schemas.values());
    }

    /**
     * 检查工具是否已注册。
     */
    public boolean has(String name) {
        return schemas.containsKey(name);
    }

    /**
     * 获取所有工具 Schema 的 function-calling 描述列表（供 LLM 使用）。
     */
    public List<Map<String, Object>> getToolSchemas() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolSchema schema : schemas.values()) {
            result.add(schema.toFunctionCallSchema());
        }
        return result;
    }

    /**
     * 执行一个已注册的工具。
     *
     * @param name 工具名称
     * @param args 调用参数
     * @return ToolResult（成功 content 包含 JSON，失败 error 含错误信息）
     */
    public ToolExecutorService.ToolResult execute(String name, Map<String, Object> args) {
        long startNs = System.nanoTime();
        String callId = "reg_" + UUID.randomUUID().toString().substring(0, 8);

        Function<Map<String, Object>, ToolExecutorService.ToolResult> executor = executors.get(name);
        if (executor == null) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            return ToolExecutorService.ToolResult.fail(callId, name,
                    "工具未注册或无执行器: " + name, elapsedMs);
        }

        try {
            ToolExecutorService.ToolResult result = executor.apply(args != null ? args : Collections.emptyMap());
            if (result.getToolCallId() == null) {
                result.setToolCallId(callId);
            }
            if (result.getToolName() == null) {
                result.setToolName(name);
            }
            return result;
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            log.error("[ToolRegistry] Tool execution failed: tool={}, error={}", name, e.getMessage(), e);
            return ToolExecutorService.ToolResult.fail(callId, name,
                    "工具执行异常: " + e.getMessage(), elapsedMs);
        }
    }

    // ─── Built-in Tools ────────────────────────────────────────────────

    private void initBuiltinTools() {
        registerQueryDb();
        registerInvokeRest();
        registerDelegateToAgent();
        registerFileTools();
    }

    /* ── query_db ── */

    private void registerQueryDb() {
        ToolSchema schema = new ToolSchema("query_db", "执行 SQL 查询并返回结果集");
        schema.addParam("sql", new ToolSchema.ParamDef("string", true, "要执行的 SQL 语句（仅支持 SELECT）"));
        schema.addParam("params", new ToolSchema.ParamDef("array", false, "SQL 绑定参数列表"));

        Function<Map<String, Object>, ToolExecutorService.ToolResult> executor = args -> {
            long startNs = System.nanoTime();
            String callId = "qdb_" + UUID.randomUUID().toString().substring(0, 8);

            if (jdbcTemplate == null) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                return ToolExecutorService.ToolResult.fail(callId, "query_db",
                        "JdbcTemplate 不可用（数据库未连接）", elapsedMs);
            }

            String sql = getStringArg(args, "sql");
            if (sql == null || sql.isBlank()) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                return ToolExecutorService.ToolResult.fail(callId, "query_db",
                        "缺少必填参数 sql", elapsedMs);
            }

            // 安全：仅允许 SELECT
            String upper = sql.trim().toUpperCase();
            if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                return ToolExecutorService.ToolResult.fail(callId, "query_db",
                        "仅允许 SELECT / WITH 查询", elapsedMs);
            }

            try {
                Object paramsObj = args.get("params");
                Object[] params;
                if (paramsObj instanceof List<?> list) {
                    params = list.toArray();
                } else {
                    params = new Object[0];
                }

                log.debug("[ToolRegistry] query_db executing: {}", sql);
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

                String content = objectMapper.writeValueAsString(Map.of(
                        "rows", rows,
                        "count", rows.size()
                ));

                // 截断过长结果
                if (content.length() > 2000) {
                    content = content.substring(0, 2000) + "...[truncated]";
                }

                return ToolExecutorService.ToolResult.ok(callId, "query_db", content, elapsedMs);
            } catch (Exception e) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                log.warn("[ToolRegistry] query_db failed: {}", e.getMessage());
                return ToolExecutorService.ToolResult.fail(callId, "query_db",
                        "SQL 执行失败: " + e.getMessage(), elapsedMs);
            }
        };

        register(schema, executor);
    }

    /* ── invoke_rest ── */

    private void registerInvokeRest() {
        ToolSchema schema = new ToolSchema("invoke_rest", "调用 HTTP REST API 接口");
        schema.addParam("url", new ToolSchema.ParamDef("string", true, "请求 URL（支持 {param} 占位符）"));
        schema.addParam("method", new ToolSchema.ParamDef("string", false, "HTTP 方法（GET/POST/PUT/DELETE，默认 POST）"));
        schema.addParam("headers", new ToolSchema.ParamDef("object", false, "请求头，JSON 对象格式"));
        schema.addParam("body", new ToolSchema.ParamDef("object", false, "请求体，JSON 对象格式"));

        Function<Map<String, Object>, ToolExecutorService.ToolResult> executor = args -> {
            long startNs = System.nanoTime();
            String callId = "rest_" + UUID.randomUUID().toString().substring(0, 8);

            String url = getStringArg(args, "url");
            if (url == null || url.isBlank()) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                return ToolExecutorService.ToolResult.fail(callId, "invoke_rest",
                        "缺少必填参数 url", elapsedMs);
            }

            // URL 占位符替换
            url = resolveUrlPlaceholders(url, args);

            String methodStr = getStringArg(args, "method", "POST");
            HttpMethod method;
            try {
                method = HttpMethod.valueOf(methodStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                return ToolExecutorService.ToolResult.fail(callId, "invoke_rest",
                        "不支持的 HTTP 方法: " + methodStr, elapsedMs);
            }

            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                // 自定义 headers
                @SuppressWarnings("unchecked")
                Map<String, Object> customHeaders = (Map<String, Object>) args.get("headers");
                if (customHeaders != null) {
                    for (Map.Entry<String, Object> e : customHeaders.entrySet()) {
                        headers.set(e.getKey(), e.getValue() != null ? e.getValue().toString() : "");
                    }
                }

                HttpEntity<?> entity;
                if (method == HttpMethod.GET || method == HttpMethod.DELETE) {
                    entity = new HttpEntity<>(headers);
                } else {
                    Object body = args.get("body");
                    entity = new HttpEntity<>(body != null ? body : Collections.emptyMap(), headers);
                }

                log.debug("[ToolRegistry] invoke_rest: {} {}", method, url);
                ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

                String bodyStr = response.getBody();
                String content = bodyStr != null ? bodyStr : "";
                if (content.length() > 2000) {
                    content = content.substring(0, 2000) + "...[truncated]";
                }

                return ToolExecutorService.ToolResult.ok(callId, "invoke_rest", content, elapsedMs);
            } catch (Exception e) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                log.warn("[ToolRegistry] invoke_rest failed: url={}, error={}", url, e.getMessage());
                return ToolExecutorService.ToolResult.fail(callId, "invoke_rest",
                        "REST 调用失败: " + e.getMessage(), elapsedMs);
            }
        };

        register(schema, executor);
    }

    /* ── delegate_to_agent ── */

    private void registerDelegateToAgent() {
        ToolSchema schema = new ToolSchema("delegate_to_agent", "将子任务委托给子 Agent 执行");
        schema.addParam("agentName", new ToolSchema.ParamDef("string", true, "子 Agent 名称"));
        schema.addParam("instruction", new ToolSchema.ParamDef("string", true, "委托给子 Agent 的指令"));
        schema.addParam("systemPrompt",
                new ToolSchema.ParamDef("string", false, "自定义 system prompt（可选，为空时自动生成）"));
        schema.addParam("model", new ToolSchema.ParamDef("string", false, "模型名（默认 deepseek-chat）"));

        Function<Map<String, Object>, ToolExecutorService.ToolResult> executor = args -> {
            if (agentDelegationService == null) {
                return ToolExecutorService.ToolResult.fail(
                        "del_" + UUID.randomUUID().toString().substring(0, 8),
                        "delegate_to_agent",
                        "AgentDelegationService 不可用", 0);
            }
            return agentDelegationService.delegate(args);
        };

        register(schema, executor);
    }

    /* ── File Tools (read_file / write_file / search_files / patch) ── */

    private void registerFileTools() {
        registerReadFile();
        registerWriteFile();
        registerSearchFiles();
        registerPatchFile();
    }

    /* ── read_file ── */

    private void registerReadFile() {
        ToolSchema schema = new ToolSchema("read_file", "读取工作区文件内容");
        schema.addParam("path", new ToolSchema.ParamDef("string", true, "相对于工作区的文件路径"));
        schema.addParam("offset", new ToolSchema.ParamDef("number", false, "起始行号（1-based，默认 1）"));
        schema.addParam("limit", new ToolSchema.ParamDef("number", false, "最大读取行数（默认 500）"));

        Function<Map<String, Object>, ToolExecutorService.ToolResult> executor = args -> {
            long startNs = System.nanoTime();
            String callId = "rf_" + UUID.randomUUID().toString().substring(0, 8);

            if (fileToolService == null) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                return ToolExecutorService.ToolResult.fail(callId, "read_file",
                        "FileToolService 不可用", elapsedMs);
            }

            String path = getStringArg(args, "path");
            if (path == null || path.isBlank()) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                return ToolExecutorService.ToolResult.fail(callId, "read_file",
                        "缺少必填参数 path", elapsedMs);
            }

            int offset = getIntArg(args, "offset", 1);
            int limit = getIntArg(args, "limit", 500);

            try {
                FileToolService.ReadFileResult result = fileToolService.readFile(path, offset, limit);
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

                if (!result.isSuccess()) {
                    return ToolExecutorService.ToolResult.fail(callId, "read_file",
                            result.getError(), elapsedMs);
                }

                String content = objectMapper.writeValueAsString(Map.of(
                        "content", result.getContent(),
                        "totalLines", result.getTotalLines(),
                        "returnedLines", result.getReturnedLines()
                ));

                if (content.length() > 2000) {
                    content = content.substring(0, 2000) + "...[truncated]";
                }

                return ToolExecutorService.ToolResult.ok(callId, "read_file", content, elapsedMs);
            } catch (Exception e) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                return ToolExecutorService.ToolResult.fail(callId, "read_file",
                        "读取文件失败: " + e.getMessage(), elapsedMs);
            }
        };

        register(schema, executor);
    }

    /* ── write_file ── */

    private void registerWriteFile() {
        ToolSchema schema = new ToolSchema("write_file", "写入（覆盖）工作区文件内容");
        schema.addParam("path", new ToolSchema.ParamDef("string", true, "相对于工作区的文件路径"));
        schema.addParam("content", new ToolSchema.ParamDef("string", true, "要写入的文件内容"));

        Function<Map<String, Object>, ToolExecutorService.ToolResult> executor = args -> {
            long startNs = System.nanoTime();
            String callId = "wf_" + UUID.randomUUID().toString().substring(0, 8);

            if (fileToolService == null) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                return ToolExecutorService.ToolResult.fail(callId, "write_file",
                        "FileToolService 不可用", elapsedMs);
            }

            String path = getStringArg(args, "path");
            if (path == null || path.isBlank()) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                return ToolExecutorService.ToolResult.fail(callId, "write_file",
                        "缺少必填参数 path", elapsedMs);
            }

            String content = getStringArg(args, "content");
            if (content == null) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                return ToolExecutorService.ToolResult.fail(callId, "write_file",
                        "缺少必填参数 content", elapsedMs);
            }

            try {
                FileToolService.WriteFileResult result = fileToolService.writeFile(path, content);
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

                if (!result.isSuccess()) {
                    return ToolExecutorService.ToolResult.fail(callId, "write_file",
                            result.getError(), elapsedMs);
                }

                String response = objectMapper.writeValueAsString(Map.of(
                        "status", "OK",
                        "path", path
                ));

                return ToolExecutorService.ToolResult.ok(callId, "write_file", response, elapsedMs);
            } catch (Exception e) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                return ToolExecutorService.ToolResult.fail(callId, "write_file",
                        "写入文件失败: " + e.getMessage(), elapsedMs);
            }
        };

        register(schema, executor);
    }

    /* ── search_files ── */

    private void registerSearchFiles() {
        ToolSchema schema = new ToolSchema("search_files", "在工作区内搜索匹配正则模式的文件内容");
        schema.addParam("pattern", new ToolSchema.ParamDef("string", true, "正则表达式搜索模式"));
        schema.addParam("fileGlob", new ToolSchema.ParamDef("string", false, "文件名 glob 过滤（如 *.java），默认 *"));

        Function<Map<String, Object>, ToolExecutorService.ToolResult> executor = args -> {
            long startNs = System.nanoTime();
            String callId = "sf_" + UUID.randomUUID().toString().substring(0, 8);

            if (fileToolService == null) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                return ToolExecutorService.ToolResult.fail(callId, "search_files",
                        "FileToolService 不可用", elapsedMs);
            }

            String pattern = getStringArg(args, "pattern");
            if (pattern == null || pattern.isBlank()) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                return ToolExecutorService.ToolResult.fail(callId, "search_files",
                        "缺少必填参数 pattern", elapsedMs);
            }

            String fileGlob = getStringArg(args, "fileGlob", "*");

            try {
                FileToolService.SearchResult result = fileToolService.searchFiles(pattern, fileGlob);
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

                if (!result.isSuccess()) {
                    return ToolExecutorService.ToolResult.fail(callId, "search_files",
                            result.getError(), elapsedMs);
                }

                List<Map<String, Object>> matches = result.getMatches().stream()
                        .map(m -> Map.<String, Object>of(
                                "file", m.getFile(),
                                "line", m.getLine(),
                                "content", m.getContent()
                        ))
                        .collect(Collectors.toList());

                String content = objectMapper.writeValueAsString(Map.of(
                        "count", matches.size(),
                        "matches", matches
                ));

                if (content.length() > 2000) {
                    content = content.substring(0, 2000) + "...[truncated]";
                }

                return ToolExecutorService.ToolResult.ok(callId, "search_files", content, elapsedMs);
            } catch (Exception e) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                return ToolExecutorService.ToolResult.fail(callId, "search_files",
                        "搜索失败: " + e.getMessage(), elapsedMs);
            }
        };

        register(schema, executor);
    }

    /* ── patch ── */

    private void registerPatchFile() {
        ToolSchema schema = new ToolSchema("patch", "在工作区文件中查找并替换字符串");
        schema.addParam("path", new ToolSchema.ParamDef("string", true, "相对于工作区的文件路径"));
        schema.addParam("oldString", new ToolSchema.ParamDef("string", true, "要查找的旧字符串"));
        schema.addParam("newString", new ToolSchema.ParamDef("string", true, "替换后的新字符串"));

        Function<Map<String, Object>, ToolExecutorService.ToolResult> executor = args -> {
            long startNs = System.nanoTime();
            String callId = "pt_" + UUID.randomUUID().toString().substring(0, 8);

            if (fileToolService == null) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                return ToolExecutorService.ToolResult.fail(callId, "patch",
                        "FileToolService 不可用", elapsedMs);
            }

            String path = getStringArg(args, "path");
            if (path == null || path.isBlank()) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                return ToolExecutorService.ToolResult.fail(callId, "patch",
                        "缺少必填参数 path", elapsedMs);
            }

            String oldString = getStringArg(args, "oldString");
            if (oldString == null || oldString.isEmpty()) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                return ToolExecutorService.ToolResult.fail(callId, "patch",
                        "缺少必填参数 oldString", elapsedMs);
            }

            String newString = getStringArg(args, "newString", "");

            try {
                FileToolService.PatchResult result = fileToolService.patch(path, oldString, newString);
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

                if (!result.isSuccess()) {
                    return ToolExecutorService.ToolResult.fail(callId, "patch",
                            result.getError(), elapsedMs);
                }

                String content = objectMapper.writeValueAsString(Map.of(
                        "status", "OK",
                        "summary", result.getSummary() != null ? result.getSummary() : "补丁已应用"
                ));

                return ToolExecutorService.ToolResult.ok(callId, "patch", content, elapsedMs);
            } catch (Exception e) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                return ToolExecutorService.ToolResult.fail(callId, "patch",
                        "补丁失败: " + e.getMessage(), elapsedMs);
            }
        };

        register(schema, executor);
    }

    // ─── Helpers ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static String getStringArg(Map<String, Object> args, String key) {
        if (args == null) return null;
        Object val = args.get(key);
        return val != null ? val.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private static String getStringArg(Map<String, Object> args, String key, String defaultVal) {
        String val = getStringArg(args, key);
        return val != null ? val : defaultVal;
    }

    private static int getIntArg(Map<String, Object> args, String key, int defaultVal) {
        if (args == null) return defaultVal;
        Object val = args.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); }
            catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }

    /**
     * 替换 URL 中的 {paramName} 占位符。
     */
    private static String resolveUrlPlaceholders(String url, Map<String, Object> args) {
        if (!url.contains("{") || args == null) {
            return url;
        }
        for (Map.Entry<String, Object> e : args.entrySet()) {
            url = url.replace("{" + e.getKey() + "}",
                    e.getValue() != null ? e.getValue().toString() : "");
        }
        return url;
    }
}
