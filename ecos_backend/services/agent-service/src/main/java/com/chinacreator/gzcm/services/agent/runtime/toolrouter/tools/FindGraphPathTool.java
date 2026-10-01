package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.dao.AgentToolDao;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class FindGraphPathTool implements ToolExecutor {

    private final AgentToolDao dao;

    public FindGraphPathTool(AgentToolDao dao) {
        this.dao = dao;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String from = (String) params.get("from");
        String to = (String) params.get("to");
        List<Map<String, Object>> edges = dao.listGraphEdgesForPath(from, to);
        return Map.of("from", from, "to", to, "edges", edges, "algorithm", "shortest-path");
    }

    @Override
    public boolean supports(String toolCode) {
        return "FindGraphPath".equals(toolCode);
    }
}
