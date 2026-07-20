package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class GetObjectRelationshipsTool implements ToolExecutor {

    private final RestTemplate restTemplate;

    public GetObjectRelationshipsTool(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String id = (String) params.get("id");
        return restTemplate.getForObject("http://localhost:8080/api/v1/ecos/entities/{id}/relationships", Object.class, id);
    }

    @Override
    public boolean supports(String toolCode) {
        return "GetObjectRelationships".equals(toolCode);
    }
}
