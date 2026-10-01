package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.dao.AgentToolDao;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RAGQueryTool implements ToolExecutor {

    private final AgentToolDao dao;

    public RAGQueryTool(AgentToolDao dao) {
        this.dao = dao;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String queryText = (String) params.get("queryText");
        int topK = params.containsKey("topK") ? ((Number) params.get("topK")).intValue() : 5;
        List<Map<String, Object>> chunks = dao.searchKnowledgeChunks(queryText, topK);
        return Map.of("query", queryText, "results", chunks, "topK", topK);
    }

    @Override
    public boolean supports(String toolCode) {
        return "RAGQuery".equals(toolCode);
    }
}
