package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SearchKnowledgeGraphTool implements ToolExecutor {

    private final JdbcTemplate jdbc;

    public SearchKnowledgeGraphTool(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String query = (String) params.get("q");
        List<Map<String, Object>> nodes = jdbc.queryForList(
            "SELECT id, name, type, properties FROM ecos_graph_node " +
            "WHERE name ILIKE ? OR type ILIKE ? ORDER BY name LIMIT 50",
            "%" + query + "%", "%" + query + "%");
        List<Map<String, Object>> edges = jdbc.queryForList(
            "SELECT e.id, e.source_id, e.target_id, e.relation_type, e.weight " +
            "FROM ecos_graph_edge e " +
            "WHERE e.relation_type ILIKE ? LIMIT 50",
            "%" + query + "%");
        return Map.of("query", query, "nodes", nodes, "edges", edges);
    }

    @Override
    public boolean supports(String toolCode) {
        return "SearchKnowledgeGraph".equals(toolCode);
    }
}
