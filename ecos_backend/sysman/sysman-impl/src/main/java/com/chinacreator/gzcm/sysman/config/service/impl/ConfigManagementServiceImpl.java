package com.chinacreator.gzcm.sysman.config.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.sysman.config.entity.IPAccess;
import com.chinacreator.gzcm.sysman.config.entity.SystemParam;
import com.chinacreator.gzcm.sysman.config.entity.SystemVariable;
import com.chinacreator.gzcm.sysman.config.service.IConfigManagementService;
import com.chinacreator.gzcm.sysman.config.service.IConfigManagementService.ConfigException;
import com.chinacreator.gzcm.sysman.config.service.IIPAccessService;
import com.chinacreator.gzcm.sysman.config.service.ISystemParamService;
import com.chinacreator.gzcm.sysman.config.service.ISystemVariableService;

/**
 * 统一配置管理服务实现
 * 整合系统参数、系统变量、IP访问控制，以及Git/LLM/Engine等模块配置
 */
public class ConfigManagementServiceImpl implements IConfigManagementService {
    
    private final ISystemParamService systemParamService;
    private final ISystemVariableService systemVariableService;
    private final IIPAccessService ipAccessService;
    
    // 配置前缀定义
    private static final String GIT_PREFIX = "git.";
    private static final String LLM_PREFIX = "llm.";
    private static final String ENGINE_PREFIX = "engine.";
    
    public ConfigManagementServiceImpl(ISystemParamService systemParamService,
                                      ISystemVariableService systemVariableService,
                                      IIPAccessService ipAccessService) {
        this.systemParamService = systemParamService;
        this.systemVariableService = systemVariableService;
        this.ipAccessService = ipAccessService;
    }
    
    // ========== 系统参数管理 ==========
    
    @Override
    public String getSystemParam(String paramName) throws ConfigException {
        try {
            return systemParamService.getParamValue(paramName);
        } catch (ISystemParamService.SystemParamException e) {
            throw new ConfigException("获取系统参数失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void setSystemParam(String paramName, String paramValue, String operator) throws ConfigException {
        try {
            SystemParam param;
            try {
                param = systemParamService.getParamByName(paramName);
                param.setParamValue(paramValue);
                systemParamService.updateParam(param.getParamId(), param, operator);
            } catch (Exception e) {
                // 参数不存在，创建新参数
                param = new SystemParam();
                param.setParamName(paramName);
                param.setParamValue(paramValue);
                systemParamService.createParam(param, operator);
            }
        } catch (ISystemParamService.SystemParamException e) {
            throw new ConfigException("设置系统参数失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Map<String, String> getSystemParams(List<String> paramNames) throws ConfigException {
        try {
            Map<String, String> result = new HashMap<>();
            for (String paramName : paramNames) {
                try {
                    String value = getSystemParam(paramName);
                    if (value != null) {
                        result.put(paramName, value);
                    }
                } catch (Exception e) {
                    // 忽略单个参数获取失败
                }
            }
            return result;
        } catch (Exception e) {
            throw new ConfigException("批量获取系统参数失败: " + e.getMessage(), e);
        }
    }
    
    // ========== 系统变量管理 ==========
    
    @Override
    public String getSystemVariable(String varCode, String scopeId) throws ConfigException {
        try {
            return systemVariableService.getVariableValue(varCode, scopeId);
        } catch (ISystemVariableService.SystemVariableException e) {
            throw new ConfigException("获取系统变量失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void setSystemVariable(String varCode, String varValue, String scopeId, String operator) throws ConfigException {
        try {
            SystemVariable variable;
            try {
                variable = systemVariableService.getVariableByCode(varCode, scopeId);
                variable.setVarValue(varValue);
                systemVariableService.updateVariable(variable.getVarId(), variable, operator);
            } catch (Exception e) {
                // 变量不存在，创建新变量
                variable = new SystemVariable();
                variable.setVarCode(varCode);
                variable.setVarValue(varValue);
                variable.setScopeId(scopeId);
                systemVariableService.createVariable(variable, operator);
            }
        } catch (ISystemVariableService.SystemVariableException e) {
            throw new ConfigException("设置系统变量失败: " + e.getMessage(), e);
        }
    }
    
    // ========== IP访问控制 ==========
    
    @Override
    public boolean checkIPAccess(String ipAddress) throws ConfigException {
        try {
            // 先检查黑名单
            if (ipAccessService.isIPDenied(ipAddress)) {
                return false;
            }
            // 再检查白名单
            return ipAccessService.isIPAllowed(ipAddress);
        } catch (IIPAccessService.IPAccessException e) {
            throw new ConfigException("检查IP访问失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void addIPWhitelist(String ipAddress, String description, String operator) throws ConfigException {
        try {
            IPAccess ipAccess = new IPAccess();
            ipAccess.setIpAddress(ipAddress);
            ipAccess.setAccessType("WHITELIST");
            ipAccess.setDescription(description);
            ipAccessService.createIPAccess(ipAccess, operator);
        } catch (IIPAccessService.IPAccessException e) {
            throw new ConfigException("添加IP白名单失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void addIPBlacklist(String ipAddress, String description, String operator) throws ConfigException {
        try {
            IPAccess ipAccess = new IPAccess();
            ipAccess.setIpAddress(ipAddress);
            ipAccess.setAccessType("BLACKLIST");
            ipAccess.setDescription(description);
            ipAccessService.createIPAccess(ipAccess, operator);
        } catch (IIPAccessService.IPAccessException e) {
            throw new ConfigException("添加IP黑名单失败: " + e.getMessage(), e);
        }
    }
    
    // ========== Git配置管理 ==========
    
    @Override
    public String getGitConfig(String configKey) throws ConfigException {
        try {
            // Git配置存储在系统参数中，使用git.前缀
            String fullKey = GIT_PREFIX + configKey;
            return getSystemParam(fullKey);
        } catch (Exception e) {
            throw new ConfigException("获取Git配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void setGitConfig(String configKey, String configValue, String operator) throws ConfigException {
        try {
            String fullKey = GIT_PREFIX + configKey;
            setSystemParam(fullKey, configValue, operator);
        } catch (Exception e) {
            throw new ConfigException("设置Git配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Map<String, String> getAllGitConfigs() throws ConfigException {
        return getConfigsByPrefix(GIT_PREFIX);
    }
    
    // ========== LLM配置管理 ==========
    
    @Override
    public String getLLMConfig(String configKey) throws ConfigException {
        try {
            String fullKey = LLM_PREFIX + configKey;
            return getSystemParam(fullKey);
        } catch (Exception e) {
            throw new ConfigException("获取LLM配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void setLLMConfig(String configKey, String configValue, String operator) throws ConfigException {
        try {
            String fullKey = LLM_PREFIX + configKey;
            setSystemParam(fullKey, configValue, operator);
        } catch (Exception e) {
            throw new ConfigException("设置LLM配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Map<String, String> getAllLLMConfigs() throws ConfigException {
        return getConfigsByPrefix(LLM_PREFIX);
    }
    
    // ========== Engine配置管理 ==========
    
    @Override
    public String getEngineConfig(String configKey) throws ConfigException {
        try {
            String fullKey = ENGINE_PREFIX + configKey;
            return getSystemParam(fullKey);
        } catch (Exception e) {
            throw new ConfigException("获取Engine配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void setEngineConfig(String configKey, String configValue, String operator) throws ConfigException {
        try {
            String fullKey = ENGINE_PREFIX + configKey;
            setSystemParam(fullKey, configValue, operator);
        } catch (Exception e) {
            throw new ConfigException("设置Engine配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Map<String, String> getAllEngineConfigs() throws ConfigException {
        return getConfigsByPrefix(ENGINE_PREFIX);
    }
    
    // ========== 统一配置查询 ==========
    
    @Override
    public Map<String, String> getConfigsByPrefix(String prefix) throws ConfigException {
        try {
            // 查询所有系统参数
            Map<String, Object> condition = new HashMap<>();
            List<SystemParam> params = systemParamService.listParams(condition);
            
            // 过滤出指定前缀的配置
            return params.stream()
                    .filter(p -> p.getParamName() != null && p.getParamName().startsWith(prefix))
                    .collect(Collectors.toMap(
                        p -> p.getParamName().substring(prefix.length()), // 去掉前缀
                        SystemParam::getParamValue,
                        (v1, v2) -> v1 // 如果有重复键，保留第一个
                    ));
        } catch (ISystemParamService.SystemParamException e) {
            throw new ConfigException("查询配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void setConfigs(Map<String, String> configs, String operator) throws ConfigException {
        try {
            for (Map.Entry<String, String> entry : configs.entrySet()) {
                setSystemParam(entry.getKey(), entry.getValue(), operator);
            }
        } catch (Exception e) {
            throw new ConfigException("批量设置配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void refreshCache() throws ConfigException {
        // 刷新配置缓存（如果实现了缓存机制）
        // 这里可以调用各个服务的缓存刷新方法
        // 目前简化实现，不做任何操作
    }
}

