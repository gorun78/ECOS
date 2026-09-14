package com.chinacreator.gzcm.buszhi.workflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 工作流实例（Workflow Instance）VO — 强类型返回。
 * （溯源：ontology-engine-impl T16-4 等价移植，PMO-57 T2 迁入 buszhi 服务层。）
 *
 * <p>字段对齐 {@code WorkflowInstanceService.toMap(WorkflowInstanceEntity)} 输出。
 * 启动实例（{@code startInstance}）额外返回 {@code definition}（子集定义摘要）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowInstanceVO {

    /** 实例 ID（pi 前缀） */
    private String id;

    /** 所属工作流 ID */
    private String workflowId;

    /** 所属工作流名称 */
    private String workflowName;

    /** 状态（Running / Suspended / Completed / Failed / Terminated） */
    private String status;

    /** 触发类型（MANUAL / SCHEDULED） */
    private String triggerType;

    /** 触发者 */
    private String triggeredBy;

    /** 开始时间 ISO 字符串 */
    private String startedAt;

    /** 完成时间 ISO 字符串 */
    private String completedAt;

    /** 错误消息 */
    private String errorMessage;

    /** 创建时间 ISO 字符串 */
    private String createdAt;

    /**
     * 关联的工作流定义摘要（仅 {@code startInstance} 返回时填充）。
     * 嵌套子集动态摘要，保持 Object 类型（与既有 Map 内 {@code definition} 子结构对齐）。
     */
    private Object definition;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public Object getDefinition() {
        return definition;
    }

    public void setDefinition(Object definition) {
        this.definition = definition;
    }
}
