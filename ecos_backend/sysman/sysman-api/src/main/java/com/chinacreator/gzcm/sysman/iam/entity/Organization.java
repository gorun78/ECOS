package com.chinacreator.gzcm.sysman.iam.entity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 机构实体类
 * 对应数据库表：TD_ORGANIZATION
 * 
 * @author CDRC Security Team
 */
public class Organization {
    
    /**
     * 机构ID
     */
    private String orgId;
    
    /**
     * 机构名称
     */
    private String orgName;
    
    /**
     * 机构编码（唯一）
     */
    private String orgCode;
    
    /**
     * 父机构ID
     */
    private String parentOrgId;
    
    /**
     * 机构类型
     */
    private String orgType;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
    
    /**
     * 创建者ID
     */
    private String createdBy;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
    
    /**
     * 更新者ID
     */
    private String updatedBy;
    
    /**
     * 状态（ACTIVE, INACTIVE, DELETED）
     */
    private String status;
    
    /**
     * 备注
     */
    private String remark;
    
    /**
     * 子机构列表（用于树形结构）
     */
    private List<Organization> children;
    
    /**
     * 父机构路径（用于快速查询）
     */
    private String path;
    
    // Getters and Setters
    
    public String getOrgId() {
        return orgId;
    }
    
    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }
    
    public String getOrgName() {
        return orgName;
    }
    
    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }
    
    public String getOrgCode() {
        return orgCode;
    }
    
    public void setOrgCode(String orgCode) {
        this.orgCode = orgCode;
    }
    
    public String getParentOrgId() {
        return parentOrgId;
    }
    
    public void setParentOrgId(String parentOrgId) {
        this.parentOrgId = parentOrgId;
    }
    
    public String getOrgType() {
        return orgType;
    }
    
    public void setOrgType(String orgType) {
        this.orgType = orgType;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public LocalDateTime getCreatedTime() {
        return createdTime;
    }
    
    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }
    
    public String getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    
    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }
    
    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }
    
    public String getUpdatedBy() {
        return updatedBy;
    }
    
    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
    
    public List<Organization> getChildren() {
        return children;
    }
    
    public void setChildren(List<Organization> children) {
        this.children = children;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
}

