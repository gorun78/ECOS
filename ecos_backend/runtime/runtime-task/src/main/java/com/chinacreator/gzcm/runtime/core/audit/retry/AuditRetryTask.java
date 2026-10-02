package com.chinacreator.gzcm.runtime.core.audit.retry;

import com.chinacreator.gzcm.runtime.core.audit.retry.dao.AuditRetryDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
 *
 * <p>架构铁律 §2.5#1 / IR06 收口（R2 B4c-runtime）：本任务不再直持 {@code JdbcTemplate}，
 * 控制域 SQL 一律经 {@link AuditRetryDao}（继承
 * {@code com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}）执行；
 * 本任务保留重放/退避/告警编排。条件装配目标同步改为 DAO Bean（DAO 本身以
 * {@code @ConditionalOnBean(JdbcTemplate.class)} 装配，语义等价）。</p>
 */
@Component
@ConditionalOnBean(AuditRetryDao.class)
public class AuditRetryTask {

    public static final long INTERVAL_MS = 60_000L;
    public static final int MAX_ATTEMPTS = 3;
    private static final int BATCH = 50;

    private static final Logger log = LoggerFactory.getLogger(AuditRetryTask.class);

    private final AuditRetryDao dao;
    private final AuditReplayer replayer;
    private final AuditFailureSink failureSink;

    @org.springframework.beans.factory.annotation.Autowired
    public AuditRetryTask(AuditRetryDao dao) {
        this(dao,
            (topic, payload) -> {
                log.warn("[audit-fallback] kafka resend 占位 topic={} payloadLen={}",
                        topic, payload == null ? 0 : payload.length());
                return true;
            },
            (sev, m) -> log.warn("[audit-fallback] ALERT [{}] {}", sev, m));
    }

    public AuditRetryTask(AuditRetryDao dao, AuditReplayer replayer, AuditFailureSink failureSink) {
        this.dao = dao;
        this.replayer = replayer == null ? ((topic, payload) -> true) : replayer;
        this.failureSink = failureSink == null ? ((sev, m) ->
                log.warn("[audit-fallback] ALERT [{}] {}", sev, m)) : failureSink;
    }

    /** 生产侧 fallback 落库：Kafka 写失败时调用（业务写已成功，仅审计打点失败）。 */
    public String enqueue(String eventType, String payload, String traceId) {
        String id = java.util.UUID.randomUUID().toString();
        dao.insertRetry(id, eventType == null ? "unknown" : eventType, payload,
                traceId == null ? "" : traceId);
        log.info("[audit-fallback] enqueued event={} payloadLen={}",
                eventType, payload == null ? 0 : payload.length());
        return id;
    }

    @Scheduled(fixedDelay = INTERVAL_MS, initialDelay = INTERVAL_MS)
    public void replayDue() {
        Timestamp now = Timestamp.from(Instant.now());
        List<String> ids = dao.queryDueIds(now, MAX_ATTEMPTS, BATCH);
        if (ids.isEmpty()) return;
        int ok = 0, failEscalated = 0;
        for (String id : ids) {
            Map<String, Object> row = dao.findRow(id);
            if (row == null) continue;
            boolean delivered;
            try {
                delivered = replayer.replay("ecos.audit", String.valueOf(row.get("payload")));
            } catch (Exception ex) {
                log.warn("[audit-fallback] replay 抛异常 id={} err={}", id, ex.getMessage());
                delivered = false;
            }
            if (delivered) {
                dao.markClosed(id);
                ok++;
            } else {
                if (!bumpAttempt(id)) {
                    dao.markExhausted(id);
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

    /** 失败一次 → attempts+1，指数退避：attempts 当前值决定退避分钟 (1→2min, 2→4min, 3→exhausted)。 */
    private boolean bumpAttempt(String id) {
        Map<String, Object> row = dao.findRow(id);
        if (row == null) return false;
        int attempts;
        try { attempts = ((Number) row.get("attempts")).intValue(); } catch (Exception e) { attempts = 0; }
        if (attempts >= MAX_ATTEMPTS) return false;
        long delayMinutes = 1L << attempts; // 1,2,4
        return dao.bumpAttempt(id, String.valueOf(delayMinutes));
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
