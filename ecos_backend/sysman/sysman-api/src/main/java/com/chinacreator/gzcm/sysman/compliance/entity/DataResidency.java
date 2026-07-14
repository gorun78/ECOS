package com.chinacreator.gzcm.sysman.compliance.entity;

import java.util.Date;

/**
 * 数据区域标记实体
 */
public class DataResidency {
    private String residencyId;
    private String resourceId;
    private String resourceType;  // TABLE, DATASOURCE, API等
    private String region;  // CN, US, EU等
    private String tenantId;
    private Date createdTime;
    private String createdBy;

    public String getResidencyId() {
        return residencyId;
    }

    public void setResidencyId(String residencyId) {
        this.residencyId = residencyId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
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

