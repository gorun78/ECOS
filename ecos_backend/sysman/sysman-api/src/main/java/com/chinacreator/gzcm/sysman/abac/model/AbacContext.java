package com.chinacreator.gzcm.sysman.abac.model;

import java.util.Map;

/**
 * ABAC 评估上下文：封装主体/资源/操作/环境属性
 */
public class AbacContext {
    private Map<String, Object> subject;
    private Map<String, Object> resource;
    private Map<String, Object> action;
    private Map<String, Object> environment;

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


