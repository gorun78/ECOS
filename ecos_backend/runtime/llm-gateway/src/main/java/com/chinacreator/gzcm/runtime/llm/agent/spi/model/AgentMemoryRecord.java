package com.chinacreator.gzcm.runtime.llm.agent.spi.model;

import java.time.Instant;

/**
 * SPI 契约：一条记忆记录（接缝义务 1 · memory 能力）。
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0
 */
public class AgentMemoryRecord {

    private String id;
    private String agentId;
    private String sessionId;
    private AgentMemoryLayer layer;
    private String content;
    private float[] embedding;
    private Instant timestamp;

    public AgentMemoryRecord() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public AgentMemoryLayer getLayer() { return layer; }
    public void setLayer(AgentMemoryLayer layer) { this.layer = layer; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
