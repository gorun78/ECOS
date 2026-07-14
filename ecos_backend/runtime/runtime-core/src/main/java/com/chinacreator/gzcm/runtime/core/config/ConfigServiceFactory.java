package com.chinacreator.gzcm.runtime.core.config;

import com.chinacreator.gzcm.runtime.core.config.dao.ConfigDao;
import com.chinacreator.gzcm.runtime.core.config.dao.impl.ConfigDaoImpl;
import com.chinacreator.gzcm.runtime.core.config.impl.DatabaseConfigServiceImpl;
import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.runtime.core.database.impl.SystemDatabaseAccessImpl;

/**
 * 配置服务工厂
 * 提供内存和数据库两种配置服务的创建
 *
 * @author CDRC Runtime Team
 */
public class ConfigServiceFactory {

    /**
     * 创建内存配置服务（默认实现）
     *
     * @return 内存配置服务
     */
    public static IConfigService createMemoryConfigService() {
        return new ConfigServiceImpl();
    }

    /**
     * 创建数据库配置服务
     *
     * @param databaseAccess 数据库访问对象
     * @return 数据库配置服务
     */
    public static IConfigService createDatabaseConfigService(ISystemDatabaseAccess databaseAccess) {
        ConfigDao configDao = new ConfigDaoImpl(databaseAccess);
        return new DatabaseConfigServiceImpl(configDao);
    }

    /**
     * 创建数据库配置服务（带缓存控制）
     *
     * @param databaseAccess 数据库访问对象
     * @param cacheEnabled 是否启用缓存
     * @return 数据库配置服务
     */
    public static IConfigService createDatabaseConfigService(ISystemDatabaseAccess databaseAccess, boolean cacheEnabled) {
        ConfigDao configDao = new ConfigDaoImpl(databaseAccess);
        return new DatabaseConfigServiceImpl(configDao, cacheEnabled);
    }

    /**
     * 创建默认数据库配置服务（使用默认数据库访问）
     *
     * @return 数据库配置服务
     */
    public static IConfigService createDefaultDatabaseConfigService() {
        ISystemDatabaseAccess databaseAccess = new SystemDatabaseAccessImpl();
        ConfigDao configDao = new ConfigDaoImpl(databaseAccess);
        return new DatabaseConfigServiceImpl(configDao);
    }

    /**
     * 根据配置选择创建配置服务
     *
     * @param useDatabase 是否使用数据库
     * @return 配置服务
     */
    public static IConfigService createConfigService(boolean useDatabase) {
        if (useDatabase) {
            return createDefaultDatabaseConfigService();
        } else {
            return createMemoryConfigService();
        }
    }

    /**
     * 创建配置服务（支持环境变量控制）
     *
     * @return 配置服务
     */
    public static IConfigService createConfigService() {
        String configStorage = System.getProperty("runtime.config.storage", "memory");
        boolean useDatabase = "database".equalsIgnoreCase(configStorage);
        return createConfigService(useDatabase);
    }
}