package com.chinacreator.gzcm.runtime.core.alert.mapper.entity;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 告警规则持久化实体（V241 public.ecos_runtime_alert_rule，详细设计-00 §6.2）。
 * <p>
 * 与控制域主控制 schema 表结构一一对应（MC01 应用侧 UUID 主键、MC02 白名单类型）。
 * 唯一写方 = runtime-core {@code AlertServiceImpl}；读方 = runtime-monitor 告警端点。
 */
public class AlertRuleRow {

    /** MC01：应用侧 UUID，DDL 无默认值 */
    private String id;
    private String ruleCode;
    private String metricKey;
    /** info|warn|error|critical */
    private String severity;
    private BigDecimal thresholdNum;
    private Integer windowSec;
    private Short upgradeMin;
    /** 0|1（MC02 布尔 SMALLINT） */
    private Short enabled;
    /** log|webhook */
    private String channel;
    private String webhookUrl;
    private Timestamp createTime;
    private Timestamp updateTime;
    private String createBy;
    private String updateBy;
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

    public String getMetricKey() {
        return metricKey;
    }

    public void setMetricKey(String metricKey) {
        this.metricKey = metricKey;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public BigDecimal getThresholdNum() {
        return thresholdNum;
    }

    public void setThresholdNum(BigDecimal thresholdNum) {
        this.thresholdNum = thresholdNum;
    }

    public Integer getWindowSec() {
        return windowSec;
    }

    public void setWindowSec(Integer windowSec) {
        this.windowSec = windowSec;
    }

    public Short getUpgradeMin() {
        return upgradeMin;
    }

    public void setUpgradeMin(Short upgradeMin) {
        this.upgradeMin = upgradeMin;
    }

    public Short getEnabled() {
        return enabled;
    }

    public void setEnabled(Short enabled) {
        this.enabled = enabled;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
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

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
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
