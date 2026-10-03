package com.chinacreator.gzcm.runtime.core.alert.vo;

import java.time.LocalDateTime;

/**
 * DLQ 列表行（§D.5.3 listDeadLetterEvents 行结构，查 ecos_runtime_event_dlq）。
 */
public class DltItem {

    private String id;
    private String topic;
    private String dltTopic;
    private String payload;
    private String errorMessage;
    private int attempts;
    private LocalDateTime firstSeenAt;
    private LocalDateTime replayedAt;
    /** pending|replayed|discarded */
    private String status;
    private String traceId;
    private LocalDateTime createTime;

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

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public LocalDateTime getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(LocalDateTime firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public LocalDateTime getReplayedAt() {
        return replayedAt;
    }

    public void setReplayedAt(LocalDateTime replayedAt) {
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
