package com.chinacreator.gzcm.runtime.core.task.persistence.entity;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 任务状态实体类
 * 对应数据库表 TD_RUNTIME_TASK_STATUS
 * 
 * @author CDRC Runtime Team
 */
public class TaskStatusEntity implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String taskId;
    private String status;
    private String statusMessage;
    private Integer progress;
    private String currentStepId;
    private Timestamp startTime;
    private Timestamp endTime;
    private Long estimatedRemainingTime;
    private Long processedCount;
    private Long totalCount;
    private String errorMessage;
    private String errorStack;
    private String result;
    private String metrics;
    private String executionNodeId;
    private Timestamp updatedTime;
    
    // Getters and Setters
    public String getTaskId() {
        return taskId;
    }
    
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getStatusMessage() {
        return statusMessage;
    }
    
    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }
    
    public Integer getProgress() {
        return progress;
    }
    
    public void setProgress(Integer progress) {
        this.progress = progress;
    }
    
    public String getCurrentStepId() {
        return currentStepId;
    }
    
    public void setCurrentStepId(String currentStepId) {
        this.currentStepId = currentStepId;
    }
    
    public Timestamp getStartTime() {
        return startTime;
    }
    
    public void setStartTime(Timestamp startTime) {
        this.startTime = startTime;
    }
    
    public Timestamp getEndTime() {
        return endTime;
    }
    
    public void setEndTime(Timestamp endTime) {
        this.endTime = endTime;
    }
    
    public Long getEstimatedRemainingTime() {
        return estimatedRemainingTime;
    }
    
    public void setEstimatedRemainingTime(Long estimatedRemainingTime) {
        this.estimatedRemainingTime = estimatedRemainingTime;
    }
    
    public Long getProcessedCount() {
        return processedCount;
    }
    
    public void setProcessedCount(Long processedCount) {
        this.processedCount = processedCount;
    }
    
    public Long getTotalCount() {
        return totalCount;
    }
    
    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getErrorStack() {
        return errorStack;
    }
    
    public void setErrorStack(String errorStack) {
        this.errorStack = errorStack;
    }
    
    public String getResult() {
        return result;
    }
    
    public void setResult(String result) {
        this.result = result;
    }
    
    public String getMetrics() {
        return metrics;
    }
    
    public void setMetrics(String metrics) {
        this.metrics = metrics;
    }
    
    public String getExecutionNodeId() {
        return executionNodeId;
    }
    
    public void setExecutionNodeId(String executionNodeId) {
        this.executionNodeId = executionNodeId;
    }
    
    public Timestamp getUpdatedTime() {
        return updatedTime;
    }
    
    public void setUpdatedTime(Timestamp updatedTime) {
        this.updatedTime = updatedTime;
    }
}

