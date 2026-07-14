package com.chinacreator.gzcm.runtime.core.task.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 任务日志服务实现
 * 使用内存存储任务执行日志
 * 
 * @author CDRC Runtime Team
 */
public class TaskLogServiceImpl implements TaskLogService {
    
    private final Map<String, List<TaskLogEntry>> logs = new ConcurrentHashMap<>();
    private static final int MAX_LOGS_PER_TASK = 10000; // 每个任务最多保存10000条日志
    
    @Override
    public void log(String taskId, LogLevel level, String message) {
        log(taskId, level, message, null);
    }
    
    @Override
    public void log(String taskId, LogLevel level, String message, Throwable throwable) {
        if (taskId == null || message == null) {
            return;
        }
        
        List<TaskLogEntry> taskLogs = logs.computeIfAbsent(taskId, k -> new ArrayList<>());
        
        TaskLogEntry entry = new TaskLogEntry(taskId, level, message, throwable, System.currentTimeMillis());
        taskLogs.add(entry);
        
        // 限制日志数量
        if (taskLogs.size() > MAX_LOGS_PER_TASK) {
            taskLogs.remove(0); // 移除最旧的日志
        }
    }
    
    @Override
    public List<TaskLogEntry> getLogs(String taskId, LogLevel level, Integer limit) {
        if (taskId == null) {
            return new ArrayList<>();
        }
        
        List<TaskLogEntry> taskLogs = logs.get(taskId);
        if (taskLogs == null) {
            return new ArrayList<>();
        }
        
        List<TaskLogEntry> result = taskLogs;
        
        // 按级别过滤
        if (level != null) {
            result = result.stream()
                .filter(entry -> entry.getLevel() == level)
                .collect(Collectors.toList());
        }
        
        // 限制数量
        if (limit != null && limit > 0) {
            int size = result.size();
            if (size > limit) {
                result = result.subList(size - limit, size); // 取最新的N条
            }
        }
        
        return new ArrayList<>(result);
    }
    
    @Override
    public List<TaskLogEntry> getLogs(String taskId, Long startTime, Long endTime) {
        if (taskId == null) {
            return new ArrayList<>();
        }
        
        List<TaskLogEntry> taskLogs = logs.get(taskId);
        if (taskLogs == null) {
            return new ArrayList<>();
        }
        
        long start = startTime != null ? startTime : 0;
        long end = endTime != null ? endTime : Long.MAX_VALUE;
        
        return taskLogs.stream()
            .filter(entry -> entry.getTimestamp() >= start && entry.getTimestamp() <= end)
            .collect(Collectors.toList());
    }
    
    @Override
    public void clearLogs(String taskId) {
        if (taskId != null) {
            logs.remove(taskId);
        }
    }
}

