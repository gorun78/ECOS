package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentGatewayClient;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 执行本体动作。走 {@link AgentGatewayClient} →
 * {@code POST /api/v1/ontology/actions/{id}/execute}（透传租户/鉴权）。
 */
@Component
public class ExecuteActionTool implements ToolExecutor {

    private final AgentGatewayClient gateway;

    public ExecuteActionTool(AgentGatewayClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String id = (String) params.get("id");
        return gateway.post("/api/v1/ontology/actions/{id}/execute", params, id);
    }

    @Override
    public boolean supports(String toolCode) {
        return "ExecuteAction".equals(toolCode);
    }
}
