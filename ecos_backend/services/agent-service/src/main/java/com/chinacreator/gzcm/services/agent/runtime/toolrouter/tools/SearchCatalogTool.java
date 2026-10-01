package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.dao.AgentToolDao;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SearchCatalogTool implements ToolExecutor {

    private final AgentToolDao dao;

    public SearchCatalogTool(AgentToolDao dao) {
        this.dao = dao;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String keyword = (String) params.getOrDefault("q", "");
        List<Map<String, Object>> items = dao.searchCatalog(keyword);
        return Map.of("keyword", keyword, "items", items, "total", items.size());
    }

    @Override
    public boolean supports(String toolCode) {
        return "SearchCatalog".equals(toolCode);
    }
}
