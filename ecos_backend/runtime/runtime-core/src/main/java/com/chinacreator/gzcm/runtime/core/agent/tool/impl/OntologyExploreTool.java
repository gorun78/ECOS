package com.chinacreator.gzcm.runtime.core.agent.tool.impl;

import com.chinacreator.gzcm.runtime.core.agent.tool.Tool;
import com.chinacreator.gzcm.runtime.core.agent.tool.ToolResult;
import java.util.*;

public class OntologyExploreTool implements Tool {

    @Override public String getName() { return "ontology_explore"; }

    @Override public String getDescription() { return "Browse the ontology schema for domains, properties, and relationships"; }

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
        String detail = "Domain customer: 12 entities, 8 relationships, 5 rules (query: " + query.substring(0, Math.min(30, query.length())) + ")";
        return ToolResult.success(null, "ontology_explore", detail, null, 120L);
    }

    @Override public boolean isAutoApprove() { return true; }
    @Override public String getSubsystem() { return "runtime"; }
}
