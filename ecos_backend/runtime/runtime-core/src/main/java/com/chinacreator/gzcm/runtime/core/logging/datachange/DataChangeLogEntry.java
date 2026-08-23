package com.chinacreator.gzcm.runtime.core.logging.datachange;

import java.util.Date;
import java.util.Map;

/**
 * 数据变更日志条目
 */
public class DataChangeLogEntry {
    
    private String id;
    private String tableName;
    private String recordId;
    private String operationType;
    private String operator; // Maybe redundant with userId? Keeping for compatibility if needed.
    private String userId;
    private String tenantId;
    private String traceId;
    private String auditLogId;
    private Date operateTime;

    public Date getOperateTime() {
        return operateTime;
    }

    public void setOperateTime(Date operateTime) {
        this.operateTime = operateTime;
    }

    public Date getChangeTime() {
        return operateTime;
    }

    public void setChangeTime(Date changeTime) {
        this.operateTime = changeTime;
    }
    private Map<String, Object> beforeData;
    private Map<String, Object> afterData;
    
    public String getId() {
        return id;
    }

    public String getLogId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }

    public void setLogId(String id) {
        this.id = id;
    }
    
    public String getTableName() {
        return tableName;
    }
    
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
    
    public String getRecordId() {
        return recordId;
    }
    
    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }
    
    public String getOperationType() {
        return operationType;
    }
    
    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }
    
    public String getOperator() {
        return operator;
    }
    
    public void setOperator(String operator) {
        this.operator = operator;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
    
    public String getTraceId() {
        return traceId;
    }
    
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
    
    public String getAuditLogId() {
        return auditLogId;
    }
    
    public void setAuditLogId(String auditLogId) {
        this.auditLogId = auditLogId;
    }
    
    public Map<String, Object> getBeforeData() {
        return beforeData;
    }
    
    public void setBeforeData(Map<String, Object> beforeData) {
        this.beforeData = beforeData;
    }
    
    public Map<String, Object> getAfterData() {
        return afterData;
    }
    
    public void setAfterData(Map<String, Object> afterData) {
        this.afterData = afterData;
    }
}
