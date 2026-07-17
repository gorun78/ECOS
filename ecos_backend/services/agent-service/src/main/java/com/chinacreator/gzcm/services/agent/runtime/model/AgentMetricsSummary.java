package com.chinacreator.gzcm.services.agent.runtime.model;

public class AgentMetricsSummary {
    private String agentId;
    private long totalPrompts;
    private long totalToolCalls;
    private double avgLatencyMs;
    private long totalTokens;
    private double totalCost;

    public AgentMetricsSummary() {}

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public long getTotalPrompts() { return totalPrompts; }
    public void setTotalPrompts(long totalPrompts) { this.totalPrompts = totalPrompts; }
    public long getTotalToolCalls() { return totalToolCalls; }
    public void setTotalToolCalls(long totalToolCalls) { this.totalToolCalls = totalToolCalls; }
    public double getAvgLatencyMs() { return avgLatencyMs; }
    public void setAvgLatencyMs(double avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }
    public long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }
    public double getTotalCost() { return totalCost; }
    public void setTotalCost(double totalCost) { this.totalCost = totalCost; }
}
