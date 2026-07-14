package com.chinacreator.gzcm.worldmodel;

import java.time.LocalDateTime;

/**
 * World Model 场景持久化实体
 * <p>
 * 对应 ecos_wm_scenario 表，存储场景配置。
 * configJson 为 JSON 字符串，可扩展存储 score/cost/risk 等自定义字段。
 * </p>
 */
public class ScenarioEntity {

    private Long id;
    private String name;
    private String description;
    private String configJson;     // JSON: {"score": 65.0, "cost": 0.0, "risk": "LOW"}
    private String status;         // DRAFT | ACTIVE | ARCHIVED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ScenarioEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
