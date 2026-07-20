package com.chinacreator.gzcm.services.agent.runtime.toolrouter;

import com.chinacreator.gzcm.services.agent.runtime.model.ToolDefinition;
import com.chinacreator.gzcm.services.agent.runtime.model.ToolType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ToolRouterServiceImpl implements ToolRouterService {
    private static final Logger log = LoggerFactory.getLogger(ToolRouterServiceImpl.class);

    private final List<ToolExecutor> toolExecutors;
    private final JdbcTemplate jdbcTemplate;

    public ToolRouterServiceImpl(List<ToolExecutor> toolExecutors, JdbcTemplate jdbcTemplate) {
        this.toolExecutors = toolExecutors;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ToolDefinition resolveTool(String actionType, Map<String, Object> params) {
        log.info("Resolving tool for action: {}", actionType);
        ToolExecutor executor = toolExecutors.stream()
            .filter(e -> e.supports(actionType))
            .findFirst()
            .orElse(null);
        if (executor != null) {
            ToolDefinition def = new ToolDefinition();
            def.setCode(actionType);
            def.setType(ToolType.API);
            def.setEndpoint("bean://" + executor.getClass().getSimpleName());
            return def;
        }
        ToolDefinition def = new ToolDefinition();
        def.setCode(actionType);
        def.setType(ToolType.API);
        def.setEndpoint("/api/v1/tools/" + actionType);
        return def;
    }

    @Override
    public List<ToolDefinition> getAvailableTools(String agentId) {
        log.info("Getting available tools for agent: {}", agentId);
        try {
            return jdbcTemplate.query(
                "SELECT code, name, tool_type, endpoint_url, status FROM ecos_agent.ecos_tool_definition WHERE status = 'ACTIVE'",
                (rs, rowNum) -> {
                    ToolDefinition def = new ToolDefinition();
                    def.setCode(rs.getString("code"));
                    def.setName(rs.getString("name"));
                    def.setType(ToolType.valueOf(rs.getString("tool_type")));
                    def.setEndpoint(rs.getString("endpoint_url"));
                    def.setStatus(rs.getString("status"));
                    return def;
                }
            );
        } catch (Exception e) {
            log.warn("Failed to load tools from DB, falling back to enum-based list: {}", e.getMessage());
            List<ToolDefinition> tools = new ArrayList<>();
            for (ToolType tt : ToolType.values()) {
                ToolDefinition def = new ToolDefinition();
                def.setCode(tt.name().toLowerCase());
                def.setType(tt);
                tools.add(def);
            }
            return tools;
        }
    }

    @Override
    public Object executeTool(String toolCode, Map<String, Object> params) {
        ToolExecutor executor = toolExecutors.stream()
            .filter(e -> e.supports(toolCode))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No executor for tool: " + toolCode));
        return executor.execute(toolCode, params);
    }
}
