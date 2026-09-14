package com.chinacreator.gzcm.sysman.iam.entity;

import java.util.Date;

/**
 * 机构权限实体类
 * 对应数据库表：TD_ORG_PERMISSION
 * 
 * @author CDRC Security Team
 */
public class OrgPermission {
    
    /**
     * 权限ID
     */
    private String permissionId;
    
    /**
     * 机构ID
     */
    private String orgId;
    
    /**
     * 资源ID
     */
    private String resourceId;
    
    /**
     * 操作（READ, WRITE, DELETE等）
     */
    private String action;
    
    /**
     * 是否继承父机构权限（0-否，1-是）
     */
    private String inheritFromParent;
    
    /**
     * 创建时间
     */
    private Date createdTime;
    
    /**
     * 创建者ID
     */
    private String createdBy;
    
    // Getters and Setters
    
    public String getPermissionId() {
        return permissionId;
    }
    
    public void setPermissionId(String permissionId) {
        this.permissionId = permissionId;
    }
    
    public String getOrgId() {
        return orgId;
    }
    
    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }
    
    public String getResourceId() {
        return resourceId;
    }
    
    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public String getInheritFromParent() {
        return inheritFromParent;
    }
    
    public void setInheritFromParent(String inheritFromParent) {
        this.inheritFromParent = inheritFromParent;
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

