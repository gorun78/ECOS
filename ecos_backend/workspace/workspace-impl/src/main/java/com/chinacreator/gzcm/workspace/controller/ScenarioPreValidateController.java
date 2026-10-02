package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.workspace.scenario.CompletenessVO;
import com.chinacreator.gzcm.workspace.scenario.ScenarioCompletenessService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioMindService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioSandboxLayoutService;
import com.chinacreator.gzcm.workspace.scenario.SandboxLayoutVO;
import com.chinacreator.gzcm.workspace.scenario.ScenarioMindVO;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 场景预校验端点（PMO-60 v2.0 P1 T7）— 6 类资源 + 心智 + 沙盘 coverage 三行 PASS/FAIL。
 *
 * <p>验证：新增 COVERATE 检查（前端沙盘画布 ≥ 6 类资源非空 coverage）；
 * ACTIVE 需全 PASS；DRAFT 允许部分 FAIL（红色徽标但可保存）。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/scenarios")
public class ScenarioPreValidateController {

    private static final Logger log = LoggerFactory.getLogger(ScenarioPreValidateController.class);
    private static final Set<String> REQUIRED_CATEGORIES = Set.of(
            "DATASOURCE", "ONTOLOGY_ENTITY", "KNOWLEDGE_ARTICLE",
            "AGENT_PROFILE", "SECURITY_POLICY", "INTERFACE_REF"
    );

    private final ScenarioMindService mindService;
    private final ScenarioSandboxLayoutService sandboxLayoutService;
    private final ScenarioCompletenessService completenessService;
    private final RestTemplate rt;

    public ScenarioPreValidateController(
            ScenarioMindService mindService,
            ScenarioSandboxLayoutService sandboxLayoutService,
            ScenarioCompletenessService completenessService,
            @org.springframework.beans.factory.annotation.Value("${ecos.gateway-base:http://localhost:8080}") String gatewayBase) {
        this.mindService = mindService;
        this.sandboxLayoutService = sandboxLayoutService;
        this.completenessService = completenessService;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.rt = new RestTemplate(factory);
    }

    @Operation(operationId = "preValidateScenario", summary = "preValidateScenario")
    @PostMapping("/{id}/pre-validate")
    public ApiResponse<List<PreValidateCheckVO>> preValidate(@PathVariable("id") String scenarioId) {
        List<PreValidateCheckVO> checks = new ArrayList<>();
        checks.add(checkMind(scenarioId));
        checks.add(checkSandboxCoverage(scenarioId));
        checks.add(checkBindings(scenarioId));
        return ApiResponse.success(checks);
    }

    private PreValidateCheckVO checkMind(String scenarioId) {
        ScenarioMindVO active = mindService.getActiveMind(scenarioId);
        boolean pass = active != null;
        return new PreValidateCheckVO("mind", pass,
                pass ? "active_mind present (id=" + active.getId() + ")"
                     : "no active mind for scenario");
    }

    private PreValidateCheckVO checkSandboxCoverage(String scenarioId) {
        // C150-2（X-27）：coverage 判定改由后端单源 completeness 提供（连边覆盖率），
        // 不再按 type=="resource" 节点计数（那会把非 resource 孤岛漏判）。
        CompletenessVO vo = completenessService.computeFor(scenarioId);
        Double coverage = vo.getCoverage();
        int missingCount = vo.getMissingEdges() == null ? 0 : vo.getMissingEdges().size();
        boolean pass = coverage != null && missingCount == 0;
        if (coverage == null) {
            // 空场景（EMPTY_SCENARIO / NOT_APPLICABLE）：coverage=null，前端显示 "—"
            return new PreValidateCheckVO("sandbox_coverage", false,
                    "empty scenario (verdict=" + vo.getVerdict() + ")");
        }
        return new PreValidateCheckVO("sandbox_coverage", pass,
                String.format("coverage=%.3f verdict=%s missingEdges=%d",
                        coverage, vo.getVerdict(), missingCount));
    }

    /** C150-1（X-27 删桩）：绑定/孤岛/缺失边一律取 completeness 单源，禁硬编码 pass=true。 */
    private PreValidateCheckVO checkBindings(String scenarioId) {
        CompletenessVO vo = completenessService.computeFor(scenarioId);
        List<String> missing = new ArrayList<>();
        if (vo.getMissingEdges() != null) {
            for (CompletenessVO.MissingEdgeVO m : vo.getMissingEdges()) {
                missing.add(m.getType());
            }
        }
        List<String> islands = new ArrayList<>();
        if (vo.getIslands() != null) {
            for (CompletenessVO.Island is : vo.getIslands()) {
                islands.add(is.getBindingId() + "(" + is.getBindingType() + ")");
            }
        }
        boolean pass = missing.isEmpty() && islands.isEmpty();
        String detail = pass
                ? "no missing edges, no isolated bindings"
                : "missingEdges=" + String.join(",", missing)
                        + ", islands=" + String.join(",", islands);
        return new PreValidateCheckVO("bindings_exist", pass, detail);
    }
}
