package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentToolEndpointResolver;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * 对象类型（本体）清单读取工具（#142/B4-3e-9 收口 D-B4-46：基址经属主单源，禁内联 :8080）。
 */
@Component
public class ListObjectTypesTool implements ToolExecutor {

    static final String PATH_ECOS_ONTOLOGIES = "/api/v1/ecos/ontologies";

    private final RestTemplate restTemplate;

    private final AgentToolEndpointResolver endpoints;

    public ListObjectTypesTool(RestTemplate restTemplate, AgentToolEndpointResolver endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        Optional<String> base = endpoints.ontologyBaseUrl();
        if (base.isEmpty()) {
            return AgentToolResponses.unreachable("ontology");
        }
        return restTemplate.getForObject(base.get() + PATH_ECOS_ONTOLOGIES, Object.class);
    }

    @Override
    public boolean supports(String toolCode) {
        return "ListObjectTypes".equals(toolCode);
    }
}
