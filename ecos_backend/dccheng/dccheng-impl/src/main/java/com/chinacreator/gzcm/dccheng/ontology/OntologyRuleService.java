package com.chinacreator.gzcm.dccheng.ontology;

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
 * 规则业务服务 — Rule CRUD + 测试/批量评估
 */
@Service
public class OntologyRuleService {

    private static final Logger log = LoggerFactory.getLogger(OntologyRuleService.class);
    private static final AtomicInteger ID_SEQ = new AtomicInteger(1000);

    private final OntologyRuleRepository repository;

    public OntologyRuleService(OntologyRuleRepository repository) {
        this.repository = repository;
    }

    private String nextId() { return "rule" + ID_SEQ.incrementAndGet(); }

    public List<Map<String, Object>> listRulesByEntity(String entityId) {
        return repository.findByEntity(entityId).stream().map(this::toMap).collect(Collectors.toList());
    }

    public List<Map<String, Object>> listAllRules() {
        return repository.findAll().stream().map(this::toMap).collect(Collectors.toList());
    }

    public Map<String, Object> getRule(String ruleId) {
        return repository.findById(ruleId).map(this::toMap).orElse(null);
    }

    public Map<String, Object> createRule(String entityId, Map<String, Object> body) {
        OntologyRule rule = new OntologyRule();
        rule.setId(nextId());
        rule.setEntityId(entityId);
        rule.setCode(String.valueOf(body.getOrDefault("code", "")));
        rule.setName(String.valueOf(body.getOrDefault("name", "")));
        rule.setRuleType(String.valueOf(body.getOrDefault("ruleType", "VALIDATION")));
        rule.setExpression(String.valueOf(body.getOrDefault("expression", "")));
        rule.setAction(String.valueOf(body.getOrDefault("action", "")));
        rule.setPriority(toInt(body.getOrDefault("priority", 0)));
        rule.setEnabled(toInt(body.getOrDefault("enabled", 1)));
        rule.setDescription(String.valueOf(body.getOrDefault("description", "")));
        repository.insert(rule);
        log.info("Rule created: {} [{}] type={} for entity {}", rule.getId(), rule.getCode(), rule.getRuleType(), entityId);
        return toMap(rule);
    }

    public Optional<Map<String, Object>> updateRule(String ruleId, Map<String, Object> body) {
        return repository.findById(ruleId).map(existing -> {
            String code = body.containsKey("code") ? String.valueOf(body.get("code")) : null;
            String name = body.containsKey("name") ? String.valueOf(body.get("name")) : null;
            String ruleType = body.containsKey("ruleType") ? String.valueOf(body.get("ruleType")) : null;
            String expression = body.containsKey("expression") ? String.valueOf(body.get("expression")) : null;
            String action = body.containsKey("action") ? String.valueOf(body.get("action")) : null;
            Integer priority = body.containsKey("priority") ? toInt(body.get("priority")) : null;
            Integer enabled = body.containsKey("enabled") ? toInt(body.get("enabled")) : null;
            String description = body.containsKey("description") ? String.valueOf(body.get("description")) : null;
            repository.update(ruleId, code, name, ruleType, expression, action, priority, enabled, description);
            return repository.findById(ruleId).map(this::toMap).orElse(null);
        });
    }

    public boolean deleteRule(String ruleId) {
        return repository.delete(ruleId) > 0;
    }

    /**
     * 测试单条规则：尝试解析表达式，返回解析结果
     */
    public Map<String, Object> testRule(String ruleId) {
        OntologyRule rule = repository.findById(ruleId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Rule '" + ruleId + "' not found"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruleId", rule.getId());
        result.put("code", rule.getCode());
        result.put("expression", rule.getExpression());
        result.put("parsable", tryParseExpression(rule.getExpression()));
        result.put("ruleType", rule.getRuleType());
        result.put("enabled", rule.getEnabled() == 1);
        return result;
    }

    /**
     * 批量评估：给定实体 ID 列表，返回每条规则对该实体的评估结果
     */
    public List<Map<String, Object>> evaluateRules(List<String> entityIds) {
        return entityIds.stream().flatMap(entityId ->
            repository.findByEntity(entityId).stream().filter(r -> r.getEnabled() == 1).map(rule -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ruleId", rule.getId());
                m.put("entityId", entityId);
                m.put("code", rule.getCode());
                m.put("expression", rule.getExpression());
                m.put("ruleType", rule.getRuleType());
                m.put("parsable", tryParseExpression(rule.getExpression()));
                return m;
            })
        ).collect(Collectors.toList());
    }

    private boolean tryParseExpression(String expr) {
        if (expr == null || expr.isBlank()) return false;
        // 简单语法检查：支持 comparison / boolean / arithmetic 表达式
        String trimmed = expr.replaceAll("\\s+", " ").trim();
        return trimmed.contains(">") || trimmed.contains("<") || trimmed.contains("=") ||
               trimmed.contains("AND") || trimmed.contains("OR") || trimmed.contains("NOT") ||
               trimmed.startsWith("SET(");
    }

    private Map<String, Object> toMap(OntologyRule r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("entityId", r.getEntityId());
        m.put("code", r.getCode());
        m.put("name", r.getName());
        m.put("ruleType", r.getRuleType());
        m.put("expression", r.getExpression());
        m.put("action", r.getAction());
        m.put("priority", r.getPriority());
        m.put("enabled", r.getEnabled());
        m.put("description", r.getDescription());
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        m.put("updatedAt", r.getUpdatedAt() != null ? r.getUpdatedAt().toString() : null);
        return m;
    }

    private static int toInt(Object val) {
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(String.valueOf(val)); } catch (Exception e) { return 0; }
    }
}
