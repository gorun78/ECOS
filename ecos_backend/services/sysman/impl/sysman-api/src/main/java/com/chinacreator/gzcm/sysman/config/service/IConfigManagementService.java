package com.chinacreator.gzcm.sysman.config.service;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.config.entity.IPAccess;
import com.chinacreator.gzcm.sysman.config.entity.SystemParam;
import com.chinacreator.gzcm.sysman.config.entity.SystemVariable;

/**
 * 统一配置管理服务接口
 * 整合系统参数、系统变量、IP访问控制，以及Git/LLM/Engine等模块配置
 */
public interface IConfigManagementService {
    
    // ========== 系统参数管理 ==========
    
    /**
     * 获取系统参数值
     * @param paramName 参数名称
     * @return 参数值
     * @throws ConfigException
     */
    String getSystemParam(String paramName) throws ConfigException;
    
    /**
     * 设置系统参数
     * @param paramName 参数名称
     * @param paramValue 参数值
     * @param operator 操作者
     * @throws ConfigException
     */
    void setSystemParam(String paramName, String paramValue, String operator) throws ConfigException;
    
    /**
     * 批量获取系统参数
     * @param paramNames 参数名称列表
     * @return 参数Map，key为参数名，value为参数值
     * @throws ConfigException
     */
    Map<String, String> getSystemParams(List<String> paramNames) throws ConfigException;
    
    // ========== 系统变量管理 ==========
    
    /**
     * 获取系统变量值
     * @param varCode 变量编码
     * @param scopeId 作用域ID（可选）
     * @return 变量值
     * @throws ConfigException
     */
    String getSystemVariable(String varCode, String scopeId) throws ConfigException;
    
    /**
     * 设置系统变量
     * @param varCode 变量编码
     * @param varValue 变量值
     * @param scopeId 作用域ID（可选）
     * @param operator 操作者
     * @throws ConfigException
     */
    void setSystemVariable(String varCode, String varValue, String scopeId, String operator) throws ConfigException;
    
    // ========== IP访问控制 ==========
    
    /**
     * 检查IP是否允许访问
     * @param ipAddress IP地址
     * @return true表示允许，false表示拒绝
     * @throws ConfigException
     */
    boolean checkIPAccess(String ipAddress) throws ConfigException;
    
    /**
     * 添加IP白名单
     * @param ipAddress IP地址或IP段（支持CIDR格式）
     * @param description 描述
     * @param operator 操作者
     * @throws ConfigException
     */
    void addIPWhitelist(String ipAddress, String description, String operator) throws ConfigException;
    
    /**
     * 添加IP黑名单
     * @param ipAddress IP地址或IP段（支持CIDR格式）
     * @param description 描述
     * @param operator 操作者
     * @throws ConfigException
     */
    void addIPBlacklist(String ipAddress, String description, String operator) throws ConfigException;
    
    // ========== Git配置管理 ==========
    
    /**
     * 获取Git配置
     * @param configKey 配置键（如：git.repository.url, git.branch等）
     * @return 配置值
     * @throws ConfigException
     */
    String getGitConfig(String configKey) throws ConfigException;
    
    /**
     * 设置Git配置
     * @param configKey 配置键
     * @param configValue 配置值
     * @param operator 操作者
     * @throws ConfigException
     */
    void setGitConfig(String configKey, String configValue, String operator) throws ConfigException;
    
    /**
     * 获取所有Git配置
     * @return Git配置Map
     * @throws ConfigException
     */
    Map<String, String> getAllGitConfigs() throws ConfigException;
    
    // ========== LLM配置管理 ==========
    
    /**
     * 获取LLM配置
     * @param configKey 配置键（如：llm.api.key, llm.model.name等）
     * @return 配置值
     * @throws ConfigException
     */
    String getLLMConfig(String configKey) throws ConfigException;
    
    /**
     * 设置LLM配置
     * @param configKey 配置键
     * @param configValue 配置值
     * @param operator 操作者
     * @throws ConfigException
     */
    void setLLMConfig(String configKey, String configValue, String operator) throws ConfigException;
    
    /**
     * 获取所有LLM配置
     * @return LLM配置Map
     * @throws ConfigException
     */
    Map<String, String> getAllLLMConfigs() throws ConfigException;
    
    // ========== Engine配置管理 ==========
    
    /**
     * 获取Engine配置
     * @param configKey 配置键（如：engine.thread.pool.size, engine.timeout等）
     * @return 配置值
     * @throws ConfigException
     */
    String getEngineConfig(String configKey) throws ConfigException;
    
    /**
     * 设置Engine配置
     * @param configKey 配置键
     * @param configValue 配置值
     * @param operator 操作者
     * @throws ConfigException
     */
    void setEngineConfig(String configKey, String configValue, String operator) throws ConfigException;
    
    /**
     * 获取所有Engine配置
     * @return Engine配置Map
     * @throws ConfigException
     */
    Map<String, String> getAllEngineConfigs() throws ConfigException;
    
    // ========== 统一配置查询 ==========
    
    /**
     * 根据前缀查询配置
     * @param prefix 配置键前缀（如：git., llm., engine.）
     * @return 配置Map
     * @throws ConfigException
     */
    Map<String, String> getConfigsByPrefix(String prefix) throws ConfigException;
    
    /**
     * 批量设置配置
     * @param configs 配置Map，key为配置键，value为配置值
     * @param operator 操作者
     * @throws ConfigException
     */
    void setConfigs(Map<String, String> configs, String operator) throws ConfigException;
    
    /**
     * 刷新配置缓存
     * @throws ConfigException
     */
    void refreshCache() throws ConfigException;
    
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

