package com.chinacreator.gzcm.runtime.llm.agent.spi.model;

import java.util.HashMap;
import java.util.Map;

/**
 * SPI 契约：Agent 目标（接缝义务 1 · planner 能力入参）。
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0
 */
public class AgentGoal {

    private String id;
    private String description;
    private int priority;
    private Map<String, Object> constraints = new HashMap<>();

    public AgentGoal() {
    }

    public AgentGoal(String id, String description, int priority) {
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
    public void setConstraints(Map<String, Object> constraints) {
        this.constraints = constraints == null ? new HashMap<>() : constraints;
    }
}
