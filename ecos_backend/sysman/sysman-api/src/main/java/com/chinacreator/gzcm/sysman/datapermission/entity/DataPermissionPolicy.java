package com.chinacreator.gzcm.sysman.datapermission.entity;

import java.util.Date;

/**
 * 数据权限策略实体，对应表 td_data_permission_policy。
 */
public class DataPermissionPolicy {

    private String policyId;
    private String policyName;
    /**
     * 策略类型：ROW/COLUMN/FIELD/DYNAMIC/MASKING
     * - ROW: 行级权限，控制可访问的数据行
     * - COLUMN: 列级权限，控制可访问的列
     * - FIELD: 字段级权限，控制特定字段的访问和脱敏
     * - DYNAMIC: 动态策略，基于上下文的动态条件
     * - MASKING: 数据脱敏策略
     */
    private String policyType;
    private String resource;
    /**
     * 策略条件内容：对于行/动态策略为 SQL 片段；对于列/脱敏策略可为 JSON。
     */
    private String policyCondition;
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

    public String getPolicyType() {
        return policyType;
    }

    public void setPolicyType(String policyType) {
        this.policyType = policyType;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getPolicyCondition() {
        return policyCondition;
    }

    public void setPolicyCondition(String policyCondition) {
        this.policyCondition = policyCondition;
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


