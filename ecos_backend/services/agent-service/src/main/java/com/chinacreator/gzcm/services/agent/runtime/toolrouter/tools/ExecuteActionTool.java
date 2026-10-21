package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentToolEndpointResolver;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * 本体动作执行工具（#142/B4-3e-9 收口 D-B4-46：基址经属主单源，禁内联 :8080）。
 */
@Component
public class ExecuteActionTool implements ToolExecutor {

    static final String PATH_ACTION_EXECUTE = "/api/v1/ontology/actions/{id}/execute";

    private final RestTemplate restTemplate;

    private final AgentToolEndpointResolver endpoints;

    public ExecuteActionTool(RestTemplate restTemplate, AgentToolEndpointResolver endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        Optional<String> base = endpoints.ontologyBaseUrl();
        if (base.isEmpty()) {
            return AgentToolResponses.unreachable("ontology");
        }
        String id = (String) params.get("id");
        return restTemplate.postForObject(base.get() + PATH_ACTION_EXECUTE, params, Object.class, id);
    }

    @Override
    public boolean supports(String toolCode) {
        return "ExecuteAction".equals(toolCode);
    }
}
