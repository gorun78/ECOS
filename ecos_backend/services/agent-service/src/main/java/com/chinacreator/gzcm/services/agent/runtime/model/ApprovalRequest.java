package com.chinacreator.gzcm.services.agent.runtime.model;

import java.time.Instant;

public class ApprovalRequest {
    private String id;
    private String taskId;
    private RiskLevel riskLevel;
    private Instant requestedAt;
    private String status = "PENDING";

    public ApprovalRequest() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }
    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
