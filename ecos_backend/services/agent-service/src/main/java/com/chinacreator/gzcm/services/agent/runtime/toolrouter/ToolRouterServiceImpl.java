package com.chinacreator.gzcm.services.agent.runtime.toolrouter;

import com.chinacreator.gzcm.services.agent.runtime.model.ToolDefinition;
import com.chinacreator.gzcm.services.agent.runtime.model.ToolType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ToolRouterServiceImpl implements ToolRouterService {
    private static final Logger log = LoggerFactory.getLogger(ToolRouterServiceImpl.class);

    @Override
    public ToolDefinition resolveTool(String actionType, Map<String, Object> params) {
        log.info("Resolving tool for action: {}", actionType);
        ToolDefinition def = new ToolDefinition();
        def.setCode(actionType);
        def.setType(ToolType.API);
        def.setEndpoint("/api/v1/tools/" + actionType);
        return def;
    }

    @Override
    public List<ToolDefinition> getAvailableTools(String agentId) {
        log.info("Getting available tools for agent: {}", agentId);
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
