package com.chinacreator.gzcm.engine.ontology.engine;

import com.chinacreator.gzcm.engine.ontology.model.ActionType;
import com.chinacreator.gzcm.sysman.abac.model.AbacContext;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPermissionChecker;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPermissionChecker.Decision;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PreconditionEngine — 解析 preconditions JSON 并比对对象当前状态。
 *
 * <p>支持的 op（操作符）：</p>
 * <ul>
 *   <li>eq — 等于</li>
 *   <li>neq — 不等于</li>
 *   <li>in — 属于列表</li>
 *   <li>gt — 大于</li>
 *   <li>lt — 小于</li>
 *   <li>contains — 字符串包含</li>
 *   <li>regex — 正则匹配</li>
 *   <li>hasRole — 角色检查（从 context 中取 userId）</li>
 *   <li>abac — ABAC 权限校验 (T5-2, 使用 IAbacPermissionChecker)</li>
 * </ul>
 */
@Component
public class PreconditionEngine {

    private static final Logger log = LoggerFactory.getLogger(PreconditionEngine.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;

    /** ABAC 权限检查器（可选注入，未配置时 ABAC 操作符宽容通过）。 */
    private final IAbacPermissionChecker abacPermissionChecker;

    public PreconditionEngine(JdbcTemplate jdbc,
                              Optional<IAbacPermissionChecker> abacPermissionChecker) {
        this.jdbc = jdbc;
        this.abacPermissionChecker = abacPermissionChecker.orElse(null);
    }

    /**
     * 检查 ActionType 的前置条件是否满足。
     *
     * @param action   ActionType 定义
     * @param objectId 待操作的业务对象 ID
     * @param context  执行上下文（含 userId 等）
     * @return {passed: bool, checks: [{field, expected, actual, passed}]}
     */
    public Map<String, Object> check(ActionType action, String objectId, Map<String, Object> context) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Map<String, Object>> conditions = parsePreconditions(action.getPreconditions());
        if (conditions == null || conditions.isEmpty()) {
            // 无前置条件 → 直接通过
            result.put("passed", true);
            result.put("checks", List.of());
            return result;
        }

        // 查询对象当前状态（多表尝试：先查 ecos_ontology_object，再查 ecos_ontology 示例）
        Map<String, Object> currentState = fetchObjectState(objectId);

        List<Map<String, Object>> checks = new ArrayList<>();
        boolean allPassed = true;

        for (Map<String, Object> cond : conditions) {
            Map<String, Object> checkResult = evaluateCondition(cond, currentState, context);
            checks.add(checkResult);
            if (!(boolean) checkResult.getOrDefault("passed", false)) {
                allPassed = false;
            }
        }

        result.put("passed", allPassed);
        result.put("checks", checks);
        log.info("Precondition check for action={} object={}: passed={} ({} checks)",
            action.getId(), objectId, allPassed, checks.size());
        return result;
    }

    // ── private helpers ────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parsePreconditions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            Object parsed = MAPPER.readValue(json, Object.class);
            if (parsed instanceof List) {
                return (List<Map<String, Object>>) parsed;
            } else if (parsed instanceof Map) {
                Map<String, Object> single = (Map<String, Object>) parsed;
                return List.of(single);
            }
        } catch (Exception e) {
            log.warn("Failed to parse preconditions JSON: {}", json, e);
        }
        return List.of();
    }

    private Map<String, Object> fetchObjectState(String objectId) {
        if (objectId == null || objectId.isBlank()) return Map.of();
        try {
            // 尝试从 ecos_ontology_object 表查询
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM ecos_ontology_object WHERE id = ?", objectId);
            if (!rows.isEmpty()) return rows.get(0);
        } catch (Exception e) {
            log.debug("ecos_ontology_object not available: {}", e.getMessage());
        }
        // 尝试 ecos_ontology 表
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM ecos_ontology WHERE id = ?", objectId);
            if (!rows.isEmpty()) return rows.get(0);
        } catch (Exception e) {
            log.debug("ecos_ontology not available: {}", e.getMessage());
        }
        return Map.of();
    }

    /**
     * 对单条条件求值。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> evaluateCondition(Map<String, Object> cond,
                                                   Map<String, Object> state,
                                                   Map<String, Object> context) {
        String field = String.valueOf(cond.getOrDefault("field", ""));
        String op = String.valueOf(cond.getOrDefault("op", "eq")).toLowerCase();
        Object expected = cond.get("value");
        Object actual = state.get(field);

        Map<String, Object> check = new LinkedHashMap<>();
        check.put("field", field);
        check.put("expected", expected);
        check.put("actual", actual);

        boolean passed = switch (op) {
            case "eq" -> objectsEqual(actual, expected);
            case "neq" -> !objectsEqual(actual, expected);
            case "in" -> {
                if (expected instanceof List<?> list && actual != null) {
                    yield list.contains(actual.toString());
                }
                yield false;
            }
            case "gt" -> compareValues(actual, expected) > 0;
            case "lt" -> compareValues(actual, expected) < 0;
            case "contains" -> actual != null && expected != null
                && actual.toString().contains(expected.toString());
            case "regex" -> actual != null && expected != null
                && actual.toString().matches(expected.toString());
            case "hasRole" -> {
                // 从 context 中取 userId，通过 security-engine 检查角色
                String userId = String.valueOf(context.getOrDefault("userId", ""));
                yield checkUserRole(userId, String.valueOf(expected));
            }
            case "abac" -> {
                // ★ T5-2: ABAC 权限校验
                // value 为 Map<String,Object>，包含 subject/resource/action/environment
                yield evaluateAbac(cond, context);
            }
            default -> {
                log.warn("Unknown precondition op: {}", op);
                yield false;
            }
        };

        check.put("passed", passed);
        return check;
    }

    private boolean objectsEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.toString().equals(b.toString());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compareValues(Object a, Object b) {
        if (a == null || b == null) return -1;
        try {
            if (a instanceof Comparable && b instanceof Comparable) {
                return ((Comparable) a).compareTo(b);
            }
            return a.toString().compareTo(b.toString());
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 通过 security-engine 的 /api/security/policy/evaluate 检查用户角色（简化实现：DB 查询）。
     */
    private boolean checkUserRole(String userId, String role) {
        if (userId == null || userId.isBlank()) return false;
        try {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role ur " +
                "JOIN sys_role r ON ur.role_id = r.id " +
                "WHERE ur.user_id = ? AND r.code = ?",
                Integer.class, userId, role);
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("Role check skipped (table may not exist): {}", e.getMessage());
            // 宽容模式：表不存在时默认通过
            return true;
        }
    }

    /**
     * ★ T5-2: ABAC 权限校验。
     *
     * <p>condition 中 {@code value} 字段为包含 ABAC 四元组的 Map：
     * <pre>{@code
     * {
     *   "subject": {"role": "admin", "department": "IT"},
     *   "resource": {"type": "ontology_object", "id": "obj-123"},
     *   "action": {"name": "read", "method": "GET"},
     *   "environment": {"time": "2026-08-06"}
     * }
     * }</pre>
     *
     * <p>同时支持从运行时 context 中注入动态值：{@code ${context.userId}} 等占位符。</p>
     */
    @SuppressWarnings("unchecked")
    private boolean evaluateAbac(Map<String, Object> cond, Map<String, Object> context) {
        // ABAC 检查器未配置时，宽容通过
        if (abacPermissionChecker == null) {
            log.debug("ABAC permission checker not configured, allowing by default");
            return true;
        }

        Object value = cond.get("value");
        if (!(value instanceof Map)) {
            log.warn("ABAC condition value must be a Map, got: {}", value);
            return false;
        }

        Map<String, Object> abacSpec = (Map<String, Object>) value;

        // 构建 ABAC 上下文
        AbacContext abacCtx = new AbacContext();

        // 主体属性：从 value.subject 取，并注入运行时 context
        Map<String, Object> subject = resolveAbacSection(
            (Map<String, Object>) abacSpec.getOrDefault("subject", Map.of()), context);
        abacCtx.setSubject(subject);

        // 资源属性
        Map<String, Object> resource = resolveAbacSection(
            (Map<String, Object>) abacSpec.getOrDefault("resource", Map.of()), context);
        abacCtx.setResource(resource);

        // 操作属性
        Map<String, Object> action = resolveAbacSection(
            (Map<String, Object>) abacSpec.getOrDefault("action", Map.of()), context);
        abacCtx.setAction(action);

        // 环境属性
        Map<String, Object> environment = resolveAbacSection(
            (Map<String, Object>) abacSpec.getOrDefault("environment", Map.of()), context);
        abacCtx.setEnvironment(environment);

        try {
            Decision decision = abacPermissionChecker.check(abacCtx);
            log.debug("ABAC evaluation: subject={} resource={} action={} → {}",
                subject, resource, action, decision);
            return decision == Decision.PERMIT;
        } catch (IAbacPermissionChecker.PolicyEvaluationException e) {
            log.warn("ABAC policy evaluation failed: {}", e.getMessage());
            // 评估失败时默认拒绝（安全优先）
            return false;
        }
    }

    /**
     * 解析 ABAC 属性段，替换 ${context.xxx} 占位符为运行时值。
     */
    private Map<String, Object> resolveAbacSection(Map<String, Object> section,
                                                     Map<String, Object> context) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : section.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof String) {
                resolved.put(entry.getKey(), resolvePlaceholders((String) val, context));
            } else {
                resolved.put(entry.getKey(), val);
            }
        }
        // 自动注入 userId（如果 section 未显式指定且 context 中有）
        if (!resolved.containsKey("userId") && context.containsKey("userId")) {
            resolved.put("userId", context.get("userId"));
        }
        return resolved;
    }

    /**
     * 替换字符串中的 ${context.xxx} 占位符。
     */
    private String resolvePlaceholders(String template, Map<String, Object> context) {
        if (template == null || !template.contains("${")) return template;
        String result = template;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            result = result.replace("${context." + entry.getKey() + "}",
                entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return result;
    }
}
