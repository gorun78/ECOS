package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.dao.AgentToolDao;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GetTableSchemaTool implements ToolExecutor {

    private final AgentToolDao dao;

    public GetTableSchemaTool(AgentToolDao dao) {
        this.dao = dao;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String resourceId = (String) params.get("resourceId");
        List<Map<String, Object>> fields = dao.listTableSchemaFields(resourceId);
        return Map.of("resourceId", resourceId, "fields", fields);
    }

    @Override
    public boolean supports(String toolCode) {
        return "GetTableSchema".equals(toolCode);
    }
}
