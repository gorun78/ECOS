package com.chinacreator.gzcm.sysman.dao;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 安全策略查询 DAO — 承接 SecurityPolicyQueryService 中的 JdbcTemplate 操作（铁律 §2.5）。
 */
@Repository
public class SecurityPolicyQueryDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_security_policy";

    private static final String COLUMNS = "id, name, domain, policy_expr, priority, "
            + "create_time, update_time, create_by";

    /** 列表/详情默认行映射：{@code policy_expr} 只出摘要（B12 防原文泄露）。 */
    private final RowMapper<Map<String, Object>> ROW_MAPPER = (rs, rowNum) -> {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("name", rs.getString("name"));
        row.put("domain", rs.getString("domain"));
        String expr = rs.getString("policy_expr");
        // B12: 列表/详情默认返摘要（前50字符），防 ABAC 策略原文泄露
        row.put("policyExprSummary", expr != null && expr.length() > 50 ? expr.substring(0, 50) + "..." : expr);
        row.put("priority", rs.getInt("priority"));
        Timestamp ct = rs.getTimestamp("create_time");
        row.put("createTime", ct != null ? ct.getTime() : null);
        Timestamp ut = rs.getTimestamp("update_time");
        row.put("updateTime", ut != null ? ut.getTime() : null);
        row.put("createBy", rs.getString("create_by"));
        return row;
    };

    /** 详情端点专用：含完整 policy_expr（受 security-engine ABAC 评估保护）。 */
    private final RowMapper<Map<String, Object>> ROW_MAPPER_FULL = (rs, rowNum) -> {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("name", rs.getString("name"));
        row.put("domain", rs.getString("domain"));
        String expr = rs.getString("policy_expr");
        row.put("policyExpr", expr);
        row.put("policyExprSummary", expr != null && expr.length() > 50 ? expr.substring(0, 50) + "..." : expr);
        row.put("priority", rs.getInt("priority"));
        Timestamp ct = rs.getTimestamp("create_time");
        row.put("createTime", ct != null ? ct.getTime() : null);
        Timestamp ut = rs.getTimestamp("update_time");
        row.put("updateTime", ut != null ? ut.getTime() : null);
        row.put("createBy", rs.getString("create_by"));
        return row;
    };

    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        return jdbcTemplate.queryForObject(sql, type, args);
    }

    public int update(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
    }

    public List<Map<String, Object>> listAll() {
        String sql = "SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE is_deleted = 0 ORDER BY priority DESC";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    public Map<String, Object> findSummaryById(String id) {
        String sql = "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE id = ? AND is_deleted = 0";
        List<Map<String, Object>> rows = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> findFullExprById(String id) {
        String sql = "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE id = ? AND is_deleted = 0";
        List<Map<String, Object>> rows = jdbcTemplate.query(sql, ROW_MAPPER_FULL, id);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
