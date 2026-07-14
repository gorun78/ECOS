package com.chinacreator.gzcm.market;

import java.time.LocalDateTime;

/**
 * 数据市场访问申请持久化实体 — 对应 ecos_marketplace_access_request 表
 */
public class MarketplaceAccessRequestEntity {

    private Long id;
    private Long assetId;
    private String reason;
    private String applicant;
    private String status;      // PENDING / APPROVED / REJECTED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public MarketplaceAccessRequestEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getApplicant() { return applicant; }
    public void setApplicant(String applicant) { this.applicant = applicant; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
