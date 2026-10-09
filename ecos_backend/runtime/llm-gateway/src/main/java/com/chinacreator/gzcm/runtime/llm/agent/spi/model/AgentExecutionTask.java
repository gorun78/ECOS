package com.chinacreator.gzcm.runtime.llm.agent.spi.model;

import java.util.HashMap;
import java.util.Map;

/**
 * SPI 契约：待执行任务（接缝义务 1 · executor 能力入参）。
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0
 */
public class AgentExecutionTask {

    private String id;
    private String planId;
    private String agentId;
    private String sessionId;
    private String instruction;
    private AgentToolType toolType;
    private Map<String, Object> toolParams = new HashMap<>();
    private String status;
    private AgentExecutionResult result;

    public AgentExecutionTask() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }
    public AgentToolType getToolType() { return toolType; }
    public void setToolType(AgentToolType toolType) { this.toolType = toolType; }
    public Map<String, Object> getToolParams() { return toolParams; }
    public void setToolParams(Map<String, Object> toolParams) {
        this.toolParams = toolParams == null ? new HashMap<>() : toolParams;
    }

    /**
     * 任务状态以字符串承载：状态机语义归分册 10（W Agent 编排域），
     * 智能域侧不自建状态枚举，避免双主责（§8.1 分工口径）。
     */
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public AgentExecutionResult getResult() { return result; }
    public void setResult(AgentExecutionResult result) { this.result = result; }
}
