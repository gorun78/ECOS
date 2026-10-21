package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentToolEndpointResolver;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * 审计访问日志读取工具（#142/B4-3e-9 收口 D-B4-46：基址经属主单源，禁内联 :8080）。
 */
@Component
public class AuditAccessLogTool implements ToolExecutor {

    static final String PATH_AUDIT_LOGS = "/api/v1/audit/logs";

    private final RestTemplate restTemplate;

    private final AgentToolEndpointResolver endpoints;

    public AuditAccessLogTool(RestTemplate restTemplate, AgentToolEndpointResolver endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        Optional<String> base = endpoints.auditBaseUrl();
        if (base.isEmpty()) {
            return AgentToolResponses.unreachable("audit");
        }
        return restTemplate.getForObject(base.get() + PATH_AUDIT_LOGS, Object.class);
    }

    @Override
    public boolean supports(String toolCode) {
        return "AuditAccessLog".equals(toolCode);
    }
}
