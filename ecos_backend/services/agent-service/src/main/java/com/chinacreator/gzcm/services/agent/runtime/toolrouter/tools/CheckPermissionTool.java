package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentGatewayClient;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 权限裁决。走 {@link AgentGatewayClient} → security-engine
 * {@code POST /api/v1/policy-engine/evaluate}（透传租户/鉴权）。
 */
@Component
public class CheckPermissionTool implements ToolExecutor {

    private final AgentGatewayClient gateway;

    public CheckPermissionTool(AgentGatewayClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        return gateway.post("/api/v1/policy-engine/evaluate", params);
    }

    @Override
    public boolean supports(String toolCode) {
        return "CheckPermission".equals(toolCode);
    }
}
