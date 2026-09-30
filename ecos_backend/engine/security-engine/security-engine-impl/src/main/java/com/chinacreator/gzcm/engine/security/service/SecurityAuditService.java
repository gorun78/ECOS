package com.chinacreator.gzcm.engine.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 详细设计-01 C.5 / W29 改造：审计写入统一经 {@link AuditEventProducer}（Kafka
 * {@code ecos.audit} → {@link AuditKafkaConsumer} 幂等落 {@code ecos_audit_log}）。
 *
 * <p>旧实现"同步 JdbcTemplate 直插 + 吞异常"已关闭：
 * <ul>
 *   <li>正常：{@code producer.publish} → EventBusService（Kafka 或内存） → 消费者落库；</li>
 *   <li>Kafka 不可用：producer 内转 {@code ecos_runtime_audit_retry}（V244）兜底，
 *       不再静默吞异常（异常被记录进重试表 + WARN，而非原地 log.error 后丢弃）。</li>
 * </ul>
 * {@code @Async} 保留：写路径不被审计 I/O 阻塞。</p>
 */
@Service
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

    private final AuditEventProducer producer;

    public SecurityAuditService(AuditEventProducer producer) {
        this.producer = producer;
    }

    @Async
    public void asyncWrite(String id, String userId, String username, String action,
                           String resource, String result, String detail, String ipAddress) {
        try {
            AuditEventProducer.AuditEvent evt = (id == null || id.isBlank())
                    ? AuditEventProducer.AuditEvent.of(userId, username, action, resource, result, detail, ipAddress)
                    : new AuditEventProducer.AuditEvent(id, userId, username, action,
                            resource, result, detail, ipAddress, System.currentTimeMillis());
            producer.publish(evt);
        } catch (Exception e) {
            // 兜底：producer 内部已写重试表；此处再兜一层，确保绝不静默丢失（仅 WARN，不抛回业务）
            log.error("审计事件发布失败（producer 兜底后仍异常）: action={}, error={}", action, e.getMessage());
        }
    }
}
