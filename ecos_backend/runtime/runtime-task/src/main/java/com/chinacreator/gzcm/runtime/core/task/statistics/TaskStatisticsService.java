package com.chinacreator.gzcm.runtime.core.task.statistics;

import java.util.List;

/**
 * 任务统计服务接口
 * 提供任务执行统计和分析功能
 * 
 * @author CDRC Runtime Team
 */
public interface TaskStatisticsService {
    
    /**
     * 任务统计信息
     */
    class TaskStatistics {
        private String taskId;
        private String taskType;
        private long totalExecutions;
        private long successCount;
        private long failureCount;
        private long averageExecutionTime; // 毫秒
        private long minExecutionTime;
        private long maxExecutionTime;
        private long lastExecutionTime;
        private String lastExecutionStatus;
        
        // Getters and Setters
        public String getTaskId() {
            return taskId;
        }
        
        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }
        
        public String getTaskType() {
            return taskType;
        }
        
        public void setTaskType(String taskType) {
            this.taskType = taskType;
        }
        
        public long getTotalExecutions() {
            return totalExecutions;
        }
        
        public void setTotalExecutions(long totalExecutions) {
            this.totalExecutions = totalExecutions;
        }
        
        public long getSuccessCount() {
            return successCount;
        }
        
        public void setSuccessCount(long successCount) {
            this.successCount = successCount;
        }
        
        public long getFailureCount() {
            return failureCount;
        }
        
        public void setFailureCount(long failureCount) {
            this.failureCount = failureCount;
        }
        
        public double getSuccessRate() {
            return totalExecutions > 0 ? (double) successCount / totalExecutions * 100 : 0;
        }
        
        public long getAverageExecutionTime() {
            return averageExecutionTime;
        }
        
        public void setAverageExecutionTime(long averageExecutionTime) {
            this.averageExecutionTime = averageExecutionTime;
        }
        
        public long getMinExecutionTime() {
            return minExecutionTime;
        }
        
        public void setMinExecutionTime(long minExecutionTime) {
            this.minExecutionTime = minExecutionTime;
        }
        
        public long getMaxExecutionTime() {
            return maxExecutionTime;
        }
        
        public void setMaxExecutionTime(long maxExecutionTime) {
            this.maxExecutionTime = maxExecutionTime;
        }
        
        public long getLastExecutionTime() {
            return lastExecutionTime;
        }
        
        public void setLastExecutionTime(long lastExecutionTime) {
            this.lastExecutionTime = lastExecutionTime;
        }
        
        public String getLastExecutionStatus() {
            return lastExecutionStatus;
        }
        
        public void setLastExecutionStatus(String lastExecutionStatus) {
            this.lastExecutionStatus = lastExecutionStatus;
        }
    }
    
    /**
     * 记录任务执行
     * 
     * @param taskId 任务ID
     * @param taskType 任务类型
     * @param executionTime 执行时间（毫秒）
     * @param success 是否成功
     */
    void recordExecution(String taskId, String taskType, long executionTime, boolean success);
    
    /**
     * 获取任务统计信息
     * 
     * @param taskId 任务ID
     * @return 统计信息
     */
    TaskStatistics getStatistics(String taskId);
    
    /**
     * 获取任务类型统计信息
     * 
     * @param taskType 任务类型
     * @return 统计信息列表（按任务ID分组）
     */
    List<TaskStatistics> getStatisticsByType(String taskType);
    
    /**
     * 获取所有任务统计信息
     * 
     * @return 统计信息列表
     */
    List<TaskStatistics> getAllStatistics();
    
    /**
     * 获取任务执行历史记录
     * 
     * @param taskId 任务ID
     * @param limit 限制数量
     * @return 执行历史记录
     */
    List<ExecutionRecord> getExecutionHistory(String taskId, Integer limit);
    
    /**
     * 执行记录
     */
    class ExecutionRecord {
        private String taskId;
        private long executionTime;
        private long duration; // 毫秒
        private boolean success;
        private String errorMessage;
        private long timestamp;
        
        public ExecutionRecord(String taskId, long executionTime, boolean success, long timestamp) {
            this.taskId = taskId;
            this.executionTime = executionTime;
            this.success = success;
            this.timestamp = timestamp;
        }
        
        // Getters and Setters
        public String getTaskId() {
            return taskId;
        }
        
        public long getExecutionTime() {
            return executionTime;
        }
        
        public long getDuration() {
            return duration;
        }
        
        public void setDuration(long duration) {
            this.duration = duration;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
}

