package com.chinacreator.gzcm.runtime.core.alert.mapper.entity;

import java.sql.Timestamp;

/**
 * 告警记录持久化实体（V241 public.ecos_runtime_alert_record，详细设计-00 §6.2）。
 * <p>
 * 状态机：{@code open}（触发）→ {@code acked}（人工 ACK）→ {@code closed}（关闭）；
 * {@code resolveAlert} 语义映射为 {@code closed}（设计任务口径），ACK 单独走
 * {@code ackAlert}（只 mark，不自动 close — 审计兜底类 critical 告警的 ack 必须人工）。
 */
public class AlertRecordRow {

    /** MC01：应用侧 UUID，DDL 无默认值 */
    private String id;
    private String ruleCode;
    /** info|warn|error|critical */
    private String severity;
    /** open|acked|closed */
    private String status;
    /** alertType + message 截 255 */
    private String title;
    private String detailJson;
    private String sourceModule;
    private String traceId;
    private String ackBy;
    private Timestamp ackTime;
    private Timestamp closeTime;
    private Timestamp occurredAt;
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

    public String getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(String detailJson) {
        this.detailJson = detailJson;
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

    public Timestamp getAckTime() {
        return ackTime;
    }

    public void setAckTime(Timestamp ackTime) {
        this.ackTime = ackTime;
    }

    public Timestamp getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(Timestamp closeTime) {
        this.closeTime = closeTime;
    }

    public Timestamp getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Timestamp occurredAt) {
        this.occurredAt = occurredAt;
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
