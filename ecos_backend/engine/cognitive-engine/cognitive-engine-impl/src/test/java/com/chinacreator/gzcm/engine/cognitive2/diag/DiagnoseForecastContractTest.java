package com.chinacreator.gzcm.engine.cognitive2.diag;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveContractSchema;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.CausalReasonerService;
import com.chinacreator.gzcm.engine.cognitive2.ScenarioSimulatorService;
import com.chinacreator.gzcm.engine.cognitive2.controller.DiagnosisController;
import com.chinacreator.gzcm.engine.cognitive2.controller.ScenarioController;
import com.chinacreator.gzcm.engine.cognitive2.controller.scenario.CognitiveErrorAdvice;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainNode;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainResult;
import com.chinacreator.gzcm.engine.cognitive2.model.SimulationResult;
import com.chinacreator.gzcm.engine.cognitive2.observability.CognitiveObservability;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveObsFixtures;
import com.chinacreator.gzcm.engine.cognitive2.service.ModelRegistryService;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.ContributionCalculator;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.ScenarioBalanceChecker;
import com.chinacreator.gzcm.engine.cognitive2.service.forecast.ForecastDiagnosisService;
import com.chinacreator.gzcm.engine.cognitive2.service.forecast.ForecastRunSnapshotReader;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.runtime.core.monitor.metrics.IMetricRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 详细设计-05 D.3 契约冻结验收（F05-09 响应增块 / F05-10 确定性重算 / DG-8 / DG-9）。
 *
 * <p>断言面：结构化 {@code chains[]}（path 节点 + 确定性 contribution + 证据/假设分离）、
 * 09 册接缝缺失即显式 stub（禁伪造数值）、LLM 不可用只降级解释、p 值越界 500 禁 clamp、
 * 归因不平/泄漏 503 禁返回不平数据，以及"只增不改"的 legacy 响应逐键不变。</p>
 */
class DiagnoseForecastContractTest {

    private static final List<String> LEGACY_KEYS = List.of(
            "diagnosisId", "degraded", "rootCause", "causalChain", "suggestions", "affectedMetrics");

    // ── 纯函数：D.3 结构化链 ─────────────────────────────────────────

    @Test
    @DisplayName("D.3：结构化 chains —— 有证据边入 path 并复算 contribution，无证据边入 unverifiedHypotheses")
    void chainsCarryStructuredPathAndSeparateUnverified() {
        List<ContributionCalculator.SnapshotEdge> edges = List.of(
                evidenced("c1", node("进度延期", "EVENT"), node("验收率下降", "METRIC:M_REALIZATION_RATE"),
                        "100", "82", List.of("ev-101")),
                evidenced("c1", node("验收率下降", "METRIC:M_REALIZATION_RATE"), node("利润下降", "METRIC:M_FC_PROFIT"),
                        "-40", "-58", List.of("kg-edge-233")),
                unevidenced("c1", node("外包比例上升", "EVENT"), node("延期放大", "EVENT"), "0", "3"));

        List<CognitiveContractSchema.DiagnoseChainVO> chains = ContributionCalculator.chains(edges);

        assertEquals(1, chains.size());
        CognitiveContractSchema.DiagnoseChainVO c1 = chains.get(0);
        assertEquals("c1", c1.getChainId());
        assertEquals(3, c1.getPath().size(), "同链有证据边按序拼接、重复节点去重");
        assertEquals("进度延期", c1.getPath().get(0).getNode());
        assertEquals("METRIC:M_FC_PROFIT", c1.getPath().get(2).getType());
        assertEquals(0, new BigDecimal("-36.0000").compareTo(c1.getContribution()),
                "contribution = Σ(scenario−baseline) 确定性复算（-18 + -18 中的两条有证据边）");
        assertEquals(List.of("ev-101", "kg-edge-233"), c1.getEvidenceRefs());
        assertTrue(c1.isRecomputable());
        assertEquals(1, c1.getUnverifiedHypotheses().size());
        assertEquals("外包比例上升→延期放大", c1.getUnverifiedHypotheses().get(0).getStatement());
        assertEquals(ContributionCalculator.UNVERIFIED_REASON, c1.getUnverifiedHypotheses().get(0).getReason());
    }

    @Test
    @DisplayName("D.3：同批边两次装配必得同值（确定性复算基准）")
    void chainsAreDeterministic() {
        List<ContributionCalculator.SnapshotEdge> edges = List.of(
                evidenced("c1", node("a", "EVENT"), node("b", "METRIC:M_X"), "10", "13.5", List.of("e1")),
                evidenced("c2", node("b", "METRIC:M_X"), node("c", "METRIC:M_Y"), "7", "4.25", List.of("e2")));
        assertEquals(ContributionCalculator.chains(edges), ContributionCalculator.chains(edges));
        assertEquals(2, ContributionCalculator.chains(edges).size(), "chainId 分组各出一条链");
    }

    @Test
    @DisplayName("D.3：全链无证据 → contribution=null + recomputable=false（禁把无证据数值当可复算结果）")
    void chainWithoutEvidenceHasNullContributionAndNotRecomputable() {
        List<ContributionCalculator.SnapshotEdge> edges = List.of(
                unevidenced("c9", node("x", "EVENT"), node("y", "EVENT"), "1", "5"));
        CognitiveContractSchema.DiagnoseChainVO vo = ContributionCalculator.chains(edges).get(0);
        assertNull(vo.getContribution());
        assertFalse(vo.isRecomputable());
        assertTrue(vo.getEvidenceRefs().isEmpty());
        assertEquals(1, vo.getUnverifiedHypotheses().size());
    }

    // ── DG-9：09 册接缝缺失 fail-loud ────────────────────────────────

    @Test
    @DisplayName("DG-9：无 ForecastRunSnapshotReader bean → ECOS-COG-503 fail-loud，禁伪造快照")
    void missingForecastSeamFailsLoudAsExplicitStub() {
        ForecastDiagnosisService service = new ForecastDiagnosisService(
                emptyProvider(), emptyProvider(), obs());
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.diagnose(ctx("fr-001")));
        assertEquals(CognitiveErrorCode.E503_NOT_IMPLEMENTED, e.errorCode);
        assertEquals(501, e.httpStatus);
        assertTrue(e.missing.contains("FC-02"), "missing 必须定位到 09 册端点: " + e.missing);
        assertFalse(service.snapshotSeamReady());
    }

    @Test
    @DisplayName("D.3 统一错误响应体：guard 异常渲染真实 501 + code=501 + data.stub=true + data.missing")
    void adviceRendersStubBodyWithMissingLocator() {
        CognitiveErrorAdvice advice = new CognitiveErrorAdvice();
        ResponseEntity<ApiResponse<Map<String, Object>>> resp = advice.onGuard(
                new CognitiveGuardException(CognitiveErrorCode.E503_NOT_IMPLEMENTED,
                        CognitiveErrorCode.MSG_NOT_IMPLEMENTED, "09册FC-02 只读快照端点"));

        assertEquals(501, resp.getStatusCode().value());
        ApiResponse<Map<String, Object>> body = resp.getBody();
        assertNotNull(body);
        assertEquals(501, body.getCode());
        assertEquals(CognitiveErrorCode.E503_NOT_IMPLEMENTED, body.getErrorCode());
        assertEquals(CognitiveErrorCode.MSG_NOT_IMPLEMENTED, body.getMessage());
        assertNotNull(body.getData(), "D.3 要求 data 回显（此前构建未挂载）");
        assertEquals(Boolean.TRUE, body.getData().get("stub"));
        assertEquals("CognitiveAvailabilityGuard", body.getData().get("guard"));
        assertEquals("09册FC-02 只读快照端点", body.getData().get("missing"));
    }

    // ── DG-8：LLM 不可用只降级解释 ───────────────────────────────────

    @Test
    @DisplayName("DG-8：llm-gateway 缺失 → explanationUnavailable=true，数值链完整返回（禁伪造解释）")
    void llmUnavailableDegradesExplanationOnly() {
        ForecastRunSnapshotReader seam = reader(List.of(cell("p1", 202707, "100", "82")),
                bd("100"), bd("82"),
                List.of(evidencedSource("c1",
                        List.of(node("进度延期", "EVENT"), node("利润下降", "METRIC:M_FC_PROFIT")),
                        "100", "82", List.of("ev-101"))));
        ForecastDiagnosisService service = new ForecastDiagnosisService(provider(seam), emptyProvider(), obs());

        CognitiveContractSchema.DiagnoseForecastData data = service.diagnose(ctx("fr-001"));

        assertNull(data.getExplanation(), "LLM 不可用时禁内置文案冒充解释");
        assertTrue(data.isExplanationUnavailable());
        assertEquals(1, data.getChains().size());
        assertEquals(0, new BigDecimal("-18.0000").compareTo(data.getChains().get(0).getContribution()),
                "数值不因 LLM 缺失而丢失");
    }

    @Test
    @DisplayName("D.2 规则4：statSupport.pValue 越界 → 500 数据缺陷（禁 clamp 静默）")
    void pValueOutOfRangeFailsLoudWithoutClamping() {
        CognitiveContractSchema.StatSupportVO bad = new CognitiveContractSchema.StatSupportVO();
        bad.setMethod("effect-estimate");
        bad.setPValue(1.4);
        ForecastRunSnapshotReader seam = reader(List.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(new ForecastRunSnapshotReader.ContributionEdgeSource("c1",
                        List.of(node("a", "EVENT")), bd("1"), bd("2"), List.of("e"), bad)));
        ForecastDiagnosisService service = new ForecastDiagnosisService(provider(seam), emptyProvider(), obs());

        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.diagnose(ctx("fr-001")));
        assertEquals(CognitiveErrorCode.E504_INTERNAL, e.errorCode);
        assertEquals(500, e.httpStatus);
    }

    // ── F05-10：确定性重算编排 ───────────────────────────────────────

    @Test
    @DisplayName("F05-10③：sum(contribution) 与 Δprofit 超容差 → 503 ECOS-COG-502，禁返回不平数据")
    void deterministicSimulateRejectsUnbalancedAttribution() {
        ForecastRunSnapshotReader unbalanced = reader(List.of(cell("p1", 202707, "100", "120")),
                new BigDecimal("100"), new BigDecimal("120.05"), List.of());
        ForecastDiagnosisService service = new ForecastDiagnosisService(provider(unbalanced), emptyProvider(), obs());

        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.simulateDeterministic("fr-001", List.of(override("p1"))));
        assertEquals(CognitiveErrorCode.E502_BALANCE_NOT_OK, e.errorCode);
        assertEquals(503, e.httpStatus);
        assertEquals(CognitiveErrorCode.MSG_NOT_BALANCED, e.getMessage());
    }

    @Test
    @DisplayName("F05-10①：命中范围外 diff≠0（归因泄漏）→ 503 ECOS-COG-502")
    void deterministicSimulateRejectsAttributionLeakage() {
        ForecastRunSnapshotReader leaky = reader(List.of(
                cell("p1", 202707, "100", "120"),
                cell("p2", 202707, "50", "55")), new BigDecimal("150"), new BigDecimal("175"), List.of());
        ForecastDiagnosisService service = new ForecastDiagnosisService(provider(leaky), emptyProvider(), obs());

        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.simulateDeterministic("fr-001", List.of(override("p1"))));
        assertEquals(CognitiveErrorCode.E502_BALANCE_NOT_OK, e.errorCode);
        assertTrue(e.missing.contains("归因泄漏"), "missing 定位归因泄漏: " + e.missing);
    }

    @Test
    @DisplayName("F05-10：平衡通过 → 契约键 mode/scenarioRunId/balanceOk/deltaProfit/contributionsSum/tolerance/chains")
    void deterministicSimulateReturnsBalancedContract() {
        ForecastRunSnapshotReader ok = reader(List.of(cell("p1", 202707, "100", "120")),
                new BigDecimal("100"), new BigDecimal("120"),
                List.of(evidencedSource("c1", List.of(node("验收率下降", "METRIC:M_REALIZATION_RATE"),
                                node("利润下降", "METRIC:M_FC_PROFIT")), "100", "120", List.of("ev-1"))));
        ForecastDiagnosisService service = new ForecastDiagnosisService(provider(ok), emptyProvider(), obs());

        CognitiveContractSchema.SimulateResponseVO vo =
                service.simulateDeterministic("fr-001", List.of(override("p1")));

        assertEquals(CognitiveContractSchema.MODE_DETERMINISTIC_DIFF, vo.getMode());
        assertEquals("fr-scenario-1", vo.getScenarioRunId());
        assertTrue(vo.isBalanceOk());
        assertEquals(0, new BigDecimal("20.0000").compareTo(vo.getDeltaProfit()));
        assertEquals(0, vo.getContributionsSum().compareTo(vo.getDeltaProfit()),
                "Σcontribution == Δprofit（容差内）");
        assertEquals(CognitiveContractSchema.BALANCE_TOLERANCE, vo.getTolerance());
        assertEquals(1, vo.getChains().size());
        assertTrue(vo.getChains().get(0).isRecomputable());
    }

    @Test
    @DisplayName("DG-9：simulate DETERMINISTIC_DIFF 无 09 册接缝 → fail-loud 定位 FC-03")
    void deterministicSimulateWithoutSeamFailsLoud() {
        ForecastDiagnosisService service = new ForecastDiagnosisService(emptyProvider(), emptyProvider(), obs());
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.simulateDeterministic("fr-001", List.of(override("p1"))));
        assertEquals(CognitiveErrorCode.E503_NOT_IMPLEMENTED, e.errorCode);
        assertTrue(e.missing.contains("FC-03"));
    }

    // ── Controller 面：只增不改 ──────────────────────────────────────

    @Test
    @DisplayName("F05-09①：无 forecastContext 时 legacy 响应逐键不变（既不含 chains 也不含 explanation）")
    @SuppressWarnings("unchecked")
    void legacyDiagnoseResponseKeysUnchanged() {
        DiagnosisController controller = diagnosisController(emptyProvider());
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("metric", "profit");
        req.put("deviation", 3.5);
        req.put("domain", "default");
        req.put("maxDepth", 5);

        ApiResponse<?> resp = controller.diagnose(req);
        Map<String, Object> data = (Map<String, Object>) resp.getData();

        assertEquals(0, resp.getCode());
        assertEquals(Set.copyOf(LEGACY_KEYS), data.keySet(),
                "legacy 响应键集合必须与改造前逐键一致（新增键只在 forecastContext 分支出现）");
        assertFalse(data.containsKey("chains"));
        assertFalse(data.containsKey("forecastContext"));
    }

    @Test
    @DisplayName("F05-09：forecastContext 存在 → legacy 键全保留 + 增量 chains/explanationUnavailable")
    @SuppressWarnings("unchecked")
    void forecastDiagnoseAddsChainsBlockOnly() {
        ForecastRunSnapshotReader reader = reader(List.of(cell("p1", 202707, "100", "82")),
                new BigDecimal("100"), new BigDecimal("82"),
                List.of(evidencedSource("c1", List.of(node("进度延期", "EVENT"),
                                node("利润下降", "METRIC:M_FC_PROFIT")), "100", "82", List.of("ev-101"))));
        DiagnosisController controller = diagnosisController(provider(reader));

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("metric", "profit");
        req.put("deviation", 3.5);
        req.put("domain", "default");
        Map<String, Object> fc = new LinkedHashMap<>();
        fc.put("forecastRunId", "fr-001");
        fc.put("caliberId", "cal-01");
        fc.put("caliberVersion", "3");
        fc.put("asOfTime", "2027-08-31T00:00:00Z");
        fc.put("projectId", "p1");
        fc.put("departmentId", "d01");
        req.put("forecastContext", fc);

        Map<String, Object> data = (Map<String, Object>) controller.diagnose(req).getData();

        assertTrue(data.keySet().containsAll(LEGACY_KEYS), "增量不得删除既有键");
        assertNotNull(data.get("chains"));
        assertEquals(Boolean.TRUE, data.get("explanationUnavailable"), "DG-8：无 LLM 时解释降级、数值照常");
        List<CognitiveContractSchema.DiagnoseChainVO> chains =
                (List<CognitiveContractSchema.DiagnoseChainVO>) data.get("chains");
        assertEquals(0, new BigDecimal("-18.0000").compareTo(chains.get(0).getContribution()));
    }

    @Test
    @DisplayName("F05-09④：forecastContext 缺 forecastRunId → 400（禁退化为全库扫描或静默忽略）")
    void forecastContextWithoutRunIdIsBadRequest() {
        DiagnosisController controller = diagnosisController(emptyProvider());
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("metric", "profit");
        req.put("domain", "default");
        req.put("forecastContext", Map.of("caliberId", "cal-01"));

        CognitiveGuardException e = assertThrows(CognitiveGuardException.class, () -> controller.diagnose(req));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, e.errorCode);
        assertEquals(400, e.httpStatus);
    }

    @Test
    @DisplayName("F05-09：forecastContext 存在但 09 册接缝缺失 → 501 stub 而非 200 伪数值")
    void forecastDiagnoseWithoutSeamIsExplicitStub() {
        DiagnosisController controller = diagnosisController(emptyProvider());
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("metric", "profit");
        req.put("domain", "default");
        req.put("forecastContext", Map.of("forecastRunId", "fr-001"));

        CognitiveGuardException e = assertThrows(CognitiveGuardException.class, () -> controller.diagnose(req));
        assertEquals(CognitiveErrorCode.E503_NOT_IMPLEMENTED, e.errorCode);
    }

    @Test
    @DisplayName("F05-10②：mode 缺省走探索通道，exploratory=true + caliber=NON_ACCOUNTING，无金额字段")
    void exploratorySimulateKeepsNonAccountingCaliberAndNoAmounts() {
        ScenarioSimulatorService simulator = mock(ScenarioSimulatorService.class);
        when(simulator.runSimulation(any())).thenReturn(new SimulationResult());
        ScenarioController controller = new ScenarioController(simulator,
                new ForecastDiagnosisService(emptyProvider(), emptyProvider(), obs()), configDemoOff());

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("name", "延期情景");
        req.put("domain", "default");

        @SuppressWarnings("unchecked")
        ApiResponse<SimulationResult> resp = (ApiResponse<SimulationResult>) controller.simulate(req);
        assertTrue(resp.getData().isExploratory());
        assertEquals(CognitiveContractSchema.CALIBER_NON_ACCOUNTING, resp.getData().getCaliber());
    }

    @Test
    @DisplayName("F05-10：未知 mode → 400（禁静默回落探索通道冒充确定性重算）")
    void unknownSimulateModeIsBadRequest() {
        ScenarioController controller = new ScenarioController(mock(ScenarioSimulatorService.class),
                new ForecastDiagnosisService(emptyProvider(), emptyProvider(), obs()), configDemoOff());
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("mode", "MONTE_CARLO");
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class, () -> controller.simulate(req));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, e.errorCode);
    }

    // ── 夹具 ─────────────────────────────────────────────────────────

    /** §E.5 配置单源：demo 开关经 sysman 门面（Controller 不带 @Value 默认值）。 */
    private static com.chinacreator.gzcm.engine.cognitive2.service.CognitiveConfigQueryService configDemoOff() {
        var cfg = mock(com.chinacreator.gzcm.engine.cognitive2.service.CognitiveConfigQueryService.class);
        when(cfg.demoEnabled()).thenReturn(false);
        return cfg;
    }

    /** 观测出口：指标/EventBus 均未装配形态（宿主降级，禁抛错；审计落 target 兜底文件）。 */
    private static CognitiveObservability obs() {
        return CognitiveObsFixtures.unassembled();
    }

    private static CognitiveObservability obs(IMetricRecorder recorder) {
        return CognitiveObsFixtures.withRecorder(recorder);
    }

    private DiagnosisController diagnosisController(ObjectProvider<ForecastRunSnapshotReader> readerProvider) {
        CausalChainResult chainResult = mock(CausalChainResult.class);
        when(chainResult.isMetricFound()).thenReturn(true);
        when(chainResult.getCausalChain()).thenReturn(List.<CausalChainNode>of());
        CausalReasonerService reasoner = mock(CausalReasonerService.class);
        when(reasoner.diagnose(any())).thenReturn(chainResult);
        return new DiagnosisController(reasoner, mock(ModelRegistryService.class),
                emptyProvider(), emptyProvider(),
                new ForecastDiagnosisService(readerProvider, emptyProvider(), obs()), false);
    }

    private static CognitiveContractSchema.ForecastContextVO ctx(String runId) {
        CognitiveContractSchema.ForecastContextVO vo = new CognitiveContractSchema.ForecastContextVO();
        vo.setForecastRunId(runId);
        vo.setCaliberId("cal-01");
        vo.setCaliberVersion("3");
        vo.setAsOfTime("2027-08-31T00:00:00Z");
        vo.setProjectId("p1");
        return vo;
    }

    private static CognitiveContractSchema.ChainNodeVO node(String name, String type) {
        return new CognitiveContractSchema.ChainNodeVO(name, type);
    }

    private static ContributionCalculator.SnapshotEdge evidenced(String chainId,
            CognitiveContractSchema.ChainNodeVO from, CognitiveContractSchema.ChainNodeVO to,
            String baseline, String scenario, List<String> evidenceRefs) {
        return new ContributionCalculator.SnapshotEdge(chainId, List.of(from, to),
                bd(baseline), bd(scenario), evidenceRefs, null);
    }

    private static ContributionCalculator.SnapshotEdge unevidenced(String chainId,
            CognitiveContractSchema.ChainNodeVO from, CognitiveContractSchema.ChainNodeVO to,
            String baseline, String scenario) {
        return new ContributionCalculator.SnapshotEdge(chainId, List.of(from, to),
                bd(baseline), bd(scenario), List.of(), null);
    }

    private static ForecastRunSnapshotReader.ContributionEdgeSource evidencedSource(String chainId,
            List<CognitiveContractSchema.ChainNodeVO> path, String baseline, String scenario,
            List<String> evidenceRefs) {
        return new ForecastRunSnapshotReader.ContributionEdgeSource(
                chainId, path, bd(baseline), bd(scenario), evidenceRefs, null);
    }

    private static ScenarioBalanceChecker.Cell cell(String project, int period, String baseline, String scenario) {
        return new ScenarioBalanceChecker.Cell(project, period, bd(baseline), bd(scenario));
    }

    private static CognitiveContractSchema.SimulateOverrideVO override(String projectId) {
        CognitiveContractSchema.SimulateOverrideVO vo = new CognitiveContractSchema.SimulateOverrideVO();
        vo.setProjectId(projectId);
        vo.setPeriods(List.of("2027-07"));
        vo.setTarget("stage.realization_rate");
        vo.setStage("ACCEPTANCE");
        vo.setDelta(new BigDecimal("-0.10"));
        return vo;
    }

    /** 可配置快照替身：模拟 09 册 FC-02/FC-03 已上线时的返回形态。 */
    private static ForecastRunSnapshotReader reader(List<ScenarioBalanceChecker.Cell> cells,
            BigDecimal baselineProfit, BigDecimal scenarioProfit,
            List<ForecastRunSnapshotReader.ContributionEdgeSource> edges) {
        return new ForecastRunSnapshotReader() {
            @Override
            public RunSnapshot getRun(SnapshotQuery query) {
                return new RunSnapshot(query.forecastRunId() == null ? "fr-scenario-1" : query.forecastRunId(),
                        "cal-01", "3", "2027-08-31T00:00:00Z",
                        baselineProfit, scenarioProfit, cells, edges);
            }

            @Override
            public String copyScenario(ScenarioCopyRequest request) {
                return "fr-scenario-1";
            }
        };
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> emptyProvider() {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bean);
        return provider;
    }
}
