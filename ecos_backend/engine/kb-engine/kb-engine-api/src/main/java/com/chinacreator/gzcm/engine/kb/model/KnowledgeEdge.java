package com.chinacreator.gzcm.engine.kb.model;

import java.time.LocalDateTime;

public class KnowledgeEdge {

    private String id;
    private String sourceNodeId;
    private String targetNodeId;
    private String relationship;
    private double weight;
    private String propertiesJson;
    private LocalDateTime createdAt;

    public KnowledgeEdge() {}

    public KnowledgeEdge(String id, String sourceNodeId, String targetNodeId, String relationship, double weight) {
        this.id = id;
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.relationship = relationship;
        this.weight = weight;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSourceNodeId() { return sourceNodeId; }
    public void setSourceNodeId(String sourceNodeId) { this.sourceNodeId = sourceNodeId; }
    public String getTargetNodeId() { return targetNodeId; }
    public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }
    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }
    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }
    public String getPropertiesJson() { return propertiesJson; }
    public void setPropertiesJson(String propertiesJson) { this.propertiesJson = propertiesJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}