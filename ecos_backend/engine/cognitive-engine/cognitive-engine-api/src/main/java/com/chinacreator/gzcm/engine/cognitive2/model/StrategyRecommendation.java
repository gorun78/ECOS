package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.ArrayList;
import java.util.List;

public class StrategyRecommendation {
    private String id;
    private String goal;
    private List<String> actions = new ArrayList<>();
    private double expectedImpact;
    private double riskLevel;
    private String reasoning;
    public StrategyRecommendation() {}
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }
    public List<String> getActions() { return actions; }
    public void setActions(List<String> actions) { this.actions = actions; }
    public double getExpectedImpact() { return expectedImpact; }
    public void setExpectedImpact(double expectedImpact) { this.expectedImpact = expectedImpact; }
    public double getRiskLevel() { return riskLevel; }
    public void setRiskLevel(double riskLevel) { this.riskLevel = riskLevel; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
}
