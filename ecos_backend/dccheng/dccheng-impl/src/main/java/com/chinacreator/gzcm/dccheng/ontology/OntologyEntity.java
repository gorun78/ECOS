package com.chinacreator.gzcm.dccheng.ontology;

import java.time.LocalDateTime;

/**
 * 本体实体持久化 POJO
 */
public class OntologyEntity {

    private String id;
    private String ontologyId;
    private String code;
    private String name;
    private String description;
    private String entityType;
    private String domainId;
    private int sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public OntologyEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOntologyId() { return ontologyId; }
    public void setOntologyId(String ontologyId) { this.ontologyId = ontologyId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getDomainId() { return domainId; }
    public void setDomainId(String domainId) { this.domainId = domainId; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
