package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class QueryPhysicalTableTool implements ToolExecutor {

    private final JdbcTemplate jdbc;

    public QueryPhysicalTableTool(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String datasourceId = (String) params.get("datasourceId");
        String sql = (String) params.get("sql");
        int maxRows = params.containsKey("maxRows") ? ((Number) params.get("maxRows")).intValue() : 1000;
        jdbc.update(
            "INSERT INTO ecos_query_log (id, datasource_id, sql_text, max_rows, status, created_at) " +
            "VALUES (?, ?, ?, ?, 'SUBMITTED', NOW())",
            java.util.UUID.randomUUID().toString(), datasourceId, sql, maxRows);
        List<Map<String, Object>> rows = jdbc.queryForList(sql + " LIMIT " + maxRows);
        return Map.of("datasourceId", datasourceId, "rows", rows, "rowCount", rows.size());
    }

    @Override
    public boolean supports(String toolCode) {
        return "QueryPhysicalTable".equals(toolCode);
    }
}
