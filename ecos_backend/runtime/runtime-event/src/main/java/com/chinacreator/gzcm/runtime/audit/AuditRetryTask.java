package com.chinacreator.gzcm.runtime.audit;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.audit.entity.AuditRetryRow;
import com.chinacreator.gzcm.runtime.audit.mapper.AuditRetryMapper;
import com.chinacreator.gzcm.runtime.core.alert.IAlertService;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;

/**
 * 审计兜底重放任务（C.5.4 审计兜底 SLA / F00-12）。
 * <p>
 * 统一注册到 runtime-task（宿主 gateway 已有 {@code @EnableScheduling}，
 * 并与 F00-08 单调度底座同包域）：{@code @Scheduled(fixedDelay=60s)} 拉到期行：
 * <ul>
 *   <li>到期 row（status=pending 且 next_retry_at &lt;= now）→ 重投 {@code ecos.audit}；</li>
 *   <li>成功 → attempts+1、status=replayed；</li>
 *   <li>失败且 attempts（含本次）&lt; 3 → next_retry_at 退避 60s、2min、4min；</li>
 *   <li>失败且 attempts 达 3 → status=alerted + 触发 critical 告警
 *       （rule {@code kafka.audit.retry.exhausted}，rule 不存在时自动创建；
 *       <b>ack 必须人工</b> = monitor 端点只 mark ack 不自动 close）。</li>
 * </ul>
 *
 * <p>SLA（C.5.4）：失败 → 落库（sink）→ 3×60s 退避 → alerted 告警，全链 &lt;= 15min 窗口。
 * 红线：兜底失败不回滚业务写（可用性）。
 */
@Component
@ConditionalOnProperty(name = "ecos.event.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class AuditRetryTask {

    private static final Logger log = LoggerFactory.getLogger(AuditRetryTask.class);
    /** 重试上限（C.5.4：&lt;=3 次） */
    static final int MAX_ATTEMPTS = 3;
    /** alerted 告警规则码（rule 不存在时自动创建，severity=critical） */
    public static final String RULE_RETRY_EXHAUSTED = "kafka.audit.retry.exhausted";
    /** 单轮拉取上限（防积压风暴；正常态 <10） */
    private static final int DUE_BATCH = 100;

    private final AuditRetryMapper auditRetryMapper;
    private final EventBusService eventBus;
    private final ObjectProvider<IAlertService> alertServiceProvider;

    public AuditRetryTask(AuditRetryMapper auditRetryMapper,
                          EventBusService eventBus,
                          ObjectProvider<IAlertService> alertServiceProvider) {
        this.auditRetryMapper = auditRetryMapper;
        this.eventBus = eventBus;
        this.alertServiceProvider = alertServiceProvider;
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    public void pollDueRetries() {
        List<AuditRetryRow> due;
        try {
            due = auditRetryMapper.listDue(Timestamp.from(Instant.now()), DUE_BATCH);
        } catch (Exception e) {
            log.warn("[AuditRetry] 到期行拉取失败（表缺失/DB 不可用）errType={} msg={}",
                    e.getClass().getSimpleName(), e.getMessage());
            return; // dev 态无表时静默降级，不刷 ERROR
        }
        if (due == null || due.isEmpty()) {
            return;
        }
        log.info("[AuditRetry] 本轮重放到期行 {} 条", due.size());
        for (AuditRetryRow row : due) {
            replayOne(row);
        }
    }

    /** 单行重放 — 包级可见（单测直调） */
    void replayOne(AuditRetryRow row) {
        Map<String, Object> envelope = Map.of(
                "eventType", row.getEventType() != null ? row.getEventType() : "AUDIT_RETRY",
                "payload", row.getPayload() != null ? row.getPayload() : "",
                "replayedAt", Instant.now().toEpochMilli(),
                "retryId", row.getId());
        try {
            eventBus.publish(KafkaTopics.AUDIT, envelope);
        } catch (Exception publishEx) {
            auditRetryMapper.markRetryFailed(row.getId(), nextRetryAt(row.getAttempts()), errMsg(publishEx));
            checkExhausted(row);
            return;
        }
        try {
            auditRetryMapper.markReplayed(row.getId());
        } catch (Exception e) {
            log.warn("[AuditRetry] 重放标记失败 id={} errType={} msg={}",
                    row.getId(), e.getClass().getSimpleName(), e.getMessage());
        }
        log.info("[AuditRetry] 重投成功 id={} eventType={} attempts={}",
                row.getId(), row.getEventType(), row.getAttempts());
    }

    /** attempts 达 3 且仍在 pending → alerted + critical 告警（人工 ack） */
    private void checkExhausted(AuditRetryRow row) {
        int attemptsAfter = row.getAttempts() != null ? row.getAttempts().intValue() + 1 : 1;
        if (attemptsAfter < MAX_ATTEMPTS) {
            return;
        }
        try {
            auditRetryMapper.markAlerted(row.getId());
        } catch (Exception e) {
            log.warn("[AuditRetry] alerted 标记失败 id={} errType={}", row.getId(), e.getClass().getSimpleName());
            return;
        }
        IAlertService alertService = alertServiceProvider.getIfAvailable();
        if (alertService == null) {
            log.error("[AuditRetry] 重试 {} 次耗尽且无 IAlertService — 审计链断裂风险 id={} eventType={}",
                    MAX_ATTEMPTS, row.getId(), row.getEventType());
            return;
        }
        try {
            alertService.triggerAlertByRuleCode(
                    RULE_RETRY_EXHAUSTED, "runtime-event", RULE_RETRY_EXHAUSTED, "critical", null, null,
                    "AUDIT_RETRY_EXHAUSTED", null, row.getId(),
                    "eventType=" + row.getEventType() + " 重试 " + MAX_ATTEMPTS + " 次仍失败 retryId=" + row.getId()
                            + "（ack 必须人工）");
        } catch (Exception e) {
            log.error("[AuditRetry] 耗尽告警触发失败 id={} errType={}", row.getId(), e.getClass().getSimpleName(), e);
        }
    }

    private static Timestamp nextRetryAt(Short attempts) {
        int a = attempts != null ? attempts : 0;
        long delay = AuditRetrySink.nextDelayMs(a);
        return Timestamp.from(Instant.now().plusMillis(delay));
    }

    private static String errMsg(Exception e) {
        String m = e.getMessage();
        return m != null ? m.substring(0, Math.min(1000, m.length())) : e.getClass().getSimpleName();
    }
}
