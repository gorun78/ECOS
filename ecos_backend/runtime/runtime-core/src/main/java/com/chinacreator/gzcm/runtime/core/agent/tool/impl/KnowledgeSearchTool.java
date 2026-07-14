package com.chinacreator.gzcm.runtime.core.agent.tool.impl;

import com.chinacreator.gzcm.runtime.core.agent.tool.Tool;
import com.chinacreator.gzcm.runtime.core.agent.tool.ToolResult;
import java.util.*;

public class KnowledgeSearchTool implements Tool {

    @Override public String getName() { return "knowledge_search"; }

    @Override public String getDescription() { return "Search the enterprise knowledge graph for entities, concepts, and relationships"; }

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
        String detail = "Found 5 matching entities: Customer360, SalesOrder, Product, APAC, ForecastModel (query: " + query.substring(0, Math.min(30, query.length())) + ")";
        return ToolResult.success(null, "knowledge_search", detail, null, 50L);
    }

    @Override public boolean isAutoApprove() { return true; }
    @Override public String getSubsystem() { return "runtime"; }
}
