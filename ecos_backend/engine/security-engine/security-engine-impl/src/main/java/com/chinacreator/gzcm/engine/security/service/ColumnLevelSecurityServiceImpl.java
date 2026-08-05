package com.chinacreator.gzcm.engine.security.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service("ecosClsService")
public class ColumnLevelSecurityServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(ColumnLevelSecurityServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    public ColumnLevelSecurityServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 根据 tableName + userId 查询列级安全策略，
     * 返回可见列列表和阻止列列表。
     */
    public Map<String, Object> getColumns(String tableName, String userId, List<String> allColumns) {
        String sql = """
            SELECT visible_cols, blocked_cols, priority, policy_name
            FROM ecos_cls_policy
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
            log.error("查询CLS策略失败: table={}, userId={}", tableName, userId, e);
            policies = Collections.emptyList();
        }

        Set<String> visibleSet = new LinkedHashSet<>(allColumns != null ? allColumns : Collections.emptyList());
        Set<String> blockedSet = new LinkedHashSet<>();

        for (Map<String, Object> p : policies) {
            String visibleJson = (String) p.get("visible_cols");
            String blockedJson = (String) p.get("blocked_cols");

            if (visibleJson != null && !visibleJson.isBlank()) {
                try {
                    List<String> vis = MAPPER.readValue(visibleJson, new TypeReference<List<String>>() {});
                    visibleSet.retainAll(vis);
                } catch (JsonProcessingException e) {
                    log.warn("解析visible_cols失败: {}", visibleJson);
                }
            }

            if (blockedJson != null && !blockedJson.isBlank()) {
                try {
                    List<String> blk = MAPPER.readValue(blockedJson, new TypeReference<List<String>>() {});
                    visibleSet.removeAll(blk);
                    blockedSet.addAll(blk);
                } catch (JsonProcessingException e) {
                    log.warn("解析blocked_cols失败: {}", blockedJson);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("visibleColumns", new ArrayList<>(visibleSet));
        result.put("blockedColumns", new ArrayList<>(blockedSet));
        result.put("tableName", tableName);
        result.put("policies", policies.stream().map(m -> m.get("policy_name")).toList());
        return result;
    }

    // ── CRUD ──────────────────────────────────────────

    public List<Map<String, Object>> listPolicies(String tableName) {
        String sql;
        Object[] args;
        if (tableName != null && !tableName.isBlank()) {
            sql = "SELECT * FROM ecos_cls_policy WHERE table_name = ? ORDER BY priority ASC, created_at DESC";
            args = new Object[]{tableName};
        } else {
            sql = "SELECT * FROM ecos_cls_policy ORDER BY priority ASC, created_at DESC";
            args = new Object[]{};
        }
        return jdbcTemplate.queryForList(sql, args);
    }

    public Map<String, Object> getPolicy(String id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM ecos_cls_policy WHERE id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> createPolicy(Map<String, Object> body) {
        String id = UUID.randomUUID().toString().replace("-", "");
        String visibleColsJson;
        try {
            @SuppressWarnings("unchecked")
            List<String> visibleCols = (List<String>) body.get("visibleCols");
            visibleColsJson = MAPPER.writeValueAsString(visibleCols != null ? visibleCols : Collections.emptyList());
        } catch (JsonProcessingException e) {
            visibleColsJson = "[]";
        }
        String blockedColsJson;
        try {
            @SuppressWarnings("unchecked")
            List<String> blockedCols = (List<String>) body.get("blockedCols");
            blockedColsJson = blockedCols != null ? MAPPER.writeValueAsString(blockedCols) : null;
        } catch (JsonProcessingException e) {
            blockedColsJson = null;
        }

        jdbcTemplate.update(
            "INSERT INTO ecos_cls_policy (id, policy_name, table_name, visible_cols, blocked_cols, role_id, user_id, priority, enabled, description, created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            id,
            body.getOrDefault("policyName", ""),
            body.getOrDefault("tableName", ""),
            visibleColsJson,
            blockedColsJson,
            body.getOrDefault("roleId", null),
            body.getOrDefault("userId", null),
            body.getOrDefault("priority", 0),
            body.getOrDefault("enabled", true),
            body.getOrDefault("description", null),
            body.getOrDefault("createdBy", null)
        );
        log.info("CLS策略创建: id={}, name={}", id, body.get("policyName"));
        return getPolicy(id);
    }

    public Map<String, Object> updatePolicy(String id, Map<String, Object> body) {
        Map<String, Object> existing = getPolicy(id);
        if (existing == null) return null;

        String visibleColsJson = existing.get("visible_cols") != null ? existing.get("visible_cols").toString() : "[]";
        String blockedColsJson = existing.get("blocked_cols") != null ? existing.get("blocked_cols").toString() : null;

        if (body.containsKey("visibleCols")) {
            try {
                @SuppressWarnings("unchecked")
                List<String> visibleCols = (List<String>) body.get("visibleCols");
                visibleColsJson = MAPPER.writeValueAsString(visibleCols != null ? visibleCols : Collections.emptyList());
            } catch (JsonProcessingException ignored) {}
        }
        if (body.containsKey("blockedCols")) {
            try {
                @SuppressWarnings("unchecked")
                List<String> blockedCols = (List<String>) body.get("blockedCols");
                blockedColsJson = blockedCols != null ? MAPPER.writeValueAsString(blockedCols) : null;
            } catch (JsonProcessingException ignored) {}
        }

        jdbcTemplate.update(
            "UPDATE ecos_cls_policy SET policy_name=?, table_name=?, visible_cols=?, blocked_cols=?, role_id=?, user_id=?, priority=?, enabled=?, description=?, updated_at=NOW() WHERE id=?",
            body.getOrDefault("policyName", existing.get("policy_name")),
            body.getOrDefault("tableName", existing.get("table_name")),
            visibleColsJson,
            blockedColsJson,
            body.getOrDefault("roleId", existing.get("role_id")),
            body.getOrDefault("userId", existing.get("user_id")),
            body.getOrDefault("priority", existing.get("priority")),
            body.getOrDefault("enabled", existing.get("enabled")),
            body.getOrDefault("description", existing.get("description")),
            id
        );
        log.info("CLS策略更新: id={}", id);
        return getPolicy(id);
    }

    public boolean deletePolicy(String id) {
        int rows = jdbcTemplate.update("DELETE FROM ecos_cls_policy WHERE id = ?", id);
        if (rows > 0) {
            log.info("CLS策略删除: id={}", id);
        }
        return rows > 0;
    }
}
