package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;
import java.util.List;

/**
 * 执行计划。
 */
public class ExecutionPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 计划唯一 ID */
    private String planId;
    /** 计划名称 */
    private String name;
    /** 状态：PENDING / EXECUTING / SUCCESS / FAILED / CANCELLED */
    private String status;
    /** 优先级：P0~P3 */
    private String priority;
    /** 来源追溯 */
    private PlanSource source;
    /** 执行步骤序列 */
    private List<PlanStep> steps;
    /** 执行进度（执行中时有效） */
    private PlanProgress progress;
    /** 预计耗时 (毫秒) */
    private Long estimatedDurationMs;
    /** Hermes 会话 ID */
    private String hermesSessionId;
    /** 创建时间 (ISO 8601) */
    private String createdAt;
    /** 创建者 */
    private String createdBy;
    /** 更新时间 (ISO 8601) */
    private String updatedAt;

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public PlanSource getSource() { return source; }
    public void setSource(PlanSource source) { this.source = source; }
    public List<PlanStep> getSteps() { return steps; }
    public void setSteps(List<PlanStep> steps) { this.steps = steps; }
    public PlanProgress getProgress() { return progress; }
    public void setProgress(PlanProgress progress) { this.progress = progress; }
    public Long getEstimatedDurationMs() { return estimatedDurationMs; }
    public void setEstimatedDurationMs(Long estimatedDurationMs) { this.estimatedDurationMs = estimatedDurationMs; }
    public String getHermesSessionId() { return hermesSessionId; }
    public void setHermesSessionId(String hermesSessionId) { this.hermesSessionId = hermesSessionId; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
