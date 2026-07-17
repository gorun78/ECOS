package com.chinacreator.gzcm.services.agent.runtime.model;

import java.util.HashMap;
import java.util.Map;

public class ExecutionTask {
    private String id;
    private String planId;
    private String agentId;
    private String instruction;
    private ToolType toolType;
    private Map<String, Object> toolParams = new HashMap<>();
    private TaskStatus status = TaskStatus.PENDING;
    private ExecutionResult result;

    public ExecutionTask() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }
    public ToolType getToolType() { return toolType; }
    public void setToolType(ToolType toolType) { this.toolType = toolType; }
    public Map<String, Object> getToolParams() { return toolParams; }
    public void setToolParams(Map<String, Object> toolParams) { this.toolParams = toolParams; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public ExecutionResult getResult() { return result; }
    public void setResult(ExecutionResult result) { this.result = result; }
}
