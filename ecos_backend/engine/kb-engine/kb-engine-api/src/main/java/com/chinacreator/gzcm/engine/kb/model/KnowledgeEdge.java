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

    /** 溯源：本体业务 ID（对齐 kb_ontology_snapshot.ontology_id，D9） */
    private String ontologyId;
    /** 溯源：生成本边时的本体版本（对齐 kb_ontology_snapshot.version，D9） */
    private String ontologyVersion;
    /** 溯源：实例抽取来源的 DW 层数据资源 ID（B3 实例抽取写入） */
    private String sourceResourceId;
    /** 溯源：实例抽取来源的 DW 表主键值（B3 实例抽取写入） */
    private String sourcePk;

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
    public String getOntologyId() { return ontologyId; }
    public void setOntologyId(String ontologyId) { this.ontologyId = ontologyId; }
    public String getOntologyVersion() { return ontologyVersion; }
    public void setOntologyVersion(String ontologyVersion) { this.ontologyVersion = ontologyVersion; }
    public String getSourceResourceId() { return sourceResourceId; }
    public void setSourceResourceId(String sourceResourceId) { this.sourceResourceId = sourceResourceId; }
    public String getSourcePk() { return sourcePk; }
    public void setSourcePk(String sourcePk) { this.sourcePk = sourcePk; }
}