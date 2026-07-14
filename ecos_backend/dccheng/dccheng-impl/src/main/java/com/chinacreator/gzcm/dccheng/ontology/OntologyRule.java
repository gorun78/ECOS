package com.chinacreator.gzcm.dccheng.ontology;

import java.time.LocalDateTime;

/**
 * 规则持久化 POJO — 对应 ecos_ontology_rule 表
 */
public class OntologyRule {

    private String id;
    private String entityId;
    private String code;
    private String name;
    private String ruleType;     // VALIDATION / CALCULATION / DECISION / AGENT
    private String expression;
    private String action;
    private int priority;
    private int enabled;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public OntologyRule() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }

    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public int getEnabled() { return enabled; }
    public void setEnabled(int enabled) { this.enabled = enabled; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
