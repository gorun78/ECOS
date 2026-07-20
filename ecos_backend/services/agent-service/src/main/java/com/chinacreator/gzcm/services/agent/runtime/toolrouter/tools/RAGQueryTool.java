package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RAGQueryTool implements ToolExecutor {

    private final JdbcTemplate jdbc;

    public RAGQueryTool(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String queryText = (String) params.get("queryText");
        int topK = params.containsKey("topK") ? ((Number) params.get("topK")).intValue() : 5;
        List<Map<String, Object>> chunks = jdbc.queryForList(
            "SELECT id, content, source, score FROM ecos_knowledge_chunk " +
            "WHERE to_tsvector('chinese', content) @@ plainto_tsquery('chinese', ?) " +
            "ORDER BY score DESC LIMIT ?", queryText, topK);
        return Map.of("query", queryText, "results", chunks, "topK", topK);
    }

    @Override
    public boolean supports(String toolCode) {
        return "RAGQuery".equals(toolCode);
    }
}
