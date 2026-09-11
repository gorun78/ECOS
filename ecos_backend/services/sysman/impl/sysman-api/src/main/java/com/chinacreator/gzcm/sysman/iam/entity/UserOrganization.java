package com.chinacreator.gzcm.sysman.iam.entity;

import java.util.Date;

/**
 * 用户机构关联实体类
 * 对应数据库表：TD_USER_ORGANIZATION
 * 
 * @author CDRC Security Team
 */
public class UserOrganization {
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 机构ID
     */
    private String orgId;
    
    /**
     * 是否主机构（0-否，1-是）
     */
    private String isPrimary;
    
    /**
     * 创建时间
     */
    private Date createdTime;
    
    /**
     * 创建者ID
     */
    private String createdBy;
    
    // Getters and Setters
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getOrgId() {
        return orgId;
    }
    
    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }
    
    public String getIsPrimary() {
        return isPrimary;
    }
    
    public void setIsPrimary(String isPrimary) {
        this.isPrimary = isPrimary;
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

