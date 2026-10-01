package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.dao.AgentToolDao;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RunQualityCheckTool implements ToolExecutor {

    private final AgentToolDao dao;

    public RunQualityCheckTool(AgentToolDao dao) {
        this.dao = dao;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String ruleId = (String) params.get("ruleId");
        String datasourceId = (String) params.get("datasourceId");
        String tableName = (String) params.get("tableName");
        int sampleSize = params.containsKey("sampleSize") ? ((Number) params.get("sampleSize")).intValue() : 1000;
        String checkId = java.util.UUID.randomUUID().toString();
        dao.insertQualityCheck(checkId, ruleId, datasourceId, tableName, sampleSize);
        Map<String, Object> result = new HashMap<>();
        result.put("checkId", checkId);
        result.put("ruleId", ruleId);
        result.put("datasourceId", datasourceId);
        result.put("tableName", tableName);
        result.put("sampleSize", sampleSize);
        result.put("status", "triggered");
        return result;
    }

    @Override
    public boolean supports(String toolCode) {
        return "RunQualityCheck".equals(toolCode);
    }
}
