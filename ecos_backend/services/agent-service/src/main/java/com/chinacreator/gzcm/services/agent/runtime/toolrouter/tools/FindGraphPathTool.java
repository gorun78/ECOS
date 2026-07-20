package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class FindGraphPathTool implements ToolExecutor {

    private final JdbcTemplate jdbc;

    public FindGraphPathTool(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String from = (String) params.get("from");
        String to = (String) params.get("to");
        List<Map<String, Object>> edges = jdbc.queryForList(
            "SELECT source_id, target_id, relation_type, weight FROM ecos_graph_edge " +
            "WHERE source_id = ? OR target_id = ? OR source_id = ? OR target_id = ? " +
            "ORDER BY weight ASC LIMIT 50", from, from, to, to);
        return Map.of("from", from, "to", to, "edges", edges, "algorithm", "shortest-path");
    }

    @Override
    public boolean supports(String toolCode) {
        return "FindGraphPath".equals(toolCode);
    }
}
