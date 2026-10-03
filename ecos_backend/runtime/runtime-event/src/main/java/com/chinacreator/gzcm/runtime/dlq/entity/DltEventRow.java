package com.chinacreator.gzcm.runtime.dlq.entity;

import java.sql.Timestamp;

/**
 * Kafka DLQ 持久化实体（V242 public.ecos_runtime_event_dlq，详细设计-00 §6.2）。
 * <p>写方唯一 = runtime-event {@code DltConsumer}（消费 {@code <topic>.DLT}）；
 * 重放 = {@code DltReplayService.replay}（attempts+1、status=replayed、replayed_at=now）。
 */
public class DltEventRow {

    /** MC01：应用侧 UUID，DDL 无默认值 */
    private String id;
    /** 原始业务 topic（.DLT 前缀去掉后的源 topic；重放目标） */
    private String topic;
    /** 死信 topic（<topic>.DLT） */
    private String dltTopic;
    /** 原始 JSON 串，禁 JSONB 直操（MC02） */
    private String payload;
    private String errorMessage;
    private Short attempts;
    private Timestamp firstSeenAt;
    private Timestamp replayedAt;
    /** pending|replayed|discarded */
    private String status;
    private String traceId;
    private Timestamp createTime;
    private Timestamp updateTime;
    private String versionNo;
    private Short isDeleted;
    private String domain;
    private String tenantId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getDltTopic() {
        return dltTopic;
    }

    public void setDltTopic(String dltTopic) {
        this.dltTopic = dltTopic;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Short getAttempts() {
        return attempts;
    }

    public void setAttempts(Short attempts) {
        this.attempts = attempts;
    }

    public Timestamp getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(Timestamp firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public Timestamp getReplayedAt() {
        return replayedAt;
    }

    public void setReplayedAt(Timestamp replayedAt) {
        this.replayedAt = replayedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Timestamp getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Timestamp createTime) {
        this.createTime = createTime;
    }

    public Timestamp getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Timestamp updateTime) {
        this.updateTime = updateTime;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(String versionNo) {
        this.versionNo = versionNo;
    }

    public Short getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Short isDeleted) {
        this.isDeleted = isDeleted;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
