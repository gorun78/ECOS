package com.chinacreator.gzcm.buszhi.workflow;

import java.time.LocalDateTime;

/**
 * 工作流任务实体 — 对应 ecos_workflow_task 表。
 */
public class WorkflowTaskEntity {

    private String id;
    private String instanceId;
    private String nodeId;
    private String taskType;             // APPROVAL/EXECUTION/AGENT/INVESTIGATION
    private String title;
    private String assignee;
    private String candidateUsers;       // TEXT[] → JSON array string
    private String candidateRoles;       // TEXT[] → JSON array string
    private String status;               // New/Assigned/InProgress/Completed/Rejected/Transferred
    private String priority;
    private String formSchema;           // JSONB
    private String formData;             // JSONB
    private String result;               // JSONB
    private String agentResult;          // JSONB
    private LocalDateTime dueDate;
    private LocalDateTime completedAt;
    private String completedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public WorkflowTaskEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }

    public String getCandidateUsers() { return candidateUsers; }
    public void setCandidateUsers(String candidateUsers) { this.candidateUsers = candidateUsers; }

    public String getCandidateRoles() { return candidateRoles; }
    public void setCandidateRoles(String candidateRoles) { this.candidateRoles = candidateRoles; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getFormSchema() { return formSchema; }
    public void setFormSchema(String formSchema) { this.formSchema = formSchema; }

    public String getFormData() { return formData; }
    public void setFormData(String formData) { this.formData = formData; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getAgentResult() { return agentResult; }
    public void setAgentResult(String agentResult) { this.agentResult = agentResult; }

    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getCompletedBy() { return completedBy; }
    public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
