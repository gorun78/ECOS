package com.chinacreator.gzcm.sysman.datapermission.model;

import java.util.Date;

/**
 * 动态数据权限策略：基于数据内容/时间等动态条件。
 */
public class DynamicPolicy {

    private String policyId;
    private String policyName;
    /**
     * 作用资源，如逻辑表名、视图名或业务资源编码
     */
    private String resource;
    /**
     * 条件表达式（SQL 片段），可以包含占位符，如：
     * amount < ${maxAmount}
     * time >= ${startTime} AND time <= ${endTime}
     */
    private String conditionExpression;
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

    public String getConditionExpression() {
        return conditionExpression;
    }

    public void setConditionExpression(String conditionExpression) {
        this.conditionExpression = conditionExpression;
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


