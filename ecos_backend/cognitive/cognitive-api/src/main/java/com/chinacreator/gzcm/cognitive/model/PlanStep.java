package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 执行步骤。
 */
public class PlanStep implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 步骤 ID */
    private String stepId;
    /** 执行顺序 (从 1 开始) */
    private Integer order;
    /** 动作类型 */
    private String action;
    /** 动作目标 */
    private String target;
    /** 动作参数 */
    private Map<String, Object> params;
    /** 步骤状态：PENDING / EXECUTING / SUCCESS / FAILED / SKIPPED */
    private String status;
    /** 前置依赖步骤 ID 列表 */
    private List<String> dependsOn;
    /** 开始时间 (ISO 8601) */
    private String startedAt;
    /** 完成时间 (ISO 8601) */
    private String completedAt;
    /** 执行耗时 (毫秒) */
    private Long durationMs;

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }
    public Integer getOrder() { return order; }
    public void setOrder(Integer order) { this.order = order; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<String> getDependsOn() { return dependsOn; }
    public void setDependsOn(List<String> dependsOn) { this.dependsOn = dependsOn; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public String getCompletedAt() { return completedAt; }
    public void setCompletedAt(String completedAt) { this.completedAt = completedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
}
