package com.chinacreator.gzcm.engine.ai.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Agent 指标查询服务 — 读取 ecos_agent_metrics 和 ecos_agent_alert 表。
 *
 * <p>从 AgentMetricsController 下沉的 JdbcTemplate 访问层。</p>
 */
@Service
public class AgentMetricsService {

    private final JdbcTemplate jdbc;

    public AgentMetricsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 查询 Agent 的指标汇总数据。
     *
     * @param agentId Agent ID
     * @return total 行 (total_count, success_count, failure_count, avg_elapsed_ms, total_tokens_in, total_tokens_out)
     */
    public Map<String, Object> queryTotal(String agentId) {
        return jdbc.queryForMap(
            "SELECT " +
            "  COUNT(*) AS total_count, " +
            "  COALESCE(SUM(CASE WHEN success THEN 1 ELSE 0 END), 0) AS success_count, " +
            "  COALESCE(SUM(CASE WHEN NOT success THEN 1 ELSE 0 END), 0) AS failure_count, " +
            "  ROUND(AVG(elapsed_ms)::numeric, 1) AS avg_elapsed_ms, " +
            "  COALESCE(SUM(tokens_in), 0) AS total_tokens_in, " +
            "  COALESCE(SUM(tokens_out), 0) AS total_tokens_out " +
            "FROM ecos_agent_metrics WHERE agent_id = ?",
            agentId
        );
    }

    /**
     * 查询 Agent 耗时 P50/P99 百分位。
     *
     * @param agentId Agent ID
     * @return pct 行 (p50_ms, p99_ms)
     */
    public Map<String, Object> queryPercentiles(String agentId) {
        return jdbc.queryForMap(
            "SELECT " +
            "  COALESCE(percentile_cont(0.5) WITHIN GROUP (ORDER BY elapsed_ms), 0)::bigint AS p50_ms, " +
            "  COALESCE(percentile_cont(0.99) WITHIN GROUP (ORDER BY elapsed_ms), 0)::bigint AS p99_ms " +
            "FROM ecos_agent_metrics WHERE agent_id = ?",
            agentId
        );
    }

    /**
     * 查询 Agent 的错误/告警记录（来自 ecos_agent_alert 表）。
     *
     * @param agentId Agent ID
     * @param limit   返回行数上限
     * @return 行列表
     */
    public List<Map<String, Object>> queryErrors(String agentId, int limit) {
        return jdbc.queryForList(
            "SELECT id, trace_id AS \"traceId\", agent_id AS \"agentId\", " +
            "alert_type AS \"alertType\", message, " +
            "created_at AS \"createdAt\" " +
            "FROM ecos_agent_alert WHERE agent_id = ? " +
            "ORDER BY created_at DESC LIMIT ?",
            agentId, limit
        );
    }
}
