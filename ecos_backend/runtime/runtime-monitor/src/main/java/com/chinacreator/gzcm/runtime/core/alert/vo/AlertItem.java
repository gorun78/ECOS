package com.chinacreator.gzcm.runtime.core.alert.vo;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 告警中心列表行（§D.5.3 AlertPage.items 元素；字段名与 D 章 AlertPage schema 对齐）。
 */
public class AlertItem {

    private String id;
    private String ruleCode;
    private String severity;
    /** open|acked|closed */
    private String status;
    private String title;
    private Map<String, Object> detail;
    private String sourceModule;
    private String traceId;
    private String ackBy;
    private LocalDateTime ackTime;
    private LocalDateTime closeTime;
    private LocalDateTime occurredAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Map<String, Object> getDetail() {
        return detail;
    }

    public void setDetail(Map<String, Object> detail) {
        this.detail = detail;
    }

    public String getSourceModule() {
        return sourceModule;
    }

    public void setSourceModule(String sourceModule) {
        this.sourceModule = sourceModule;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getAckBy() {
        return ackBy;
    }

    public void setAckBy(String ackBy) {
        this.ackBy = ackBy;
    }

    public LocalDateTime getAckTime() {
        return ackTime;
    }

    public void setAckTime(LocalDateTime ackTime) {
        this.ackTime = ackTime;
    }

    public LocalDateTime getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(LocalDateTime closeTime) {
        this.closeTime = closeTime;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    /** 包络序列化友好：单对象转 detail map（detail_json 已在 item 展开，去重列） */
    public Map<String, Object> toDetailMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("ruleCode", ruleCode);
        m.put("severity", severity);
        m.put("status", status);
        m.put("title", title);
        m.put("sourceModule", sourceModule);
        m.put("traceId", traceId);
        m.put("occurredAt", occurredAt);
        return m;
    }
}
