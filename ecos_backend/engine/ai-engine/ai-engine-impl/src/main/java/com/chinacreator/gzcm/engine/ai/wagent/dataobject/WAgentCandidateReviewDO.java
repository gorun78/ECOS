package com.chinacreator.gzcm.engine.ai.wagent.dataobject;

import java.time.LocalDateTime;

/**
 * V239 {@code ecos_ai.ecos_wagent_candidate_review} 候选评审记录 DO（评审凭证与决定留痕）。
 * <p>{@code decision} 单源 = {@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReviewDecision}
 * （APPROVE/REJECT/REVISE；本表 DDL 未设 CHECK——评审决定形态随 R3-A 收口，R-15 纪律）。
 * {@code approval_token} 凭证令牌禁回显（来源含存量 {@code public.ecos_decision_approval}/
 * {@code ecos_workflow_approval} 审批链，不建 FK，ST09）。</p>
 * <p>评审动作事件走 Kafka {@code ecos.audit}（ST06），本表只存评审事实。</p>
 */
public class WAgentCandidateReviewDO extends WAgentBaseDO {

    private String id;
    private String candidateId;
    private String reviewerId;
    private String decision;
    private String commentText;
    private String changesJson;
    private String approvalToken;
    private LocalDateTime reviewTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCandidateId() { return candidateId; }
    public void setCandidateId(String candidateId) { this.candidateId = candidateId; }

    public String getReviewerId() { return reviewerId; }
    public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getCommentText() { return commentText; }
    public void setCommentText(String commentText) { this.commentText = commentText; }

    public String getChangesJson() { return changesJson; }
    public void setChangesJson(String changesJson) { this.changesJson = changesJson; }

    public String getApprovalToken() { return approvalToken; }
    public void setApprovalToken(String approvalToken) { this.approvalToken = approvalToken; }

    public LocalDateTime getReviewTime() { return reviewTime; }
    public void setReviewTime(LocalDateTime reviewTime) { this.reviewTime = reviewTime; }
}
