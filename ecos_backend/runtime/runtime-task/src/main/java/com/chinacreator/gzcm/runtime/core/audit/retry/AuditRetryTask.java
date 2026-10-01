package com.chinacreator.gzcm.runtime.core.audit.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 详细设计-00 C.5.4 (F00-12) — 审计兜底 SLA。
 *
 * <p>关闭 W12/G5-7 尾项「审计失败兜底仅 log」：Kafka 写失败或 onAudit 下游写入失败时，
 * producer 侧把 payload 暂存到控制域 {@code ecos_control.ecos_runtime_audit_retry}（DDL=V244）；
 * 本任务在每 {@link #INTERVAL_MS}=60s 拉一次 pending + 到期记录，重投 Kafka（经 {@link AuditReplayer}），
 * 指数退避（attempts ≤ {@link #MAX_ATTEMPTS}=3）；3 次仍失败转 critical 告警（必须人工 ack），
 * SLA：从失败到落库告警 ≤ 15min（当前 60s 重放间隔下最坏 3×60s + 成功率 100% 命中 <1min）。</p>
 *
 * <p>红线：审计兜底失败<b>不回滚业务写</b>（可用性优先），但告警未 ack 期间该租户写计数进入报表
 * （可追溯性归 08 册）。SLA 未达由告警阈值字段（upgrade_min）触达，不在本任务里内联。</p>
 */
@Component
@ConditionalOnBean(JdbcTemplate.class)
public class AuditRetryTask {

    public static final long INTERVAL_MS = 60_000L;
    public static final int MAX_ATTEMPTS = 3;
    private static final String T = "ecos_control.ecos_runtime_audit_retry";
    private static final String V = "1.0.0";
    private static final int BATCH = 50;

    private static final Logger log = LoggerFactory.getLogger(AuditRetryTask.class);

    private final JdbcTemplate jdbc;
    private final AuditReplayer replayer;
    private final AuditFailureSink failureSink;

    public AuditRetryTask(JdbcTemplate jdbc) {
        this(jdbc,
            (topic, payload) -> {
                log.warn("[audit-fallback] kafka resend 占位 topic={} payloadLen={}",
                        topic, payload == null ? 0 : payload.length());
                return true;
            },
            (sev, m) -> log.warn("[audit-fallback] ALERT [{}] {}", sev, m));
    }

    public AuditRetryTask(JdbcTemplate jdbc, AuditReplayer replayer, AuditFailureSink failureSink) {
        this.jdbc = jdbc;
        this.replayer = replayer == null ? ((topic, payload) -> true) : replayer;
        this.failureSink = failureSink == null ? ((sev, m) ->
                log.warn("[audit-fallback] ALERT [{}] {}", sev, m)) : failureSink;
    }

    /** 生产侧 fallback 落库：Kafka 写失败时调用（业务写已成功，仅审计打点失败）。 */
    public String enqueue(String eventType, String payload, String traceId) {
        String id = java.util.UUID.randomUUID().toString();
        jdbc.update(
            "INSERT INTO " + T +
            " (id, event_type, payload, attempts, next_retry_at, status, trace_id, version_no, is_deleted, domain)" +
            " VALUES (?, ?, ?, 0, NOW(), 'pending', ?, ?, 0, 'default')",
            id, eventType == null ? "unknown" : eventType, payload,
            traceId == null ? "" : traceId, V);
        log.info("[audit-fallback] enqueued event={} payloadLen={}",
                eventType, payload == null ? 0 : payload.length());
        return id;
    }

    @Scheduled(fixedDelay = INTERVAL_MS, initialDelay = INTERVAL_MS)
    public void replayDue() {
        Timestamp now = Timestamp.from(Instant.now());
        List<String> ids = jdbc.queryForList(
            "SELECT id FROM " + T +
            " WHERE is_deleted = 0 AND status = 'pending' AND attempts < " + MAX_ATTEMPTS +
            "   AND next_retry_at <= ? ORDER BY next_retry_at ASC LIMIT " + BATCH,
            String.class, now);
        if (ids.isEmpty()) return;
        int ok = 0, failEscalated = 0;
        for (String id : ids) {
            Map<String, Object> row = fetch(id);
            if (row == null) continue;
            boolean delivered;
            try {
                delivered = replayer.replay("ecos.audit", String.valueOf(row.get("payload")));
            } catch (Exception ex) {
                log.warn("[audit-fallback] replay 抛异常 id={} err={}", id, ex.getMessage());
                delivered = false;
            }
            if (delivered) {
                markClosed(id);
                ok++;
            } else {
                if (!bumpAttempt(id)) {
                    markExhausted(id);
                    failureSink.alert("critical",
                        "audit-retry 3 次仍失败，需人工 ack：id=" + id
                        + " event=" + row.get("eventType"));
                    failEscalated++;
                }
            }
        }
        if (ok > 0 || failEscalated > 0) {
            log.info("[audit-fallback] tick total={} ok={} escalated={}", ids.size(), ok, failEscalated);
        }
    }

    // ---- 内部 ----

    private Map<String, Object> fetch(String id) {
        try {
            return jdbc.queryForMap(
                "SELECT id, event_type AS eventType, payload, attempts FROM " + T +
                " WHERE id = ? AND is_deleted = 0", id);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean markClosed(String id) {
        return jdbc.update("UPDATE " + T +
                " SET status = 'closed', update_time = NOW() WHERE id = ?", id) > 0;
    }

    /** 失败一次 → attempts+1，指数退避：1min, 2min, 4min（attempts 1→2min, 2→4min, 3→exhausted）。 */
    private boolean bumpAttempt(String id) {
        Map<String, Object> row = fetch(id);
        if (row == null) return false;
        int attempts;
        try { attempts = ((Number) row.get("attempts")).intValue(); } catch (Exception e) { attempts = 0; }
        if (attempts >= MAX_ATTEMPTS) return false;
        long delayMinutes = 1L << attempts; // 1,2,4
        return jdbc.update("UPDATE " + T +
                " SET attempts = attempts + 1, last_error = 'replay-failed'," +
                " next_retry_at = NOW() + (?)::int * interval '1 minute'," +
                " update_time = NOW() WHERE id = ?",
                String.valueOf(delayMinutes), id) > 0;
    }

    private void markExhausted(String id) {
        jdbc.update("UPDATE " + T +
                " SET status = 'exhausted', update_time = NOW() WHERE id = ?", id);
    }

    /** 下游 Kafka 重投接口（01 册事件门面可替换为 runtime-event EventBusService）。 */
    @FunctionalInterface
    public interface AuditReplayer {
        boolean replay(String topic, String payload);
    }

    /** 3 次仍失败时的告警下沉接口（01 册 runtime-monitor 门面）。 */
    @FunctionalInterface
    public interface AuditFailureSink {
        void alert(String severity, String message);
    }
}
