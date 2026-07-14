package com.chinacreator.gzcm.sysman.config.service;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.config.entity.Config;

/**
 * 配置管理服务接口
 * 
 * @author CDRC Design Team
 */
public interface IConfigService {
    
    /**
     * 创建配置
     */
    Config createConfig(Config config) throws ConfigException;
    
    /**
     * 获取配置
     */
    Config getConfig(String configId) throws ConfigException;
    
    /**
     * 更新配置
     */
    Config updateConfig(String configId, Config config) throws ConfigException;
    
    /**
     * 删除配置
     */
    void deleteConfig(String configId) throws ConfigException;
    
    /**
     * 列表查询
     */
    List<Config> listConfigs(Map<String, Object> condition) throws ConfigException;
    
    /**
     * 搜索配置
     */
    List<Config> searchConfigs(String keyword, String configType, String environment) throws ConfigException;
    
    /**
     * 配置异常
     */
    class ConfigException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public ConfigException(String message) {
            super(message);
        }
        
        public ConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


