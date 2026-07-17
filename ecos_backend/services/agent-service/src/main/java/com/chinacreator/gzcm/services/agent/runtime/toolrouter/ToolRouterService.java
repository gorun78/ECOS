package com.chinacreator.gzcm.services.agent.runtime.toolrouter;

import com.chinacreator.gzcm.services.agent.runtime.model.ToolDefinition;
import java.util.List;
import java.util.Map;

public interface ToolRouterService {
    ToolDefinition resolveTool(String actionType, Map<String, Object> params);
    List<ToolDefinition> getAvailableTools(String agentId);
}
