package com.chinacreator.gzcm.sysman.config.service.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.sysman.config.entity.Config;
import com.chinacreator.gzcm.sysman.config.event.ConfigChangedEvent;

/**
 * 配置热更新服）?
 * 监听配置变更事件，实现配置热更新
 * 
 * @author CDRC Design Team
 */
@Component
public class ConfigHotUpdateService implements ApplicationListener<ConfigChangedEvent> {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigHotUpdateService.class);
    
    // 配置缓存
    private final Map<String, Config> configCache = new ConcurrentHashMap<>();
    
    // 配置变更监听器注册表
    private final Map<String, ConfigChangeListener> listeners = new ConcurrentHashMap<>();
    
    @Override
    public void onApplicationEvent(ConfigChangedEvent event) {
        String configId = event.getConfigId();
        String eventType = event.getEventType();
        Config config = event.getConfig();
        
        logger.info("配置变更事件: configId={}, eventType={}", configId, eventType);
        
        // 更新缓存
        if ("DELETE".equals(eventType)) {
            configCache.remove(configId);
        } else if (config != null) {
            configCache.put(configId, config);
        }
        
        // 通知监听）?
        ConfigChangeListener listener = listeners.get(configId);
        if (listener != null) {
            try {
                listener.onConfigChanged(configId, eventType, config);
            } catch (Exception e) {
                logger.error("配置变更监听器执行失）? configId={}", configId, e);
            }
        }
        
        // 通知所有通用监听）?
        ConfigChangeListener globalListener = listeners.get("*");
        if (globalListener != null) {
            try {
                globalListener.onConfigChanged(configId, eventType, config);
            } catch (Exception e) {
                logger.error("全局配置变更监听器执行失败", e);
            }
        }
    }
    
    /**
     * 注册配置变更监听）?
     */
    public void registerListener(String configId, ConfigChangeListener listener) {
        listeners.put(configId, listener);
        logger.info("注册配置变更监听）? configId={}", configId);
    }
    
    /**
     * 注册全局配置变更监听）?
     */
    public void registerGlobalListener(ConfigChangeListener listener) {
        listeners.put("*", listener);
        logger.info("注册全局配置变更监听器");
    }
    
    /**
     * 移除监听）?
     */
    public void removeListener(String configId) {
        listeners.remove(configId);
        logger.info("移除配置变更监听）? configId={}", configId);
    }
    
    /**
     * 获取缓存的配）?
     */
    public Config getCachedConfig(String configId) {
        return configCache.get(configId);
    }
    
    /**
     * 刷新配置缓存
     */
    public void refreshCache(String configId, Config config) {
        if (config != null) {
            configCache.put(configId, config);
        } else {
            configCache.remove(configId);
        }
    }
    
    /**
     * 配置变更监听器接）?
     */
    public interface ConfigChangeListener {
        void onConfigChanged(String configId, String eventType, Config config) throws Exception;
    }
}


