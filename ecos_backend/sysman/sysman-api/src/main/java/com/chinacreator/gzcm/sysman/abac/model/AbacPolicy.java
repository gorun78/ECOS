package com.chinacreator.gzcm.sysman.abac.model;

import java.time.LocalDateTime;

/**
 * ABAC 策略实体
 */
public class AbacPolicy {
    private String policyId;
    private String policyName;
    private String subjectCondition;
    private String resourceCondition;
    private String actionCondition;
    private String environmentCondition;
    private String effect;  // ALLOW / DENY
    private Integer priority;
    private String scopeType;  // GLOBAL / TENANT / ORG
    private String scopeId;    // tenant_id or org_id
    private LocalDateTime createdTime;

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

    public String getSubjectCondition() {
        return subjectCondition;
    }

    public void setSubjectCondition(String subjectCondition) {
        this.subjectCondition = subjectCondition;
    }

    public String getResourceCondition() {
        return resourceCondition;
    }

    public void setResourceCondition(String resourceCondition) {
        this.resourceCondition = resourceCondition;
    }

    public String getActionCondition() {
        return actionCondition;
    }

    public void setActionCondition(String actionCondition) {
        this.actionCondition = actionCondition;
    }

    public String getEnvironmentCondition() {
        return environmentCondition;
    }

    public void setEnvironmentCondition(String environmentCondition) {
        this.environmentCondition = environmentCondition;
    }

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }
}


