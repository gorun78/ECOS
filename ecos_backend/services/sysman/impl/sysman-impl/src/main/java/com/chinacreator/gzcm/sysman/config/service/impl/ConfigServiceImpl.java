package com.chinacreator.gzcm.sysman.config.service.impl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.sysman.config.dao.ConfigDao;
import com.chinacreator.gzcm.sysman.config.entity.Config;
import com.chinacreator.gzcm.sysman.config.event.ConfigChangedEvent;
import com.chinacreator.gzcm.sysman.config.service.IConfigService;

/**
 * 配置管理服务实现）?
 * 
 * @author CDRC Design Team
 */
@Service
public class ConfigServiceImpl implements IConfigService {
    
    private final ConfigDao configDao;
    private final ApplicationEventPublisher eventPublisher;
    
    @Autowired
    public ConfigServiceImpl(@Qualifier("sysManConfigDao") ConfigDao configDao, ApplicationEventPublisher eventPublisher) {
        this.configDao = configDao;
        this.eventPublisher = eventPublisher;
    }
    
    @Override
    public Config createConfig(Config config) throws ConfigException {
        try {
            // 检查是否已存在
            Config existing = configDao.findByTypeNameEnv(
                    config.getConfigType(), 
                    config.getConfigName(), 
                    config.getEnvironment() != null ? config.getEnvironment() : "dev");
            
            if (existing != null) {
                throw new ConfigException("配置已存）? " + config.getConfigType() + "/" + config.getConfigName());
            }
            
            // 设置默认）?
            if (config.getConfigId() == null) {
                config.setConfigId(UUID.randomUUID().toString());
            }
            if (config.getEnvironment() == null) {
                config.setEnvironment("dev");
            }
            if (config.getVersion() == null) {
                config.setVersion("1.0.0");
            }
            
            LocalDateTime now = LocalDateTime.now();
            config.setCreatedTime(now);
            config.setUpdatedTime(now);
            
            configDao.insert(config);
            
            // 发布配置变更事件
            eventPublisher.publishEvent(new ConfigChangedEvent(this, config.getConfigId(), "CREATE", config));
            
            return configDao.findById(config.getConfigId());
            
        } catch (Exception e) {
            throw new ConfigException("创建配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Config getConfig(String configId) throws ConfigException {
        try {
            Config config = configDao.findById(configId);
            if (config == null) {
                throw new ConfigException("配置不存）? " + configId);
            }
            return config;
        } catch (ConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigException("获取配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Config updateConfig(String configId, Config config) throws ConfigException {
        try {
            Config existing = configDao.findById(configId);
            if (existing == null) {
                throw new ConfigException("配置不存）? " + configId);
            }
            
            // 更新字段
            if (config.getConfigType() != null) {
                existing.setConfigType(config.getConfigType());
            }
            if (config.getConfigName() != null) {
                existing.setConfigName(config.getConfigName());
            }
            if (config.getConfigContent() != null) {
                existing.setConfigContent(config.getConfigContent());
            }
            if (config.getVersion() != null) {
                existing.setVersion(config.getVersion());
            }
            if (config.getEnvironment() != null) {
                existing.setEnvironment(config.getEnvironment());
            }
            
            existing.setUpdatedTime(LocalDateTime.now());
            if (config.getUpdatedBy() != null) {
                existing.setUpdatedBy(config.getUpdatedBy());
            }
            
            configDao.update(existing);
            
            // 发布配置变更事件
            eventPublisher.publishEvent(new ConfigChangedEvent(this, configId, "UPDATE", existing));
            
            return configDao.findById(configId);
            
        } catch (ConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigException("更新配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteConfig(String configId) throws ConfigException {
        try {
            Config config = configDao.findById(configId);
            if (config == null) {
                throw new ConfigException("配置不存）? " + configId);
            }
            
            configDao.delete(configId);
            
            // 发布配置变更事件
            eventPublisher.publishEvent(new ConfigChangedEvent(this, configId, "DELETE", null));
            
        } catch (ConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigException("删除配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Config> listConfigs(Map<String, Object> condition) throws ConfigException {
        try {
            return configDao.query(condition != null ? condition : new HashMap<>());
        } catch (Exception e) {
            throw new ConfigException("查询配置列表失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Config> searchConfigs(String keyword, String configType, String environment) throws ConfigException {
        try {
            Map<String, Object> condition = new HashMap<>();
            if (keyword != null && !keyword.isEmpty()) {
                condition.put("keyword", keyword);
            }
            if (configType != null && !configType.isEmpty()) {
                condition.put("configType", configType);
            }
            if (environment != null && !environment.isEmpty()) {
                condition.put("environment", environment);
            }
            return configDao.query(condition);
        } catch (Exception e) {
            throw new ConfigException("搜索配置失败: " + e.getMessage(), e);
        }
    }
}


