package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentGatewayClient;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 查对象关系。走 {@link AgentGatewayClient} → {@code /api/v1/ecos/entities/{id}/relationships}。
 */
@Component
public class GetObjectRelationshipsTool implements ToolExecutor {

    private final AgentGatewayClient gateway;

    public GetObjectRelationshipsTool(AgentGatewayClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String id = (String) params.get("id");
        return gateway.get("/api/v1/ecos/entities/{id}/relationships", id);
    }

    @Override
    public boolean supports(String toolCode) {
        return "GetObjectRelationships".equals(toolCode);
    }
}
