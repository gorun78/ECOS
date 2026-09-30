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
        // H8-T1: ecos_agent_metrics / ecos_agent_alert 建表 DDL 收编至 db/migration（V162），
        // 构造函数不再内嵌 DDL；写入路径保留，表缺失时降级为 debug 日志。
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

    // H8-T1: ecos_agent_metrics / ecos_agent_alert 建表 DDL（含索引）收编至 db/migration（V162），
    // 运行时不再内嵌 DDL；写入失败降级为日志。

    private static String nvl(String val, String fallback) {
        return val != null ? val : fallback;
    }
}
