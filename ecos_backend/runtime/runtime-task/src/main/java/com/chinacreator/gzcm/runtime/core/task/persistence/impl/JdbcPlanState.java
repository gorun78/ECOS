package com.chinacreator.gzcm.runtime.core.task.persistence.impl;

import java.util.Date;

/**
 * PMO-72 W4 — 计划 PG 行投影（不污染 {@link com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan}）。
 *
 * <p> {@code TaskExecutionPlan} 是跨端 JSON 序列化契约（步骤、上下文、元数据），不能塞列名。
 * 调度四要素 (cron/next/last/lastStatus) 在这里持有，
 * 由 {@link JdbcTaskPersistenceService#saveJdbcPlanState} / {@link JdbcTaskPersistenceService#loadJdbcPlanState(String)}
 * 落 PG 的 4 个新列。</p>
 */
public final class JdbcPlanState {

    private String taskId;
    private String taskName;
    private String taskType;
    private String cronExpression;
    private Date nextRunAt;
    private Date lastRunAt;
    private String lastStatus;
    private String taskBodyJson;
    private String executionMode;
    private String targetNodeId;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public Date getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(Date nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public Date getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Date lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }

    public String getTaskBodyJson() {
        return taskBodyJson;
    }

    public void setTaskBodyJson(String taskBodyJson) {
        this.taskBodyJson = taskBodyJson;
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
}
