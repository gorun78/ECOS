package com.chinacreator.gzcm.runtime.core.config.impl;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.runtime.core.config.ConfigException;
import com.chinacreator.gzcm.runtime.core.config.ConfigListener;
import com.chinacreator.gzcm.runtime.core.config.IConfigService;
import com.chinacreator.gzcm.runtime.core.config.dao.ConfigDao;
import com.chinacreator.gzcm.runtime.core.config.entity.ConfigEntity;
import com.chinacreator.gzcm.runtime.core.config.entity.ConfigVersionEntity;

/**
 * 数据库配置服务实现
 * 支持配置的持久化存储和版本管理
 *
 * @author CDRC Runtime Team
 */
@Service("runtimeDatabaseConfigService")
public class DatabaseConfigServiceImpl implements IConfigService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfigServiceImpl.class);

    private static final String DEFAULT_CONFIG_TYPE = "runtime";
    private static final String DEFAULT_ENVIRONMENT = "default";

    private final ConfigDao configDao;
    private final CopyOnWriteArrayList<ConfigListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, String> memoryCache = new ConcurrentHashMap<>();
    private String environment = DEFAULT_ENVIRONMENT;
    private boolean cacheEnabled = true;

    @Autowired
    public DatabaseConfigServiceImpl(@Qualifier("runtimeConfigDao") ConfigDao configDao) {
        this.configDao = configDao;
    }

    public DatabaseConfigServiceImpl(ConfigDao configDao, boolean cacheEnabled) {
        this.configDao = configDao;
        this.cacheEnabled = cacheEnabled;
    }

    @Override
    public String getProperty(String key) {
        return getProperty(key, null);
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        if (key == null) {
            return defaultValue;
        }

        // 检查内存缓存
        if (cacheEnabled && memoryCache.containsKey(key)) {
            return memoryCache.get(key);
        }

        // 从数据库获取
        ConfigEntity config = configDao.getConfigByTypeNameEnv(DEFAULT_CONFIG_TYPE, key, environment);
        if (config == null || config.getConfigContent() == null) {
            return defaultValue;
        }

        String value = config.getConfigContent();

        // 更新缓存
        if (cacheEnabled) {
            memoryCache.put(key, value);
        }

        return value;
    }

    @Override
    public int getIntProperty(String key, int defaultValue) {
        String value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            logger.warn("配置值不是有效的整数: key={}, value={}", key, value);
            return defaultValue;
        }
    }

    @Override
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    @Override
    public long getLongProperty(String key, long defaultValue) {
        String value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            logger.warn("配置值不是有效的长整数: key={}, value={}", key, value);
            return defaultValue;
        }
    }

    @Override
    public Properties getAllProperties() {
        Properties properties = new Properties();

        // 查询所有配置
        Map<String, Object> condition = new HashMap<>();
        condition.put("config_type", DEFAULT_CONFIG_TYPE);
        condition.put("environment", environment);

        // 分页查询所有配置
        int offset = 0;
        int limit = 100;
        int total = configDao.countConfigs(condition);

        while (offset < total) {
            for (ConfigEntity config : configDao.queryConfigs(condition, offset, limit)) {
                properties.setProperty(config.getConfigName(), config.getConfigContent());

                // 更新缓存
                if (cacheEnabled) {
                    memoryCache.put(config.getConfigName(), config.getConfigContent());
                }
            }
            offset += limit;
        }

        return properties;
    }

    @Override
    public Properties getPropertiesByPrefix(String prefix) {
        if (prefix == null) {
            return new Properties();
        }

        Properties properties = new Properties();

        // 查询所有配置
        Map<String, Object> condition = new HashMap<>();
        condition.put("config_type", DEFAULT_CONFIG_TYPE);
        condition.put("environment", environment);

        // 分页查询所有配置，然后过滤前缀
        int offset = 0;
        int limit = 100;
        int total = configDao.countConfigs(condition);

        while (offset < total) {
            for (ConfigEntity config : configDao.queryConfigs(condition, offset, limit)) {
                if (config.getConfigName().startsWith(prefix)) {
                    properties.setProperty(config.getConfigName(), config.getConfigContent());

                    // 更新缓存
                    if (cacheEnabled) {
                        memoryCache.put(config.getConfigName(), config.getConfigContent());
                    }
                }
            }
            offset += limit;
        }

        return properties;
    }

    @Override
    public void setProperty(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("配置键不能为空");
        }

        String oldValue = getProperty(key);

        // 保存到数据库
        ConfigEntity config = new ConfigEntity();
        config.setConfigType(DEFAULT_CONFIG_TYPE);
        config.setConfigName(key);
        config.setConfigContent(value);
        config.setEnvironment(environment);
        config.setUpdatedTime(new Timestamp(System.currentTimeMillis()));

        // 检查是否已存在
        ConfigEntity existing = configDao.getConfigByTypeNameEnv(DEFAULT_CONFIG_TYPE, key, environment);
        if (existing != null) {
            config.setConfigId(existing.getConfigId());
            config.setCreatedTime(existing.getCreatedTime());
            config.setCreatedBy(existing.getCreatedBy());
            config.setVersion(incrementVersion(existing.getVersion()));

            // 保存版本历史
            saveConfigVersion(existing);
        } else {
            config.setConfigId(UUID.randomUUID().toString());
            config.setCreatedTime(new Timestamp(System.currentTimeMillis()));
            config.setVersion("1.0");
        }

        configDao.saveConfig(config);

        // 更新缓存
        if (cacheEnabled) {
            memoryCache.put(key, value);
        }

        // 通知监听器
        notifyListeners(key, value, oldValue);

        logger.info("配置已更新: key={}, environment={}", key, environment);
    }

    @Override
    public void reload() throws ConfigException {
        try {
            // 清空缓存
            memoryCache.clear();

            // 重新加载所有配置到缓存
            getAllProperties();

            logger.info("配置已重新加载: environment={}", environment);
        } catch (Exception e) {
            throw new ConfigException("重新加载配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void validate() throws ConfigException {
        try {
            // 验证数据库连接
            configDao.countConfigs(new HashMap<>());

            // 验证必需配置
            validateRequiredProperties();

            logger.debug("配置验证通过: environment={}", environment);
        } catch (Exception e) {
            throw new ConfigException("配置验证失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getEnvironment() {
        return environment;
    }

    @Override
    public void setEnvironment(String environment) {
        if (environment == null) {
            environment = DEFAULT_ENVIRONMENT;
        }

        if (!this.environment.equals(environment)) {
            this.environment = environment;
            // 环境切换时清空缓存
            memoryCache.clear();
            logger.info("配置环境已切换: {}", environment);
        }
    }

    @Override
    public void addConfigListener(ConfigListener listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    @Override
    public void removeConfigListener(ConfigListener listener) {
        listeners.remove(listener);
    }

    /**
     * 递增版本号
     */
    private String incrementVersion(String currentVersion) {
        if (currentVersion == null) {
            return "1.0";
        }

        try {
            // 简单版本递增逻辑：主版本.次版本
            String[] parts = currentVersion.split("\\.");
            if (parts.length >= 2) {
                int minor = Integer.parseInt(parts[1]);
                return parts[0] + "." + (minor + 1);
            } else {
                return currentVersion + ".1";
            }
        } catch (NumberFormatException e) {
            return currentVersion + ".1";
        }
    }

    /**
     * 保存配置版本
     */
    private void saveConfigVersion(ConfigEntity config) {
        ConfigVersionEntity version = new ConfigVersionEntity();
        version.setVersionId(UUID.randomUUID().toString());
        version.setConfigId(config.getConfigId());
        version.setVersion(config.getVersion());
        version.setConfigContent(config.getConfigContent());
        version.setChangelog("配置更新");
        version.setCreatedTime(new Timestamp(System.currentTimeMillis()));

        configDao.saveConfigVersion(version);
    }

    /**
     * 通知监听器
     */
    private void notifyListeners(String key, String newValue, String oldValue) {
        for (ConfigListener listener : listeners) {
            try {
                listener.onConfigChanged(key, newValue, oldValue);
            } catch (Exception e) {
                logger.warn("配置监听器执行失败: listener={}", listener.getClass().getName(), e);
            }
        }
    }

    /**
     * 验证必需配置
     */
    private void validateRequiredProperties() throws ConfigException {
        // 这里可以定义必需配置的检查逻辑
        // 例如：检查数据库连接配置、服务端点配置等
    }

    /**
     * 设置缓存是否启用
     */
    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
        if (!cacheEnabled) {
            memoryCache.clear();
        }
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        memoryCache.clear();
    }

    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return memoryCache.size();
    }
}