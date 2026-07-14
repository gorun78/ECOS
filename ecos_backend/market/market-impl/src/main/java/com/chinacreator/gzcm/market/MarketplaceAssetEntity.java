package com.chinacreator.gzcm.market;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 数据市场资产持久化实体 — 对应 ecos_marketplace_asset 表
 */
public class MarketplaceAssetEntity {

    private Long id;
    private String name;
    private String description;
    private String category;     // 数据集 / AI模型 / API / 报表
    private String owner;
    private BigDecimal rating;   // 0.00~5.00
    private Integer popularity;  // 综合热度值
    private String status;       // DRAFT / PUBLISHED / DEPRECATED
    private String ontologyEntityId; // linked ontology entity ID
    private LocalDateTime createdAt;

    public MarketplaceAssetEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }

    public Integer getPopularity() { return popularity; }
    public void setPopularity(Integer popularity) { this.popularity = popularity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOntologyEntityId() { return ontologyEntityId; }
    public void setOntologyEntityId(String ontologyEntityId) { this.ontologyEntityId = ontologyEntityId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
