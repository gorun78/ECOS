package com.chinacreator.gzcm.runtime.core.datasource.entity;

import java.sql.Timestamp;

/**
 * 数据源实体类
 * 支持数据库数据源、文件数据源、服务数据源三种类型
 * 
 * @author CDRC Runtime Team
 */
public class DataSourceEntity {
    
    private String datasourceId;
    private String datasourceName;
    private String datasourceType;
    private String orgId;
    private String nodeId;
    private String description;
    private String connectionConfig;
    private String status;
    private String isDefault;
    private Timestamp lastTestTime;
    private String lastTestResult;
    private String lastTestMessage;
    private String createBy;
    private Timestamp createTime;
    private String updateBy;
    private Timestamp updateTime;
    private String tags;
    private String remark;
    
    public String getDatasourceId() {
        return datasourceId;
    }
    
    public void setDatasourceId(String datasourceId) {
        this.datasourceId = datasourceId;
    }
    
    public String getDatasourceName() {
        return datasourceName;
    }
    
    public void setDatasourceName(String datasourceName) {
        this.datasourceName = datasourceName;
    }
    
    public String getDatasourceType() {
        return datasourceType;
    }
    
    public void setDatasourceType(String datasourceType) {
        this.datasourceType = datasourceType;
    }
    
    public String getOrgId() {
        return orgId;
    }
    
    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }
    
    public String getNodeId() {
        return nodeId;
    }
    
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getConnectionConfig() {
        return connectionConfig;
    }
    
    public void setConnectionConfig(String connectionConfig) {
        this.connectionConfig = connectionConfig;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getIsDefault() {
        return isDefault;
    }
    
    public void setIsDefault(String isDefault) {
        this.isDefault = isDefault;
    }
    
    public Timestamp getLastTestTime() {
        return lastTestTime;
    }
    
    public void setLastTestTime(Timestamp lastTestTime) {
        this.lastTestTime = lastTestTime;
    }
    
    public String getLastTestResult() {
        return lastTestResult;
    }
    
    public void setLastTestResult(String lastTestResult) {
        this.lastTestResult = lastTestResult;
    }
    
    public String getLastTestMessage() {
        return lastTestMessage;
    }
    
    public void setLastTestMessage(String lastTestMessage) {
        this.lastTestMessage = lastTestMessage;
    }
    
    public String getCreateBy() {
        return createBy;
    }
    
    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }
    
    public Timestamp getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(Timestamp createTime) {
        this.createTime = createTime;
    }
    
    public String getUpdateBy() {
        return updateBy;
    }
    
    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }
    
    public Timestamp getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(Timestamp updateTime) {
        this.updateTime = updateTime;
    }
    
    public String getTags() {
        return tags;
    }
    
    public void setTags(String tags) {
        this.tags = tags;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
    
    public static class DataSourceType {
        public static final String DATABASE = "DATABASE";
        public static final String FILE = "FILE";
        public static final String SERVICE = "SERVICE";
    }
    
    public static class DataSourceStatus {
        public static final String ACTIVE = "ACTIVE";
        public static final String INACTIVE = "INACTIVE";
        public static final String TESTING = "TESTING";
        public static final String ERROR = "ERROR";
    }
}
