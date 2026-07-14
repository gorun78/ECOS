package com.chinacreator.gzcm.runtime.core.task.logging;

import java.util.List;

/**
 * 任务日志服务接口
 * 提供任务执行日志的记录和查询功能
 * 
 * @author CDRC Runtime Team
 */
public interface TaskLogService {
    
    /**
     * 日志级别
     */
    enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
    
    /**
     * 记录日志
     * 
     * @param taskId 任务ID
     * @param level 日志级别
     * @param message 日志消息
     */
    void log(String taskId, LogLevel level, String message);
    
    /**
     * 记录日志（带异常）
     * 
     * @param taskId 任务ID
     * @param level 日志级别
     * @param message 日志消息
     * @param throwable 异常
     */
    void log(String taskId, LogLevel level, String message, Throwable throwable);
    
    /**
     * 查询任务日志
     * 
     * @param taskId 任务ID
     * @param level 日志级别（可选，null表示所有级别）
     * @param limit 限制数量（可选，null表示不限制）
     * @return 日志列表
     */
    List<TaskLogEntry> getLogs(String taskId, LogLevel level, Integer limit);
    
    /**
     * 查询任务日志（按时间范围）
     * 
     * @param taskId 任务ID
     * @param startTime 开始时间（时间戳，毫秒）
     * @param endTime 结束时间（时间戳，毫秒）
     * @return 日志列表
     */
    List<TaskLogEntry> getLogs(String taskId, Long startTime, Long endTime);
    
    /**
     * 清除任务日志
     * 
     * @param taskId 任务ID
     */
    void clearLogs(String taskId);
    
    /**
     * 任务日志条目
     */
    class TaskLogEntry {
        private String taskId;
        private LogLevel level;
        private String message;
        private String exception;
        private long timestamp;
        
        public TaskLogEntry(String taskId, LogLevel level, String message, long timestamp) {
            this.taskId = taskId;
            this.level = level;
            this.message = message;
            this.timestamp = timestamp;
        }
        
        public TaskLogEntry(String taskId, LogLevel level, String message, Throwable throwable, long timestamp) {
            this.taskId = taskId;
            this.level = level;
            this.message = message;
            this.exception = throwable != null ? throwable.getMessage() : null;
            this.timestamp = timestamp;
        }
        
        // Getters
        public String getTaskId() {
            return taskId;
        }
        
        public LogLevel getLevel() {
            return level;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getException() {
            return exception;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
}

