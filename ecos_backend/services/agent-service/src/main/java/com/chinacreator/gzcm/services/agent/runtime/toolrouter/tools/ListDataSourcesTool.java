package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.dao.AgentToolDao;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ListDataSourcesTool implements ToolExecutor {

    private final AgentToolDao dao;

    public ListDataSourcesTool(AgentToolDao dao) {
        this.dao = dao;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        List<Map<String, Object>> sources = dao.listDataSources();
        return Map.of("dataSources", sources, "total", sources.size());
    }

    @Override
    public boolean supports(String toolCode) {
        return "ListDataSources".equals(toolCode);
    }
}
