package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentGatewayClient;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 读取访问审计日志。走 {@link AgentGatewayClient} → security-engine
 * {@code /api/v1/audit/logs}（透传租户/鉴权头，由 security-engine 裁决）。
 */
@Component
public class AuditAccessLogTool implements ToolExecutor {

    private final AgentGatewayClient gateway;

    public AuditAccessLogTool(AgentGatewayClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        return gateway.get("/api/v1/audit/logs");
    }

    @Override
    public boolean supports(String toolCode) {
        return "AuditAccessLog".equals(toolCode);
    }
}
