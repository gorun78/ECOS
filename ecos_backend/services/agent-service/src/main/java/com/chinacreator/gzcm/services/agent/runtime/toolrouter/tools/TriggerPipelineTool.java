package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.dao.AgentToolDao;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TriggerPipelineTool implements ToolExecutor {

    private final AgentToolDao dao;

    public TriggerPipelineTool(AgentToolDao dao) {
        this.dao = dao;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String taskId = (String) params.get("id");
        String runId = java.util.UUID.randomUUID().toString();
        dao.insertPipelineRun(runId, taskId);
        return Map.of("runId", runId, "taskId", taskId, "status", "triggered");
    }

    @Override
    public boolean supports(String toolCode) {
        return "TriggerPipeline".equals(toolCode);
    }
}
