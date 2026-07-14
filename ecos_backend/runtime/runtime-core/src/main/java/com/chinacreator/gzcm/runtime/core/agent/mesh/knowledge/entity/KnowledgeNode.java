package com.chinacreator.gzcm.runtime.core.agent.mesh.knowledge.entity;

import java.time.LocalDateTime;

/**
 * 知识图谱节点 — 映射 ecos_knowledge_graph_node
 */
public class KnowledgeNode {

    private String id;
    private String label;
    private String nodeType;
    private String description;
    private String propertiesJson;  // JSONB
    private LocalDateTime createdAt;

    public KnowledgeNode() {}

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
