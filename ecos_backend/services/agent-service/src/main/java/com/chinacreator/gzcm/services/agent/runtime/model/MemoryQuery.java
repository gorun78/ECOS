package com.chinacreator.gzcm.services.agent.runtime.model;

public class MemoryQuery {
    private String agentId;
    private String sessionId;
    private MemoryLayer layer;
    private String keywords;
    private float[] vector;
    private int topK = 10;

    public MemoryQuery() {}

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public MemoryLayer getLayer() { return layer; }
    public void setLayer(MemoryLayer layer) { this.layer = layer; }
    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
    public float[] getVector() { return vector; }
    public void setVector(float[] vector) { this.vector = vector; }
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
}
