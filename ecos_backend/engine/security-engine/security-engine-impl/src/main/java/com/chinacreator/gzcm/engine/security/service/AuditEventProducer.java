package com.chinacreator.gzcm.engine.security.service;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.sysman.audit.model.AuditEvent;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 详细设计-01 C.5（W29/W34，ST06 真实实现）— 审计事件生产者。
 *
 * <p>写操作 → {@code EventBusService.publish(ecos.audit)}（Kafka 在 → topic；
 * 不在 → 内存总线降级，由分册 00 C.5.4 重试表兜底 SLA ≤15min）。
 * 消费者 = sysman-impl {@code AuditKafkaConsumer}（幂等 ON CONFLICT(id) DO NOTHING）。
 * <b>异常不再吞</b>：发送失败 → 写 {@code ecos_runtime_audit_retry}（分册 00 E.6.2），
 * 业务流不阻塞。</p>
 */
@Service
public class AuditEventProducer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventProducer.class);

    private final EventBusService eventBus;
    private final JdbcTemplate jdbcTemplate;

    public AuditEventProducer(EventBusService eventBus, JdbcTemplate jdbcTemplate) {
        this.eventBus = eventBus;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 发布审计事件（业务写路径统一入口）。
     *
     * @return 事件 ID（幂等键）
     */
    @Async
    public String publish(AuditEvent event) {
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(LocalDateTime.now());
        }
        try {
            eventBus.publish(KafkaTopics.AUDIT, event);
            return event.getEventId();
        } catch (Exception e) {
            // C.5 兜底：总线失败进重试表（不阻塞业务，不吞异常）
            log.error("审计事件发布失败，转重试表: id={}, action={}", event.getEventId(), event.getAction(), e);
            writeRetryRow(event, e.getMessage());
            return event.getEventId();
        }
    }

    /**
     * 裁决类短事件（OPA 评估、decide 调用等，C.2.5 ④）的便捷入口。
     *
     * @param inputDigest 输入摘要（sha256，禁落原文）
     */
    public void publishScanEvent(String action, String resource, String result,
                                 Map<String, Object> detail, String inputDigest) {
        AuditEvent event = new AuditEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setTimestamp(LocalDateTime.now());
        event.setUserId(com.chinacreator.gzcm.sysman.iam.context.UserContext.getCurrentUserId());
        event.setAction(action);
        event.setResource(resource);
        event.setResult(result);
        event.setEventType(action);
        Map<String, Object> details = new LinkedHashMap<>(detail == null ? Map.of() : detail);
        if (inputDigest != null) details.put("inputDigest", inputDigest);
        event.setDetails(details);
        publish(event);
    }

    private void writeRetryRow(AuditEvent event, String error) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO ecos_runtime_audit_retry (id, topic, payload, error, retry_count, status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, 0, 'PENDING', NOW(), NOW()) " +
                    "ON CONFLICT (id) DO NOTHING",
                    event.getEventId(), KafkaTopics.AUDIT, toJson(event), truncate(error));
        } catch (Exception e2) {
            // 重试表本身失败（库不可用）→ 只记日志，业务不阻塞（C.6 矩阵"审计：进重试表（不阻塞业务）"）
            log.error("审计重试表落库失败: id={}", event.getEventId(), e2);
        }
    }

    private static String toJson(AuditEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventId", e.getEventId());
        m.put("userId", e.getUserId());
        m.put("action", e.getAction());
        m.put("resource", e.getResource());
        m.put("result", e.getResult());
        m.put("eventType", e.getEventType());
        m.put("timestamp", e.getTimestamp() == null ? null : e.getTimestamp().toString());
        m.put("ipAddress", e.getIpAddress());
        if (e.getDetails() != null) m.put("details", e.getDetails());
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(m);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
