package com.chinacreator.gzcm.dccheng.ontology;

import java.time.LocalDateTime;

/**
 * 版本持久化 POJO — 对应 ecos_ontology_version 表
 */
public class OntologyVersion {

    private String id;
    private String ontologyId;
    private String versionNo;        // Major.Minor.Patch
    private String status;           // Draft / Review / Published / Deprecated / Archived
    private String snapshot;         // JSONB — 完整快照
    private String changeLog;
    private String publisher;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;

    public OntologyVersion() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOntologyId() { return ontologyId; }
    public void setOntologyId(String ontologyId) { this.ontologyId = ontologyId; }

    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }

    public String getChangeLog() { return changeLog; }
    public void setChangeLog(String changeLog) { this.changeLog = changeLog; }

    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }

    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
