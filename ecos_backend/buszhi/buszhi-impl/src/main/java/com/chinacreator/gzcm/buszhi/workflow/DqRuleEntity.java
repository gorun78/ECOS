package com.chinacreator.gzcm.buszhi.workflow;

import java.time.LocalDateTime;

/**
 * 数据质量规则持久化实体
 * <p>
 * 对应 ecos_dq_rule 表，存储数据质量规则的定义。
 * config_json 以 JSON 字符串存储（PostgreSQL TEXT 列）。
 * </p>
 */
public class DqRuleEntity {

    private String id;
    private String name;
    private String description;
    private String ruleType;
    private String configJson;   // JSON: {"threshold":95.0, "passRate":95.0}
    private String severity;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public DqRuleEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
