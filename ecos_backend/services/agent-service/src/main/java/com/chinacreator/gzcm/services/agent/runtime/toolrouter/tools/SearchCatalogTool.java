package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SearchCatalogTool implements ToolExecutor {

    private final JdbcTemplate jdbc;

    public SearchCatalogTool(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String keyword = (String) params.getOrDefault("q", "");
        List<Map<String, Object>> items = jdbc.queryForList(
            "SELECT id, name, type, description, owner, created_at FROM ecos_catalog_item " +
            "WHERE name ILIKE ? OR description ILIKE ? ORDER BY name LIMIT 50",
            "%" + keyword + "%", "%" + keyword + "%");
        return Map.of("keyword", keyword, "items", items, "total", items.size());
    }

    @Override
    public boolean supports(String toolCode) {
        return "SearchCatalog".equals(toolCode);
    }
}
