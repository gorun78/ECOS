package com.chinacreator.gzcm.sysman.iam.entity;

import java.util.Date;

/**
 * 租户实体
 */
public class Tenant {
    private String tenantId;
    private String tenantName;
    private String tenantCode;
    private String status;      // ACTIVE/INACTIVE/DELETED
    private String description;
    
    // 资源配额
    private Integer maxUsers;           // 最大用户数
    private Integer maxConcurrentUsers;  // 最大并发用户数
    private Long maxStorage;            // 最大存储空间（字节）
    private Long maxApiCallsPerDay;     // 每日最大API调用次数
    private Integer maxOrganizations;    // 最大机构数
    private Integer maxRoles;            // 最大角色数
    
    // 租户配置
    private String isolationMode;       // 隔离模式：DATABASE/SCHEMA/TABLE_PREFIX
    private String databaseName;         // 数据库名称（用于DATABASE隔离）
    private String schemaName;           // Schema名称（用于SCHEMA隔离）
    private String tablePrefix;          // 表前缀（用于TABLE_PREFIX隔离）
    
    private Date createdTime;
    private String createdBy;
    private Date updatedTime;
    private String updatedBy;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(Integer maxUsers) {
        this.maxUsers = maxUsers;
    }

    public Long getMaxStorage() {
        return maxStorage;
    }

    public void setMaxStorage(Long maxStorage) {
        this.maxStorage = maxStorage;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Date createdTime) {
        this.createdTime = createdTime;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Date getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(Date updatedTime) {
        this.updatedTime = updatedTime;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getMaxConcurrentUsers() {
        return maxConcurrentUsers;
    }

    public void setMaxConcurrentUsers(Integer maxConcurrentUsers) {
        this.maxConcurrentUsers = maxConcurrentUsers;
    }

    public Long getMaxApiCallsPerDay() {
        return maxApiCallsPerDay;
    }

    public void setMaxApiCallsPerDay(Long maxApiCallsPerDay) {
        this.maxApiCallsPerDay = maxApiCallsPerDay;
    }

    public Integer getMaxOrganizations() {
        return maxOrganizations;
    }

    public void setMaxOrganizations(Integer maxOrganizations) {
        this.maxOrganizations = maxOrganizations;
    }

    public Integer getMaxRoles() {
        return maxRoles;
    }

    public void setMaxRoles(Integer maxRoles) {
        this.maxRoles = maxRoles;
    }

    public String getIsolationMode() {
        return isolationMode;
    }

    public void setIsolationMode(String isolationMode) {
        this.isolationMode = isolationMode;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public void setTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }
}


