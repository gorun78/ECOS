package com.chinacreator.gzcm.engine.cognitive2.model;

import java.sql.Timestamp;

/**
 * 溯源条目 — 决策/事实/规则的统一来源记录。
 * 对应表 ecos_provenance_entry。
 */
public class ProvenanceEntry {

    private String id;
    private String entityType;
    private String entityId;
    private String sourceType;
    private String sourceRef;
    private String agent;
    private String activity;
    private Timestamp timestamp;

    public ProvenanceEntry() {}

    public ProvenanceEntry(String id, String entityType, String entityId,
                           String sourceType, String sourceRef, String agent, String activity) {
        this.id = id;
        this.entityType = entityType;
        this.entityId = entityId;
        this.sourceType = sourceType;
        this.sourceRef = sourceRef;
        this.agent = agent;
        this.activity = activity;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public String getAgent() { return agent; }
    public void setAgent(String agent) { this.agent = agent; }
    public String getActivity() { return activity; }
    public void setActivity(String activity) { this.activity = activity; }
    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }
}
