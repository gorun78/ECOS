package com.chinacreator.gzcm.runtime.core.logging.config;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.runtime.core.logging.ILogArchiveService;
import com.chinacreator.gzcm.runtime.core.logging.ILoggingService;
import com.chinacreator.gzcm.runtime.core.logging.ILogStorage;
import com.chinacreator.gzcm.runtime.core.logging.archive.LogArchiveServiceImpl;
import com.chinacreator.gzcm.runtime.core.logging.datachange.DataChangeLogServiceImpl;
import com.chinacreator.gzcm.runtime.core.logging.datachange.IDataChangeLogService;
import com.chinacreator.gzcm.runtime.core.logging.impl.LoggingServiceImpl;
import com.chinacreator.gzcm.runtime.core.logging.storage.DatabaseLogStorage;

/**
 * 日志服务配置类
 * 用于创建和配置日志相关的服务Bean
 * 
 * @author CDRC Runtime Team
 */
public class LoggingServiceConfig {
    
    /**
     * 创建日志存储服务
     */
    public static ILogStorage createLogStorage(ISystemDatabaseAccess databaseAccess) {
        return new DatabaseLogStorage(databaseAccess);
    }
    
    /**
     * 创建数据变更日志服务
     */
    public static IDataChangeLogService createDataChangeLogService(ISystemDatabaseAccess databaseAccess) {
        return new DataChangeLogServiceImpl(databaseAccess);
    }
    
    /**
     * 创建日志归档服务
     */
    public static ILogArchiveService createLogArchiveService(ISystemDatabaseAccess databaseAccess) {
        return new LogArchiveServiceImpl(databaseAccess);
    }
    
    /**
     * 创建统一日志服务
     */
    public static ILoggingService createLoggingService(ISystemDatabaseAccess databaseAccess) {
        ILogStorage logStorage = createLogStorage(databaseAccess);
        IDataChangeLogService dataChangeLogService = createDataChangeLogService(databaseAccess);
        ILogArchiveService archiveService = createLogArchiveService(databaseAccess);
        
        return new LoggingServiceImpl(logStorage, dataChangeLogService, archiveService);
    }
}

