package com.chinacreator.gzcm.sysman.policy.model;

import java.util.Map;

/**
 * 访问请求：封装访问控制决策所需的所有信息
 */
public class AccessRequest {
    private String userId;
    private String tenantId;
    private String resource;  // 资源标识（如API路径、数据表名）
    private String action;    // 操作（如GET、POST、READ、WRITE）
    private Map<String, Object> subjectAttributes;    // 主体属性
    private Map<String, Object> resourceAttributes;  // 资源属性
    private Map<String, Object> environmentAttributes; // 环境属性（时间、IP等）
    private Map<String, Object> requestAttributes;   // 请求属性（请求参数等）

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Map<String, Object> getSubjectAttributes() {
        return subjectAttributes;
    }

    public void setSubjectAttributes(Map<String, Object> subjectAttributes) {
        this.subjectAttributes = subjectAttributes;
    }

    public Map<String, Object> getResourceAttributes() {
        return resourceAttributes;
    }

    public void setResourceAttributes(Map<String, Object> resourceAttributes) {
        this.resourceAttributes = resourceAttributes;
    }

    public Map<String, Object> getEnvironmentAttributes() {
        return environmentAttributes;
    }

    public void setEnvironmentAttributes(Map<String, Object> environmentAttributes) {
        this.environmentAttributes = environmentAttributes;
    }

    public Map<String, Object> getRequestAttributes() {
        return requestAttributes;
    }

    public void setRequestAttributes(Map<String, Object> requestAttributes) {
        this.requestAttributes = requestAttributes;
    }
}

