package com.chinacreator.gzcm.runtime.core.logging.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.chinacreator.gzcm.runtime.core.logging.ILogArchiveService;
import com.chinacreator.gzcm.runtime.core.logging.ILoggingService;
import com.chinacreator.gzcm.runtime.core.logging.ILogStorage;
import com.chinacreator.gzcm.runtime.core.logging.LogEntry;
import com.chinacreator.gzcm.runtime.core.logging.LogQueryCondition;
import com.chinacreator.gzcm.runtime.core.logging.datachange.DataChangeLogEntry;
import com.chinacreator.gzcm.runtime.core.logging.datachange.IDataChangeLogService;
import com.chinacreator.gzcm.runtime.core.logging.plugin.ILoggingPlugin;
import com.chinacreator.gzcm.runtime.core.logging.plugin.IModuleLogHandler;
import com.chinacreator.gzcm.runtime.core.logging.plugin.LoggingPluginRegistry;

/**
 * 统一日志服务实现
 * 支持多种日志存储后端（数据库、文件、Elasticsearch）
 * 异步日志写入，避免阻塞业务
 * 日志格式统一（JSON格式）
 * 支持日志采样和过滤
 * 
 * @author CDRC Runtime Team
 */
public class LoggingServiceImpl implements ILoggingService {
    
    private final ILogStorage logStorage;
    private final IDataChangeLogService dataChangeLogService;
    private final ILogArchiveService archiveService;
    private final LoggingPluginRegistry pluginRegistry;
    
    public LoggingServiceImpl(ILogStorage logStorage, 
                             IDataChangeLogService dataChangeLogService,
                             ILogArchiveService archiveService) {
        this.logStorage = logStorage;
        this.dataChangeLogService = dataChangeLogService;
        this.archiveService = archiveService;
        this.pluginRegistry = LoggingPluginRegistry.getInstance();
    }
    
    public LoggingServiceImpl(ILogStorage logStorage, 
                             IDataChangeLogService dataChangeLogService,
                             ILogArchiveService archiveService,
                             LoggingPluginRegistry pluginRegistry) {
        this.logStorage = logStorage;
        this.dataChangeLogService = dataChangeLogService;
        this.archiveService = archiveService;
        this.pluginRegistry = pluginRegistry != null ? pluginRegistry : LoggingPluginRegistry.getInstance();
    }
    
    @Override
    public void log(LogLevel level, String message) {
        log(level, message, null);
    }
    
    @Override
    public void log(LogLevel level, String message, Throwable throwable) {
        LogEntry entry = createLogEntry(null, level, message, throwable);
        logStorage.save(entry);
    }
    
    @Override
    public void log(LogEntry entry) {
        if (entry.getLogId() == null) {
            entry.setLogId(UUID.randomUUID().toString());
        }
        
        String module = entry.getModule();
        Map<String, Object> context = entry.getContext() != null ? entry.getContext() : new HashMap<>();
        
        // 合并模块特定的上下文
        Map<String, Object> mergedContext = mergeModuleContext(module, context);
        entry.setContext(mergedContext);
        
        // 调用模块处理器
        if (module != null) {
            IModuleLogHandler moduleHandler = pluginRegistry.getModuleHandler(module);
            if (moduleHandler != null) {
                moduleHandler.handleLog(entry, mergedContext, this);
            }
        }
        
        // 执行插件前置处理
        if (executePluginsBeforeSave(module, entry, mergedContext)) {
            logStorage.save(entry);
            // 执行插件后置处理
            executePluginsAfterSave(module, entry, mergedContext);
        }
    }
    
    @Override
    public void log(String module, LogLevel level, String message, Map<String, Object> context) {
        LogEntry entry = createLogEntry(module, level, message, null);
        
        // 合并模块特定的上下文
        Map<String, Object> mergedContext = mergeModuleContext(module, context);
        entry.setContext(mergedContext);
        
        // 调用模块处理器
        IModuleLogHandler moduleHandler = pluginRegistry.getModuleHandler(module);
        if (moduleHandler != null) {
            moduleHandler.handleLog(entry, mergedContext, this);
        }
        
        // 执行插件前置处理
        if (executePluginsBeforeSave(module, entry, mergedContext)) {
            logStorage.save(entry);
            // 执行插件后置处理
            executePluginsAfterSave(module, entry, mergedContext);
        }
    }
    
    @Override
    public void logTask(String taskId, LogLevel level, String message) {
        logTask(taskId, level, message, null);
    }
    
    @Override
    public void logTask(String taskId, LogLevel level, String message, Throwable throwable) {
        LogEntry entry = createLogEntry("runtime.task", level, message, throwable);
        if (entry.getContext() != null) {
            entry.getContext().put("taskId", taskId);
        } else {
            java.util.HashMap<String, Object> context = new java.util.HashMap<>();
            context.put("taskId", taskId);
            entry.setContext(context);
        }
        logStorage.save(entry);
    }
    
    @Override
    public void logDataChange(DataChangeLogEntry entry) {
        if (entry.getLogId() == null) {
            entry.setLogId(UUID.randomUUID().toString());
        }
        if (dataChangeLogService != null) {
            dataChangeLogService.logChange(entry);
        }
    }
    
    @Override
    public List<LogEntry> query(LogQueryCondition condition) {
        List<LogEntry> results = logStorage.query(condition);
        
        // 执行插件查询处理
        if (condition != null && condition.getModule() != null) {
            Map<String, Object> queryContext = new HashMap<>();
            queryContext.put("module", condition.getModule());
            queryContext.put("level", condition.getLevel());
            queryContext.put("startTime", condition.getStartTime());
            queryContext.put("endTime", condition.getEndTime());
            
            List<ILoggingPlugin> plugins = pluginRegistry.getPluginsForModule(condition.getModule());
            for (ILoggingPlugin plugin : plugins) {
                plugin.onQuery(queryContext, results);
            }
        }
        
        return results;
    }
    
    /**
     * 合并模块特定的上下文
     */
    private Map<String, Object> mergeModuleContext(String module, Map<String, Object> context) {
        Map<String, Object> merged = context != null ? new HashMap<>(context) : new HashMap<>();
        
        // 获取模块处理器提供的上下文
        if (module != null) {
            IModuleLogHandler moduleHandler = pluginRegistry.getModuleHandler(module);
            if (moduleHandler != null) {
                Map<String, Object> moduleContext = moduleHandler.getModuleContext();
                if (moduleContext != null) {
                    merged.putAll(moduleContext);
                }
            }
        }
        
        return merged;
    }
    
    /**
     * 执行插件前置处理
     */
    private boolean executePluginsBeforeSave(String module, LogEntry entry, Map<String, Object> context) {
        if (module == null) {
            return true;
        }
        
        List<ILoggingPlugin> plugins = pluginRegistry.getPluginsForModule(module);
        for (ILoggingPlugin plugin : plugins) {
            if (!plugin.beforeSave(entry, context)) {
                return false; // 插件阻止保存
            }
        }
        
        return true;
    }
    
    /**
     * 执行插件后置处理
     */
    private void executePluginsAfterSave(String module, LogEntry entry, Map<String, Object> context) {
        if (module == null) {
            return;
        }
        
        List<ILoggingPlugin> plugins = pluginRegistry.getPluginsForModule(module);
        for (ILoggingPlugin plugin : plugins) {
            plugin.afterSave(entry, context);
        }
    }
    
    @Override
    public void archive(String logType, Date beforeDate) {
        if (archiveService != null) {
            archiveService.archive(logType, beforeDate);
        }
    }
    
    /**
     * 创建日志条目
     */
    private LogEntry createLogEntry(String module, LogLevel level, String message, Throwable throwable) {
        LogEntry entry = new LogEntry();
        entry.setLogId(UUID.randomUUID().toString());
        entry.setModule(module != null ? module : "runtime");
        entry.setLevel(level);
        entry.setMessage(message);
        entry.setLogger(getCallerClassName());
        entry.setThread(Thread.currentThread().getName());
        entry.setTimestamp(new Date());
        
        if (throwable != null) {
            entry.setException(new LogEntry.ExceptionInfo(throwable));
        }
        
        return entry;
    }
    
    /**
     * 获取调用者类名
     */
    private String getCallerClassName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // 跳过Thread.getStackTrace, getCallerClassName, createLogEntry, log方法
        for (int i = 4; i < stackTrace.length; i++) {
            String className = stackTrace[i].getClassName();
            if (!className.startsWith("com.chinacreator.gzcm.runtime.core.logging")) {
                return className;
            }
        }
        return "unknown";
    }
}

