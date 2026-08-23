package com.chinacreator.gzcm.runtime.core.alert;

import java.sql.Timestamp;
import java.util.List;

/**
 * 告警规则实体
 * 
 * @author CDRC Runtime Team
 */
public class AlertRule {
    
    private String ruleId;
    private String ruleName;
    private String metricType;  // 指标类型：TASK_FAILURE, TASK_TIMEOUT, NODE_OFFLINE, CPU_USAGE等
    private String operator;  // 操作符：>, <, >=, <=, ==
    private Double threshold;  // 阈值
    private String alertLevel;  // 告警级别：P0, P1, P2, P3
    private Integer duration;  // 持续时间（秒）
    private String enabled;  // 是否启用：1-启用，0-禁用
    private List<String> notificationChannels;  // 通知渠道：email, sms, wechat_work, dingtalk, webhook
    private String description;
    private Timestamp createTime;
    private Timestamp updateTime;
    private String createBy;
    private String updateBy;
    
    // Getters and Setters
    public String getRuleId() {
        return ruleId;
    }
    
    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }
    
    public String getRuleName() {
        return ruleName;
    }
    
    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }
    
    public String getMetricType() {
        return metricType;
    }
    
    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }
    
    public String getOperator() {
        return operator;
    }
    
    public void setOperator(String operator) {
        this.operator = operator;
    }
    
    public Double getThreshold() {
        return threshold;
    }
    
    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }
    
    public String getAlertLevel() {
        return alertLevel;
    }
    
    public void setAlertLevel(String alertLevel) {
        this.alertLevel = alertLevel;
    }
    
    public Integer getDuration() {
        return duration;
    }
    
    public void setDuration(Integer duration) {
        this.duration = duration;
    }
    
    public String getEnabled() {
        return enabled;
    }
    
    public void setEnabled(String enabled) {
        this.enabled = enabled;
    }
    
    public List<String> getNotificationChannels() {
        return notificationChannels;
    }
    
    public void setNotificationChannels(List<String> notificationChannels) {
        this.notificationChannels = notificationChannels;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
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
}

