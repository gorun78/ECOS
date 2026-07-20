package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class ValidateMappingTool implements ToolExecutor {

    private final RestTemplate restTemplate;

    public ValidateMappingTool(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        return restTemplate.postForObject("http://localhost:8080/api/v1/ecos/ontology-mappings", params, Object.class);
    }

    @Override
    public boolean supports(String toolCode) {
        return "ValidateMapping".equals(toolCode);
    }
}
