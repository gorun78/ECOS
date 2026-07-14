package com.chinacreator.gzcm.buszhi.workflow;

import java.time.LocalDateTime;

/**
 * 工作流实例实体 — 对应 ecos_workflow_instance 表。
 */
public class WorkflowInstanceEntity {

    private String id;
    private String workflowId;
    private String workflowName;
    private String versionNo;
    private String status;               // Created/Running/Waiting/Completed/Failed/Suspended
    private String triggerType;
    private String triggeredBy;
    private String triggeredObjectId;
    private String triggerEvent;
    private String variables;            // JSONB
    private String context;              // JSONB
    private String currentNodeIds;       // TEXT[] → JSON array string
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private int retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public WorkflowInstanceEntity() {}

    // Getters/Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public String getWorkflowName() { return workflowName; }
    public void setWorkflowName(String workflowName) { this.workflowName = workflowName; }

    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }

    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }

    public String getTriggeredObjectId() { return triggeredObjectId; }
    public void setTriggeredObjectId(String triggeredObjectId) { this.triggeredObjectId = triggeredObjectId; }

    public String getTriggerEvent() { return triggerEvent; }
    public void setTriggerEvent(String triggerEvent) { this.triggerEvent = triggerEvent; }

    public String getVariables() { return variables; }
    public void setVariables(String variables) { this.variables = variables; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public String getCurrentNodeIds() { return currentNodeIds; }
    public void setCurrentNodeIds(String currentNodeIds) { this.currentNodeIds = currentNodeIds; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
