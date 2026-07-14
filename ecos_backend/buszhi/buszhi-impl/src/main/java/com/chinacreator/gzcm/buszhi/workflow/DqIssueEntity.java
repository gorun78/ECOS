package com.chinacreator.gzcm.buszhi.workflow;

import java.time.LocalDateTime;

/**
 * 数据质量问题持久化实体
 * <p>
 * 对应 ecos_dq_issue 表，存储数据质量规则检查发现的问题。
 * </p>
 */
public class DqIssueEntity {

    private String id;
    private String ruleId;
    private String assetId;      // "entity:entityId" e.g. "Customer:c005"
    private String description;
    private String status;       // open | resolved
    private String severity;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    public DqIssueEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
