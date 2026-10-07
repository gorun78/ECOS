package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentGatewayClient;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 列出本体类型。走 {@link AgentGatewayClient} → {@code /api/v1/ecos/ontologies}。
 */
@Component
public class ListObjectTypesTool implements ToolExecutor {

    private final AgentGatewayClient gateway;

    public ListObjectTypesTool(AgentGatewayClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        return gateway.get("/api/v1/ecos/ontologies");
    }

    @Override
    public boolean supports(String toolCode) {
        return "ListObjectTypes".equals(toolCode);
    }
}
