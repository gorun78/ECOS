package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentToolEndpointResolver;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * 当前用户安全画像读取工具（#142/B4-3e-9 收口 D-B4-46：基址经属主单源，禁内联 :8080）。
 */
@Component
public class GetSecurityProfileTool implements ToolExecutor {

    static final String PATH_SECURITY_PROFILE = "/api/v1/security/profile";

    private final RestTemplate restTemplate;

    private final AgentToolEndpointResolver endpoints;

    public GetSecurityProfileTool(RestTemplate restTemplate, AgentToolEndpointResolver endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        Optional<String> base = endpoints.securityBaseUrl();
        if (base.isEmpty()) {
            return AgentToolResponses.unreachable("security");
        }
        return restTemplate.getForObject(base.get() + PATH_SECURITY_PROFILE, Object.class);
    }

    @Override
    public boolean supports(String toolCode) {
        return "GetSecurityProfile".equals(toolCode);
    }
}
