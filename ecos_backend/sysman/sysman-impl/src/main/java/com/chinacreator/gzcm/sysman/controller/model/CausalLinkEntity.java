package com.chinacreator.gzcm.sysman.controller.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serial;
import java.io.Serializable;

/**
 * ECOS Phase 1 P1-3: World Model — 因果链模型。
 * <p>
 * MVP 阶段使用内存 ConcurrentHashMap 存储，无 JDBC 依赖。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CausalLinkEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String sourceGoalId;
    private String targetGoalId;
    private String label;
    private Double strength;        // 0.0 ~ 1.0
    private Long createdAt;

    public CausalLinkEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSourceGoalId() { return sourceGoalId; }
    public void setSourceGoalId(String sourceGoalId) { this.sourceGoalId = sourceGoalId; }

    public String getTargetGoalId() { return targetGoalId; }
    public void setTargetGoalId(String targetGoalId) { this.targetGoalId = targetGoalId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public Double getStrength() { return strength; }
    public void setStrength(Double strength) { this.strength = strength; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}
