package com.chinacreator.gzcm.engine.ai.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * 数据访问收口（R2-B4-ai / 架构铁律 §2.5 #1 / IR06）：Agent 指标写入的 JDBC 落点。
 * <p>SQL 原样搬运自原 {@code service.AgentMetricsCollector}，只搬不改语义；
 * 异步线程池与 nwv 降级逻辑保留在 Collector 层。</p>
 */
@Repository
public class AgentMetricsWriteDao extends JdbcTemplateDaoSupport {

    public void insertMetric(String agentId, String action, boolean success,
                             long elapsedMs, int tokensIn, int tokensOut, String traceId) {
        jdbcTemplate.update(
            "INSERT INTO ecos_agent_metrics " +
            "(agent_id, action, success, elapsed_ms, tokens_in, tokens_out, trace_id, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())",
            agentId, action, success, elapsedMs, tokensIn, tokensOut, traceId);
    }

    public void insertAlert(String traceId, String agentId, String alertType, String message) {
        jdbcTemplate.update(
            "INSERT INTO ecos_agent_alert " +
            "(trace_id, agent_id, alert_type, message, created_at) " +
            "VALUES (?, ?, ?, ?, NOW())",
            traceId, agentId, alertType, message);
    }
}
