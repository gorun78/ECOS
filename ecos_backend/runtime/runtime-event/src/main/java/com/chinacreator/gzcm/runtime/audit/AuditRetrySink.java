package com.chinacreator.gzcm.runtime.audit;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.context.TraceContext;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.audit.entity.AuditRetryRow;
import com.chinacreator.gzcm.runtime.audit.mapper.AuditRetryMapper;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;

/**
 * 审计兜底投递实现（C.5.4）。
 * <p>
 * 双通道：先同步 {@link EventBusService#publish}（Kafka/内存）；
 * publish 抛异常或不可用 → 落 {@code public.ecos_runtime_audit_retry}
 * （attempts=0、next_retry_at=now+60s±退避、status=pending）。
 * <p>
 * 红线：本链任何失败不回滚业务写（可用性优先），仅 WARN/ERROR 日志；
 * 重放与 3 次上限告警由 {@code AuditRetryTask}（runtime-task 统一调度）承担。
 */
@Service
public class AuditRetrySink implements AuditSink {

    private static final Logger log = LoggerFactory.getLogger(AuditRetrySink.class);
    private static final String VERSION_NO = "1";
    private static final String DOMAIN = "default";
    /** 首重放基础间隔 60s（C.5.4 固定 60s 调度 + 指数退避基础） */
    private static final long INITIAL_RETRY_DELAY_MS = 60_000L;
    /** ±30% 抖动避免惊群 */
    private static final double JITTER = 0.3;

    private final EventBusService eventBus;
    private final AuditRetryMapper auditRetryMapper;

    public AuditRetrySink(EventBusService eventBus, AuditRetryMapper auditRetryMapper) {
        this.eventBus = eventBus;
        this.auditRetryMapper = auditRetryMapper;
    }

    @Override
    public boolean tryPublish(String eventType, String payloadJson) {
        if (eventType == null || eventType.isBlank()) {
            log.warn("[AuditSink] 空 eventType，拒绝投递（不落兜底表）");
            return false;
        }
        if (payloadJson == null) {
            payloadJson = "{}";
        }
        Map<String, Object> envelope = Map.of(
                "eventType", eventType,
                "payload", stripOuterQuotes(payloadJson),
                "publishedAt", Instant.now().toEpochMilli());
        try {
            // 无条件走事件总线（Kafka 在 → 入 broker；内存 fallback → 本地分发 — 本地分发同样算投递成功）
            eventBus.publish(KafkaTopics.AUDIT, envelope);
            return true;
        } catch (Exception e) {
            // 同步投递失败 → 兜底表（不阻塞业务）
            log.warn("[AuditSink] publish 同步失败，转兜底重试 eventType={} errType={} msg={}",
                    eventType, e.getClass().getSimpleName(), e.getMessage());
            return writeRetryRow(eventType, payloadJson, e);
        }
    }

    /** 落兜底行；表缺失/落库失败仅日志（红线：不回滚业务） */
    boolean writeRetryRow(String eventType, String payloadJson, Exception cause) {
        try {
            AuditRetryRow row = new AuditRetryRow();
            row.setId(UUID.randomUUID().toString());
            row.setEventType(eventType);
            row.setPayload(payloadJson);
            row.setAttempts((short) 0);
            row.setNextRetryAt(Timestamp.from(
                    Instant.now().plusMillis(nextDelayMs(0))));
            row.setLastError(cause != null && cause.getMessage() != null
                    ? cause.getMessage().substring(0, Math.min(1000, cause.getMessage().length()))
                    : cause != null ? cause.getClass().getSimpleName() : null);
            row.setStatus("pending");
            row.setTraceId(TraceContext.current());
            row.setVersionNo(VERSION_NO);
            row.setIsDeleted((short) 0);
            row.setDomain(DOMAIN);
            auditRetryMapper.insert(row);
            return true;
        } catch (Exception e) {
            log.error("[AuditSink] 兜底表落行失败（审计丢失风险 — 需告警但本链无可用通道） eventType={} errType={} msg={}",
                    eventType, e.getClass().getSimpleName(), e.getMessage(), e);
            return false;
        }
    }

    /** attempts 次失败后的下次间隔：60s * 2^min(attempts,3)，±30% 抖动（attempts=0 → 60s 基础窗） */
    static long nextDelayMs(int attempts) {
        long base = 60_000L << Math.min(Math.max(attempts, 0), 3);
        long jitter = (long) (base * JITTER);
        long delta = (long) ((Math.random() * 2 - 1) * jitter);
        return base + delta;
    }

    /** Kafka 路径 wire format：payloadJson 可能自带对象层，去外层引号防双重编码 */
    private static String stripOuterQuotes(String s) {
        if (s != null && s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
