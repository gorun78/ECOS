package com.chinacreator.gzcm.sysman.policy.model;

import java.util.Map;

/**
 * 策略上下文：包含评估策略所需的所有属性
 */
public class PolicyContext {
    private Map<String, Object> subject;      // 主体属性（用户、角色等）
    private Map<String, Object> resource;     // 资源属性（数据、服务等）
    private Map<String, Object> action;        // 操作属性
    private Map<String, Object> environment;   // 环境属性（时间、IP、设备等）

    public Map<String, Object> getSubject() {
        return subject;
    }

    public void setSubject(Map<String, Object> subject) {
        this.subject = subject;
    }

    public Map<String, Object> getResource() {
        return resource;
    }

    public void setResource(Map<String, Object> resource) {
        this.resource = resource;
    }

    public Map<String, Object> getAction() {
        return action;
    }

    public void setAction(Map<String, Object> action) {
        this.action = action;
    }

    public Map<String, Object> getEnvironment() {
        return environment;
    }

    public void setEnvironment(Map<String, Object> environment) {
        this.environment = environment;
    }
}

