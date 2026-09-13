package com.chinacreator.gzcm.common.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 本体版本发布事件 — Bus-Zhi (金·I) 与 Dc-Cheng (水·K) 的解耦契约。
 *
 * <p>PMO-50 T4 (T4.4 KB 侧) 引入。Payload 走 Kafka {@value KafkaTopics#ONTOLOGY_PUBLISHED}
 * topic, 亦兼容 Spring {@code @EventListener} 内存路径 (buszhi 进程内 dccheng EcosOntologyEventConsumer)。
 *
 * <p>发布侧调用 ({@code OntologyVersionService#publishVersion})：
 * <pre>{@code
 * OntologyPublishedEvent evt = OntologyPublishedEvent.of(
 *         ontologyId, versionNo, entityCodes, relationshipCodes, actor);
 * appEventPublisher.publishEvent(evt);              // 内存 @EventListener 路径
 * eventBusService.publish(KafkaTopics.ONTOLOGY_PUBLISHED, evt);   // Kafka 路径 (条件化)
 * }</pre>
 *
 * <p>消费侧 ({@code EcosOntologyEventConsumer})：
 * <pre>{@code
 * @EventListener public void onOntologyPublished(OntologyPublishedEvent evt) { ... }
 * @KafkaListener(topics=KafkaTopics.ONTOLOGY_PUBLISHED, ...)
 * public void onKafkaJson(String json) { evt = MAPPER.readValue(json, OntologyPublishedEvent.class); ... }
 * }</pre>
 *
 * <p>载荷语义：
 * <ul>
 *   <li>{@code ontologyId} — 本体 ID (buszhi 域)；</li>
 *   <li>{@code version} — 版本号 (语义化 {@code x.y.z[-rollback]});</li>
 *   <li>{@code entityCodes} / {@code relationshipCodes} — 实体/关系 code 列表 (KB 侧
 *       snapshot 入库用, 空列表表示未填满, 消费侧按 placeholder 兜底)；</li>
 *   <li>{@code actor} — 发布人 (审计字段, 可为 "system");</li>
 *   <li>{@code ts} — 发布时刻 (Instant, Jackson 自动 ISO-8601 序列化)。</li>
 * </ul>
 *
 * <p>不携带 schema 全量 JSON (消费侧按 ontologyId+version 反查 buszhi REST 拉全量,
 * 避免 Kafka 消息体膨胀)。
 *
 * @author ECOS-PMO
 * @since 1.0.0
 */
public class OntologyPublishedEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonProperty("id")
    private final String eventId;
    @JsonProperty("ontologyId")
    private final String ontologyId;
    @JsonProperty("version")
    private final String version;
    @JsonProperty("entityCodes")
    private final List<String> entityCodes;
    @JsonProperty("relationshipCodes")
    private final List<String> relationshipCodes;
    @JsonProperty("actor")
    private final String actor;
    @JsonProperty("ts")
    private final Instant ts;

    public OntologyPublishedEvent(String eventId, String ontologyId, String version,
                                  List<String> entityCodes, List<String> relationshipCodes,
                                  String actor, Instant ts) {
        this.eventId = eventId;
        this.ontologyId = ontologyId;
        this.version = version;
        this.entityCodes = entityCodes == null ? List.of() : entityCodes;
        this.relationshipCodes = relationshipCodes == null ? List.of() : relationshipCodes;
        this.actor = actor;
        this.ts = ts;
    }

    /** 工厂方法 — 无参 defaults, 不要求列表非空 (空列表语义: 未填入快照摘要)。 */
    public static OntologyPublishedEvent of(String ontologyId, String version,
                                            List<String> entityCodes,
                                            List<String> relationshipCodes,
                                            String actor) {
        return new OntologyPublishedEvent(
                UUID.randomUUID().toString(),
                ontologyId,
                version,
                entityCodes,
                relationshipCodes,
                actor,
                Instant.now());
    }

    /** 序列化为 JSON (Kafka 路径消费时复用同一 ObjectMapper)。 */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            // 序列化失败时返回最小骨架 JSON 而非抛 (消费侧按 placeholder 兜底, 不阻塞)
            return "{\"ontologyId\":\"" + (ontologyId == null ? "" : ontologyId) +
                    "\",\"version\":\"" + (version == null ? "" : version) + "\"}";
        }
    }

    // ── getter (record-style accessors for Jackson + 消费侧便捷读取) ──────────────

    public String eventId() { return eventId; }
    public String ontologyId() { return ontologyId; }
    public String version() { return version; }
    public List<String> entityCodes() { return entityCodes; }
    public List<String> relationshipCodes() { return relationshipCodes; }
    public String actor() { return actor; }
    public Instant ts() { return ts; }

    @Override
    public String toString() {
        return "OntologyPublishedEvent{id=" + eventId +
                ", ontology=" + ontologyId + ", version=" + version +
                ", entities=" + entityCodes.size() + ", rels=" + relationshipCodes.size() +
                ", actor=" + actor + ", ts=" + ts + '}';
    }
}
