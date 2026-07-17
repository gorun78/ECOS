package com.chinacreator.gzcm.services.agent.runtime.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrchestrationPlan {
    private String id;
    private String missionId;
    private Map<String, List<String>> agentAssignments = new HashMap<>();
    private List<String> executionOrder = new ArrayList<>();
    private Map<String, List<String>> dependencies = new HashMap<>();

    public OrchestrationPlan() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMissionId() { return missionId; }
    public void setMissionId(String missionId) { this.missionId = missionId; }
    public Map<String, List<String>> getAgentAssignments() { return agentAssignments; }
    public void setAgentAssignments(Map<String, List<String>> agentAssignments) { this.agentAssignments = agentAssignments; }
    public List<String> getExecutionOrder() { return executionOrder; }
    public void setExecutionOrder(List<String> executionOrder) { this.executionOrder = executionOrder; }
    public Map<String, List<String>> getDependencies() { return dependencies; }
    public void setDependencies(Map<String, List<String>> dependencies) { this.dependencies = dependencies; }
}
