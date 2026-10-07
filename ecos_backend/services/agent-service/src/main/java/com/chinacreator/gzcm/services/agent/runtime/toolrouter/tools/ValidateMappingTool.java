package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentGatewayClient;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 校验本体映射。走 {@link AgentGatewayClient} → {@code POST /api/v1/ecos/ontology-mappings}。
 */
@Component
public class ValidateMappingTool implements ToolExecutor {

    private final AgentGatewayClient gateway;

    public ValidateMappingTool(AgentGatewayClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        return gateway.post("/api/v1/ecos/ontology-mappings", params);
    }

    @Override
    public boolean supports(String toolCode) {
        return "ValidateMapping".equals(toolCode);
    }
}
