package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentToolEndpointResolver;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * 本体全图检索工具（#142/B4-3e-9 收口 D-B4-46：基址经属主单源，禁内联 :8080）。
 */
@Component
public class SearchOntologyGraphTool implements ToolExecutor {

    static final String PATH_ENGINE_ONTOLOGY_GRAPH_FULL = "/api/v1/engine/ontology/graph/full";

    private final RestTemplate restTemplate;

    private final AgentToolEndpointResolver endpoints;

    public SearchOntologyGraphTool(RestTemplate restTemplate, AgentToolEndpointResolver endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        Optional<String> base = endpoints.ontologyBaseUrl();
        if (base.isEmpty()) {
            return AgentToolResponses.unreachable("ontology");
        }
        return restTemplate.getForObject(base.get() + PATH_ENGINE_ONTOLOGY_GRAPH_FULL, Object.class);
    }

    @Override
    public boolean supports(String toolCode) {
        return "SearchOntologyGraph".equals(toolCode);
    }
}
