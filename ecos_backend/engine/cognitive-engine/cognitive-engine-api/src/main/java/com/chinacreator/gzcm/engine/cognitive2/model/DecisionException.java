package com.chinacreator.gzcm.engine.cognitive2.model;

import java.sql.Timestamp;

/**
 * 决策例外 — 对特定决策的策略偏离记录。
 * 对应表 ecos_decision_exception。
 */
public class DecisionException {

    private String id;
    private String decisionId;
    private String reason;
    private String approver;
    private String status;
    private Timestamp createdAt;

    public DecisionException() {}

    public DecisionException(String id, String decisionId, String reason,
                             String approver, String status) {
        this.id = id;
        this.decisionId = decisionId;
        this.reason = reason;
        this.approver = approver;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getApprover() { return approver; }
    public void setApprover(String approver) { this.approver = approver; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
