package com.chinacreator.gzcm.runtime.llm.agent.spi.model;

/**
 * SPI 契约：记忆检索条件（接缝义务 1 · memory 能力入参）。
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0
 */
public class AgentMemoryQuery {

    private String agentId;
    private String sessionId;
    private AgentMemoryLayer layer;
    private String keywords;
    private float[] vector;
    private int topK = 10;

    public AgentMemoryQuery() {
    }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public AgentMemoryLayer getLayer() { return layer; }
    public void setLayer(AgentMemoryLayer layer) { this.layer = layer; }
    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
    public float[] getVector() { return vector; }
    public void setVector(float[] vector) { this.vector = vector; }
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
}
