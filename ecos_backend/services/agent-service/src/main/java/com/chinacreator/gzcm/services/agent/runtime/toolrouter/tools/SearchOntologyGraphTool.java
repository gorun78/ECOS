package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentGatewayClient;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 全本体图谱检索。走 {@link AgentGatewayClient} → {@code /api/v1/engine/ontology/graph/full}。
 */
@Component
public class SearchOntologyGraphTool implements ToolExecutor {

    private final AgentGatewayClient gateway;

    public SearchOntologyGraphTool(AgentGatewayClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        return gateway.get("/api/v1/engine/ontology/graph/full");
    }

    @Override
    public boolean supports(String toolCode) {
        return "SearchOntologyGraph".equals(toolCode);
    }
}
