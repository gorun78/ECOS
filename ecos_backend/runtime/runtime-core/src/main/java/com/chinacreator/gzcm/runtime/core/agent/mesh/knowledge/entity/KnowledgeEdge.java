package com.chinacreator.gzcm.runtime.core.agent.mesh.knowledge.entity;

import java.time.LocalDateTime;

/**
 * 知识图谱边 — 映射 ecos_knowledge_graph_edge
 */
public class KnowledgeEdge {

    private String id;
    private String sourceNodeId;
    private String targetNodeId;
    private String relationship;
    private Double weight;
    private LocalDateTime createdAt;

    public KnowledgeEdge() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(String sourceNodeId) { this.sourceNodeId = sourceNodeId; }

    public String getTargetNodeId() { return targetNodeId; }
    public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }

    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
