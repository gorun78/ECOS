package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentToolEndpointResolver;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * 本体映射校验工具（#142/B4-3e-9 收口 D-B4-46：基址经属主单源，禁内联 :8080）。
 *
 * <p><b>路径缺陷另案（D-B4-47）</b>：{@link #PATH_ECOS_ONTOLOGY_MAPPINGS} 在全仓<b>没有任何
 * Controller 服务</b>（实测：本体映射真实端点是 {@code /api/v1/ontology/mappings} 单条 CRUD 与
 * {@code /api/v1/ecos/mappings} 全局视图，两者都不是本串）。即本腿收口前恒 404。
 * 本批只把<b>基址</b>收进属主单源，<b>不动路径</b>——把死路径改判成活路径需要
 * 校验语义归属裁决（新增 validate 端点属「API 只增」可为／改指既有 mappings 端点），
 * 由 {@code AgentToolPathReachabilityTest} 以显式幻影清单钉住（清单只准缩短，
 * 新增幻影路径即红），不得静默放过。</p>
 */
@Component
public class ValidateMappingTool implements ToolExecutor {

    static final String PATH_ECOS_ONTOLOGY_MAPPINGS = "/api/v1/ecos/ontology-mappings";

    private final RestTemplate restTemplate;

    private final AgentToolEndpointResolver endpoints;

    public ValidateMappingTool(RestTemplate restTemplate, AgentToolEndpointResolver endpoints) {
        this.restTemplate = restTemplate;
        this.endpoints = endpoints;
    }

    @Override
    public Object execute(String toolCode, Map<String, Object> params) {
        Optional<String> base = endpoints.ontologyBaseUrl();
        if (base.isEmpty()) {
            return AgentToolResponses.unreachable("ontology");
        }
        return restTemplate.postForObject(base.get() + PATH_ECOS_ONTOLOGY_MAPPINGS, params, Object.class);
    }

    @Override
    public boolean supports(String toolCode) {
        return "ValidateMapping".equals(toolCode);
    }
}
