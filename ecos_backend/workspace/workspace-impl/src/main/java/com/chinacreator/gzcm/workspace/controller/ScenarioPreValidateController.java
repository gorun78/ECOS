package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.workspace.scenario.BindingNode;
import com.chinacreator.gzcm.workspace.scenario.CompletenessDataLoader;
import com.chinacreator.gzcm.workspace.scenario.CompletenessReport;
import com.chinacreator.gzcm.workspace.scenario.LinkEdge;
import com.chinacreator.gzcm.workspace.scenario.ScenarioCompletenessService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioMindService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioSandboxLayoutService;
import com.chinacreator.gzcm.workspace.scenario.SandboxLayoutVO;
import com.chinacreator.gzcm.workspace.scenario.ScenarioMindVO;
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
    private final CompletenessDataLoader loader;
    private final ScenarioCompletenessService completenessService;
    private final RestTemplate rt;

    public ScenarioPreValidateController(
            ScenarioMindService mindService,
            ScenarioSandboxLayoutService sandboxLayoutService,
            CompletenessDataLoader loader,
            ScenarioCompletenessService completenessService,
            @org.springframework.beans.factory.annotation.Value("${ecos.gateway-base:http://localhost:8080}") String gatewayBase) {
        this.mindService = mindService;
        this.sandboxLayoutService = sandboxLayoutService;
        this.loader = loader;
        this.completenessService = completenessService;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.rt = new RestTemplate(factory);
    }

    @PostMapping("/{id}/pre-validate")
    public ApiResponse<List<PreValidateCheckVO>> preValidate(@PathVariable("id") String scenarioId) {
        List<PreValidateCheckVO> checks = new ArrayList<>();
        checks.add(checkMind(scenarioId));
        checks.add(checkSandboxCoverage(scenarioId));
        checks.add(checkResources(scenarioId));
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
        boolean pass = false;
        String detail = "no sandbox layout found";
        SandboxLayoutVO layout = sandboxLayoutService.getLayout(scenarioId);
        if (layout != null && layout.getLayout() != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> layoutMap = (Map<String, Object>) layout.getLayout();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) layoutMap.getOrDefault("nodes", List.of());
            Set<String> presentCategories = new HashSet<>();
            for (Map<String, Object> node : nodes) {
                String type = String.valueOf(node.getOrDefault("type", ""));
                if ("resource".equals(type)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) node.getOrDefault("data", Map.of());
                    String cat = String.valueOf(data.getOrDefault("category", ""));
                    if (!cat.isBlank()) presentCategories.add(cat);
                }
            }
            Set<String> missing = new LinkedHashSet<>(REQUIRED_CATEGORIES);
            missing.removeAll(presentCategories);
            pass = missing.isEmpty();
            detail = pass ? "all 6 categories present" : "missing: " + String.join(", ", missing);
        }
        return new PreValidateCheckVO("sandbox_coverage", pass, detail);
    }

    private PreValidateCheckVO checkResources(String scenarioId) {
        // 卷07 F07-04 / X-27：删除硬编码 pass=true 桩，改由完整度单源（连边覆盖 + 孤岛可达性）判定。
        List<BindingNode> bindings = loader.bindingsOf(scenarioId);
        List<LinkEdge> links = loader.linksOf(scenarioId);
        CompletenessReport report = completenessService.compute(scenarioId, bindings, links);
        boolean hasIsland = !report.getIslands().isEmpty();
        boolean empty = report.getVerdict() == CompletenessReport.Verdict.EMPTY_SCENARIO;
        boolean pass = !bindings.isEmpty() && !hasIsland && !empty;
        String detail;
        if (empty) {
            detail = "空场景：无任何绑定";
        } else if (hasIsland) {
            detail = "存在 " + report.getIslands().size() + " 个孤岛绑定，禁止激活";
        } else {
            detail = "覆盖 " + report.getPresentEdges().size() + "/" + report.getRequiredEdges().size()
                    + "，verdict=" + report.getVerdict();
        }
        return new PreValidateCheckVO("bindings_reachability", pass, detail);
    }
}
