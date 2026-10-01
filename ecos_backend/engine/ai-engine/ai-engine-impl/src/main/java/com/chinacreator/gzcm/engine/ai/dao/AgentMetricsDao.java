package com.chinacreator.gzcm.engine.ai.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * 数据访问收口（R2-B4-ai / 架构铁律 §2.5 #1 / IR06）：Agent 指标查询的 JDBC 落点。
 * <p>SQL 原样搬运自原 {@code service.AgentMetricsService}，只搬不改语义；
 * 存在性判定的异常降级逻辑保留在 Service 层。</p>
 */
@Repository
public class AgentMetricsDao extends JdbcTemplateDaoSupport {

    public Map<String, Object> queryTotal(String agentId) {
        return jdbcTemplate.queryForMap(
            "SELECT " +
            "  COUNT(*) AS total_count, " +
            "  COALESCE(SUM(CASE WHEN success THEN 1 ELSE 0 END), 0) AS success_count, " +
            "  COALESCE(SUM(CASE WHEN NOT success THEN 1 ELSE 0 END), 0) AS failure_count, " +
            "  ROUND(AVG(elapsed_ms)::numeric, 1) AS avg_elapsed_ms, " +
            "  COALESCE(SUM(tokens_in), 0) AS total_tokens_in, " +
            "  COALESCE(SUM(tokens_out), 0) AS total_tokens_out " +
            "FROM ecos_agent_metrics WHERE agent_id = ?",
            agentId);
    }

    public Map<String, Object> queryPercentiles(String agentId) {
        return jdbcTemplate.queryForMap(
            "SELECT " +
            "  COALESCE(percentile_cont(0.5) WITHIN GROUP (ORDER BY elapsed_ms), 0)::bigint AS p50_ms, " +
            "  COALESCE(percentile_cont(0.99) WITHIN GROUP (ORDER BY elapsed_ms), 0)::bigint AS p99_ms " +
            "FROM ecos_agent_metrics WHERE agent_id = ?",
            agentId);
    }

    public Integer queryExists(String agentId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ecos_agent_metrics WHERE agent_id = ?",
            Integer.class, agentId);
    }

    public List<Map<String, Object>> queryErrors(String agentId, int limit) {
        return jdbcTemplate.queryForList(
            "SELECT id, trace_id AS \"traceId\", agent_id AS \"agentId\", " +
            "alert_type AS \"alertType\", message, " +
            "created_at AS \"createdAt\" " +
            "FROM ecos_agent_alert WHERE agent_id = ? " +
            "ORDER BY created_at DESC LIMIT ?",
            agentId, limit);
    }
}
