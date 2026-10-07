package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentGatewayClient;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 查对象属性。走 {@link AgentGatewayClient} → {@code /api/v1/ecos/entities/{id}/properties}。
 */
@Component
public class GetObjectPropertiesTool implements ToolExecutor {

    private final AgentGatewayClient gateway;

    public GetObjectPropertiesTool(AgentGatewayClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String id = (String) params.get("id");
        return gateway.get("/api/v1/ecos/entities/{id}/properties", id);
    }

    @Override
    public boolean supports(String toolCode) {
        return "GetObjectProperties".equals(toolCode);
    }
}
