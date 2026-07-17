package com.chinacreator.gzcm.services.agent.runtime.model;

public class GovernanceDecision {
    private String policyId;
    private ExecutionTask task;
    private boolean allowed;
    private String reason;

    public GovernanceDecision() {}
    public GovernanceDecision(boolean allowed, String reason) { this.allowed = allowed; this.reason = reason; }

    public String getPolicyId() { return policyId; }
    public void setPolicyId(String policyId) { this.policyId = policyId; }
    public ExecutionTask getTask() { return task; }
    public void setTask(ExecutionTask task) { this.task = task; }
    public boolean isAllowed() { return allowed; }
    public void setAllowed(boolean allowed) { this.allowed = allowed; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
