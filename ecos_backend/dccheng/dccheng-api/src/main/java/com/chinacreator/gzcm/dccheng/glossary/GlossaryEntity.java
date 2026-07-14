package com.chinacreator.gzcm.dccheng.glossary;

import java.time.LocalDateTime;

/**
 * 术语库持久化实体 — 对应 ecos_glossary_term 表
 */
public class GlossaryEntity {

    private Long id;
    private String code;
    private String name;
    private String definition;
    private String domain;
    private String owner;
    private String status;       // DRAFT / REVIEW / PUBLISHED / DEPRECATED
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public GlossaryEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
