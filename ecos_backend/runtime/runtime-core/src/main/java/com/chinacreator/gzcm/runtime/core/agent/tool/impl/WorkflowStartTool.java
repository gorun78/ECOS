package com.chinacreator.gzcm.runtime.core.agent.tool.impl;

import com.chinacreator.gzcm.runtime.core.agent.tool.Tool;
import com.chinacreator.gzcm.runtime.core.agent.tool.ToolResult;
import java.util.*;

public class WorkflowStartTool implements Tool {

    @Override public String getName() { return "workflow_start"; }

    @Override public String getDescription() { return "Start a data pipeline workflow"; }

    @Override public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("query", Map.of("type", "string", "description", "Search query or input"));
        schema.put("properties", props);
        schema.put("required", List.of("query"));
        return schema;
    }

    @Override public ToolResult execute(Map<String, Object> arguments) {
        String query = (String) arguments.getOrDefault("query", "");
        String detail = "Workflow wf-001 started. Status: RUNNING. ETA: 15s (query: " + query.substring(0, Math.min(30, query.length())) + ")";
        return ToolResult.success(null, "workflow_start", detail, null, 200L);
    }

    @Override public boolean isAutoApprove() { return true; }
    @Override public String getSubsystem() { return "runtime"; }
}
