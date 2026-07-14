package com.chinacreator.gzcm.dccheng.ontology;

import java.time.LocalDateTime;

/**
 * 动作定义持久化 POJO — 本体动作（同步/发布/通知等）
 */
public class OntologyAction {

    private String id;
    private String entityId;
    private String code;
    private String name;
    private String actionType;
    private String description;
    private String preconditions;   // JSON
    private String effects;         // JSON
    private String ruleJson;
    private String strategy;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public OntologyAction() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPreconditions() { return preconditions; }
    public void setPreconditions(String preconditions) { this.preconditions = preconditions; }

    public String getEffects() { return effects; }
    public void setEffects(String effects) { this.effects = effects; }

    public String getRuleJson() { return ruleJson; }
    public void setRuleJson(String ruleJson) { this.ruleJson = ruleJson; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
