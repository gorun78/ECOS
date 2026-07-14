package com.chinacreator.gzcm.sysman.security.policy.entity;

import java.util.Date;

/**
 * 数据安全策略实体
 */
public class DataSecurityPolicy {
    private String policyId;
    private String policyName;
    private String policyType;
    private String policyContent;
    private String status;
    private Integer priority;
    /**
     * 是否启用
     */
    private Boolean enabled;
    private Date createdTime;
    private String createdBy;
    private Date updatedTime;
    private String updatedBy;
    private String description;
    private String scope;
    private String scopeId;

    public String getPolicyId() { return policyId; }
    public void setPolicyId(String policyId) { this.policyId = policyId; }
    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }
    public String getPolicyType() { return policyType; }
    public void setPolicyType(String policyType) { this.policyType = policyType; }
    public String getPolicyContent() { return policyContent; }
    public void setPolicyContent(String policyContent) { this.policyContent = policyContent; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Date getCreatedTime() { return createdTime; }
    public void setCreatedTime(Date createdTime) { this.createdTime = createdTime; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Date getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(Date updatedTime) { this.updatedTime = updatedTime; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getScopeId() { return scopeId; }
    public void setScopeId(String scopeId) { this.scopeId = scopeId; }
}
