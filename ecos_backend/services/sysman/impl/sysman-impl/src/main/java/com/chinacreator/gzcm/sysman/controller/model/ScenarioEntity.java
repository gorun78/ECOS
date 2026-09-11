package com.chinacreator.gzcm.sysman.controller.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * ECOS Phase 1 P1-3: World Model — 场景模型。
 * <p>
 * MVP 阶段使用内存 ConcurrentHashMap 存储，无 JDBC 依赖。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScenarioEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String description;
    private List<String> relatedGoalIds;
    private String status;          // active | draft | archived
    private Long createdAt;
    private Long updatedAt;

    public ScenarioEntity() {
        this.relatedGoalIds = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getRelatedGoalIds() { return relatedGoalIds; }
    public void setRelatedGoalIds(List<String> relatedGoalIds) { this.relatedGoalIds = relatedGoalIds; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }
}
