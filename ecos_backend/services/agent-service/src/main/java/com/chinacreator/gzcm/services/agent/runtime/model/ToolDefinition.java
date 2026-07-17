package com.chinacreator.gzcm.services.agent.runtime.model;

import java.util.HashMap;
import java.util.Map;

public class ToolDefinition {
    private String code;
    private String endpoint;
    private ToolType type;
    private Map<String, Object> parameters = new HashMap<>();

    public ToolDefinition() {}

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public ToolType getType() { return type; }
    public void setType(ToolType type) { this.type = type; }
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
}
