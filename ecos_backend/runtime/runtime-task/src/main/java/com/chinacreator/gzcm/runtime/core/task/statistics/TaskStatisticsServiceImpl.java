package com.chinacreator.gzcm.runtime.core.task.statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 任务统计服务实现
 * 使用内存存储任务执行统计信息
 * 
 * @author CDRC Runtime Team
 */
public class TaskStatisticsServiceImpl implements TaskStatisticsService {
    
    private final Map<String, TaskStatistics> statistics = new ConcurrentHashMap<>();
    private final Map<String, List<ExecutionRecord>> executionHistory = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_PER_TASK = 1000; // 每个任务最多保存1000条历史记录
    
    @Override
    public void recordExecution(String taskId, String taskType, long executionTime, boolean success) {
        if (taskId == null) {
            return;
        }
        
        // 更新统计信息
        TaskStatistics stats = statistics.computeIfAbsent(taskId, k -> {
            TaskStatistics s = new TaskStatistics();
            s.setTaskId(taskId);
            s.setTaskType(taskType);
            return s;
        });
        
        stats.setTotalExecutions(stats.getTotalExecutions() + 1);
        if (success) {
            stats.setSuccessCount(stats.getSuccessCount() + 1);
        } else {
            stats.setFailureCount(stats.getFailureCount() + 1);
        }
        
        // 更新执行时间统计
        if (stats.getTotalExecutions() == 1) {
            stats.setAverageExecutionTime(executionTime);
            stats.setMinExecutionTime(executionTime);
            stats.setMaxExecutionTime(executionTime);
        } else {
            // 计算平均执行时间
            long totalTime = stats.getAverageExecutionTime() * (stats.getTotalExecutions() - 1) + executionTime;
            stats.setAverageExecutionTime(totalTime / stats.getTotalExecutions());
            
            if (executionTime < stats.getMinExecutionTime()) {
                stats.setMinExecutionTime(executionTime);
            }
            if (executionTime > stats.getMaxExecutionTime()) {
                stats.setMaxExecutionTime(executionTime);
            }
        }
        
        stats.setLastExecutionTime(System.currentTimeMillis());
        stats.setLastExecutionStatus(success ? "SUCCESS" : "FAILED");
        
        // 记录执行历史
        List<ExecutionRecord> history = executionHistory.computeIfAbsent(taskId, k -> new ArrayList<>());
        ExecutionRecord record = new ExecutionRecord(taskId, executionTime, success, System.currentTimeMillis());
        record.setDuration(executionTime);
        history.add(record);
        
        // 限制历史记录数量
        if (history.size() > MAX_HISTORY_PER_TASK) {
            history.remove(0); // 移除最旧的记录
        }
    }
    
    @Override
    public TaskStatistics getStatistics(String taskId) {
        if (taskId == null) {
            return null;
        }
        return statistics.get(taskId);
    }
    
    @Override
    public List<TaskStatistics> getStatisticsByType(String taskType) {
        if (taskType == null) {
            return new ArrayList<>(statistics.values());
        }
        
        return statistics.values().stream()
            .filter(stats -> taskType.equals(stats.getTaskType()))
            .collect(Collectors.toList());
    }
    
    @Override
    public List<TaskStatistics> getAllStatistics() {
        return new ArrayList<>(statistics.values());
    }
    
    @Override
    public List<ExecutionRecord> getExecutionHistory(String taskId, Integer limit) {
        if (taskId == null) {
            return new ArrayList<>();
        }
        
        List<ExecutionRecord> history = executionHistory.get(taskId);
        if (history == null) {
            return new ArrayList<>();
        }
        
        List<ExecutionRecord> result = new ArrayList<>(history);
        
        // 限制数量
        if (limit != null && limit > 0 && result.size() > limit) {
            int size = result.size();
            result = result.subList(size - limit, size); // 取最新的N条
        }
        
        return result;
    }
}

