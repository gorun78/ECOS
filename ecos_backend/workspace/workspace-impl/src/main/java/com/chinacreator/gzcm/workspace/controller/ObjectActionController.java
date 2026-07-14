package com.chinacreator.gzcm.workspace.controller;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.service.ObjectRuntimeService;
import com.chinacreator.gzcm.dccheng.ontology.ActionHookExecutor;

/**
 * 对象 Action 执行 Controller — 对对象实例触发 Ontology Action。
 *
 * <p>Sprint 1 支持两种执行类型：
 * <ul>
 *   <li><b>Script</b> — 执行 Groovy/JavaScript 脚本</li>
 *   <li><b>API_CALL</b> — 发起 HTTP API 调用</li>
 * </ul>
 *
 * <h3>端点</h3>
 * <pre>
 * POST /api/v1/ecos/objects/{entityCode}/{id}/actions/{actionCode} — 对对象执行 Action
 * GET  /api/v1/ecos/objects/{entityCode}/{id}/actions              — 查询可用 Action 列表
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/ecos/objects")
public class ObjectActionController {

    private static final Logger log = LoggerFactory.getLogger(ObjectActionController.class);

    private final JdbcTemplate jdbc;
    private final ObjectRuntimeService runtimeService;
    private final ActionHookExecutor hookExecutor;

    public ObjectActionController(JdbcTemplate jdbc, ObjectRuntimeService runtimeService,
                                   ActionHookExecutor hookExecutor) {
        this.jdbc = jdbc;
        this.runtimeService = runtimeService;
        this.hookExecutor = hookExecutor;
    }

    // ═══════════════ Action 执行 ═══════════════════

    /**
     * POST /{entityCode}/{id}/actions/{actionCode}
     * 对指定对象执行 Ontology Action。
     *
     * Request body (optional):
     * {
     *   "params": {"comment": "...", "nextAssignee": "..."},
     *   "actor": "user001"
     * }
     */
    @PostMapping("/{entityCode}/{id}/actions/{actionCode}")
    public ApiResponse<Map<String, Object>> executeAction(
            @PathVariable String entityCode,
            @PathVariable String id,
            @PathVariable String actionCode,
            @RequestBody(required = false) Map<String, Object> body) {

        if (body == null) body = Map.of();

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.getOrDefault("params", Map.of());
        String actor = (String) body.getOrDefault("actor", "system");

        // 1. 查找 Action 定义（从 ecos_ontology_action 表）
        List<Map<String, Object>> actions = findActionByCode(actionCode);
        if (actions.isEmpty()) {
            return ApiResponse.notFound("OBJ-003: Action " + actionCode + " 不存在");
        }

        Map<String, Object> actionDef = actions.get(0);
        String actionName = (String) actionDef.getOrDefault("name", actionCode);
        String actionStatus = (String) actionDef.getOrDefault("status", "ACTIVE");
        String actionType = (String) actionDef.getOrDefault("action_type", "SCRIPT");

        if (!"ACTIVE".equals(actionStatus)) {
            return ApiResponse.badRequest("OBJ-003: Action " + actionCode + " 当前不可执行（状态: " + actionStatus + "）");
        }

        // ★ Pre-Hook: VALIDATION
        Map<String, Object> validationError = hookExecutor.executePreHooks(actionDef, params);
        if (validationError != null) {
            int errCode = validationError.containsKey("code") ? ((Number) validationError.get("code")).intValue() : 400;
            String errMsg = (String) validationError.getOrDefault("message", "Validation failed");
            return ApiResponse.error(errCode, errMsg);
        }

        // 2. 验证对象是否存在
        String table = ObjectRuntimeService.ENTITY_TABLE.get(entityCode);
        if (table != null) {
            try {
                List<Map<String, Object>> obj = jdbc.queryForList(
                    "SELECT id FROM " + table + " WHERE id = ?", id);
                if (obj.isEmpty()) {
                    return ApiResponse.notFound("OBJ-001: 对象 " + entityCode + "/" + id + " 不存在");
                }
            } catch (Exception e) {
                log.debug("Cannot verify object existence for {}/{}: {}", entityCode, id, e.getMessage());
            }
        }

        // 3. 根据 action_type 执行
        String executionResult;
        if ("SCRIPT".equalsIgnoreCase(actionType)) {
            executionResult = executeScript(actionDef, params);
        } else if ("API_CALL".equalsIgnoreCase(actionType)) {
            executionResult = executeApiCall(actionDef, params);
        } else {
            return ApiResponse.badRequest("OBJ-003: Action 类型 " + actionType + " 暂不支持（Sprint 1 仅支持 SCRIPT/API_CALL）");
        }

        // 4. 记录执行结果，发布事件
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("action", actionCode);
        details.put("actionName", actionName);
        details.put("actionType", actionType);
        details.put("executionResult", executionResult);
        details.put("params", params);

        runtimeService.recordTimeline(id, entityCode, "ActionExecuted",
            actionName + " 执行" + ("success".equals(executionResult) ? "成功" : " — " + executionResult),
            actor, details);

        runtimeService.publishActionExecuted(id, entityCode, actionCode,
            "success".equals(executionResult) ? "success" : "failed", params);

        // ★ Post-Hook: NOTIFICATION + AUDIT_LOG
        String currentUserId = getCurrentUserId();
        hookExecutor.executePostHooks(actionDef, params, executionResult, entityCode, currentUserId);

        // 5. 构建响应
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", actionCode);
        result.put("actionName", actionName);
        result.put("actionType", actionType);
        result.put("status", "success".equals(executionResult) ? "executed" : "failed");
        result.put("executionResult", executionResult);
        result.put("objectId", id);
        result.put("entityCode", entityCode);
        result.put("executedAt", java.time.Instant.now().toString());
        result.put("executedBy", actor);
        result.put("events", List.of("ActionExecuted"));

        log.info("Action {} ({}) executed on {}/{} by {}: {}", actionCode, actionType, entityCode, id, actor, executionResult);
        return ApiResponse.success(result);
    }

    // ═══════════════ Script 执行 ═══════════════════

    /**
     * 执行 Script 类型的 Action。
     * 使用 JSR-223 ScriptEngine（支持 Groovy/JavaScript）。
     */
    private String executeScript(Map<String, Object> actionDef, Map<String, Object> params) {
        String strategy = (String) actionDef.getOrDefault("strategy", "");
        if (strategy == null || strategy.isEmpty()) {
            log.warn("Script action has no strategy/script content");
            return "success"; // No-op script is treated as success
        }

        try {
            ScriptEngineManager manager = new ScriptEngineManager();
            // Try Groovy first, fall back to JavaScript (Nashorn/GraalVM)
            ScriptEngine engine = manager.getEngineByName("groovy");
            if (engine == null) {
                engine = manager.getEngineByName("javascript");
            }
            if (engine == null) {
                engine = manager.getEngineByName("nashorn");
            }

            if (engine != null) {
                // Inject params and context into script bindings
                engine.put("params", params);
                engine.put("actionDef", actionDef);

                Object evalResult = engine.eval(strategy);
                log.info("Script executed successfully, result: {}", evalResult);
                return "success";
            } else {
                log.warn("No script engine available (Groovy/JS), treating as success");
                return "success"; // Graceful degradation
            }
        } catch (Exception e) {
            log.error("Script execution failed: {}", e.getMessage());
            return "script_error: " + e.getMessage();
        }
    }

    // ═══════════════ API Call 执行 ═══════════════════

    /**
     * 执行 API_CALL 类型的 Action。
     * 使用 java.net.HttpURLConnection 发起 HTTP 调用。
     */
    private String executeApiCall(Map<String, Object> actionDef, Map<String, Object> params) {
        String strategy = (String) actionDef.getOrDefault("strategy", "");
        if (strategy == null || strategy.isEmpty()) {
            log.warn("API call action has no strategy/URL configured");
            return "api_error: 未配置 API URL";
        }

        // Parse strategy: expected format is "METHOD|URL" or just "URL" (default GET)
        String method = "GET";
        String url = strategy.trim();

        if (url.contains("|")) {
            String[] parts = url.split("\\|", 2);
            method = parts[0].trim().toUpperCase();
            url = parts[1].trim();
        }

        // Substitute params in URL (e.g., {objectId} → actual value)
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            url = url.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Content-Type", "application/json");

            if (!"GET".equals(method) && !"DELETE".equals(method)) {
                conn.setDoOutput(true);
                // Send params as JSON body
                StringBuilder jsonBody = new StringBuilder("{");
                boolean first = true;
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    if (!first) jsonBody.append(", ");
                    jsonBody.append("\"").append(entry.getKey()).append("\": \"")
                            .append(entry.getValue()).append("\"");
                    first = false;
                }
                jsonBody.append("}");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }

            int responseCode = conn.getResponseCode();
            conn.disconnect();

            if (responseCode >= 200 && responseCode < 300) {
                log.info("API call to {} returned {}", url, responseCode);
                return "success";
            } else {
                log.warn("API call to {} returned {}", url, responseCode);
                return "api_error: HTTP " + responseCode;
            }
        } catch (Exception e) {
            log.error("API call failed: {}", e.getMessage());
            return "api_error: " + e.getMessage();
        }
    }

    // ═══════════════ 可用 Action 查询 ═══════════════════

    /**
     * GET /{entityCode}/{id}/actions
     * 查询指定对象可用的 Action 列表（从 ecos_ontology_action 查询）。
     */
    @GetMapping("/{entityCode}/{id}/actions")
    public ApiResponse<List<Map<String, Object>>> listAvailableActions(
            @PathVariable String entityCode,
            @PathVariable String id) {
        try {
            // Query actions by entity matching entityCode
            List<Map<String, Object>> actions = jdbc.queryForList(
                """
                SELECT a.id, a.entity_id, a.name, a.action_type, a.strategy, a.status, a.created_at
                FROM ecos_ontology_action a
                JOIN ecos_ontology_entity e ON a.entity_id = e.id
                WHERE e.code = ? AND a.status = 'ACTIVE'
                ORDER BY a.created_at
                """, entityCode);
            return ApiResponse.success(actions);
        } catch (Exception e) {
            log.warn("Failed to list actions for {}/{}: {}", entityCode, id, e.getMessage());
            return ApiResponse.success(List.of());
        }
    }

    // ═══════════════ Helper ═══════════════════

    /**
     * 从 SecurityContext 中提取当前用户 ID。
     */
    private String getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String) {
                return (String) auth.getPrincipal();
            }
        } catch (Exception e) {
            log.debug("Cannot get current user from SecurityContext: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 按 action code 查找 Action 定义。
     */
    private List<Map<String, Object>> findActionByCode(String actionCode) {
        try {
            return jdbc.queryForList(
                "SELECT * FROM ecos_ontology_action WHERE name = ? OR id = ? LIMIT 1",
                actionCode, actionCode);
        } catch (Exception e) {
            log.warn("Failed to find action {}: {}", actionCode, e.getMessage());
            return List.of();
        }
    }
}
