package com.chinacreator.gzcm.engine.ai.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 诊断 Agent 服务 — 从 DiagnosticAgentController 下沉的 JdbcTemplate 访问层。
 *
 * <p>包含工具定义查询、Agent 配置查询、目标偏差/因果链/场景查询等 SQL 操作。
 * Fallback 硬编码数据仍保留在 Controller（与原行为一致）。</p>
 */
@Service
public class DiagnosticAgentService {

    private final JdbcTemplate jdbc;

    public DiagnosticAgentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 查询诊断工具定义。
     *
     * @param sql    完整 SQL (Controller 拼接 category 条件后传入)
     * @param params SQL 参数数组
     * @return 工具行列表
     */
    public List<Map<String, Object>> queryTools(String sql, Object[] params) {
        return jdbc.query(sql.toString(), (rs, _i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getString("id"));
            m.put("code", rs.getString("code"));
            m.put("name", rs.getString("name"));
            m.put("description", rs.getString("description"));
            m.put("toolType", rs.getString("tool_type"));
            m.put("schema", rs.getString("schema_json"));
            m.put("status", rs.getString("status"));
            return m;
        }, params);
    }

    /**
     * 查询 Agent 配置（ecos_agent 表）。
     *
     * @param agentId Agent ID
     * @return Agent 配置行列表（通常 0 或 1 行）
     */
    public List<Map<String, Object>> queryAgentConfig(String agentId) {
        return jdbc.query(
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
            }, agentId);
    }

    /**
     * 查询目标偏差（ecos_wm_goal，状态 AT_RISK / CRITICAL）。
     *
     * @return 偏差行列表；查询失败时返回 null（由 Controller 决定 fallback）
     */
    public List<Map<String, Object>> queryGoalDeviations() {
        return jdbc.query(
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
            });
    }

    /**
     * 查询因果链（ecos_wm_causal_link JOIN ecos_wm_goal）。
     *
     * @return 因果链行列表
     */
    public List<Map<String, Object>> queryCausalChains() {
        return jdbc.query(
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
            });
    }

    /**
     * 查询场景（ecos_wm_scenario）。
     *
     * @return 场景行列表
     */
    public List<Map<String, Object>> queryScenarios() {
        return jdbc.query(
            "SELECT id, name, description, config_json, status FROM ecos_wm_scenario ORDER BY id",
            (rs, _i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("name", rs.getString("name"));
                m.put("description", rs.getString("description"));
                m.put("configJson", rs.getString("config_json"));
                m.put("status", rs.getString("status"));
                return m;
            });
    }
}
