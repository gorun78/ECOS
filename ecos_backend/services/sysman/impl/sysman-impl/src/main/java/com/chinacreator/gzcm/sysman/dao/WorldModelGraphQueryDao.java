package com.chinacreator.gzcm.sysman.dao;

import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WorldModelGraph 查询 DAO — 承接 WorldModelGraphQueryService 中的 JdbcTemplate 操作（铁律 §2.5）。
 * <p>SQL 与行映射自 Controller 原样下沉，字节级等价。
 */
@Repository
public class WorldModelGraphQueryDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport {

    public Integer ping() {
        return jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    }

    public List<Map<String, Object>> listGoals() {
        String sql = """
            SELECT id, name, description, parent_id, progress, status,
                   goal_type, weight, org_id, owner_user_id,
                   start_date, end_date, target_value, current_value, unit,
                   linked_workflow_id, kpi_formula, measure_frequency,
                   alert_threshold_warn, alert_threshold_critical,
                   created_at, updated_at
            FROM ecos_wm_goal
            ORDER BY id
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("name", rs.getString("name"));
            m.put("description", rs.getString("description"));
            long pid = rs.getLong("parent_id");
            m.put("parentId", rs.wasNull() ? null : pid);
            m.put("progress", rs.getInt("progress"));
            m.put("status", rs.getString("status"));
            m.put("goalType", rs.getString("goal_type"));
            m.put("weight", rs.getObject("weight"));
            m.put("orgId", rs.getString("org_id"));
            m.put("ownerUserId", rs.getString("owner_user_id"));
            m.put("startDate", rs.getDate("start_date"));
            m.put("endDate", rs.getDate("end_date"));
            m.put("targetValue", rs.getBigDecimal("target_value"));
            m.put("currentValue", rs.getBigDecimal("current_value"));
            m.put("unit", rs.getString("unit"));
            m.put("linkedWorkflowId", rs.getString("linked_workflow_id"));
            m.put("kpiFormula", rs.getString("kpi_formula"));
            m.put("measureFrequency", rs.getString("measure_frequency"));
            m.put("alertThresholdWarn", rs.getBigDecimal("alert_threshold_warn"));
            m.put("alertThresholdCritical", rs.getBigDecimal("alert_threshold_critical"));
            Timestamp ca = rs.getTimestamp("created_at");
            m.put("createdAt", ca != null ? ca.toLocalDateTime().toString() : null);
            Timestamp ua = rs.getTimestamp("updated_at");
            m.put("updatedAt", ua != null ? ua.toLocalDateTime().toString() : null);
            return m;
        });
    }

    public List<Map<String, Object>> listCausalGraphNodes() {
        return jdbcTemplate.query(
            "SELECT id, name, description, status, goal_type FROM ecos_wm_goal ORDER BY id",
            (rs, rowNum) -> {
                Map<String, Object> n = new LinkedHashMap<>();
                n.put("id", "goal-" + rs.getLong("id"));
                n.put("label", rs.getString("name"));
                n.put("description", rs.getString("description"));
                n.put("status", rs.getString("status"));
                n.put("type", rs.getString("goal_type"));
                return n;
            });
    }

    public List<Map<String, Object>> listCausalGraphEdges() {
        return jdbcTemplate.query(
            "SELECT id, source_goal_id, target_goal_id, relationship_type, description FROM ecos_wm_causal_link",
            (rs, rowNum) -> {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("id", "edge-" + rs.getLong("id"));
                e.put("source", "goal-" + rs.getLong("source_goal_id"));
                e.put("target", "goal-" + rs.getLong("target_goal_id"));
                e.put("label", rs.getString("relationship_type"));
                e.put("description", rs.getString("description"));
                return e;
            });
    }
}
