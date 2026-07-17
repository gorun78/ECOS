package com.chinacreator.gzcm.services.agent.runtime.model;

import java.time.Instant;

public class MemoryRecord {
    private String id;
    private String agentId;
    private String sessionId;
    private MemoryLayer layer;
    private String content;
    private float[] embedding;
    private Instant timestamp;

    public MemoryRecord() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public MemoryLayer getLayer() { return layer; }
    public void setLayer(MemoryLayer layer) { this.layer = layer; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
