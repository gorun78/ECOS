package com.chinacreator.gzcm.sysman.datapermission.model;

import java.util.Date;
import java.util.List;

/**
 * 列级数据权限策略：为指定资源定义可访问列集合。
 */
public class ColumnLevelPolicy {

    private String policyId;
    private String policyName;
    /**
     * 作用资源，如逻辑表名、视图名或业务资源编码
     */
    private String resource;
    /**
     * 允许访问的列名列表（使用逻辑列名，后续可映射到物理列）
     */
    private List<String> allowedColumns;
    private Date createdTime;
    private String createdBy;

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public List<String> getAllowedColumns() {
        return allowedColumns;
    }

    public void setAllowedColumns(List<String> allowedColumns) {
        this.allowedColumns = allowedColumns;
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
}


