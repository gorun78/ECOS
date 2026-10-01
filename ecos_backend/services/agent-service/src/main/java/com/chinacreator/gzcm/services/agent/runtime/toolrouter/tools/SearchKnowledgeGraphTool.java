package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.dao.AgentToolDao;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SearchKnowledgeGraphTool implements ToolExecutor {

    private final AgentToolDao dao;

    public SearchKnowledgeGraphTool(AgentToolDao dao) {
        this.dao = dao;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String query = (String) params.get("q");
        List<Map<String, Object>> nodes = dao.searchGraphNodes(query);
        List<Map<String, Object>> edges = dao.searchGraphEdges(query);
        return Map.of("query", query, "nodes", nodes, "edges", edges);
    }

    @Override
    public boolean supports(String toolCode) {
        return "SearchKnowledgeGraph".equals(toolCode);
    }
}
