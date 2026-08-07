package com.chinacreator.gzcm.engine.ontology.engine;

import com.chinacreator.gzcm.engine.ontology.model.ActionType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * PostActionExecutor — 后置动作异步执行器。
 *
 * <p>支持的 type：</p>
 * <ul>
 *   <li>update_field — 更新对象字段值</li>
 *   <li>notify — 发送通知</li>
 *   <li>trigger_pipeline — 触发流水线</li>
 *   <li>write_audit — 写审计日志</li>
 * </ul>
 *
 * <p>使用 {@code @Async} 异步执行，失败写审计不阻塞主流程。</p>
 */
@Component
public class PostActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(PostActionExecutor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final List<Map<String, Object>> postActionResults = new CopyOnWriteArrayList<>();

    public PostActionExecutor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 异步执行所有后置动作。
     */
    @Async
    public void execute(ActionType action, String objectId,
                         Map<String, Object> context, Map<String, Object> executionResult) {
        postActionResults.clear();

        List<Map<String, Object>> actions = parsePostActions(action.getPostActions());
        if (actions == null || actions.isEmpty()) {
            return;
        }

        for (Map<String, Object> postAction : actions) {
            String type = String.valueOf(postAction.getOrDefault("type", "unknown")).toLowerCase();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", type);
            try {
                switch (type) {
                    case "update_field" -> {
                        String field = String.valueOf(postAction.getOrDefault("field", ""));
                        Object value = postAction.get("value");
                        updateObjectField(objectId, field, value);
                        result.put("status", "applied");
                        result.put("field", field);
                        result.put("value", value);
                    }
                    case "notify" -> {
                        String target = String.valueOf(postAction.getOrDefault("target", "system"));
                        String message = String.valueOf(postAction.getOrDefault("message",
                            "Action '" + action.getName() + "' executed on object " + objectId));
                        sendNotification(target, message);
                        result.put("status", "sent");
                        result.put("target", target);
                    }
                    case "trigger_pipeline" -> {
                        String pipelineId = String.valueOf(postAction.getOrDefault("pipelineId", ""));
                        triggerPipeline(pipelineId, objectId, context);
                        result.put("status", "triggered");
                        result.put("pipelineId", pipelineId);
                    }
                    case "write_audit" -> {
                        String auditId = writeAudit(action, objectId, context, executionResult);
                        result.put("status", "written");
                        result.put("auditId", auditId);
                    }
                    default -> {
                        log.warn("Unknown post action type: {}", type);
                        result.put("status", "skipped");
                    }
                }
            } catch (Exception e) {
                log.error("Post action failed: type={} action={} object={}: {}",
                    type, action.getId(), objectId, e.getMessage());
                result.put("status", "failed");
                result.put("error", e.getMessage());
                // 失败也写审计
                writeAudit(action, objectId, context,
                    Map.of("postActionFailed", true, "type", type, "error", e.getMessage()));
            }
            postActionResults.add(result);
        }
    }

    /**
     * 获取后置动作执行结果快照。
     */
    public List<Map<String, Object>> getPostActionResults() {
        return new ArrayList<>(postActionResults);
    }

    // ── private helpers ────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parsePostActions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            Object parsed = MAPPER.readValue(json, Object.class);
            if (parsed instanceof List) {
                return (List<Map<String, Object>>) parsed;
            } else if (parsed instanceof Map) {
                return List.of((Map<String, Object>) parsed);
            }
        } catch (Exception e) {
            log.warn("Failed to parse post_actions JSON: {}", json, e);
        }
        return List.of();
    }

    private void updateObjectField(String objectId, String field, Object value) {
        if (objectId == null || objectId.isBlank() || field == null || field.isBlank()) return;
        // 安全校验：禁止修改 id / created_at 等系统字段
        String safeField = field.toLowerCase();
        if (List.of("id", "created_at", "created_by").contains(safeField)) {
            log.warn("Skipping update of protected field '{}' on object {}", field, objectId);
            return;
        }
        try {
            jdbc.update(
                "UPDATE ecos_ontology_object SET " + field + " = ?, updated_at = NOW() WHERE id = ?",
                value != null ? value.toString() : null, objectId);
            log.info("Updated field '{}' of object {}", field, objectId);
        } catch (Exception e) {
            log.error("Failed to update field '{}' of object {}: {}", field, objectId, e.getMessage());
        }
    }

    private void sendNotification(String target, String message) {
        log.info("NOTIFICATION [to={}]: {}", target, message);
        // 占位：未来可接入 sys_notification 表或消息队列
    }

    private void triggerPipeline(String pipelineId, String objectId, Map<String, Object> context) {
        log.info("PIPELINE triggered: id={} object={} context={}", pipelineId, objectId, context);
        // 占位：未来可接入 ecos_pipeline 表
    }

    private String writeAudit(ActionType action, String objectId,
                               Map<String, Object> context, Map<String, Object> execResult) {
        String auditId = "audit_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        try {
            String userId = context != null
                ? String.valueOf(context.getOrDefault("userId", "system"))
                : "system";
            jdbc.update("""
                INSERT INTO ecos_audit_log (id, action_type_id, object_id, user_id,
                    audit_type, summary, created_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                """,
                auditId, action.getId(), objectId, userId,
                "ACTION_EXECUTION",
                "Action '" + action.getName() + "' executed on object " + objectId
                    + ": " + execResult.getOrDefault("success", "unknown"));
            log.info("Audit log written: {}", auditId);
        } catch (Exception e) {
            log.error("Failed to write audit log: {}", e.getMessage());
        }
        return auditId;
    }
}
