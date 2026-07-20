package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class ExecuteActionTool implements ToolExecutor {

    private final RestTemplate restTemplate;

    public ExecuteActionTool(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        String id = (String) params.get("id");
        return restTemplate.postForObject("http://localhost:8080/api/v1/ontology/actions/{id}/execute", params, Object.class, id);
    }

    @Override
    public boolean supports(String toolCode) {
        return "ExecuteAction".equals(toolCode);
    }
}
