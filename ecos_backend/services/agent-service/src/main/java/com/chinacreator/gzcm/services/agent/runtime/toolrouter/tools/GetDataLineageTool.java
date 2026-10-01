package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.dao.AgentToolDao;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GetDataLineageTool implements ToolExecutor {

    private final AgentToolDao dao;

    public GetDataLineageTool(AgentToolDao dao) {
        this.dao = dao;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String pipelineId = (String) params.get("id");
        Map<String, Object> pipeline = dao.findPipeline(pipelineId);
        List<Map<String, Object>> steps = dao.listPipelineSteps(pipelineId);
        return Map.of("pipeline", pipeline, "steps", steps);
    }

    @Override
    public boolean supports(String toolCode) {
        return "GetDataLineage".equals(toolCode);
    }
}
