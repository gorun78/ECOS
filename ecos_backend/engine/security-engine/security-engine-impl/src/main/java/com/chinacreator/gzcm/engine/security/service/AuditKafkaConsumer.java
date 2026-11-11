package com.chinacreator.gzcm.engine.security.service;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.security.dao.AuditDao;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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

    private final AuditDao auditDao;
    private final ObjectProvider<EventBusService> eventBus;

    public AuditKafkaConsumer(AuditDao auditDao,
                              ObjectProvider<EventBusService> eventBus) {
        this.auditDao = auditDao;
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
        String id = asStr(evt.get("id"));
        if (id == null || id.isBlank() || "null".equals(id)) {
            // #147BD：原实现缺 id 即整条丢弃。全仓多处 AUDIT 发布器只放 eventId（kb/workspace 等），
            // 于是这些域的证据从未落库且无人察觉（#147BB 只在 kb 现身，实为同族）。
            // 现在：有 eventId 就派生**确定性**主键（同 eventId ⇒ 同 id，重放幂等），
            // 只有 id 与 eventId 双缺才丢弃——丢证据是最高代价，宁可在消费端兜住并告警。
            String eventId = asStr(evt.get("eventId"));
            if (eventId == null || eventId.isBlank()) {
                log.warn("AuditKafkaConsumer: 审计事件缺 id 且缺 eventId, 丢弃 payload keys={}", evt.keySet());
                return;
            }
            id = deriveIdFromEventId(eventId);
            log.warn("AuditKafkaConsumer: 审计事件缺 id，按 eventId 派生确定性主键 id={} action={}（请让发布端补 id）",
                    id, evt.get("action"));
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
            int inserted = auditDao.insertAuditLogIdempotent(id, userId, username, action, resource, result, detail, ip);
            if (inserted == 0) {
                log.trace("AuditKafkaConsumer: id={} 已存在(重放跳过)", id);
            } else {
                log.debug("AuditKafkaConsumer: 落库 id={} action={}", id, action);
            }
            fillGovernance(id, evt);
            fillTraceId(id, evt);
        } catch (org.springframework.dao.DataAccessException e) {
            // 非 PG 或 ON CONFLICT 不支持时退化路径：先查后插（幂等语义等价，性能略降）
            try {
                int exists = auditDao.countAuditLog(id);
                if (exists > 0) {
                    log.trace("AuditKafkaConsumer(退化): id={} 已存在, 重放跳过", id);
                } else {
                    auditDao.insertAuditLog(id, userId, username, action, resource, result, detail, ip);
                }
                fillGovernance(id, evt);
                fillTraceId(id, evt);
            } catch (Exception inner) {
                log.error("AuditKafkaConsumer 落库失败(退化) id={}: {}", id, inner.getMessage());
            }
        }
    }

    /**
     * 链路标识回填：优先取发布端随事件带的 {@code traceId}（异步消费线程没有请求 MDC），
     * 缺失即留空——禁在消费端编造链路标识。
     */
    private void fillTraceId(String id, Map<String, Object> evt) {
        String traceId = asStr(evt.get("traceId"));
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        try {
            auditDao.updateTraceId(id, traceId);
        } catch (RuntimeException e) {
            log.warn("AuditKafkaConsumer: trace_id 回填失败 id={} traceId={}: {}", id, traceId, e.getMessage());
        }
    }

    /**
     * 补写治理审计列（§2.9.4③；#147M）。白名单提取后参数化 UPDATE；失败只记日志，
     * 不回滚已落库的基础行（治理列缺失 ≠ 审计事件丢失）。
     */
    private void fillGovernance(String id, Map<String, Object> evt) {
        try {
            Map<String, Object> cols = com.chinacreator.gzcm.common.audit.AuditGovernancePayload.extract(evt);
            if (cols.isEmpty()) {
                return;
            }
            int updated = auditDao.fillGovernanceColumns(id, cols);
            log.debug("AuditKafkaConsumer: 治理列补写 id={} 列数={} 影响行={}", id, cols.size(), updated);
        } catch (Exception e) {
            log.warn("AuditKafkaConsumer: 治理列补写失败 id={}: {}", id, e.getMessage());
        }
    }

    private static String asStr(Object o) {
        return o == null ? null : o.toString();
    }

    /**
     * 由 {@code eventId} 派生确定性主键（#147BD）。
     *
     * <p>FNV-1a 64 位散列取 17 位十进制、前缀 {@code 9}：同 eventId 恒得同 id ⇒ 重放幂等；
     * 前缀 9 与发布端「毫秒×1000 + 随机」型 id（首位为 1）不相撞。</p>
     */
    static String deriveIdFromEventId(String eventId) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < eventId.length(); i++) {
            h ^= eventId.charAt(i);
            h *= 0x100000001b3L;
        }
        return "9" + String.format("%017d", (h & Long.MAX_VALUE) % 100_000_000_000_000_000L);
    }

    /** 暴露给单元测试（@VisibleForTesting）。 */
    public static final String TOPIC = KafkaTopics.AUDIT;
}
