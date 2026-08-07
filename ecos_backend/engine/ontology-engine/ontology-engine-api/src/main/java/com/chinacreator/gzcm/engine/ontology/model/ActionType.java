package com.chinacreator.gzcm.engine.ontology.model;

import java.time.LocalDateTime;

/**
 * ActionType POJO — 动作类型定义。
 *
 * <p>每一行代表一个可执行的动作类型（如"审批订单""发布版本"），
 * 包含前置条件（preconditions JSON）、后置动作（post_actions JSON）和审计开关。</p>
 */
public class ActionType {

    private String id;
    private String name;
    private String description;
    private String objectTypeId;
    private String preconditions;    // JSON
    private String postActions;      // JSON
    private Boolean auditRequired;
    private Boolean enabled;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ActionType() {}

    // ── getters / setters ──

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getObjectTypeId() { return objectTypeId; }
    public void setObjectTypeId(String objectTypeId) { this.objectTypeId = objectTypeId; }

    public String getPreconditions() { return preconditions; }
    public void setPreconditions(String preconditions) { this.preconditions = preconditions; }

    public String getPostActions() { return postActions; }
    public void setPostActions(String postActions) { this.postActions = postActions; }

    public Boolean getAuditRequired() { return auditRequired; }
    public void setAuditRequired(Boolean auditRequired) { this.auditRequired = auditRequired; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
