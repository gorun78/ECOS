package com.chinacreator.gzcm.market;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资产评价实体 — 对应 ecos_marketplace_review 表
 */
public class MarketplaceReviewEntity {

    private Long id;
    private Long assetId;
    private String reviewer;
    private BigDecimal rating;    // 1.00~5.00
    private String comment;
    private LocalDateTime createdAt;

    public MarketplaceReviewEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }

    public String getReviewer() { return reviewer; }
    public void setReviewer(String reviewer) { this.reviewer = reviewer; }

    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
