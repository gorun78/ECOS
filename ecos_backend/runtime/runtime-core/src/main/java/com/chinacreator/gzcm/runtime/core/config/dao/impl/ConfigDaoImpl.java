package com.chinacreator.gzcm.runtime.core.config.dao.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.config.dao.ConfigDao;
import com.chinacreator.gzcm.runtime.core.config.entity.ConfigEntity;
import com.chinacreator.gzcm.runtime.core.config.entity.ConfigVersionEntity;
import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;

/**
 * 配置数据访问实现
 *
 * @author CDRC Runtime Team
 */
@Repository("runtimeConfigDao")
public class ConfigDaoImpl implements ConfigDao {

    private static final Logger logger = LoggerFactory.getLogger(ConfigDaoImpl.class);

    private static final String CONFIG_TABLE = "td_config";
    private static final String CONFIG_VERSION_TABLE = "td_config_version";

    private final ISystemDatabaseAccess databaseAccess;

    @Autowired
    public ConfigDaoImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }

    @Override
    public void saveConfig(ConfigEntity config) {
        if (config == null) {
            throw new IllegalArgumentException("ConfigEntity cannot be null");
        }

        try {
            // 生成配置ID
            if (config.getConfigId() == null) {
                config.setConfigId(UUID.randomUUID().toString());
            }

            Map<String, Object> condition = new HashMap<>();
            condition.put("config_id", config.getConfigId());

            // 检查是否存在
            ConfigEntity existing = databaseAccess.findOne(CONFIG_TABLE, ConfigEntity.class, condition);
            if (existing != null) {
                // 更新
                databaseAccess.update(CONFIG_TABLE, config);
            } else {
                // 插入
                databaseAccess.insert(CONFIG_TABLE, config);
            }

            logger.debug("配置已保存: configId={}, configName={}", config.getConfigId(), config.getConfigName());
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("保存配置失败: configId={}", config.getConfigId(), e);
            throw new RuntimeException("保存配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ConfigEntity getConfigById(String configId) {
        if (configId == null) {
            return null;
        }

        try {
            Map<String, Object> condition = new HashMap<>();
            condition.put("config_id", configId);
            return databaseAccess.findOne(CONFIG_TABLE, ConfigEntity.class, condition);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("获取配置失败: configId={}", configId, e);
            throw new RuntimeException("获取配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ConfigEntity getConfigByTypeNameEnv(String configType, String configName, String environment) {
        if (configType == null || configName == null) {
            return null;
        }

        try {
            Map<String, Object> condition = new HashMap<>();
            condition.put("config_type", configType);
            condition.put("config_name", configName);
            condition.put("environment", environment != null ? environment : "dev");

            return databaseAccess.findOne(CONFIG_TABLE, ConfigEntity.class, condition);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("获取配置失败: type={}, name={}, env={}", configType, configName, environment, e);
            throw new RuntimeException("获取配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ConfigEntity> queryConfigs(Map<String, Object> condition, int offset, int limit) {
        try {
            return databaseAccess.query(CONFIG_TABLE, ConfigEntity.class, condition, offset, limit);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("查询配置失败", e);
            throw new RuntimeException("查询配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteConfig(String configId) {
        if (configId == null) {
            return;
        }

        try {
            databaseAccess.delete(CONFIG_TABLE, configId);
            logger.debug("配置已删除: configId={}", configId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("删除配置失败: configId={}", configId, e);
            throw new RuntimeException("删除配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveConfigVersion(ConfigVersionEntity version) {
        if (version == null) {
            throw new IllegalArgumentException("ConfigVersionEntity cannot be null");
        }

        try {
            // 生成版本ID
            if (version.getVersionId() == null) {
                version.setVersionId(UUID.randomUUID().toString());
            }

            Map<String, Object> condition = new HashMap<>();
            condition.put("version_id", version.getVersionId());

            // 检查是否存在
            ConfigVersionEntity existing = databaseAccess.findOne(CONFIG_VERSION_TABLE, ConfigVersionEntity.class, condition);
            if (existing != null) {
                // 更新
                databaseAccess.update(CONFIG_VERSION_TABLE, version);
            } else {
                // 插入
                databaseAccess.insert(CONFIG_VERSION_TABLE, version);
            }

            logger.debug("配置版本已保存: versionId={}, configId={}, version={}",
                version.getVersionId(), version.getConfigId(), version.getVersion());
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("保存配置版本失败: configId={}, version={}", version.getConfigId(), version.getVersion(), e);
            throw new RuntimeException("保存配置版本失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ConfigVersionEntity> getConfigVersions(String configId) {
        if (configId == null) {
            return java.util.Collections.emptyList();
        }

        try {
            Map<String, Object> condition = new HashMap<>();
            condition.put("config_id", configId);
            return databaseAccess.query(CONFIG_VERSION_TABLE, ConfigVersionEntity.class, condition, 0, 100);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("获取配置版本失败: configId={}", configId, e);
            throw new RuntimeException("获取配置版本失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ConfigVersionEntity getConfigVersion(String configId, String version) {
        if (configId == null || version == null) {
            return null;
        }

        try {
            Map<String, Object> condition = new HashMap<>();
            condition.put("config_id", configId);
            condition.put("version", version);
            return databaseAccess.findOne(CONFIG_VERSION_TABLE, ConfigVersionEntity.class, condition);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("获取配置版本失败: configId={}, version={}", configId, version, e);
            throw new RuntimeException("获取配置版本失败: " + e.getMessage(), e);
        }
    }

    @Override
    public int countConfigs(Map<String, Object> condition) {
        try {
            return (int) databaseAccess.count(CONFIG_TABLE, condition);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("统计配置数量失败", e);
            throw new RuntimeException("统计配置数量失败: " + e.getMessage(), e);
        }
    }
}