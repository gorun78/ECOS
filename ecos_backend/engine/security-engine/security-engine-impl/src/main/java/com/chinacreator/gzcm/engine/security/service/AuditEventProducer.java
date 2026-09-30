package com.chinacreator.gzcm.engine.security.service;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 详细设计-01 C.5 / W29 — 审计事件 Kafka 化生产者。
 *
 * <p>替代 `SecurityAuditService` 内"同步 JdbcTemplate 直插 + 吞异常"旧路径。
 * 发布到 {@code ecos.audit} topic 由 sysman 侧 {@code AuditKafkaConsumer} 消费落库。
 * 兜底：Kafka 不可用时写 {@code ecos_runtime_audit_retry}（V244）。
 *
 * <p>调用方式：</p>
 * <pre>{@code
 *   auditEventProducer.publish(new AuditEventProducer.AuditEvent(
 *       UUID.randomUUID().toString(), "user1", "admin", "login.success",
 *       "/api/v1/auth/login", "SUCCESS", "{\"ip\":\"10.0.0.1\"}", "10.0.0.1"));
 * }</pre>
 */
@Service
public class AuditEventProducer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventProducer.class);

    private final JdbcTemplate jdbc;
    @Autowired(required = false)
    private EventBusService eventBus;

    public AuditEventProducer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 审计事件结构（发布到 Kafka 的 payload 序列化形态）。 */
    public record AuditEvent(
            String id, String userId, String username, String action,
            String resource, String result, String detail, String ipAddress,
            long timestamp
    ) {
        public static AuditEvent of(String userId, String username, String action,
                                    String resource, String result, String detail,
                                    String ipAddress) {
            return new AuditEvent(UUID.randomUUID().toString(),
                    userId, username, action, resource, result, detail, ipAddress,
                    System.currentTimeMillis());
        }
    }

    /**
     * 发布审计事件。Kafka 不可用 → 写重试表兜底（不阻塞业务）。
     */
    public void publish(AuditEvent event) {
        if (eventBus != null) {
            try {
                Map<String, Object> payload = toMap(event);
                eventBus.publish(KafkaTopics.AUDIT, payload);
                log.debug("审计事件已发布: id={}, action={}", event.id(), event.action());
                return;
            } catch (Exception e) {
                log.warn("审计事件 Kafka 发布失败, 转重试表: id={}, error={}", event.id(), e.getMessage());
            }
        }
        // Kafka fallback：写重试表（分册 00 E.6.2 兜底，不阻塞主流程）
        writeRetryTable(event);
    }

    private void writeRetryTable(AuditEvent event) {
        try {
            Map<String, Object> payload = toMap(event);
            String payloadJson = toJsonSafe(payload);
            jdbc.update(
                "INSERT INTO ecos_runtime_audit_retry (id, event_type, payload, attempts, next_retry_at, status, create_time, update_time, version_no, is_deleted, domain) " +
                "VALUES (?, 'security_audit', ?, 0, NOW(), 'pending', NOW(), NOW(), '1', 0, 'default')",
                event.id(), payloadJson);
            log.warn("审计事件入重试表: id={}, action={}", event.id(), event.action());
        } catch (Exception retryEx) {
            log.error("审计事件重试表也写入失败(仅 log): id={}, error={}",
                    event.id(), retryEx.getMessage());
        }
    }

    private static Map<String, Object> toMap(AuditEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.id());
        m.put("userId", e.userId());
        m.put("username", e.username());
        m.put("action", e.action());
        m.put("resource", e.resource());
        m.put("result", e.result());
        m.put("detail", e.detail());
        m.put("ipAddress", e.ipAddress());
        m.put("timestamp", e.timestamp());
        return m;
    }

    private static String toJsonSafe(Map<String, Object> m) {
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(m); }
        catch (Exception e) { return "{}"; }
    }
}
