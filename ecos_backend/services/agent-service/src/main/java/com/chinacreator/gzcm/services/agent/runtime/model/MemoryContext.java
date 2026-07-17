package com.chinacreator.gzcm.services.agent.runtime.model;

import java.util.List;

public class MemoryContext {
    private String agentId;
    private String sessionId;
    private List<MemoryRecord> workingMemory;
    private List<MemoryRecord> sessionMemory;
    private List<MemoryRecord> longTermMemory;
    private List<MemoryRecord> enterpriseMemory;

    public MemoryContext() {}

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public List<MemoryRecord> getWorkingMemory() { return workingMemory; }
    public void setWorkingMemory(List<MemoryRecord> workingMemory) { this.workingMemory = workingMemory; }
    public List<MemoryRecord> getSessionMemory() { return sessionMemory; }
    public void setSessionMemory(List<MemoryRecord> sessionMemory) { this.sessionMemory = sessionMemory; }
    public List<MemoryRecord> getLongTermMemory() { return longTermMemory; }
    public void setLongTermMemory(List<MemoryRecord> longTermMemory) { this.longTermMemory = longTermMemory; }
    public List<MemoryRecord> getEnterpriseMemory() { return enterpriseMemory; }
    public void setEnterpriseMemory(List<MemoryRecord> enterpriseMemory) { this.enterpriseMemory = enterpriseMemory; }
}
