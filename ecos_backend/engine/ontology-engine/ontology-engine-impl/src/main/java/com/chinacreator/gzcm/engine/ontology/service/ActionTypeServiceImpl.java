package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.ActionTypeService;
import com.chinacreator.gzcm.engine.ontology.dao.ActionTypeDao;
import com.chinacreator.gzcm.engine.ontology.engine.PostActionExecutor;
import com.chinacreator.gzcm.engine.ontology.engine.PreconditionEngine;
import com.chinacreator.gzcm.engine.ontology.model.ActionType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ActionTypeServiceImpl — CRUD 委托 {@link ActionTypeDao}，
 * execute 委托 PreconditionEngine + PostActionExecutor。
 */
@Service
public class ActionTypeServiceImpl implements ActionTypeService {

    private static final Logger log = LoggerFactory.getLogger(ActionTypeServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ActionTypeDao actionTypeDao;
    private final PreconditionEngine preconditionEngine;
    private final PostActionExecutor postActionExecutor;

    public ActionTypeServiceImpl(ActionTypeDao actionTypeDao,
                                 PreconditionEngine preconditionEngine,
                                 PostActionExecutor postActionExecutor) {
        this.actionTypeDao = actionTypeDao;
        this.preconditionEngine = preconditionEngine;
        this.postActionExecutor = postActionExecutor;
    }

    // ── CRUD ───────────────────────────────────────────

    @Override
    public List<ActionType> listActionTypes(String objectTypeId) {
        return actionTypeDao.listActionTypes(objectTypeId);
    }

    @Override
    public Optional<ActionType> getActionType(String id) {
        return actionTypeDao.getActionType(id);
    }

    @Override
    public ActionType createActionType(ActionType body) {
        if (body.getId() == null || body.getId().isBlank()) {
            body.setId("act_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        actionTypeDao.createActionType(body);
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

        actionTypeDao.updateActionType(id, name, desc, objTypeId, pre, post, audit, enabled);
        log.info("ActionType updated: {}", id);
        return getActionType(id).orElse(existing);
    }

    @Override
    public boolean deleteActionType(String id) {
        int rows = actionTypeDao.deleteActionType(id);
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
