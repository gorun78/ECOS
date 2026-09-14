package com.chinacreator.gzcm.buszhi.workflow;

import java.time.LocalDateTime;

/**
 * 工作流审批记录实体 — 对应 ecos_workflow_approval 表。
 */
public class WorkflowApprovalEntity {

    private String id;
    private String taskId;
    private String instanceId;
    private String approver;
    private String decision;             // Approved/Rejected/Transferred
    private String opinion;
    private String formData;             // JSONB
    private LocalDateTime createdAt;

    public WorkflowApprovalEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public String getApprover() { return approver; }
    public void setApprover(String approver) { this.approver = approver; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getOpinion() { return opinion; }
    public void setOpinion(String opinion) { this.opinion = opinion; }

    public String getFormData() { return formData; }
    public void setFormData(String formData) { this.formData = formData; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
