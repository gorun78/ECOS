package com.chinacreator.gzcm.sysman.config.event;

import org.springframework.context.ApplicationEvent;

import com.chinacreator.gzcm.sysman.config.entity.Config;

/**
 * 配置变更事件
 * 
 * @author CDRC Design Team
 */
public class ConfigChangedEvent extends ApplicationEvent {
    
    private String configId;
    private String eventType; // CREATE, UPDATE, DELETE
    private Config config;
    
    public ConfigChangedEvent(Object source, String configId, String eventType, Config config) {
        super(source);
        this.configId = configId;
        this.eventType = eventType;
        this.config = config;
    }
    
    public String getConfigId() {
        return configId;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public Config getConfig() {
        return config;
    }
}

