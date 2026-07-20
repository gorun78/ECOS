package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TriggerPipelineTool implements ToolExecutor {

    private final JdbcTemplate jdbc;

    public TriggerPipelineTool(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String taskId = (String) params.get("id");
        String runId = java.util.UUID.randomUUID().toString();
        jdbc.update(
            "INSERT INTO ecos_pipeline_run (id, task_id, status, created_at) VALUES (?, ?, 'PENDING', NOW())",
            runId, taskId);
        return Map.of("runId", runId, "taskId", taskId, "status", "triggered");
    }

    @Override
    public boolean supports(String toolCode) {
        return "TriggerPipeline".equals(toolCode);
    }
}
