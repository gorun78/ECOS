package com.chinacreator.gzcm.engine.kb.model;

import java.util.Map;

public class RuleExecutionResult {

    private String ruleId;
    private boolean fired;
    private Map<String, Object> output;
    private long executionTimeMs;

    public RuleExecutionResult() {}

    public RuleExecutionResult(String ruleId, boolean fired, Map<String, Object> output, long executionTimeMs) {
        this.ruleId = ruleId;
        this.fired = fired;
        this.output = output;
        this.executionTimeMs = executionTimeMs;
    }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public boolean isFired() { return fired; }
    public void setFired(boolean fired) { this.fired = fired; }
    public Map<String, Object> getOutput() { return output; }
    public void setOutput(Map<String, Object> output) { this.output = output; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
}