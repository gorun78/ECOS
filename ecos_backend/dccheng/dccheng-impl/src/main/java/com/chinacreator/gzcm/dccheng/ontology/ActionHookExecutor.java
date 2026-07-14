package com.chinacreator.gzcm.dccheng.ontology;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Action 钩子执行器 — preHook（VALIDATION） + postHook（NOTIFICATION, AUDIT_LOG）。
 *
 * <p>内置三种钩子类型：
 * <ul>
 *   <li><b>VALIDATION</b> — 执行前校验参数，读取 action.ruleJson 中的 validation 规则</li>
 *   <li><b>NOTIFICATION</b> — 执行后发送通知，写入 td_audit_log（event_type="NOTIFICATION"）</li>
 *   <li><b>AUDIT_LOG</b> — 执行后记录审计日志，写入 td_audit_log（event_type="ACTION_EXECUTE"）</li>
 * </ul>
 *
 * <p>关键约束：Hook 执行异常不阻塞主流程（捕获 + log）。
 */
@Component
public class ActionHookExecutor {

    private static final Logger log = LoggerFactory.getLogger(ActionHookExecutor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;

    public ActionHookExecutor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ═══════════════ Public API ═══════════════════

    /**
     * 执行前置钩子（仅 VALIDATION）。
     *
     * @param actionDef Action 定义 Map（来自 ecos_ontology_action 查询结果）
     * @param input     执行参数
     * @return 校验失败时返回 ApiResponse 风格的 Map（含 code 和 message），通过时返回 null
     */
    public Map<String, Object> executePreHooks(Map<String, Object> actionDef, Map<String, Object> input) {
        try {
            return executeValidationHook(actionDef, input);
        } catch (Exception e) {
            log.warn("PreHook VALIDATION failed (non-blocking): {}", e.getMessage());
            return null;
        }
    }

    /**
     * 执行后置钩子（NOTIFICATION + AUDIT_LOG）。
     *
     * @param actionDef       Action 定义 Map
     * @param input           执行参数
     * @param executionResult 执行结果："success" 或其他
     * @param entityCode      实体代码
     * @param userId          当前用户 ID（由 Controller 从 SecurityContext 提取，可为 null）
     */
    public void executePostHooks(Map<String, Object> actionDef, Map<String, Object> input,
                                  String executionResult, String entityCode, String userId) {

        // NOTIFICATION hook
        try {
            executeNotificationHook(actionDef, input, executionResult, entityCode, userId);
        } catch (Exception e) {
            log.warn("PostHook NOTIFICATION failed (non-blocking): {}", e.getMessage());
        }

        // AUDIT_LOG hook
        try {
            executeAuditLogHook(actionDef, input, executionResult, entityCode, userId);
        } catch (Exception e) {
            log.warn("PostHook AUDIT_LOG failed (non-blocking): {}", e.getMessage());
        }
    }

    // ═══════════════ Hook Implementations ═══════════════════

    /**
     * VALIDATION 钩子：从 action.ruleJson 中读取 validation 规则并校验 input 参数。
     *
     * <p>ruleJson 格式示例：
     * <pre>{@code
     * {
     *   "validation": {
     *     "required": ["reason", "amount"],
     *     "rules": [{"field": "amount", "min": 0}]
     *   }
     * }
     * }</pre>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeValidationHook(Map<String, Object> actionDef, Map<String, Object> input) {
        Object ruleJsonRaw = actionDef.get("rule_json");
        if (ruleJsonRaw == null || ruleJsonRaw.toString().isBlank()) {
            return null; // No validation rules configured
        }

        Map<String, Object> ruleJson;
        try {
            ruleJson = MAPPER.readValue(ruleJsonRaw.toString(), Map.class);
        } catch (JsonProcessingException e) {
            log.debug("Cannot parse ruleJson as JSON, skipping validation: {}", e.getMessage());
            return null;
        }

        Map<String, Object> validation = (Map<String, Object>) ruleJson.get("validation");
        if (validation == null) {
            return null; // No validation section
        }

        // Check required fields
        java.util.List<String> required = (java.util.List<String>) validation.get("required");
        if (required != null) {
            for (String field : required) {
                Object value = input.get(field);
                if (value == null || (value instanceof String && ((String) value).isBlank())) {
                    return buildErrorResponse("OBJ-004: 缺少必填参数: " + field);
                }
            }
        }

        // Check field rules
        java.util.List<Map<String, Object>> rules = (java.util.List<Map<String, Object>>) validation.get("rules");
        if (rules != null) {
            for (Map<String, Object> rule : rules) {
                String field = (String) rule.get("field");
                Object rawValue = input.get(field);
                if (rawValue == null) continue;

                // Numeric min/max validation
                if (rule.containsKey("min") || rule.containsKey("max")) {
                    try {
                        double value = rawValue instanceof Number
                            ? ((Number) rawValue).doubleValue()
                            : Double.parseDouble(rawValue.toString());

                        if (rule.containsKey("min") && value < ((Number) rule.get("min")).doubleValue()) {
                            return buildErrorResponse("OBJ-004: 参数 " + field + " 不能小于 " + rule.get("min"));
                        }
                        if (rule.containsKey("max") && value > ((Number) rule.get("max")).doubleValue()) {
                            return buildErrorResponse("OBJ-004: 参数 " + field + " 不能大于 " + rule.get("max"));
                        }
                    } catch (NumberFormatException e) {
                        return buildErrorResponse("OBJ-004: 参数 " + field + " 必须是数字");
                    }
                }
            }
        }

        log.debug("Validation passed for action {}", actionDef.get("name"));
        return null;
    }

    /**
     * NOTIFICATION 钩子：将 Action 执行结果写入 td_audit_log（event_type="NOTIFICATION"）。
     */
    private void executeNotificationHook(Map<String, Object> actionDef, Map<String, Object> input,
                                          String executionResult, String entityCode, String userId) {
        String detailsJson;
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("actionCode", actionDef.getOrDefault("code", actionDef.get("name")));
            detail.put("actionName", actionDef.get("name"));
            detail.put("input", input);
            detail.put("result", executionResult);
            detail.put("entityCode", entityCode);
            detailsJson = MAPPER.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            detailsJson = "{}";
        }

        jdbc.update(
            "INSERT INTO td_audit_log (log_id, event_type, timestamp, user_id, resource, action, result, details) " +
            "VALUES (?, ?, NOW(), ?, ?, ?, ?, ?)",
            UUID.randomUUID().toString(),
            "NOTIFICATION",
            userId != null ? userId : "system",
            entityCode,
            String.valueOf(actionDef.getOrDefault("code", actionDef.get("name"))),
            "success".equals(executionResult) ? "SUCCESS" : "FAILED",
            detailsJson
        );

        log.debug("NOTIFICATION hook recorded for action {}", actionDef.get("name"));
    }

    /**
     * AUDIT_LOG 钩子：记录完整的审计日志到 td_audit_log（event_type="ACTION_EXECUTE"）。
     */
    private void executeAuditLogHook(Map<String, Object> actionDef, Map<String, Object> input,
                                      String executionResult, String entityCode, String userId) {
        String requestDataJson;
        try {
            Map<String, Object> requestData = new LinkedHashMap<>();
            requestData.put("actionCode", actionDef.getOrDefault("code", actionDef.get("name")));
            requestData.put("input", input);
            requestDataJson = MAPPER.writeValueAsString(requestData);
        } catch (JsonProcessingException e) {
            requestDataJson = "{}";
        }

        jdbc.update(
            "INSERT INTO td_audit_log (log_id, event_type, timestamp, user_id, resource, action, result, details) " +
            "VALUES (?, ?, NOW(), ?, ?, ?, ?, ?)",
            UUID.randomUUID().toString(),
            "ACTION_EXECUTE",
            userId != null ? userId : "system",
            entityCode,
            String.valueOf(actionDef.getOrDefault("code", actionDef.get("name"))),
            "success".equals(executionResult) ? "SUCCESS" : "FAILED",
            requestDataJson
        );

        log.info("AUDIT_LOG recorded: action={}, result={}, user={}",
            actionDef.getOrDefault("code", actionDef.get("name")), executionResult, userId);
    }

    // ═══════════════ Helpers ═══════════════════

    private Map<String, Object> buildErrorResponse(String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", 400);
        error.put("message", message);
        return error;
    }
}
