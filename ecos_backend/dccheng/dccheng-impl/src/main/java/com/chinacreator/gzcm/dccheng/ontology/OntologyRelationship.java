package com.chinacreator.gzcm.dccheng.ontology;

import java.time.LocalDateTime;

/**
 * 本体关系持久化 POJO
 */
public class OntologyRelationship {

    private String id;
    private String sourceEntityId;
    private String targetEntityId;
    private String code;
    private String name;
    private String relationshipType;
    private LocalDateTime createdAt;

    public OntologyRelationship() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSourceEntityId() { return sourceEntityId; }
    public void setSourceEntityId(String sourceEntityId) { this.sourceEntityId = sourceEntityId; }

    public String getTargetEntityId() { return targetEntityId; }
    public void setTargetEntityId(String targetEntityId) { this.targetEntityId = targetEntityId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRelationshipType() { return relationshipType; }
    public void setRelationshipType(String relationshipType) { this.relationshipType = relationshipType; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
