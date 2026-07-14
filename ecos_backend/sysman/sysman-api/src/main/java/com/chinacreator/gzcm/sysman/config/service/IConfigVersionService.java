package com.chinacreator.gzcm.sysman.config.service;

import java.util.List;

import com.chinacreator.gzcm.sysman.config.entity.ConfigVersion;

/**
 * 配置版本管理服务接口
 * 
 * @author CDRC Design Team
 */
public interface IConfigVersionService {
    
    /**
     * 创建新版）?
     */
    ConfigVersion createVersion(String configId, String version, String changelog, String createdBy) throws ConfigVersionException;
    
    /**
     * 查询历史版本
     */
    List<ConfigVersion> listVersions(String configId) throws ConfigVersionException;
    
    /**
     * 获取版本详情
     */
    ConfigVersion getVersion(String versionId) throws ConfigVersionException;
    
    /**
     * 版本对比
     */
    String compareVersions(String configId, String version1, String version2) throws ConfigVersionException;
    
    /**
     * 版本回滚
     */
    void rollbackVersion(String configId, String version) throws ConfigVersionException;
    
    /**
     * 配置版本异常
     */
    class ConfigVersionException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public ConfigVersionException(String message) {
            super(message);
        }
        
        public ConfigVersionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


