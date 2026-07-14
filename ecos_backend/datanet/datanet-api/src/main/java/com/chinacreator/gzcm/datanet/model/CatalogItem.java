package com.chinacreator.gzcm.datanet.model;

import java.time.LocalDateTime;

/**
 * 目录项 — 数据资源在统一目录中的展示条目。
 * <p>
 * 不同于 DataResource（技术视角），CatalogItem 面向数据消费者：
 * 聚合资源摘要、质量评分、访问方式等信息，便于搜索和发现。
 *
 * @author DataBridge Datanet Team
 */
public class CatalogItem {

    /** 目录条目 ID */
    private String catalogId;

    /** 关联资源 ID */
    private String resourceId;

    /** 资源名称 */
    private String resourceName;

    /** 资源类型 */
    private String resourceType;

    /** 所属部门 */
    private String orgName;

    /** 资源描述 */
    private String description;

    /** 标签 */
    private String tags;

    /** 分类路径（如 /政务/公安/人口） */
    private String categoryPath;

    /** 访问方式: JDBC, REST, FILE */
    private String accessType;

    /** 数据格式: JSON, CSV, TABLE, etc. */
    private String dataFormat;

    /** 字段数量 */
    private Integer fieldCount;

    /** 估算数据量 */
    private Long recordCount;

    /** 质量评分 (0-100) */
    private Integer qualityScore;

    /** 最近更新时间 */
    private LocalDateTime lastUpdated;

    /** 状态 */
    private String status;

    // ===== Getters/Setters =====

    public String getCatalogId() { return catalogId; }
    public void setCatalogId(String catalogId) { this.catalogId = catalogId; }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getCategoryPath() { return categoryPath; }
    public void setCategoryPath(String categoryPath) { this.categoryPath = categoryPath; }

    public String getAccessType() { return accessType; }
    public void setAccessType(String accessType) { this.accessType = accessType; }

    public String getDataFormat() { return dataFormat; }
    public void setDataFormat(String dataFormat) { this.dataFormat = dataFormat; }

    public Integer getFieldCount() { return fieldCount; }
    public void setFieldCount(Integer fieldCount) { this.fieldCount = fieldCount; }

    public Long getRecordCount() { return recordCount; }
    public void setRecordCount(Long recordCount) { this.recordCount = recordCount; }

    public Integer getQualityScore() { return qualityScore; }
    public void setQualityScore(Integer qualityScore) { this.qualityScore = qualityScore; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
