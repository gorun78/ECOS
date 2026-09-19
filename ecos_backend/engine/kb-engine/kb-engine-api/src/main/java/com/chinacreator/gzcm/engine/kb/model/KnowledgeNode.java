package com.chinacreator.gzcm.engine.kb.model;

import java.time.LocalDateTime;

public class KnowledgeNode {

    private String id;
    private String label;
    private String nodeType;
    private String description;
    private String propertiesJson;
    private String domain;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 溯源：本体业务 ID（对齐 kb_ontology_snapshot.ontology_id，D9） */
    private String ontologyId;
    /** 溯源：生成本节点时的本体版本（对齐 kb_ontology_snapshot.version，D9） */
    private String ontologyVersion;
    /** 溯源：实例抽取来源的 DW 层数据资源 ID（B3 实例抽取写入） */
    private String sourceResourceId;
    /** 溯源：实例抽取来源的 DW 表主键值（B3 实例抽取写入） */
    private String sourcePk;

    public KnowledgeNode() {}

    public KnowledgeNode(String id, String label, String nodeType, String description, String propertiesJson) {
        this.id = id;
        this.label = label;
        this.nodeType = nodeType;
        this.description = description;
        this.propertiesJson = propertiesJson;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPropertiesJson() { return propertiesJson; }
    public void setPropertiesJson(String propertiesJson) { this.propertiesJson = propertiesJson; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getOntologyId() { return ontologyId; }
    public void setOntologyId(String ontologyId) { this.ontologyId = ontologyId; }
    public String getOntologyVersion() { return ontologyVersion; }
    public void setOntologyVersion(String ontologyVersion) { this.ontologyVersion = ontologyVersion; }
    public String getSourceResourceId() { return sourceResourceId; }
    public void setSourceResourceId(String sourceResourceId) { this.sourceResourceId = sourceResourceId; }
    public String getSourcePk() { return sourcePk; }
    public void setSourcePk(String sourcePk) { this.sourcePk = sourcePk; }
}