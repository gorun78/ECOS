package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GetDataLineageTool implements ToolExecutor {

    private final JdbcTemplate jdbc;

    public GetDataLineageTool(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String pipelineId = (String) params.get("id");
        Map<String, Object> pipeline = jdbc.queryForMap(
            "SELECT id, name, description, status, created_at, updated_at FROM ecos_pipeline WHERE id = ?", pipelineId);
        List<Map<String, Object>> steps = jdbc.queryForList(
            "SELECT step_order, step_name, step_type, config FROM ecos_pipeline_step WHERE pipeline_id = ? ORDER BY step_order", pipelineId);
        return Map.of("pipeline", pipeline, "steps", steps);
    }

    @Override
    public boolean supports(String toolCode) {
        return "GetDataLineage".equals(toolCode);
    }
}
