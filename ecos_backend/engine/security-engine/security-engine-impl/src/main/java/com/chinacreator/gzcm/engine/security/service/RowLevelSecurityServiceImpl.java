package com.chinacreator.gzcm.engine.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service("ecosRlsService")
public class RowLevelSecurityServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(RowLevelSecurityServiceImpl.class);

    private final JdbcTemplate jdbcTemplate;

    public RowLevelSecurityServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 根据 tableName + userId 查询所有匹配的行级安全策略，
     * 按 priority 升序排序，用 AND 合并所有 filter_expr。
     */
    public Map<String, Object> apply(String tableName, String userId) {
        String sql = """
            SELECT filter_expr, priority, policy_name
            FROM ecos_rls_policy
            WHERE table_name = ? AND enabled = true
              AND (user_id = ? OR user_id IS NULL)
              AND (role_id IS NULL OR role_id IN (
                  SELECT "ROLE_ID" FROM TD_USER_ROLE WHERE "USER_ID" = ?
              ))
            ORDER BY priority ASC
            """;

        List<Map<String, Object>> policies;
        try {
            policies = jdbcTemplate.queryForList(sql, tableName, userId, userId);
        } catch (Exception e) {
            log.error("查询RLS策略失败: table={}, userId={}", tableName, userId, e);
            policies = Collections.emptyList();
        }

        if (policies.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("condition", "1=1");
            result.put("params", Collections.emptyMap());
            result.put("policies", Collections.emptyList());
            result.put("tableName", tableName);
            return result;
        }

        List<String> conditions = new ArrayList<>();
        for (Map<String, Object> p : policies) {
            String expr = (String) p.get("filter_expr");
            if (expr != null && !expr.isBlank()) {
                conditions.add("(" + expr + ")");
            }
        }

        String combined = conditions.isEmpty() ? "1=1" : String.join(" AND ", conditions);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("condition", combined);
        result.put("params", Collections.emptyMap());
        result.put("policies", policies.stream()
                .map(m -> {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("name", m.get("policy_name"));
                    p.put("filterExpr", m.get("filter_expr"));
                    p.put("priority", m.get("priority"));
                    return p;
                }).collect(Collectors.toList()));
        result.put("tableName", tableName);
        return result;
    }

    // ── CRUD ──────────────────────────────────────────

    public List<Map<String, Object>> listPolicies(String tableName) {
        String sql;
        Object[] args;
        if (tableName != null && !tableName.isBlank()) {
            sql = "SELECT * FROM ecos_rls_policy WHERE table_name = ? ORDER BY priority ASC, created_at DESC";
            args = new Object[]{tableName};
        } else {
            sql = "SELECT * FROM ecos_rls_policy ORDER BY priority ASC, created_at DESC";
            args = new Object[]{};
        }
        return jdbcTemplate.queryForList(sql, args);
    }

    public Map<String, Object> getPolicy(String id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM ecos_rls_policy WHERE id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> createPolicy(Map<String, Object> body) {
        String id = UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(
            "INSERT INTO ecos_rls_policy (id, policy_name, table_name, filter_expr, role_id, user_id, priority, enabled, description, created_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
            id,
            body.getOrDefault("policyName", ""),
            body.getOrDefault("tableName", ""),
            body.getOrDefault("filterExpr", ""),
            body.getOrDefault("roleId", null),
            body.getOrDefault("userId", null),
            body.getOrDefault("priority", 0),
            body.getOrDefault("enabled", true),
            body.getOrDefault("description", null),
            body.getOrDefault("createdBy", null)
        );
        log.info("RLS策略创建: id={}, name={}", id, body.get("policyName"));
        return getPolicy(id);
    }

    public Map<String, Object> updatePolicy(String id, Map<String, Object> body) {
        Map<String, Object> existing = getPolicy(id);
        if (existing == null) return null;

        jdbcTemplate.update(
            "UPDATE ecos_rls_policy SET policy_name=?, table_name=?, filter_expr=?, role_id=?, user_id=?, priority=?, enabled=?, description=?, updated_at=NOW() WHERE id=?",
            body.getOrDefault("policyName", existing.get("policy_name")),
            body.getOrDefault("tableName", existing.get("table_name")),
            body.getOrDefault("filterExpr", existing.get("filter_expr")),
            body.getOrDefault("roleId", existing.get("role_id")),
            body.getOrDefault("userId", existing.get("user_id")),
            body.getOrDefault("priority", existing.get("priority")),
            body.getOrDefault("enabled", existing.get("enabled")),
            body.getOrDefault("description", existing.get("description")),
            id
        );
        log.info("RLS策略更新: id={}", id);
        return getPolicy(id);
    }

    public boolean deletePolicy(String id) {
        int rows = jdbcTemplate.update("DELETE FROM ecos_rls_policy WHERE id = ?", id);
        if (rows > 0) {
            log.info("RLS策略删除: id={}", id);
        }
        return rows > 0;
    }
}
