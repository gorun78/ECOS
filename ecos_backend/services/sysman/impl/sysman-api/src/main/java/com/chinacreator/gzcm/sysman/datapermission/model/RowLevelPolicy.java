package com.chinacreator.gzcm.sysman.datapermission.model;

import java.util.Date;

/**
 * 行级数据权限策略
 */
public class RowLevelPolicy {

    private String policyId;
    private String policyName;
    /**
     * 作用资源，如逻辑表名、视图名或业务资源编码
     */
    private String resource;
    /**
     * SQL 片段形式的行级条件，如：department = ${userDepartment}
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


