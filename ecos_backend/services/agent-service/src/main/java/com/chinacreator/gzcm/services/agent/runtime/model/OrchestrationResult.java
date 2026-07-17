package com.chinacreator.gzcm.services.agent.runtime.model;

import java.util.HashMap;
import java.util.Map;

public class OrchestrationResult {
    private String planId;
    private String missionId;
    private Map<String, ExecutionResult> results = new HashMap<>();
    private boolean success;
    private String summary;

    public OrchestrationResult() {}

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getMissionId() { return missionId; }
    public void setMissionId(String missionId) { this.missionId = missionId; }
    public Map<String, ExecutionResult> getResults() { return results; }
    public void setResults(Map<String, ExecutionResult> results) { this.results = results; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
