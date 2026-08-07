package com.chinacreator.gzcm.engine.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Agent 指标采集器 — 异步写入 ecos_agent_metrics / ecos_agent_alert 表。
 * <p>
 * 使用单线程 ThreadPoolExecutor 串行写入，避免阻塞 Agent 推理主线程。
 * </p>
 */
@Component
public class AgentMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(AgentMetricsCollector.class);

    /** 慢查询阈值（毫秒）: 5 分钟 */
    private static final long SLOW_THRESHOLD_MS = 300_000L;

    private final JdbcTemplate jdbc;
    private final ThreadPoolExecutor writer;

    public AgentMetricsCollector(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.writer = new ThreadPoolExecutor(
                1, 1, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(2000),
                r -> new Thread(r, "agent-metrics-writer"),
                new ThreadPoolExecutor.DiscardOldestPolicy());
        ensureTables();
    }

    /**
     * 异步记录一条指标。
     *
     * @param agentId    Agent 标识
     * @param action     操作类型（think/act/observe）
     * @param success    是否成功
     * @param elapsedMs  耗时（毫秒）
     * @param tokensIn   输入 token
     * @param tokensOut  输出 token
     * @param traceId    追踪 ID
     */
    public void record(String agentId, String action, boolean success,
                        long elapsedMs, int tokensIn, int tokensOut, String traceId) {
        writer.execute(() -> {
            try {
                jdbc.update(
                    "INSERT INTO ecos_agent_metrics " +
                    "(agent_id, action, success, elapsed_ms, tokens_in, tokens_out, trace_id, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())",
                    nvl(agentId, "unknown"), action, success, elapsedMs,
                    tokensIn, tokensOut, nvl(traceId, ""));
            } catch (Exception e) {
                log.debug("[AgentMetrics] DB write failed: {}", e.getMessage());
            }
        });
    }

    /**
     * 慢查询告警 — elapsedMs > 5 分钟时写入告警表。
     */
    public void alertSlow(String agentId, String traceId, String action, long elapsedMs) {
        if (elapsedMs <= SLOW_THRESHOLD_MS) {
            return;
        }
        writer.execute(() -> {
            try {
                String message = String.format(
                    "Agent '%s' 执行 '%s' 耗时 %d ms (阈值 %d ms), trace=%s",
                    nvl(agentId, "unknown"), action, elapsedMs, SLOW_THRESHOLD_MS,
                    nvl(traceId, ""));
                jdbc.update(
                    "INSERT INTO ecos_agent_alert " +
                    "(trace_id, agent_id, alert_type, message, created_at) " +
                    "VALUES (?, ?, ?, ?, NOW())",
                    nvl(traceId, ""), nvl(agentId, "unknown"), "SLOW_QUERY", message);
                log.warn("[AgentMetrics] ALERT: {}", message);
            } catch (Exception e) {
                log.debug("[AgentMetrics] Alert write failed: {}", e.getMessage());
            }
        });
    }

    // ─── DDL 自建 ────────────────────────────────────────────

    private void ensureTables() {
        try {
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS ecos_agent_metrics (\n" +
                "    id BIGSERIAL PRIMARY KEY,\n" +
                "    agent_id VARCHAR(64),\n" +
                "    action VARCHAR(32),\n" +
                "    success BOOLEAN,\n" +
                "    elapsed_ms BIGINT,\n" +
                "    tokens_in INT DEFAULT 0,\n" +
                "    tokens_out INT DEFAULT 0,\n" +
                "    trace_id VARCHAR(16),\n" +
                "    created_at TIMESTAMP DEFAULT NOW()\n" +
                ")");
            jdbc.execute(
                "CREATE INDEX IF NOT EXISTS idx_agent_metrics_agent " +
                "ON ecos_agent_metrics(agent_id, created_at DESC)");
        } catch (Exception e) {
            log.debug("[AgentMetrics] Metrics table ensure: {}", e.getMessage());
        }

        try {
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS ecos_agent_alert (\n" +
                "    id BIGSERIAL PRIMARY KEY,\n" +
                "    trace_id VARCHAR(16),\n" +
                "    agent_id VARCHAR(64),\n" +
                "    alert_type VARCHAR(32),\n" +
                "    message TEXT,\n" +
                "    created_at TIMESTAMP DEFAULT NOW()\n" +
                ")");
        } catch (Exception e) {
            log.debug("[AgentMetrics] Alert table ensure: {}", e.getMessage());
        }
        log.info("[AgentMetrics] Tables ensured");
    }

    private static String nvl(String val, String fallback) {
        return val != null ? val : fallback;
    }
}
