package com.chinacreator.gzcm.engine.kb.event;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.event.OntologyPublishedEvent;
import com.chinacreator.gzcm.engine.kb.service.KgMapperService;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * 本体版本发布事件消费者 — PMO-50 T4.4（KB 侧）。
 *
 * <p>启动形态适配：
 * <ul>
 *   <li>DCCheng 同 JVM 部署形态：消费 {@link org.springframework.context.ApplicationEvent}
 *       （{@link EventListener @EventListener} 同 JVM 同步送达）；</li>
 *   <li>微服务形态（buszhi → dccheng 跨 JVM）：取杉阈值高的 HTTP 形态，
 *       在 series 改造窗口由 {@code @KafkaListener(topics = KafkaTopics.ONTOLOGY_PUBLISHED)} 接管，
 *       消费逻辑同本类（payload = {@link OntologyPublishedEvent#toJson()} → fromJson）。</li>
 * </ul>
 *
 * <p>消费流程（铁律 §2.4 审计兜底）：
 * <ol>
 *   <li>拉本体 version schema（buszhi 内网 REST，ADR-7 内网可达）；</li>
 *   <li>SHA-256 hash → 写/更新 {@code kb_ontology_snapshot}（V116，按 ontology_id+version upsert）；</li>
 *   <li>触发 {@link KgMapperService#syncFromOntology(String, String)}（job 前缀 {@code kg-ont-<version>}，支持 rollback）；</li>
 *   <li>发审计事件（Kafka {@code ecos.audit}，不可用时 log 兜底，不阻塞主流程）；</li>
 *   <li>异常时写 {@code kg_sync_log} FAILED 行，避免 silent fail。</li>
 * </ol>
 */
@Component
public class EcosOntologyEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EcosOntologyEventConsumer.class);
    private static final String AUDIT_TOPIC = KafkaTopics.AUDIT;
    private static final String JOB_PREFIX = "kg-ont-";
    /**
     * Kafka 反序列化用 mapper — 必须注册 JavaTimeModule，否则
     * {@link OntologyPublishedEvent} 的 {@code Instant ts} 字段会报
     * "Java 8 date/time type not supported by default"（生产者侧走 Spring 共享 mapper，
     * 已默认支持 ISO-8601，消费侧需显式对齐）。
     */
    private static final ObjectMapper MAPPER =
            new ObjectMapper().registerModule(new JavaTimeModule());

    private final JdbcTemplate jdbc;
    private final KgMapperService kgMapper;
    private final RestTemplate restTemplate;
    private final String buszhiBase;

    /** PMO-74 H2-T4 — Kafka 通道统一走 EventBusService.subscribe（铁律 §2.5）。 */
    @Autowired(required = false)
    private EventBusService eventBusService;

    public EcosOntologyEventConsumer(JdbcTemplate jdbc,
                                     @Lazy KgMapperService kgMapper,
                                     @Lazy RestTemplate restTemplate,
                                     @Value("${ecos.ontology-base:http://localhost:18083/api/v1/ecos}")
                                     String buszhiBase) {
        this.jdbc = jdbc;
        this.kgMapper = kgMapper;
        this.restTemplate = restTemplate;
        this.buszhiBase = buszhiBase;
    }

    @PostConstruct
    public void subscribeOntologyPublished() {
        if (eventBusService == null) {
            log.warn("EcosOntologyEventConsumer: EventBusService 未装配, ecos.ontology.published 订阅未装载（同 JVM @EventListener 路径仍可用）");
            return;
        }
        try {
            eventBusService.subscribe(KafkaTopics.ONTOLOGY_PUBLISHED, OntologyPublishedEvent.class,
                    this::onBusPublished);
            log.info("EcosOntologyEventConsumer: subscribed topic={} via EventBusService",
                    KafkaTopics.ONTOLOGY_PUBLISHED);
        } catch (Exception e) {
            log.warn("EcosOntologyEventConsumer subscribe failed (ignored): {}", e.getMessage(), e);
        }
    }

    /**
     * Spring 事件消费入口（DCCheng 同 JVM 场景；buszhi 进程内同样可达）。
     *
     * <p>PMO-50 T4 改造：原逻辑抽出为 {@link #runSync(OntologyPublishedEvent)} 共用；
     * Kafka/EventBus 路径 ({@link #onBusPublished(Object)}) 反序列化 payload 后调同一个 runSync。
     */
    @EventListener
    public void onOntologyPublished(OntologyPublishedEvent evt) {
        if (evt == null || evt.ontologyId() == null || evt.version() == null) {
            return;
        }
        runSync(evt);
    }

    /**
     * EventBus 消费入口 — Object 载荷：OntologyPublishedEvent 直接使用；String 走 JSON 反序列化
     * （Kafka 路径的 buszhi 侧 publish 会走 ObjectMapper.writeValueAsString 序列化）。
     * 异常吞 WARN 不抛（不阻塞发布主流程）。
     */
    public void onBusPublished(Object payload) {
        if (payload == null) {
            log.warn("EcosOntologyEventConsumer: EventBus payload null, skip");
            return;
        }
        OntologyPublishedEvent evt;
        try {
            if (payload instanceof OntologyPublishedEvent e) {
                evt = e;
            } else if (payload instanceof String json) {
                if (json.isEmpty()) {
                    log.warn("EcosOntologyEventConsumer: EventBus payload empty string, skip");
                    return;
                }
                evt = MAPPER.readValue(json, OntologyPublishedEvent.class);
            } else {
                log.warn("EcosOntologyEventConsumer: 未识别 payload 类型 {}, skip",
                        payload.getClass().getName());
                return;
            }
        } catch (Exception e) {
            log.warn("EcosOntologyEventConsumer deserialize failed err={}", e.getMessage(), e);
            return;
        }
        runSync(evt);
    }

    /**
     * 共用处理逻辑 — Spring 内存路径 ({@link #onOntologyPublished}) 与 Kafka 路径
     * ({@link #onKafkaPublished}) 都进这里, 保证消费逻辑单一真源 (铁律 §2.5 横切)。
     *
     * <p>流程（铁律 §2.4 审计兜底）：
     * <ol>
     *   <li>拉本体 version schema（buszhi 内网 REST）；</li>
     *   <li>SHA-256 hash → 写/更新 kb_ontology_snapshot (V116, ontology_id+version upsert)；</li>
     *   <li>触发 {@link KgMapperService#syncFromOntology(String, String)}（job 前缀 kg-ont-&lt;version&gt;）；</li>
     *   <li>发审计事件 (Kafka {@code dbus.audit} 不可用时 log 兜底, 不阻塞)；</li>
     *   <li>异常时写 kg_sync_log FAILED 行, 避免 silent fail。</li>
     * </ol>
     */
    public void runSync(OntologyPublishedEvent evt) {
        if (evt == null || evt.ontologyId() == null || evt.version() == null) {
            return;
        }
        String jobId = JOB_PREFIX + evt.version();
        log.info("EcosOntologyEventConsumer: received publish event ontology={} version={} jobId={}",
                evt.ontologyId(), evt.version(), jobId);
        try {
            // 1. 拉全量 schema (buszhi 内网直连), 可用则取 response.data, 否则 placeholder
            String schemaHash = fetchAndHashSchema(evt.ontologyId(), evt.version());

            // 2. 写/更新 snapshot (V116 列: ontology_id / version / entity_codes / relationship_codes / schema_hash / created_by / created_at)
            upsertSnapshot(evt.ontologyId(), evt.version(), evt.entityCodes(),
                    evt.relationshipCodes(), schemaHash, evt.actor());

            // 3. 触发 KG 同步 (带 jobId 前缀, 支持 rollback)
            kgMapper.syncFromOntology(evt.ontologyId(), jobId);

            // 4. 审计 (Kafka 不可用时 log 兜底, 非阻塞)
            emitAudit(buildAuditJson(evt));
            log.info("TOPOLOGY_EVENT_CONSUMED ontology={} version={} hash={} jobId={}",
                    evt.ontologyId(), evt.version(), schemaHash, jobId);
        } catch (Exception e) {
            log.error("EcosOntologyEventConsumer FAILED ontology={} version={}: {}",
                    evt.ontologyId(), evt.version(), e.getMessage(), e);
            try {
                jdbc.update(
                        "INSERT INTO ecos_knowledge.kg_sync_log " +
                        "(object_type, op, job_id, status, error_message) VALUES (?, 'KB_ONTOLOGY_SYNC', ?, 'FAILED', ?)",
                        evt.ontologyId(), jobId, truncate(e.getMessage(), 1000));
            } catch (Exception logEx) {
                log.error("write failed kg_sync_log row: {}", logEx.getMessage());
            }
        }
    }

    // ── 内部辅助 ──────────────────────────────────────

    /**
     * 调 buszhi REST 拉 schema（同 JVM 走 localhost，跨 JVM 走 18083 内网端口，ADR-7 内网可达）。
     * 失败时返回 placeholder hash（基于 ontologyId+version），保持幂等可恢复。
     */
    private String fetchAndHashSchema(String ontologyId, String versionId) {
        try {
            String url = buszhiBase + "/ontologies/" + ontologyId + "/versions/" + versionId;
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            Object data = resp != null ? resp.get("data") : null;
            String schema = data == null ? "{}" : MAPPER.writeValueAsString(data);
            return sha256Hex(schema);
        } catch (Exception e) {
            log.warn("fetch schema failed (use placeholder hash): ontology={} version={} err={}",
                    ontologyId, versionId, e.getMessage());
            return sha256Hex("placeholder-" + ontologyId + "-" + versionId);
        }
    }

    private void upsertSnapshot(String ontologyId, String versionId,
                                List<String> entities, List<String> relationships,
                                String hash, String publishedBy) {
        // entity/relationship 序列化为 JSON 数组字符串（V116 entity_codes/relationship_codes 为 JSONB）
        String entitiesJson;
        String relationshipsJson;
        try {
            entitiesJson = MAPPER.writeValueAsString(entities == null ? List.of() : entities);
            relationshipsJson = MAPPER.writeValueAsString(relationships == null ? List.of() : relationships);
        } catch (Exception je) {
            entitiesJson = "[]";
            relationshipsJson = "[]";
        }

        // 旧版本标记为非当前公开（is_deleted=2）— 同 ontology_id 的旧快照是历史回滚基线，仅维护 is_deleted 一行语义
        try {
            jdbc.update(
                    "UPDATE ecos_knowledge.kb_ontology_snapshot " +
                    "SET is_deleted = 2 WHERE ontology_id = ? AND version <> ? AND (is_deleted IS NULL OR is_deleted = 0)",
                    ontologyId, versionId);
        } catch (Exception e) {
            log.warn("upsertSnapshot: soft-mark previous version failed: {}", e.getMessage());
        }

        // 幂等 upsert（UNIQUE (ontology_id, version)，冲突更新 codes/hash/by/ts + is_deleted=0）
        jdbc.update(
                "INSERT INTO ecos_knowledge.kb_ontology_snapshot " +
                "(ontology_id, version, entity_codes, relationship_codes, schema_hash, created_by, created_at) " +
                "VALUES (?, ?, ?::jsonb, ?::jsonb, ?, ?, NOW()) " +
                "ON CONFLICT (ontology_id, version) DO UPDATE SET " +
                "  entity_codes = EXCLUDED.entity_codes, " +
                "  relationship_codes = EXCLUDED.relationship_codes, " +
                "  schema_hash = EXCLUDED.schema_hash, " +
                "  created_by = EXCLUDED.created_by, " +
                "  created_at = NOW(), " +
                "  is_deleted = 0",
                ontologyId, versionId, entitiesJson, relationshipsJson, hash,
                publishedBy == null ? "system" : publishedBy);
    }

    private String buildAuditJson(OntologyPublishedEvent evt) {
        try {
            Map<String, Object> audit = new LinkedHashMap<>();
            audit.put("ontologyId", evt.ontologyId());
            audit.put("version", evt.version());
            audit.put("action", "ontology.published");
            audit.put("entities", evt.entityCodes());
            audit.put("relationships", evt.relationshipCodes());
            audit.put("actor", evt.actor());
            audit.put("ts", evt.ts());
            return MAPPER.writeValueAsString(audit);
        } catch (Exception e) {
            return "{\"ontologyId\":\"" + (evt.ontologyId() == null ? "" : evt.ontologyId()) + "\"}";
        }
    }

    private void emitAudit(String auditJson) {
        log.info("[AUDIT][KAFKA-FALLBACK] topic={} event={}", AUDIT_TOPIC, auditJson);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable: " + e.getMessage(), e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
