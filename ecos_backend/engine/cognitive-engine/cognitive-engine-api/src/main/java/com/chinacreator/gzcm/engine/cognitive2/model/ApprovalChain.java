package com.chinacreator.gzcm.engine.cognitive2.model;

import java.sql.Timestamp;

/**
 * 审批链 — 决策的多级审批记录。
 * 对应表 ecos_decision_approval。
 */
public class ApprovalChain {

    private String id;
    private String decisionId;
    private String approver;
    private int level;
    private String status;
    private String comment;
    private Timestamp createdAt;

    public ApprovalChain() {}

    public ApprovalChain(String id, String decisionId, String approver,
                         int level, String status, String comment) {
        this.id = id;
        this.decisionId = decisionId;
        this.approver = approver;
        this.level = level;
        this.status = status;
        this.comment = comment;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }
    public String getApprover() { return approver; }
    public void setApprover(String approver) { this.approver = approver; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
