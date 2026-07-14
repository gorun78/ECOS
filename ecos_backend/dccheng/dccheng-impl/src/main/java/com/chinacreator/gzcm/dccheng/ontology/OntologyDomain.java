package com.chinacreator.gzcm.dccheng.ontology;

import java.time.LocalDateTime;

/**
 * 领域持久化 POJO — 对应 ecos_domain 表
 */
public class OntologyDomain {

    private String id;
    private String code;
    private String name;
    private String owner;
    private String description;
    private String status;       // Draft / Published / Deprecated
    private int sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public OntologyDomain() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
