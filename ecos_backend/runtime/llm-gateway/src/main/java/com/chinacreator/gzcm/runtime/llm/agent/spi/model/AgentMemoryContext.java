package com.chinacreator.gzcm.runtime.llm.agent.spi.model;

import java.util.List;

/**
 * SPI 契约：会话上下文装配结果（接缝义务 1 · memory 能力出参）。
 *
 * <p>四层各自独立，缺层为 {@code null}（表示 provider 未装配该层）而非空列表，
 * 以免调用方把「未装配」误读为「无记忆」。</p>
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0
 */
public class AgentMemoryContext {

    private String agentId;
    private String sessionId;
    private List<AgentMemoryRecord> workingMemory;
    private List<AgentMemoryRecord> sessionMemory;
    private List<AgentMemoryRecord> longTermMemory;
    private List<AgentMemoryRecord> enterpriseMemory;

    public AgentMemoryContext() {
    }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public List<AgentMemoryRecord> getWorkingMemory() { return workingMemory; }
    public void setWorkingMemory(List<AgentMemoryRecord> workingMemory) { this.workingMemory = workingMemory; }
    public List<AgentMemoryRecord> getSessionMemory() { return sessionMemory; }
    public void setSessionMemory(List<AgentMemoryRecord> sessionMemory) { this.sessionMemory = sessionMemory; }
    public List<AgentMemoryRecord> getLongTermMemory() { return longTermMemory; }
    public void setLongTermMemory(List<AgentMemoryRecord> longTermMemory) { this.longTermMemory = longTermMemory; }
    public List<AgentMemoryRecord> getEnterpriseMemory() { return enterpriseMemory; }
    public void setEnterpriseMemory(List<AgentMemoryRecord> enterpriseMemory) { this.enterpriseMemory = enterpriseMemory; }

    /** 四层记录总数（上下文预算判定用）。 */
    public int totalRecords() {
        return size(workingMemory) + size(sessionMemory) + size(longTermMemory) + size(enterpriseMemory);
    }

    private static int size(List<AgentMemoryRecord> layer) {
        return layer == null ? 0 : layer.size();
    }
}
