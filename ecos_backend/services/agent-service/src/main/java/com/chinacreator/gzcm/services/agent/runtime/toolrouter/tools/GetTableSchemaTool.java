package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GetTableSchemaTool implements ToolExecutor {

    private final JdbcTemplate jdbc;

    public GetTableSchemaTool(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String resourceId = (String) params.get("resourceId");
        List<Map<String, Object>> fields = jdbc.queryForList(
            "SELECT column_name, data_type, is_nullable, column_default, column_comment " +
            "FROM ecos_metadata_field WHERE resource_id = ? ORDER BY ordinal_position", resourceId);
        return Map.of("resourceId", resourceId, "fields", fields);
    }

    @Override
    public boolean supports(String toolCode) {
        return "GetTableSchema".equals(toolCode);
    }
}
