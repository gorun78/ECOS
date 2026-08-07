package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.ActionTypeService;
import com.chinacreator.gzcm.engine.ontology.engine.PostActionExecutor;
import com.chinacreator.gzcm.engine.ontology.engine.PreconditionEngine;
import com.chinacreator.gzcm.engine.ontology.model.ActionType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ActionTypeServiceImpl — CRUD 基于 JdbcTemplate，execute 委托 PreconditionEngine + PostActionExecutor。
 */
@Service
public class ActionTypeServiceImpl implements ActionTypeService {

    private static final Logger log = LoggerFactory.getLogger(ActionTypeServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final PreconditionEngine preconditionEngine;
    private final PostActionExecutor postActionExecutor;

    public ActionTypeServiceImpl(JdbcTemplate jdbc,
                                 PreconditionEngine preconditionEngine,
                                 PostActionExecutor postActionExecutor) {
        this.jdbc = jdbc;
        this.preconditionEngine = preconditionEngine;
        this.postActionExecutor = postActionExecutor;
    }

    // ── RowMapper ──────────────────────────────────────

    private final RowMapper<ActionType> ROW_MAPPER = (rs, rn) -> {
        ActionType a = new ActionType();
        a.setId(rs.getString("id"));
        a.setName(rs.getString("name"));
        a.setDescription(rs.getString("description"));
        a.setObjectTypeId(rs.getString("object_type_id"));
        a.setPreconditions(rs.getString("preconditions"));
        a.setPostActions(rs.getString("post_actions"));
        a.setAuditRequired(rs.getBoolean("audit_required"));
        a.setEnabled(rs.getBoolean("enabled"));
        a.setCreatedBy(rs.getString("created_by"));
        a.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        a.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        return a;
    };

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    // ── CRUD ───────────────────────────────────────────

    @Override
    public List<ActionType> listActionTypes(String objectTypeId) {
        if (objectTypeId != null && !objectTypeId.isBlank()) {
            return jdbc.query(
                "SELECT * FROM ecos_action_type WHERE object_type_id = ? ORDER BY created_at DESC",
                ROW_MAPPER, objectTypeId);
        }
        return jdbc.query("SELECT * FROM ecos_action_type ORDER BY created_at DESC", ROW_MAPPER);
    }

    @Override
    public Optional<ActionType> getActionType(String id) {
        List<ActionType> list = jdbc.query(
            "SELECT * FROM ecos_action_type WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public ActionType createActionType(ActionType body) {
        if (body.getId() == null || body.getId().isBlank()) {
            body.setId("act_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        jdbc.update("""
            INSERT INTO ecos_action_type (id, name, description, object_type_id,
                preconditions, post_actions, audit_required, enabled, created_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """,
            body.getId(), body.getName(), body.getDescription(), body.getObjectTypeId(),
            body.getPreconditions(), body.getPostActions(),
            body.getAuditRequired() != null ? body.getAuditRequired() : true,
            body.getEnabled() != null ? body.getEnabled() : true,
            body.getCreatedBy());
        log.info("ActionType created: {} [{}]", body.getId(), body.getName());
        return getActionType(body.getId()).orElse(body);
    }

    @Override
    public ActionType updateActionType(String id, ActionType body) {
        ActionType existing = getActionType(id).orElse(null);
        if (existing == null) return null;

        String name = body.getName() != null ? body.getName() : existing.getName();
        String desc = body.getDescription() != null ? body.getDescription() : existing.getDescription();
        String objTypeId = body.getObjectTypeId() != null ? body.getObjectTypeId() : existing.getObjectTypeId();
        String pre = body.getPreconditions() != null ? body.getPreconditions() : existing.getPreconditions();
        String post = body.getPostActions() != null ? body.getPostActions() : existing.getPostActions();
        Boolean audit = body.getAuditRequired() != null ? body.getAuditRequired() : existing.getAuditRequired();
        Boolean enabled = body.getEnabled() != null ? body.getEnabled() : existing.getEnabled();

        jdbc.update("""
            UPDATE ecos_action_type SET
                name = ?, description = ?, object_type_id = ?,
                preconditions = ?, post_actions = ?,
                audit_required = ?, enabled = ?,
                updated_at = NOW()
            WHERE id = ?
            """,
            name, desc, objTypeId, pre, post, audit, enabled, id);
        log.info("ActionType updated: {}", id);
        return getActionType(id).orElse(existing);
    }

    @Override
    public boolean deleteActionType(String id) {
        int rows = jdbc.update("DELETE FROM ecos_action_type WHERE id = ?", id);
        if (rows > 0) {
            log.info("ActionType deleted: {}", id);
        }
        return rows > 0;
    }

    // ── execute ────────────────────────────────────────

    @Override
    public Map<String, Object> executeAction(String actionId, Map<String, Object> payload) {
        ActionType action = getActionType(actionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: ActionType '" + actionId + "' not found"));

        String objectId = String.valueOf(payload.getOrDefault("objectId", ""));
        @SuppressWarnings("unchecked")
        Map<String, Object> context = payload.containsKey("context")
            ? (Map<String, Object>) payload.get("context")
            : new LinkedHashMap<>();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("actionId", actionId);
        result.put("objectId", objectId);

        // 1. 前置条件检查
        Map<String, Object> preconditionResult = preconditionEngine.check(action, objectId, context);
        result.put("preconditionCheck", preconditionResult);

        // 2. 执行
        Map<String, Object> execution = new LinkedHashMap<>();
        boolean preconditionsPassed = (boolean) preconditionResult.getOrDefault("passed", false);
        if (preconditionsPassed) {
            execution = executeActionInternal(action, objectId, context);
            result.put("execution", execution);
            // 3. 后置动作（异步）
            postActionExecutor.execute(action, objectId, context, execution);
        } else {
            execution.put("success", false);
            execution.put("message", "Preconditions not met");
            execution.put("changes", List.of());
            result.put("execution", execution);
        }
        result.put("postActions", postActionExecutor.getPostActionResults());
        result.put("auditId", "audit_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));

        log.info("ActionType executed: {} on object={}, passed={}",
            actionId, objectId, preconditionsPassed);
        return result;
    }

    private Map<String, Object> executeActionInternal(ActionType action, String objectId,
                                                       Map<String, Object> context) {
        Map<String, Object> exec = new LinkedHashMap<>();
        exec.put("success", true);
        exec.put("changes", List.of());
        exec.put("message", "Action executed: " + action.getName());
        return exec;
    }
}
