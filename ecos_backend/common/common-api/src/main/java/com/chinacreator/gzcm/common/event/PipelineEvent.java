package com.chinacreator.gzcm.common.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 数据管道事件 — Bus-Zhi 与 Dc-Cheng 的解耦契约。
 * <p>
 * Bus-Zhi（数据集成）完成采集/清洗/转换后发布事件，
 * Dc-Cheng（数据治理）订阅事件，触发元数据更新、质量检查等。
 * <p>
 * 两个模块只依赖此 DTO，不互相引入对方的包。
 */
public class PipelineEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ── 事件类型枚举 ────────────────────────────────

    public enum EventType {
        /** 数据采集完成 */
        COLLECTION_COMPLETED,
        /** 数据清洗完成 */
        CLEANSING_COMPLETED,
        /** 数据转换完成 */
        TRANSFORM_COMPLETED,
        /** 管道执行失败 */
        PIPELINE_FAILED,
        /** 数据源状态变更 */
        DATASOURCE_STATUS_CHANGED
    }

    // ── 字段 ────────────────────────────────────────

    /** 事件唯一 ID */
    private String eventId;

    /** 事件类型 */
    private EventType eventType;

    /** 数据源 ID（来自 Datanet） */
    private String dataSourceId;

    /** 数据源名称 */
    private String dataSourceName;

    /** 关联的任务/管道 ID */
    private String pipelineId;

    /** 事件发生时间 */
    private Instant occurredAt;

    /** 事件携带的附加数据（如行数、表名等） */
    private Map<String, Object> metadata;

    /** 事件来源模块 */
    private String sourceModule;

    // ── 构造器 ──────────────────────────────────────

    public PipelineEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = Instant.now();
    }

    // ── 工厂方法 ────────────────────────────────────

    public static PipelineEvent of(EventType type, String dataSourceId, String dataSourceName) {
        PipelineEvent event = new PipelineEvent();
        event.eventType = type;
        event.dataSourceId = dataSourceId;
        event.dataSourceName = dataSourceName;
        return event;
    }

    public PipelineEvent withPipelineId(String pipelineId) {
        this.pipelineId = pipelineId;
        return this;
    }

    public PipelineEvent withMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
    }

    public PipelineEvent fromModule(String module) {
        this.sourceModule = module;
        return this;
    }

    // ── getter/setter ───────────────────────────────

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public String getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(String dataSourceId) { this.dataSourceId = dataSourceId; }

    public String getDataSourceName() { return dataSourceName; }
    public void setDataSourceName(String dataSourceName) { this.dataSourceName = dataSourceName; }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public String getSourceModule() { return sourceModule; }
    public void setSourceModule(String sourceModule) { this.sourceModule = sourceModule; }

    @Override
    public String toString() {
        return "PipelineEvent{type=" + eventType +
                ", dataSource=" + dataSourceName +
                ", id=" + eventId + '}';
    }
}
