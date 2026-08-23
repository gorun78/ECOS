package com.chinacreator.gzcm.runtime.core.alert;

import java.sql.Timestamp;

/**
 * 告警记录实体
 * 
 * @author CDRC Runtime Team
 */
public class AlertRecord {
    
    private String alertId;
    private String ruleId;
    private String ruleName;
    private String alertType;  // 告警类型
    private String alertLevel;  // 告警级别：P0, P1, P2, P3
    private String nodeId;  // 节点ID
    private String taskId;  // 任务ID
    private String alertMessage;  // 告警消息
    private String alertStatus;  // 告警状态：PENDING, NOTIFIED, RESOLVED, IGNORED
    private Timestamp alertTime;  // 告警时间
    private Timestamp resolveTime;  // 解决时间
    private String resolveBy;  // 解决人
    private String resolveNote;  // 解决备注
    private Integer notificationCount;  // 通知次数
    private Timestamp lastNotificationTime;  // 最后通知时间
    
    // Getters and Setters
    public String getAlertId() {
        return alertId;
    }
    
    public void setAlertId(String alertId) {
        this.alertId = alertId;
    }
    
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
    
    public String getAlertType() {
        return alertType;
    }
    
    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }
    
    public String getAlertLevel() {
        return alertLevel;
    }
    
    public void setAlertLevel(String alertLevel) {
        this.alertLevel = alertLevel;
    }
    
    public String getNodeId() {
        return nodeId;
    }
    
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
    
    public String getTaskId() {
        return taskId;
    }
    
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
    
    public String getAlertMessage() {
        return alertMessage;
    }
    
    public void setAlertMessage(String alertMessage) {
        this.alertMessage = alertMessage;
    }
    
    public String getAlertStatus() {
        return alertStatus;
    }
    
    public void setAlertStatus(String alertStatus) {
        this.alertStatus = alertStatus;
    }
    
    public Timestamp getAlertTime() {
        return alertTime;
    }
    
    public void setAlertTime(Timestamp alertTime) {
        this.alertTime = alertTime;
    }
    
    public Timestamp getResolveTime() {
        return resolveTime;
    }
    
    public void setResolveTime(Timestamp resolveTime) {
        this.resolveTime = resolveTime;
    }
    
    public String getResolveBy() {
        return resolveBy;
    }
    
    public void setResolveBy(String resolveBy) {
        this.resolveBy = resolveBy;
    }
    
    public String getResolveNote() {
        return resolveNote;
    }
    
    public void setResolveNote(String resolveNote) {
        this.resolveNote = resolveNote;
    }
    
    public Integer getNotificationCount() {
        return notificationCount;
    }
    
    public void setNotificationCount(Integer notificationCount) {
        this.notificationCount = notificationCount;
    }
    
    public Timestamp getLastNotificationTime() {
        return lastNotificationTime;
    }
    
    public void setLastNotificationTime(Timestamp lastNotificationTime) {
        this.lastNotificationTime = lastNotificationTime;
    }
}

