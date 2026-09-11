package com.chinacreator.gzcm.sysman.config.service.impl;



import java.util.Date;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.chinacreator.gzcm.sysman.config.dao.ConfigDao;
import com.chinacreator.gzcm.sysman.config.dao.ConfigVersionDao;
import com.chinacreator.gzcm.sysman.config.entity.Config;
import com.chinacreator.gzcm.sysman.config.entity.ConfigVersion;
import com.chinacreator.gzcm.sysman.config.service.IConfigService;
import com.chinacreator.gzcm.sysman.config.service.IConfigVersionService;

/**
 * 配置版本管理服务实现
 * 
 * @author CDRC Design Team
 */
public class ConfigVersionServiceImpl implements IConfigVersionService {
    
    private final ConfigVersionDao configVersionDao;
    private final ConfigDao configDao;
    private final IConfigService configService;
    
    public ConfigVersionServiceImpl(ConfigVersionDao configVersionDao, ConfigDao configDao, IConfigService configService) {
        this.configVersionDao = configVersionDao;
        this.configDao = configDao;
        this.configService = configService;
    }
    
    @Override
    public ConfigVersion createVersion(String configId, String version, String changelog, String createdBy) throws ConfigVersionException {
        try {
            Config config = configDao.findById(configId);
            if (config == null) {
                throw new ConfigVersionException("配置不存在: " + configId);
            }
            
            // 检查版本是否已存在
            ConfigVersion existing = configVersionDao.findByConfigIdAndVersion(configId, version);
            if (existing != null) {
                throw new ConfigVersionException("版本已存在: " + version);
            }
            
            ConfigVersion configVersion = new ConfigVersion();
            configVersion.setVersionId(UUID.randomUUID().toString());
            configVersion.setConfigId(configId);
            configVersion.setVersion(version);
            configVersion.setConfigContent(config.getConfigContent());
            configVersion.setChangelog(changelog);
            configVersion.setCreatedTime(new Date());
            configVersion.setCreatedBy(createdBy);
            
            configVersionDao.insert(configVersion);
            
            // 更新配置的版本号
            config.setVersion(version);
            configDao.update(config);
            
            return configVersionDao.findById(configVersion.getVersionId());
            
        } catch (Exception e) {
            throw new ConfigVersionException("创建版本失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<ConfigVersion> listVersions(String configId) throws ConfigVersionException {
        try {
            return configVersionDao.listByConfigId(configId);
        } catch (Exception e) {
            throw new ConfigVersionException("查询版本列表失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public ConfigVersion getVersion(String versionId) throws ConfigVersionException {
        try {
            ConfigVersion version = configVersionDao.findById(versionId);
            if (version == null) {
                throw new ConfigVersionException("版本不存在: " + versionId);
            }
            return version;
        } catch (ConfigVersionException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigVersionException("获取版本失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String compareVersions(String configId, String version1, String version2) throws ConfigVersionException {
        try {
            ConfigVersion v1 = configVersionDao.findByConfigIdAndVersion(configId, version1);
            ConfigVersion v2 = configVersionDao.findByConfigIdAndVersion(configId, version2);
            
            if (v1 == null || v2 == null) {
                throw new ConfigVersionException("版本不存在");
            }
            
            // 简化实现，实际应该使用diff工具
            StringBuilder diff = new StringBuilder();
            diff.append("版本对比: ").append(version1).append(" vs ").append(version2).append("\n");
            diff.append("版本1内容长度: ").append(v1.getConfigContent() != null ? v1.getConfigContent().length() : 0).append("\n");
            diff.append("版本2内容长度: ").append(v2.getConfigContent() != null ? v2.getConfigContent().length() : 0).append("\n");
            
            return diff.toString();
            
        } catch (ConfigVersionException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigVersionException("版本对比失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void rollbackVersion(String configId, String version) throws ConfigVersionException {
        try {
            ConfigVersion configVersion = configVersionDao.findByConfigIdAndVersion(configId, version);
            if (configVersion == null) {
                throw new ConfigVersionException("版本不存在: " + version);
            }
            
            Config config = configDao.findById(configId);
            if (config == null) {
                    throw new ConfigVersionException("配置不存在: " + configId);
            }
            
            // 恢复配置内容
            config.setConfigContent(configVersion.getConfigContent());
            config.setVersion(version);
            config.setUpdatedTime(LocalDateTime.now());
            
            configDao.update(config);
            
        } catch (ConfigVersionException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigVersionException("版本回滚失败: " + e.getMessage(), e);
        }
    }
}


