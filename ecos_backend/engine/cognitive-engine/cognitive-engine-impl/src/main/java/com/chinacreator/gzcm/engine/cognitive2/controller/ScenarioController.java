package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveContractSchema;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.ScenarioSimulatorService;
import com.chinacreator.gzcm.engine.cognitive2.model.*;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveConfigQueryService;
import com.chinacreator.gzcm.engine.cognitive2.service.forecast.ForecastDiagnosisService;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.engine.cognitive2.dto.ScenarioCompareItemVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.ScenarioCompareRequest;
import com.chinacreator.gzcm.engine.cognitive2.dto.ScenarioTemplateVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 情景推演（{@code /api/v1/cognitive/scenario}）。
 *
 * <p>F05-16 / X-44：{@code GET /list} 原以 5 条内置模板冒充真实场景目录。这些模板是演示素材而非
 * 业务数据 ⇒ 标 {@code demo=true} 并只在 {@code ecos.cognitive.demo=true}（dev profile）下可达，
 * 默认（未配置）/生产返回真实 HTTP 404（端点不可达），禁 200 + 内置数据。</p>
 *
 * <p>{@code /simulate} 与 {@code /compare} 走 {@link ScenarioSimulatorService} 真实推演，不受开关影响。</p>
 *
 * <p>DI-2 #147EK：模板目录 5 键与对比条目 4 键改 record，对比请求体改
 * {@code ScenarioCompareRequest}；{@code /simulate} 请求体（mode/variables/overrides 自由位）
 * 保留泛型 Map。</p>
 */
@RestController("cognitiveScenarioController")
@RequestMapping("/api/v1/cognitive/scenario")
public class ScenarioController {

    private static final Logger log = LoggerFactory.getLogger(ScenarioController.class);

    private final ScenarioSimulatorService simulatorService;
    /** F05-10 确定性重算通道（mode=DETERMINISTIC_DIFF）；金额只来自预测运行服务。 */
    private final ForecastDiagnosisService forecastDiagnosisService;
    /** demo 开关等认知配置的唯一入口（§E.5 配置单源，缺省裁决不在 Controller 里散落）。 */
    private final CognitiveConfigQueryService configQueryService;

    public ScenarioController(ScenarioSimulatorService simulatorService,
                              ForecastDiagnosisService forecastDiagnosisService,
                              CognitiveConfigQueryService configQueryService) {
        this.simulatorService = simulatorService;
        this.forecastDiagnosisService = forecastDiagnosisService;
        this.configQueryService = configQueryService;
    }

    /**
     * 情景模拟（F05-10）。
     *
     * <p>{@code mode} 为可选新增字段（D.3）：{@code DETERMINISTIC_DIFF} → 预测运行服务确定性重算 +
     * 归因平衡自检（不平 503 {@code ECOS-COG-502}，禁返回不平数据）；缺省 → 既有 RAG+Agent 探索通道，
     * 出口强制 {@code exploratory=true} + {@code caliber=NON_ACCOUNTING}，响应内无金额字段。</p>
     */
    @PostMapping("/simulate")
    public ApiResponse<?> simulate(@RequestBody Map<String, Object> request) {
        String mode = (String) request.get("mode");
        if (CognitiveContractSchema.MODE_DETERMINISTIC_DIFF.equals(mode)) {
            return ApiResponse.success(forecastDiagnosisService.simulateDeterministic(
                    (String) request.get("baselineRunId"),
                    parseOverrides(request.get("overrides"))));
        }
        if (mode != null && !mode.isBlank()) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "未知 simulate.mode: " + mode);
        }

        String name = (String) request.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) request.getOrDefault("variables", new HashMap<>());
        String domain = (String) request.getOrDefault("domain", "default");

        log.info("情景推演: name={}, domain={}", name, domain);

        Scenario scenario = new Scenario();
        scenario.setId(UUID.randomUUID().toString());
        scenario.setName(name);
        scenario.setType(ScenarioType.CUSTOM);
        scenario.setAssumptions(new HashMap<>(variables));
        scenario.setDescription("情景推演: " + name + " in " + domain);

        SimulationResult result = simulatorService.runSimulation(scenario);
        // F05-10②：探索通道非核算口径（UI 据此打"非核算口径"徽标）
        result.setCaliber(CognitiveContractSchema.CALIBER_NON_ACCOUNTING);
        return ApiResponse.success(result);
    }

    /** D.3 overrides 六字段结构（projectId/departmentId/periods[]/target/stage/delta）。 */
    @SuppressWarnings("unchecked")
    private static List<CognitiveContractSchema.SimulateOverrideVO> parseOverrides(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List)) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM, "overrides 必须为数组");
        }
        List<CognitiveContractSchema.SimulateOverrideVO> out = new ArrayList<>();
        for (Object item : (List<Object>) raw) {
            if (!(item instanceof Map)) {
                throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                        CognitiveErrorCode.MSG_INVALID_PARAM, "overrides[] 元素必须为对象");
            }
            Map<String, Object> o = (Map<String, Object>) item;
            CognitiveContractSchema.SimulateOverrideVO vo = new CognitiveContractSchema.SimulateOverrideVO();
            vo.setProjectId((String) o.get("projectId"));
            vo.setDepartmentId((String) o.get("departmentId"));
            Object periods = o.get("periods");
            vo.setPeriods(periods instanceof List ? ((List<Object>) periods).stream()
                    .map(String::valueOf).toList() : List.of());
            vo.setTarget((String) o.get("target"));
            vo.setStage((String) o.get("stage"));
            Object delta = o.get("delta");
            vo.setDelta(delta instanceof Number ? new java.math.BigDecimal(String.valueOf(delta)) : null);
            out.add(vo);
        }
        return out;
    }

    /**
     * GET /api/v1/cognitive/scenario/list — 内置演示模板目录（demo 内容）。
     *
     * <p>{@code ecos.cognitive.demo=false}（默认）→ 真实 HTTP 404；开启时每条模板带
     * {@code demo=true}，前端不得当作真实场景台账渲染。</p>
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<ScenarioTemplateVO>>> listTemplates() {
        if (!configQueryService.demoEnabled()) {
            return ResponseEntity.notFound().build();
        }
        List<ScenarioTemplateVO> templates = new ArrayList<>();

        addTemplate(templates, "原材料涨价10%", "供应链",
            Map.of("raw_material_cost", "+10%"), "模拟原材料成本上涨对利润的影响");
        addTemplate(templates, "汇率波动5%", "财务",
            Map.of("exchange_rate", "+5%"), "模拟汇率变动对进出口业务的影响");
        addTemplate(templates, "客户流失20%", "销售",
            Map.of("customer_churn", "+20%"), "模拟大客户流失对营收的影响");
        addTemplate(templates, "IT预算削减15%", "IT",
            Map.of("it_budget", "-15%"), "模拟IT预算削减对系统运维的影响");
        addTemplate(templates, "新法规合规成本+30%", "合规",
            Map.of("compliance_cost", "+30%"), "模拟新法规实施对运营成本的影响");

        return ResponseEntity.ok(ApiResponse.success(templates));
    }

    private void addTemplate(List<ScenarioTemplateVO> list, String name,
            String domain, Map<String, String> vars, String desc) {
        list.add(new ScenarioTemplateVO(name, domain, vars, desc, true));
    }

    @PostMapping("/compare")
    public ApiResponse<List<ScenarioCompareItemVO>> compare(@RequestBody ScenarioCompareRequest request) {
        List<ScenarioCompareRequest.ItemSpec> scenarios = request.getScenarios();

        if (scenarios == null || scenarios.isEmpty()) {
            return ApiResponse.success(Collections.emptyList());
        }

        List<ScenarioCompareItemVO> results = new ArrayList<>();
        for (ScenarioCompareRequest.ItemSpec s : scenarios) {
            String name = s.getName();

            Scenario scenario = new Scenario();
            scenario.setId(UUID.randomUUID().toString());
            scenario.setName(name);
            scenario.setType(ScenarioType.CUSTOM);
            scenario.setAssumptions(new HashMap<>(s.getVariables() != null ? s.getVariables() : Map.of()));

            SimulationResult result = simulatorService.runSimulation(scenario);
            results.add(new ScenarioCompareItemVO(name, result.getConfidence(),
                    result.getSummary(), result.getPredictions()));
        }

        return ApiResponse.success(results);
    }
}
