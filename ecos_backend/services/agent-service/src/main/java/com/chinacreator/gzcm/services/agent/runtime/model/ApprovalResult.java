package com.chinacreator.gzcm.services.agent.runtime.model;

import java.time.Instant;

public class ApprovalResult {
    private String approvalId;
    private boolean approved;
    private String comment;
    private Instant processedAt;

    public ApprovalResult() {}
    public ApprovalResult(String approvalId, boolean approved, String comment) {
        this.approvalId = approvalId; this.approved = approved; this.comment = comment; this.processedAt = Instant.now();
    }

    public String getApprovalId() { return approvalId; }
    public void setApprovalId(String approvalId) { this.approvalId = approvalId; }
    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
