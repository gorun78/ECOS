package com.chinacreator.gzcm.services.agent.runtime.model;

import java.util.HashMap;
import java.util.Map;

public class Goal {
    private String id;
    private String description;
    private int priority;
    private Map<String, Object> constraints = new HashMap<>();

    public Goal() {}

    public Goal(String id, String description, int priority) {
        this.id = id;
        this.description = description;
        this.priority = priority;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public Map<String, Object> getConstraints() { return constraints; }
    public void setConstraints(Map<String, Object> constraints) { this.constraints = constraints; }
}
