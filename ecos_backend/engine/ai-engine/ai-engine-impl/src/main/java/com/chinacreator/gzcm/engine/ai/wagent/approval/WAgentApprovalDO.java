package com.chinacreator.gzcm.engine.ai.wagent.approval;

import java.time.LocalDateTime;

/**
 * {@code ecos_ai.agent_approval} 行（V50 建表 + V326 补凭证列）。
 *
 * <p>{@code tokenDigest} 只存 SHA-256 摘要；<b>明文凭证不入库</b>，只在签发响应出现一次。</p>
 */
public class WAgentApprovalDO {

    private String id;
    private String taskId;
    private String riskLevel;
    private String status;
    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;
    private String approvedBy;
    private String comment;
    private String tokenDigest;
    private String scopeType;
    private String scopeId;
    private String requestedBy;
    private LocalDateTime expiredAt;
    private LocalDateTime consumedAt;
    private String traceId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getTokenDigest() {
        return tokenDigest;
    }

    public void setTokenDigest(String tokenDigest) {
        this.tokenDigest = tokenDigest;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public LocalDateTime getExpiredAt() {
        return expiredAt;
    }

    public void setExpiredAt(LocalDateTime expiredAt) {
        this.expiredAt = expiredAt;
    }

    public LocalDateTime getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(LocalDateTime consumedAt) {
        this.consumedAt = consumedAt;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public boolean consumed() {
        return consumedAt != null;
    }
}
