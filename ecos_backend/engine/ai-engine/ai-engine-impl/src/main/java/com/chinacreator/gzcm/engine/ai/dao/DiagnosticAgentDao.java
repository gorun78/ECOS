package com.chinacreator.gzcm.engine.ai.dao;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 数据访问收口（R2-B4-ai / 架构铁律 §2.5 #1 / IR06）：诊断 Agent 的 JDBC 落点。
 *
 * <p>SQL 与 {@code RowMapper} 原样搬运自原 {@code service.DiagnosticAgentService}，
 * 只搬不改语义；Fallback 硬编码与编排保留在 Service/Controller 层。</p>
 */
@Repository
public class DiagnosticAgentDao extends JdbcTemplateDaoSupport {

    /** 工具定义行 RowMapper */
    private final RowMapper<Map<String, Object>> TOOL_ROW_MAPPER = (rs, _i) -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getString("id"));
        m.put("code", rs.getString("code"));
        m.put("name", rs.getString("name"));
        m.put("description", rs.getString("description"));
        m.put("toolType", rs.getString("tool_type"));
        m.put("schema", rs.getString("schema_json"));
        m.put("status", rs.getString("status"));
        return m;
    };

    public List<Map<String, Object>> queryTools(String sql, Object[] params) {
        return jdbcTemplate.query(sql.toString(), TOOL_ROW_MAPPER, params);
    }

    public List<Map<String, Object>> queryAgentConfig(String agentId) {
        return jdbcTemplate.query(
            "SELECT id, name, model_provider, model_name, system_prompt, tools " +
            "FROM ecos_agent WHERE id = ?",
            (rs, _i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getString("id"));
                m.put("name", rs.getString("name"));
                m.put("modelProvider", rs.getString("model_provider"));
                m.put("modelName", rs.getString("model_name"));
                m.put("systemPrompt", rs.getString("system_prompt"));
                m.put("tools", rs.getString("tools"));
                return m;
            },
            agentId
        );
    }

    public List<Map<String, Object>> queryGoalDeviations() {
        return jdbcTemplate.query(
            "SELECT name, target_value, current_value, status, " +
            "CASE WHEN target_value > 0 " +
            "  THEN ROUND(((target_value - current_value) / target_value * 100)::numeric, 1) " +
            "  ELSE 0 END AS deviation_pct " +
            "FROM ecos_wm_goal WHERE status IN ('AT_RISK','CRITICAL') " +
            "ORDER BY deviation_pct DESC NULLS LAST",
            (rs, _i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", rs.getString("name"));
                m.put("targetValue", rs.getBigDecimal("target_value"));
                m.put("currentValue", rs.getBigDecimal("current_value"));
                m.put("deviationPct", rs.getDouble("deviation_pct"));
                m.put("status", rs.getString("status"));
                return m;
            }
        );
    }

    public List<Map<String, Object>> queryCausalChains() {
        return jdbcTemplate.query(
            "SELECT sg.name AS source_name, tg.name AS target_name, cl.relationship_type, cl.description " +
            "FROM ecos_wm_causal_link cl " +
            "JOIN ecos_wm_goal sg ON cl.source_goal_id = sg.id " +
            "JOIN ecos_wm_goal tg ON cl.target_goal_id = tg.id " +
            "ORDER BY cl.id",
            (rs, _i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("source", rs.getString("source_name"));
                m.put("target", rs.getString("target_name"));
                m.put("relationshipType", rs.getString("relationship_type"));
                m.put("description", rs.getString("description"));
                return m;
            }
        );
    }

    public List<Map<String, Object>> queryScenarios() {
        return jdbcTemplate.query(
            "SELECT id, name, description, config_json, status FROM ecos_wm_scenario ORDER BY id",
            (rs, _i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("name", rs.getString("name"));
                m.put("description", rs.getString("description"));
                m.put("configJson", rs.getString("config_json"));
                m.put("status", rs.getString("status"));
                return m;
            }
        );
    }

    /**
     * 通用查询直通：其余诊断场景的 JDBC 访问统一走本 DAO。
     *
     * @param sql      完整 SQL
     * @param rowMapper 行映射器
     * @param args     参数
     * @return 行列表
     */
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
        return jdbcTemplate.query(sql, rowMapper, args);
    }
}
