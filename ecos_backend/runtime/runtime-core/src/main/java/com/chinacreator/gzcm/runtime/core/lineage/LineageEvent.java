package com.chinacreator.gzcm.runtime.core.lineage;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据血缘事件 — 描述一次数据操作的来源、目标和变换关系。
 *
 * 由内核在数据管道关键节点（采集、转换、融合、发布）自动生成，
 * 上层 DC-CHENG 模块负责存储、索引、查询和可视化。
 */
public class LineageEvent {

    /** 事件唯一标识 */
    private final String eventId;

    /** 事件时间戳 */
    private final Instant timestamp;

    /** 操作类型：READ / WRITE / TRANSFORM / DERIVE / PUBLISH */
    private final String operationType;

    /** 数据源标识（输入） */
    private final java.util.List<String> sourceIds;

    /** 数据目标标识（输出） */
    private final String targetId;

    /** 变换描述，如 "去重 + 字段映射(身份证→证件号码)" */
    private final String transformation;

    /** 上下文信息：任务ID、用户、模块等 */
    private final Map<String, String> context;

    /** 字段级映射：源字段 → 目标字段 */
    private final Map<String, String> fieldMappings;

    private LineageEvent(Builder builder) {
        this.eventId = builder.eventId;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.operationType = builder.operationType;
        this.sourceIds = Collections.unmodifiableList(
            new java.util.ArrayList<>(builder.sourceIds != null ? builder.sourceIds : java.util.List.of()));
        this.targetId = builder.targetId;
        this.transformation = builder.transformation;
        this.context = Collections.unmodifiableMap(
            new HashMap<>(builder.context != null ? builder.context : Map.of()));
        this.fieldMappings = Collections.unmodifiableMap(
            new HashMap<>(builder.fieldMappings != null ? builder.fieldMappings : Map.of()));
    }

    // ── Getters ──

    public String getEventId() {
        return eventId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getOperationType() {
        return operationType;
    }

    public java.util.List<String> getSourceIds() {
        return sourceIds;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getTransformation() {
        return transformation;
    }

    public Map<String, String> getContext() {
        return context;
    }

    public Map<String, String> getFieldMappings() {
        return fieldMappings;
    }

    @Override
    public String toString() {
        return "LineageEvent{" +
            "eventId='" + eventId + '\'' +
            ", operationType='" + operationType + '\'' +
            ", sources=" + sourceIds +
            ", target='" + targetId + '\'' +
            '}';
    }

    // ── Builder ──

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String eventId;
        private Instant timestamp;
        private String operationType;
        private java.util.List<String> sourceIds;
        private String targetId;
        private String transformation;
        private Map<String, String> context;
        private Map<String, String> fieldMappings;

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder operationType(String operationType) {
            this.operationType = operationType;
            return this;
        }

        public Builder sourceIds(java.util.List<String> sourceIds) {
            this.sourceIds = sourceIds;
            return this;
        }

        public Builder targetId(String targetId) {
            this.targetId = targetId;
            return this;
        }

        public Builder transformation(String transformation) {
            this.transformation = transformation;
            return this;
        }

        public Builder context(Map<String, String> context) {
            this.context = context;
            return this;
        }

        public Builder fieldMappings(Map<String, String> fieldMappings) {
            this.fieldMappings = fieldMappings;
            return this;
        }

        public LineageEvent build() {
            if (eventId == null) {
                eventId = java.util.UUID.randomUUID().toString();
            }
            if (operationType == null) {
                throw new IllegalArgumentException("operationType is required");
            }
            return new LineageEvent(this);
        }
    }
}
