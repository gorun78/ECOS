package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentGatewayClient;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 列出数据源。走 {@link AgentGatewayClient} → data-engine {@code /api/v1/datasource}
 * （网关织入 RLS/多租户），不再自建 JDBC 直读跨引擎表（铁律 §5 禁跨引擎直读 + §2.5 横切收敛）。
 */
@Component
public class ListDataSourcesTool implements ToolExecutor {

    private final AgentGatewayClient gateway;

    public ListDataSourcesTool(AgentGatewayClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        return gateway.get("/api/v1/datasource");
    }

    @Override
    public boolean supports(String toolCode) {
        return "ListDataSources".equals(toolCode);
    }
}
