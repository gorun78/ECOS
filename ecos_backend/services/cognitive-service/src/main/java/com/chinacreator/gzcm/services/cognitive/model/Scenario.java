package com.chinacreator.gzcm.services.cognitive.model;

import java.util.Map;

public class Scenario {
    private String id;
    private String name;
    private ScenarioType type;
    private Map<String, Object> assumptions;
    private String description;

    public Scenario() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ScenarioType getType() { return type; }
    public void setType(ScenarioType type) { this.type = type; }
    public Map<String, Object> getAssumptions() { return assumptions; }
    public void setAssumptions(Map<String, Object> assumptions) { this.assumptions = assumptions; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
