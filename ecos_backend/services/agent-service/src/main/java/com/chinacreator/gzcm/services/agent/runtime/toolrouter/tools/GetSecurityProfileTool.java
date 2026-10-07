package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentGatewayClient;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 查安全画像。走 {@link AgentGatewayClient} → {@code /api/v1/security/profile}。
 */
@Component
public class GetSecurityProfileTool implements ToolExecutor {

    private final AgentGatewayClient gateway;

    public GetSecurityProfileTool(AgentGatewayClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        return gateway.get("/api/v1/security/profile");
    }

    @Override
    public boolean supports(String toolCode) {
        return "GetSecurityProfile".equals(toolCode);
    }
}
