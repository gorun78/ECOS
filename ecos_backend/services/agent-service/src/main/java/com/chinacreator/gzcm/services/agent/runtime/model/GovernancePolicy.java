package com.chinacreator.gzcm.services.agent.runtime.model;

import java.util.HashMap;
import java.util.Map;

public class GovernancePolicy {
    private String id;
    private String agentId;
    private GovernanceType type;
    private Map<String, Object> rule = new HashMap<>();
    private boolean enabled = true;

    public GovernancePolicy() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public GovernanceType getType() { return type; }
    public void setType(GovernanceType type) { this.type = type; }
    public Map<String, Object> getRule() { return rule; }
    public void setRule(Map<String, Object> rule) { this.rule = rule; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
