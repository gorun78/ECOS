package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.dao.AgentToolDao;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class QueryPhysicalTableTool implements ToolExecutor {

    private final AgentToolDao dao;

    public QueryPhysicalTableTool(AgentToolDao dao) {
        this.dao = dao;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String datasourceId = (String) params.get("datasourceId");
        String sql = (String) params.get("sql");
        int maxRows = params.containsKey("maxRows") ? ((Number) params.get("maxRows")).intValue() : 1000;
        dao.insertQueryLog(java.util.UUID.randomUUID().toString(), datasourceId, sql, maxRows);
        List<Map<String, Object>> rows = dao.queryPhysicalTable(sql, maxRows);
        return Map.of("datasourceId", datasourceId, "rows", rows, "rowCount", rows.size());
    }

    @Override
    public boolean supports(String toolCode) {
        return "QueryPhysicalTable".equals(toolCode);
    }
}
