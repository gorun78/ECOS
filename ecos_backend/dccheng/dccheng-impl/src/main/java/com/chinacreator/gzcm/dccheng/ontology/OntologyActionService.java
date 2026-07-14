package com.chinacreator.gzcm.dccheng.ontology;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 动作设计器业务服务 — 本体动作 CRUD（增强：前置条件/后置效果/权限绑定）
 */
@Service
public class OntologyActionService {

    private static final Logger log = LoggerFactory.getLogger(OntologyActionService.class);
    private static final AtomicInteger ID_SEQ = new AtomicInteger(100);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OntologyRepository repository;

    public OntologyActionService(OntologyRepository repository) {
        this.repository = repository;
    }

    private String nextId() { return "act" + String.valueOf(ID_SEQ.incrementAndGet()); }

    // ═══════════════ Action CRUD ═══════════════════

    public List<Map<String, Object>> listActionsByEntity(String entityId) {
        return repository.findActionsByEntity(entityId).stream()
            .map(this::actionToMap)
            .collect(Collectors.toList());
    }

    public List<Map<String, Object>> listAllActions() {
        return repository.findAllActions().stream()
            .map(this::actionToMap)
            .collect(Collectors.toList());
    }

    public Map<String, Object> getAction(String actionId) {
        return repository.findActionById(actionId).map(this::actionToMap).orElse(null);
    }

    public Map<String, Object> createAction(String entityId, Map<String, Object> body) {
        OntologyAction action = new OntologyAction();
        String id = nextId();
        action.setId(id);
        action.setEntityId(entityId);
        action.setCode(String.valueOf(body.getOrDefault("code", "")));
        action.setName(String.valueOf(body.getOrDefault("name", "")));
        action.setActionType(String.valueOf(body.getOrDefault("actionType", "CUSTOM")));
        action.setDescription(String.valueOf(body.getOrDefault("description", "")));
        // 序列化 preconditions / effects 为 JSON
        action.setPreconditions(toJson(body.get("preconditions")));
        action.setEffects(toJson(body.get("effects")));
        action.setRuleJson(String.valueOf(body.getOrDefault("ruleJson", "")));
        action.setStrategy(String.valueOf(body.getOrDefault("strategy", "SYNC")));
        action.setStatus(String.valueOf(body.getOrDefault("status", "ACTIVE")));
        repository.insertAction(action);
        log.info("Ontology action created: {} [{}] for entity {}", id, action.getCode(), entityId);
        return actionToMap(action);
    }

    public Optional<Map<String, Object>> updateAction(String actionId, Map<String, Object> body) {
        return repository.findActionById(actionId).map(existing -> {
            String code = body.containsKey("code") ? String.valueOf(body.get("code")) : null;
            String name = body.containsKey("name") ? String.valueOf(body.get("name")) : null;
            String actionType = body.containsKey("actionType") ? String.valueOf(body.get("actionType")) : null;
            String description = body.containsKey("description") ? String.valueOf(body.get("description")) : null;
            String preconditions = body.containsKey("preconditions") ? toJson(body.get("preconditions")) : null;
            String effects = body.containsKey("effects") ? toJson(body.get("effects")) : null;
            String ruleJson = body.containsKey("ruleJson") ? String.valueOf(body.get("ruleJson")) : null;
            String strategy = body.containsKey("strategy") ? String.valueOf(body.get("strategy")) : null;
            String status = body.containsKey("status") ? String.valueOf(body.get("status")) : null;
            repository.updateAction(actionId, code, name, actionType, description, preconditions, effects,
                ruleJson, strategy, status);
            return repository.findActionById(actionId).map(this::actionToMap).orElse(null);
        });
    }

    public boolean deleteAction(String actionId) {
        return repository.deleteAction(actionId) > 0;
    }

    /**
     * 测试动作执行流程
     */
    public Map<String, Object> testAction(String actionId) {
        OntologyAction action = repository.findActionById(actionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Action '" + actionId + "' not found"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("actionId", action.getId());
        result.put("code", action.getCode());
        result.put("name", action.getName());
        result.put("actionType", action.getActionType());
        result.put("strategy", action.getStrategy());
        result.put("preconditions", safeParseJson(action.getPreconditions()));
        result.put("effects", safeParseJson(action.getEffects()));
        // 模拟执行步骤
        Map<String, Object> execSteps = new LinkedHashMap<>();
        execSteps.put("1-permissionCheck", "PASSED — assuming 'SalesManager' role");
        execSteps.put("2-preconditionCheck", "PASSED — all preconditions met");
        execSteps.put("3-execution", action.getStrategy().equals("SYNC") ? "SYNC_EXECUTED" : "ASYNC_QUEUED");
        execSteps.put("4-effects", "APPLIED");
        execSteps.put("5-notify", "NOTIFIED roles: [CustomerOwner]");
        result.put("executionSteps", execSteps);
        result.put("testResult", "SUCCESS");
        return result;
    }

    /**
     * 执行审批通过的 Action（占位实现）。
     * <p>当前仅校验 Action 是否存在并返回执行摘要，后续可接入真实执行引擎。</p>
     *
     * @param actionId Action 主键
     * @param payload  执行入参（由调用方传入，可为空）
     * @return 执行结果 Map
     */
    public Map<String, Object> executeAction(String actionId, Map<String, Object> payload) {
        OntologyAction action = repository.findActionById(actionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Action '" + actionId + "' not found"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("actionId", action.getId());
        result.put("code", action.getCode());
        result.put("name", action.getName());
        result.put("actionType", action.getActionType());
        result.put("strategy", action.getStrategy());
        result.put("status", action.getStatus());
        result.put("payload", payload);
        result.put("executed", true);
        result.put("message", "Action executed (placeholder)");
        log.info("Ontology action executed: {} [{}]", action.getId(), action.getCode());
        return result;
    }

    // ═══════════════ Map Converter ═══════════════════

    private Map<String, Object> actionToMap(OntologyAction a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("entityId", a.getEntityId());
        m.put("code", a.getCode());
        m.put("name", a.getName());
        m.put("actionType", a.getActionType());
        m.put("description", a.getDescription());
        m.put("preconditions", safeParseJson(a.getPreconditions()));
        m.put("effects", safeParseJson(a.getEffects()));
        m.put("ruleJson", a.getRuleJson());
        m.put("validationRules", safeParseJson(a.getRuleJson()));
        m.put("strategy", a.getStrategy());
        m.put("status", a.getStatus());
        m.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
        m.put("updatedAt", a.getUpdatedAt() != null ? a.getUpdatedAt().toString() : null);
        return m;
    }

    @SuppressWarnings("unchecked")
    private Object safeParseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return json;
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
}
