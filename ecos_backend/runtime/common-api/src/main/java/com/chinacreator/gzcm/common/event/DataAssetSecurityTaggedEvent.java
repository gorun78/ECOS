package com.chinacreator.gzcm.common.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * 数据资产安全打标事件 — data-engine (土·D) 与 security-engine (护) 的解耦契约。
 *
 * <p>PMO-data10 引入。Payload 走 Kafka {@value KafkaTopics#DATA_SECURITY_TAGGED}
 * topic，亦兼容 Spring {@code @EventListener} 内存路径。
 *
 * <p>发布侧 (data-engine {@code AssetService.tagAssetFields})：
 * <pre>{@code
 * DataAssetSecurityTaggedEvent evt = new DataAssetSecurityTaggedEvent(
 *     eventId, assetId, resourceId, tableName, resourceType, domain,
 *     sensitivityLevel, fields, operator, Instant.now());
 * eventBusService.publish(KafkaTopics.DATA_SECURITY_TAGGED, evt);
 * }</pre>
 *
 * <p>消费侧 (security-engine {@code DataSecurityLevelEventListener})：
 * <ul>
 *   <li>{@code EventBusService.subscribe} (编程式) — 覆盖 Kafka + 内存双路径；</li>
 *   <li>L3/L4 字段 → 生成/更新 {@code ecos_cls_policy}（敏感列 → blocked + 脱敏策略）；</li>
 *   <li>L4 资产 → 生成 {@code ecos_rls_policy}（clearance_level >= 4 准入）。</li>
 * </ul>
 *
 * <p>载荷语义（安全红线 ST03）：
 * <ul>
 *   <li>{@code sensitivityLevel} — 资产级敏感度（取字段级最大值，发布前 MAX 计算）；</li>
 *   <li>{@code fields} — 命中字段明细<b>仅含字段名 + 类型 + 敏感度 + 脱敏策略</b>，
 *       不存敏感值；实际脱敏由 {@code DataMaskingService} 行级 regex 执行；</li>
 *   <li>{@code confirmed=true} 才发（安全阀：LLM 推荐仅写 recommend_level，人工确认才落库）。</li>
 * </ul>
 *
 * @author ECOS-PMO
 * @since 1.0.0
 */
public class DataAssetSecurityTaggedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String eventId;
    private final String assetId;
    private final String resourceId;
    private final String tableName;
    private final String resourceType;
    private final String domain;
    private final String sensitivityLevel;
    private final Integer confirmedFieldCount;
    private final List<TaggedField> fields;
    private final String operator;
    private final Instant ts;

    /**
     * 全参构造器 — 同时作为 Jackson 反序列化入口
     * （与 {@link OntologyPublishedEvent} 同款约定：字段 final 且需 {@code @JsonCreator}）。
     */
    @JsonCreator
    public DataAssetSecurityTaggedEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("assetId") String assetId,
            @JsonProperty("resourceId") String resourceId,
            @JsonProperty("tableName") String tableName,
            @JsonProperty("resourceType") String resourceType,
            @JsonProperty("domain") String domain,
            @JsonProperty("sensitivityLevel") String sensitivityLevel,
            @JsonProperty("confirmedFieldCount") Integer confirmedFieldCount,
            @JsonProperty("fields") List<TaggedField> fields,
            @JsonProperty("operator") String operator,
            @JsonProperty("ts") Instant ts) {
        this.eventId = eventId;
        this.assetId = assetId;
        this.resourceId = resourceId;
        this.tableName = tableName;
        this.resourceType = resourceType;
        this.domain = domain;
        this.sensitivityLevel = sensitivityLevel;
        this.confirmedFieldCount = confirmedFieldCount;
        this.fields = fields == null ? List.of() : fields;
        this.operator = operator;
        this.ts = ts;
    }

    public String getEventId() { return eventId; }
    public String getAssetId() { return assetId; }
    public String getResourceId() { return resourceId; }
    public String getTableName() { return tableName; }
    public String getResourceType() { return resourceType; }
    public String getDomain() { return domain; }
    public String getSensitivityLevel() { return sensitivityLevel; }
    public Integer getConfirmedFieldCount() { return confirmedFieldCount; }
    public List<TaggedField> getFields() { return fields; }
    public String getOperator() { return operator; }
    public Instant getTs() { return ts; }

    /** toString 不暴露字段明细（安全日志脱敏，ST03）。 */
    @Override
    public String toString() {
        return "DataAssetSecurityTaggedEvent{assetId=" + assetId +
                ", resourceId=" + resourceId +
                ", level=" + sensitivityLevel +
                ", confirmedFields=" + fields.size() +
                ", operator=" + operator + '}';
    }

    /**
     * 单个命中字段（事件镜像）— 仅含字段名、类型、敏感度、脱敏策略，不存敏感值。
     */
    public static class TaggedField implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String fieldId;
        private final String fieldName;
        private final String dataType;
        private final String fieldSensitivity;
        private final String maskStrategy;

        @JsonCreator
        public TaggedField(
                @JsonProperty("fieldId") String fieldId,
                @JsonProperty("fieldName") String fieldName,
                @JsonProperty("dataType") String dataType,
                @JsonProperty("fieldSensitivity") String fieldSensitivity,
                @JsonProperty("maskStrategy") String maskStrategy) {
            this.fieldId = fieldId;
            this.fieldName = fieldName;
            this.dataType = dataType;
            this.fieldSensitivity = fieldSensitivity;
            this.maskStrategy = maskStrategy;
        }

        public String getFieldId() { return fieldId; }
        public String getFieldName() { return fieldName; }
        public String getDataType() { return dataType; }
        public String getFieldSensitivity() { return fieldSensitivity; }
        public String getMaskStrategy() { return maskStrategy; }

        @Override
        public String toString() {
            return "TaggedField{fieldName=" + fieldName +
                    ", dataType=" + dataType +
                    ", sensitivity=" + fieldSensitivity +
                    ", mask=" + maskStrategy + '}';
        }
    }
}