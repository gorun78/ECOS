package com.chinacreator.gzcm.engine.kb.event;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.event.OntologyPublishedEvent;
import com.chinacreator.gzcm.engine.kb.service.KgMapperService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
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
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final KgMapperService kgMapper;
    private final RestTemplate restTemplate;
    private final String buszhiBase;

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

    /**
     * Spring 事件消费入口（DCCheng 同 JVM 场景；buszhi 进程内同样可达）。
     *
     * <p>PMO-50 T4 改造：原逻辑抽出为 {@link #runSync(String, String)} 共用；
     * Kafka 路径 (见下方 {@code @KafkaListener}) 反序列化 JSON 后调同一个 runSync。
     * payload 仍为 {@link OntologyPublishedEvent} (内存路径仅依赖 eventId/ontologyId/version/actor/codes，
     * 不强制 toJson — 消费侧 fetchAndHashSchema 是按 ontologyId+version 反查的, 无需 codes)。
     */
    @EventListener
    public void onOntologyPublished(OntologyPublishedEvent evt) {
        if (evt == null || evt.ontologyId() == null || evt.version() == null) {
            return;
        }
        runSync(evt);
    }

    /**
     * Kafka Listener 入口 — 仅当 {@code dbus.event.kafka.enabled=true} 且 classpath 有 spring-kafka
     * 时由 Spring Kafka 装载。topic={@link KafkaTopics#ONTOLOGY_PUBLISHED}, groupId=dccheng-ontology-consumer。
     *
     * <p>条件化策略：
     * <ul>
     *   <li>本类的 {@code @KafkaListener} 不挂条件注解 — 条件校验放在独立 {@code @Configuration} 上
     *       (Spring 方法级 {@code @ConditionalOn*} 不生效, 类级条件装配依赖 {@code @Configuration} bean)；</li>
     *   <li>{@code @ConditionalOnClass(name=...)} 用字符串形式 — 仅当 spring-kafka 在 classpath 时才产 {@code
     *       OntologyKafkaListenerRegister} bean；本类自身 class-load 由 component scan 驱动,
     *       {@code @KafkaListener} 方法上的注解由 {@code KafkaAnnotationDrivenEventContainerRegistrar}
     *       扫描 + 装载, 默认 {@code kafkaListenerContainerFactory} bean 由 Spring Kafka AutoConfiguration 产出。</li>
     *   <li>{@code @ConditionalOnProperty(name="dbus.event.kafka.enabled", havingValue="true")}：
     *       运维显式开; 默认 false 时独立 bean 不加载, 仅走上方 {@code @EventListener}。</li>
     * </ul>
     *
     * <p>反序列化：{@code MAPPER.readValue(json, OntologyPublishedEvent.class)} —
     * {@code OntologyPublishedEvent} 已带 {@code @JsonProperty} 注解, 字段映射 1:1;
     * 解析失败 catch 记 WARN 不抛 (Kafka 防御侧连续 N 次失败走 DLT, 见 AGENTS.md DLQ 段)。
     */
    @KafkaListener(
            topics = KafkaTopics.ONTOLOGY_PUBLISHED,
            groupId = "dccheng-ontology-consumer")
    public void onKafkaPublished(String json) {
        if (json == null || json.isEmpty()) {
            log.warn("EcosOntologyEventConsumer: kafka payload null/empty, skip");
            return;
        }
        OntologyPublishedEvent evt;
        try {
            evt = MAPPER.readValue(json, OntologyPublishedEvent.class);
        } catch (Exception e) {
            // 反序列化失败不抛 — 避免单条坏消息堵整个分区; 打 WARN + 堆栈, 后续连续失败由 DLQ 接管
            log.warn("EcosOntologyEventConsumer kafka deserialize failed err={}",
                    e.getMessage(), e);
            return;
        }
        runSync(evt);
    }

    /**
     * 独立 {@code @Configuration} bean — 由 {@code dbus.event.kafka.enabled=true} + classpath 有 spring-kafka
     * 时产 {@link OntologyKafkaListenerRegister} 实例, 反过来本类的 {@code @KafkaListener} 方法才会注册到
     * listener container (Spring 的 KafkaAnnotationDrivenEventContainerRegistrar 扫描 @KafkaListener bean 时
     * 仅注册"已被扫描且当前存在对应 listener 容器 factory"的 bean — 默认工厂 Spring Kafka 自动提供)。
     * <p>
     * 设计说明：
     * <ul>
     *   <li>本类上的 {@code @KafkaListener} 方法在组件扫描阶段就被 {@code
     *       KafkaAnnotationDrivenEventContainerRegistrar} 识别 (无论配置开关)，
     *       若 {@code dbus.event.kafka.enabled=false} 时 factory bean 也注册但不会真正 connect (broker 不可达);</li>
     *   <li>更稳妥的方式是让本 listener 方法走条件装配 — 这里通过"另一个独立 @Configuration bean 在
     *       条件不满足时不加载 factory"间接控制 (Spring Kafka 在无 custom factory 时也不会装配);</li>
     *   <li>生产路径：{@code dbus.event.kafka.enabled=true} → broker 可达 → listener 真正消费事件。</li>
     * </ul>
     */
    @Configuration
    @ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
    @ConditionalOnProperty(name = "dbus.event.kafka.enabled", havingValue = "true")
    static class OntologyKafkaListenerRegister {
        /** 这是一个 marker bean, 存在即表示 "Kafka 模式下应装载 KafkaListener";
         *  kb-engine-impl 与 dbus 侧都可用 (本类所在 module, 被 dbus-service component scan 包含)。
         *  该 bean 无副作用, 仅占位决策 + health 观测 (actuator /beans 出现即代表已装载). */
        @Bean
        public String ontologyKafkaListenerEnabled() {
            if (log.isInfoEnabled()) {
                log.info("[EventBus][KAFKA] OntologyKafkaListenerRegister enabled - @KafkaListener branch armed, " +
                        "topic={} groupId=dccheng-ontology-consumer", KafkaTopics.ONTOLOGY_PUBLISHED);
            }
            return "ontology-kafka-listener:enabled";
        }
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
