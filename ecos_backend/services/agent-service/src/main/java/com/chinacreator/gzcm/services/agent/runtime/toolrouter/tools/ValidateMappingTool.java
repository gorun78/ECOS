package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentToolEndpointResolver;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.ToolExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * 本体映射校验工具（#142/B4-3e-9 收口 D-B4-46 基址单源；#142/B4-3e-10 收口 D-B4-47 路径归属）。
 *
 * <p><b>D-B4-47 的裁决与依据</b>：上一批实测本腿原路径
 * {@code /api/v1/ecos/ontology-mappings} 在全仓<b>没有任何 Controller 服务</b>（收口前恒 404），
 * 而本体域早已存在语义完全对口的真实端点
 * {@code OntologyMappingController#validateMappings}＝{@code POST /api/v1/ontology/mappings/validate}
 * （入参 {@code OntologyMappingValidateDTO}：mappingId／内联 datasetId+fieldMappings／entityCode／全量，
 * 应答 {@code data.valid}＋{@code data.issues[]}＋{@code data.rejectCode=INVALID_MAPPING}）。
 * 本工具把 {@code params} 整包作 JSON body 送出，与该 DTO 形态一致，故裁决为<b>改指既有端点</b>
 * 而非新增 validate 端点——不扩 API 面、不动既有签名（开发约束 10「API 只增不改」下取更小的那一步），
 * 且新路径的属主仍是 {@code buszhi}，基址单源腿无需改形。</p>
 *
 * <p><b>为什么路径与基址要分两批改</b>：基址单源是"打到哪台机器"，路径可达是"这台机器上有没有这个端点"；
 * 两件事各自的裁决依据不同（前者看 route-manifest 属主，后者要逐条核对 Controller），混在一批里
 * 会让"看着更合规但依旧恒 404"的假绿面失去被单独取证的机会。可达性由
 * {@code AgentToolPathReachabilityTest} 以幻影清单钉住（清单现为空，新增幻影即红）。</p>
 */
@Component
public class ValidateMappingTool implements ToolExecutor {

    static final String PATH_ONTOLOGY_MAPPINGS_VALIDATE = "/api/v1/ontology/mappings/validate";

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
        return restTemplate.postForObject(base.get() + PATH_ONTOLOGY_MAPPINGS_VALIDATE, params, Object.class);
    }

    @Override
    public boolean supports(String toolCode) {
        return "ValidateMapping".equals(toolCode);
    }
}
