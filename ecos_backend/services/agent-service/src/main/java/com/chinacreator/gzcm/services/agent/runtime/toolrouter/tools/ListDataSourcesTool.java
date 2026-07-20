package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ListDataSourcesTool implements ToolExecutor {

    private final JdbcTemplate jdbc;

    public ListDataSourcesTool(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        List<Map<String, Object>> sources = jdbc.queryForList(
            "SELECT id, name, type, host, port, database_name, status, created_at FROM ecos_datasource ORDER BY name");
        return Map.of("dataSources", sources, "total", sources.size());
    }

    @Override
    public boolean supports(String toolCode) {
        return "ListDataSources".equals(toolCode);
    }
}
