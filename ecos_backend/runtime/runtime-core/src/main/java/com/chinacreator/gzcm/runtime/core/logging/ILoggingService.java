package com.chinacreator.gzcm.runtime.core.logging;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.logging.datachange.DataChangeLogEntry;

/**
 * 统一日志服务接口
 * 提供系统统一的日志记录、查询和归档功能
 * 
 * @author CDRC Runtime Team
 */
public interface ILoggingService {
    
    /**
     * 日志级别
     */
    enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
    
    /**
     * 基础日志记录
     * 
     * @param level 日志级别
     * @param message 日志消息
     */
    void log(LogLevel level, String message);
    
    /**
     * 基础日志记录（带异常）
     * 
     * @param level 日志级别
     * @param message 日志消息
     * @param throwable 异常
     */
    void log(LogLevel level, String message, Throwable throwable);
    
    /**
     * 记录日志条目
     * 
     * @param entry 日志条目
     */
    void log(LogEntry entry);
    
    /**
     * 上下文日志（支持模块扩展）
     * 
     * @param module 模块标识
     * @param level 日志级别
     * @param message 日志消息
     * @param context 上下文信息
     */
    void log(String module, LogLevel level, String message, Map<String, Object> context);
    
    /**
     * 任务日志（兼容现有TaskLogService）
     * 
     * @param taskId 任务ID
     * @param level 日志级别
     * @param message 日志消息
     */
    void logTask(String taskId, LogLevel level, String message);
    
    /**
     * 任务日志（带异常）
     * 
     * @param taskId 任务ID
     * @param level 日志级别
     * @param message 日志消息
     * @param throwable 异常
     */
    void logTask(String taskId, LogLevel level, String message, Throwable throwable);
    
    /**
     * 数据变更日志
     * 
     * @param entry 数据变更日志条目
     */
    void logDataChange(DataChangeLogEntry entry);
    
    /**
     * 查询日志
     * 
     * @param condition 查询条件
     * @return 日志列表
     */
    List<LogEntry> query(LogQueryCondition condition);
    
    /**
     * 归档日志
     * 
     * @param logType 日志类型
     * @param beforeDate 归档此日期之前的数据
     */
    void archive(String logType, Date beforeDate);
}

