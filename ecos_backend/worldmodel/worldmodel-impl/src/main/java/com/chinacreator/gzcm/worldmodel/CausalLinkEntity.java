package com.chinacreator.gzcm.worldmodel;

import java.time.LocalDateTime;

/**
 * World Model 因果链持久化实体
 * <p>
 * 对应 ecos_wm_causal_link 表，存储目标之间的因果关系。
 * </p>
 */
public class CausalLinkEntity {

    private Long id;
    private Long sourceGoalId;
    private Long targetGoalId;
    private String relationshipType;   // POSITIVE | NEGATIVE
    private String description;
    private LocalDateTime createdAt;

    public CausalLinkEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSourceGoalId() { return sourceGoalId; }
    public void setSourceGoalId(Long sourceGoalId) { this.sourceGoalId = sourceGoalId; }

    public Long getTargetGoalId() { return targetGoalId; }
    public void setTargetGoalId(Long targetGoalId) { this.targetGoalId = targetGoalId; }

    public String getRelationshipType() { return relationshipType; }
    public void setRelationshipType(String relationshipType) { this.relationshipType = relationshipType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
