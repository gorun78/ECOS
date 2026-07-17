package com.chinacreator.gzcm.services.agent.runtime.model;

import java.time.Instant;

public class ToolCallRecord {
    private String id;
    private String agentId;
    private String toolName;
    private String params;
    private String result;
    private long latencyMs;
    private Instant timestamp;

    public ToolCallRecord() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getParams() { return params; }
    public void setParams(String params) { this.params = params; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
