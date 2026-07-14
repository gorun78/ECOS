package com.chinacreator.gzcm.runtime.core.task.persistence.entity;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 任务执行计划实体类
 * 对应数据库表 TD_RUNTIME_TASK_PLAN
 * 
 * @author CDRC Runtime Team
 */
public class TaskPlanEntity implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String taskId;
    private String planContent;
    private String executionMode;
    private String targetNodeId;
    private Timestamp createdTime;
    private Timestamp updatedTime;
    
    // Getters and Setters
    public String getTaskId() {
        return taskId;
    }
    
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
    
    public String getPlanContent() {
        return planContent;
    }
    
    public void setPlanContent(String planContent) {
        this.planContent = planContent;
    }
    
    public String getExecutionMode() {
        return executionMode;
    }
    
    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }
    
    public String getTargetNodeId() {
        return targetNodeId;
    }
    
    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }
    
    public Timestamp getCreatedTime() {
        return createdTime;
    }
    
    public void setCreatedTime(Timestamp createdTime) {
        this.createdTime = createdTime;
    }
    
    public Timestamp getUpdatedTime() {
        return updatedTime;
    }
    
    public void setUpdatedTime(Timestamp updatedTime) {
        this.updatedTime = updatedTime;
    }
}

