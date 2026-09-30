package com.chinacreator.gzcm.engine.security.service;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 详细设计-01 C.5 / W29·W34 — 审计消费者：落 {@code ecos_audit_log}。
 *
 * <p>Kafka 路径（{@code ecos.event.kafka.enabled=true}）：由 {@code KafkaEventBusServiceImpl}
 * 把本 handler 作为 {@code ecos.audit} topic 订阅者；内存路径：由
 * {@code MemoryEventBusServiceImpl} 同 JVM 同步分发。两种路径共用同一 handler 逻辑
 * （与 {@code DataSecurityLevelEventListener} 同一范式）。</p>
 *
 * <p><b>幂等</b>（C.5 指定 {INSERT ... ON CONFLICT (id) DO NOTHING} 语义；MySQL 走 INSERT IGNORE
 * 等价，多库分支待 MC03 阶段收口）：同一 {@code id} 重放不产生重复行。</p>
 *
 * <p><b>列形态与既有 {@link SecurityAuditService#asyncWrite} 写入保持一致</b>
 * （id/user_id/username/action/resource/result/detail/ip_address），避免与 {@code AuditHashChainService}
 * 使用的长整型 id 群组列（operation/entity_type/entity_id）相互干扰——后者的链式哈希
 * 落列是它所在部署形态的既有约定，本消费者不重复戳链（戳链动作仍由直接写者触发）。</p>
 */
@Component
public class AuditKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditKafkaConsumer.class);
    /** 消费组（C.5 指定 sysman-audit-consumer）。 */
    public static final String GROUP_PREFIX = "sysman-audit-consumer";
    private static final ObjectMapper MAPPER =
            new ObjectMapper().registerModule(new JavaTimeModule());

    private final JdbcTemplate jdbc;
    private final ObjectProvider<EventBusService> eventBus;

    public AuditKafkaConsumer(JdbcTemplate jdbc,
                              ObjectProvider<EventBusService> eventBus) {
        this.jdbc = jdbc;
        this.eventBus = eventBus;
    }

    @PostConstruct
    public void init() {
        EventBusService bus = eventBus.getIfAvailable();
        if (bus == null) {
            log.warn("AuditKafkaConsumer: EventBusService 未装配, ecos.audit 订阅未装载（本 JVM 不消费跨进程审计）");
            return;
        }
        bus.subscribe(KafkaTopics.AUDIT, Map.class, this::onAuditEvent);
        log.info("AuditKafkaConsumer: subscribed topic={} (group={})",
                KafkaTopics.AUDIT, GROUP_PREFIX);
    }

    /**
     * 消费入口：payload 可能是 Map（同 JVM 或 Kafka JSON 反序列化）或 JSON 字符串（Kafka 字符串路径）。
     */
    public void onAuditEvent(Object payload) {
        Map<String, Object> evt;
        try {
            if (payload instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) m;
                evt = cast;
            } else if (payload instanceof String s && !s.isBlank()) {
                evt = MAPPER.readValue(s, MAPPER.getTypeFactory().constructMapType(
                        Map.class, String.class, Object.class));
            } else {
                log.debug("AuditKafkaConsumer: unsupported payload type={}, skip",
                        payload == null ? "null" : payload.getClass().getName());
                return;
            }
        } catch (Exception e) {
            log.warn("AuditKafkaConsumer: payload parse failed, drop ({}): {}", 
                    e.getClass().getSimpleName(), e.getMessage());
            return;
        }
        insertAuditLog(evt);
    }

    /**
     * 幂等落库。PG 使用 {@code ON CONFLICT (id) DO NOTHING}；非 PG 部署形态退化为先 SELECT 再 INSERT。
     */
    private void insertAuditLog(Map<String, Object> evt) {
        String id = String.valueOf(evt.get("id"));
        if (id == null || id.isBlank() || "null".equals(id)) {
            log.warn("AuditKafkaConsumer: 审计事件缺 id, 丢弃 payload keys={}", evt.keySet());
            return;
        }
        String userId = asStr(evt.get("userId"));
        String username = asStr(evt.get("username"));
        String action = asStr(evt.get("action"));
        String resource = asStr(evt.get("resource"));
        String result = asStr(evt.get("result"));
        String detail = asStr(evt.get("detail"));
        String ip = asStr(evt.get("ipAddress"));
        try {
            // PG 幂等：ON CONFLICT (id) DO NOTHING（id 已 PK 化，多库分支待 MC03 阶段收口）
            int inserted = jdbc.update(
                "INSERT INTO ecos_audit_log (id, user_id, username, action, resource, result, detail, ip_address) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                id, userId, username, action, resource, result, detail, ip);
            if (inserted == 0) {
                log.trace("AuditKafkaConsumer: id={} 已存在(重放跳过)", id);
            } else {
                log.debug("AuditKafkaConsumer: 落库 id={} action={}", id, action);
            }
        } catch (org.springframework.dao.DataAccessException e) {
            // 非 PG 或 ON CONFLICT 不支持时退化路径：先查后插（幂等语义等价，性能略降）
            try {
                Integer exists = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ecos_audit_log WHERE id = ?", Integer.class, id);
                if (exists != null && exists > 0) {
                    log.trace("AuditKafkaConsumer(退化): id={} 已存在, 重放跳过", id);
                } else {
                    jdbc.update(
                        "INSERT INTO ecos_audit_log (id, user_id, username, action, resource, result, detail, ip_address) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        id, userId, username, action, resource, result, detail, ip);
                }
            } catch (Exception inner) {
                log.error("AuditKafkaConsumer 落库失败(退化) id={}: {}", id, inner.getMessage());
            }
        }
    }

    private static String asStr(Object o) {
        return o == null ? null : o.toString();
    }

    /** 暴露给单元测试（@VisibleForTesting）。 */
    public static final String TOPIC = KafkaTopics.AUDIT;
}
