package com.chinacreator.gzcm.workspace.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.forecast.ForecastExportService;
import com.chinacreator.gzcm.workspace.forecast.ForecastAuditPackService;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunCommand;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunEvidence;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunGateway;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunNotFoundException;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunOrchestrationService;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunRequestValidator;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunStateMachine;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunStatus;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunView;
import com.chinacreator.gzcm.workspace.forecast.ForecastPeriodLock;
import com.chinacreator.gzcm.workspace.scenario.ActionProposal;
import com.chinacreator.gzcm.workspace.scenario.ActionProposalValidator;
import com.chinacreator.gzcm.workspace.scenario.BaselineReferenceGuard;
import com.chinacreator.gzcm.workspace.scenario.CognitiveEngineUnavailableException;
import com.chinacreator.gzcm.workspace.scenario.DecisionGateway;
import com.chinacreator.gzcm.workspace.scenario.RunMode;
import com.chinacreator.gzcm.workspace.scenario.ScenarioActionService;
import com.chinacreator.gzcm.workspace.scenario.SandboxReferenceForbiddenException;
import com.chinacreator.gzcm.workspace.backtest.BacktestContractController;
import com.chinacreator.gzcm.workspace.backtest.BacktestReadService;
import com.chinacreator.gzcm.workspace.security.AbacQueryFilter;
import com.chinacreator.gzcm.workspace.security.RlsFilter;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;
import com.chinacreator.gzcm.workspace.security.WorkspaceSecurityEngineClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.chinacreator.gzcm.workspace.exception.WorkspaceExceptionHandler;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 经营动作提案 run 锚定契约面验收（#146aa 附录A 第 32 条
 * {@code POST /api/v1/workspace/forecast-runs/{runId}/actions}，createFcAction，详设 line 8266）。
 *
 * <p>形态 = 窄 MockMvc（standaloneSetup + {@code WorkspaceExceptionHandler} 单源 advice，
 * 与 {@code ApiResponseTruthTest} 同法）：三滤波器已由 {@code /api/v1/workspace/**}
 * 既有族统一覆盖，本批不新增登记 ⇒ 只钉「契约路径逐字 + 承载腿真红 + 缺列诚实位」，
 * 纯内存无 DB、无网络。</p>
 */
@DisplayName("createFcAction（第32条）契约面")
class ForecastContractActionsContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    // ── 桩：内存 run 仓库 ──

    private final Map<String, ForecastRunView> runs = new LinkedHashMap<>();
    private final Map<String, RunMode> modes = new LinkedHashMap<>();

    private final ForecastRunGateway gateway = new ForecastRunGateway() {
        @Override
        public ForecastRunView submit(ForecastRunCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ForecastRunView findRun(String forecastRunId) {
            return runs.get(forecastRunId);
        }

        @Override
        public ForecastRunView findByIdempotencyKey(String idempotencyKey) {
            return null;
        }

        @Override
        public Map<String, Object> readResults(String forecastRunId) {
            return Map.of();
        }

        @Override
        public ForecastRunEvidence readEvidence(String forecastRunId) {
            return null;
        }

        @Override
        public String copyScenario(String sourceRunId, Map<String, Object> overrides) {
            throw new UnsupportedOperationException();
        }
    };

    /** 记录底座收到的入参（场景 id、审批人主体、runId 覆写取证）。 */
    private final Map<String, Object> gatewaySaw = new LinkedHashMap<>();
    private DecisionGateway decisionGateway = new DecisionGateway() {
        @Override
        public Map<String, Object> proposeAction(String scenarioId, ActionProposal proposal, String approverSubject) {
            gatewaySaw.put("scenarioId", scenarioId);
            gatewaySaw.put("approver", approverSubject);
            gatewaySaw.put("forecastRunId", proposal.getForecastRunId());
            return new LinkedHashMap<>(Map.of("actionId", "act-1"));
        }

        @Override
        public Map<String, Object> approve(String actionId, String approverSubject) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Map<String, Object>> listActions(String scenarioId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Map<String, Object>> listDecisions(String scenarioId) {
            throw new UnsupportedOperationException();
        }
    };

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
        runs.clear();
        modes.clear();
        gatewaySaw.clear();
    }

    // ── 装配 ──

    private MockMvc mvc(ScenarioActionService actionService) {
        ForecastRunOrchestrationService orchestration =
                new ForecastRunOrchestrationService(provider(gateway), new ForecastRunStateMachine(),
                        new ForecastPeriodLock(""));
        return MockMvcBuilders.standaloneSetup(new ForecastRunController(orchestration,
                        mock(ForecastRunRequestValidator.class),
                        new ScenarioReadGuard(new AbacQueryFilter(rlsClient(true))),
                        mock(ForecastExportService.class),
                        mock(ForecastAuditPackService.class),
                        actionService))
                .setControllerAdvice(new WorkspaceExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(MAPPER))
                .build();
    }

    private ScenarioActionService actionService(DecisionGateway g, RunMode probeMode) {
        ObjectProvider<DecisionGateway> gp = provider(g);
        ObjectProvider<BaselineReferenceGuard.BaselineRunModeResolver> rp =
                provider(id -> modes.containsKey(id) ? modes.get(id) : probeMode);
        return new ScenarioActionService(new ActionProposalValidator(),
                new BaselineReferenceGuard(rp), gp);
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T instance) {
        ObjectProvider<T> p = mock(ObjectProvider.class);
        if (instance != null) {
            when(p.getIfAvailable()).thenReturn(instance);
        }
        return p;
    }

    private WorkspaceSecurityEngineClient rlsClient(boolean allowAll) {
        WorkspaceSecurityEngineClient c = mock(WorkspaceSecurityEngineClient.class);
        when(c.applyRlsFilter(any(), any(), any()))
                .thenReturn(allowAll ? RlsFilter.unrestricted() : RlsFilter.deny("DENY", true, List.of()));
        return c;
    }

    private void stubRun(String runId, String scenarioId, String baselineRunId) {
        runs.put(runId, ForecastRunView.builder(runId)
                .scenarioId(scenarioId)
                .runType("FORMAL")
                .status(ForecastRunStatus.SUCCEEDED)
                .caliberId("cal-1").caliberVersion("v1").asOfTime("2026-10-29T00:00:00")
                .snapshotId("s-1").formulaVersion("f1")
                .idempotencyKey("k-" + runId).contentHash("h-" + runId)
                .baselineRunId(baselineRunId).reusedFromRunId(null)
                .evidenceRefs(List.of()).quantile(null)
                .build());
    }

    private static void setSubject(String name) {
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        name, "n/a", List.of()));
    }

    private static MvcResult post(MockMvc m, String url, String body) throws Exception {
        return m.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(url).contentType("application/json").content(body)).andReturn();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(MvcResult r) throws Exception {
        return MAPPER.readValue(
                r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                Map.class);
    }

    // ── 用例 ──

    @Test
    @DisplayName("契约路径逐字：类级 /api/v1/workspace/forecast-runs × 方法级 POST /{runId}/actions ⇒ 200 透传底座值")
    void contractPathIsVerbatim() throws Exception {
        // 反射钉注解组合（类级前缀 × 方法级字面段），禁前缀漂移
        Method hit = null;
        for (Method m : ForecastRunController.class.getDeclaredMethods()) {
            var pm = m.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);
            // 同 Controller 里裸 @PostMapping（value() 默认空串数组）需先守卫长度，
            // 与本次契约段是否存在无关，防 AIOOBE
            if (pm != null && pm.value().length > 0 && "/{runId}/actions".equals(pm.value()[0])) {
                hit = m;
            }
        }
        assertNotNull(hit, "方法级 POST /{runId}/actions 必须在位");
        assertEquals("createFcAction", hit.getName());
        String[] classPrefix =
                ForecastRunController.class.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class)
                        .value();
        assertEquals("/api/v1/workspace/forecast-runs", classPrefix[0]);

        stubRun("run-1", "sc-1", null);
        setSubject("subj-9");
        MvcResult r = post(mvc(actionService(decisionGateway, null)),
                "/api/v1/workspace/forecast-runs/run-1/actions",
                "{\"ownerUserId\":\"u-1\",\"dueDate\":\"2026-11-01\","
                        + "\"expectedImpact\":10.0,\"controlMetric\":\"mape\"}");
        assertEquals(200, r.getResponse().getStatus());
        Map<String, Object> data = ((Map<String, Object>) json(r).get("data"));
        assertEquals("act-1", data.get("actionId"), "底座返回值必须透传");
        assertEquals("run-1", data.get("forecastRunId"));
        assertEquals("sc-1", gatewaySaw.get("scenarioId"), "场景 id 必须读自 run 行");
    }

    @Test
    @DisplayName("404 腿：run 不存在 ⇒ FORECAST_RUN_NOT_FOUND（禁拿不存在的 run 挂动作）")
    void runNotFoundIs404() throws Exception {
        setSubject("subj-9");
        // runs 空 ⇒ status() 抛 404 载体
        MvcResult r = post(mvc(actionService(decisionGateway, null)),
                "/api/v1/workspace/forecast-runs/run-404/actions",
                "{\"ownerUserId\":\"u-1\",\"dueDate\":\"2026-11-01\","
                        + "\"expectedImpact\":10.0,\"controlMetric\":\"mape\"}");
        assertEquals(404, r.getResponse().getStatus());
        Map<String, Object> root = json(r);
        assertEquals("FORECAST_RUN_NOT_FOUND", root.get("errorCode"), "机读码 404 腿");
    }

    @Test
    @DisplayName("400 逐字段：缺 dueDate/expectedImpact/controlMetric ⇒ 三字段齐出（逐字段，禁整体吞）")
    void missingFieldsAre400PerField() throws Exception {
        stubRun("run-1", "sc-1", null);
        setSubject("subj-9");
        MvcResult r = post(mvc(actionService(decisionGateway, null)),
                "/api/v1/workspace/forecast-runs/run-1/actions",
                "{\"ownerUserId\":\"u-1\"}");
        assertEquals(400, r.getResponse().getStatus());
        Map<String, Object> root = json(r);
        assertEquals("ACTION_FIELDS_MISSING", root.get("errorCode"));
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) ((Map<String, Object>) root.get("data")).get("missingFields");
        assertEquals(List.of("dueDate", "expectedImpact", "controlMetric"), missing);
    }

    @Test
    @DisplayName("approver 只取 JWT subject：无主体 ⇒ approver 落缺失字段（禁 body 自报）")
    void approverOnlyFromJwtSubject() throws Exception {
        stubRun("run-1", "sc-1", null);
        // 不 setSubject：SecurityContext 空 ⇒ currentSubject()=null
        MvcResult r = post(mvc(actionService(decisionGateway, null)),
                "/api/v1/workspace/forecast-runs/run-1/actions",
                "{\"ownerUserId\":\"u-1\",\"dueDate\":\"2026-11-01\","
                        + "\"expectedImpact\":10.0,\"controlMetric\":\"mape\"}");
        assertEquals(400, r.getResponse().getStatus());
        Map<String, Object> root = json(r);
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) ((Map<String, Object>) root.get("data")).get("missingFields");
        assertEquals(List.of("approver"), missing, "只剩 approver 缺失（主体缺，是 identity 而非字段错）");
    }

    @Test
    @DisplayName("body.forecastRunId 被路径 runId 覆写（URL 单源，禁两处自相矛盾）")
    void bodyForecastRunIdIsOverridden() throws Exception {
        stubRun("run-1", "sc-1", null);
        setSubject("subj-9");
        MvcResult r = post(mvc(actionService(decisionGateway, null)),
                "/api/v1/workspace/forecast-runs/run-1/actions",
                "{\"ownerUserId\":\"u-1\",\"dueDate\":\"2026-11-01\","
                        + "\"expectedImpact\":10.0,\"controlMetric\":\"mape\","
                        + "\"forecastRunId\":\"run-bot\"}");
        assertEquals(200, r.getResponse().getStatus());
        assertEquals("run-1", gatewaySaw.get("forecastRunId"),
                "底座收到的 proposal.forecastRunId 必须 = 路径 runId");
        Map<String, Object> data = ((Map<String, Object>) json(r).get("data"));
        assertEquals("run-1", data.get("forecastRunId"), "回显也必须是路径 runId");
    }

    @Test
    @DisplayName("缺列诚实位：factorRef/projectId 恒 null + *Applicable=false（禁造值）")
    void missingColumnsAreHonestNulls() throws Exception {
        stubRun("run-1", "sc-1", null);
        setSubject("subj-9");
        MvcResult r = post(mvc(actionService(decisionGateway, null)),
                "/api/v1/workspace/forecast-runs/run-1/actions",
                "{\"ownerUserId\":\"u-1\",\"dueDate\":\"2026-11-01\","
                        + "\"expectedImpact\":10.0,\"controlMetric\":\"mape\"}");
        assertEquals(200, r.getResponse().getStatus());
        Map<String, Object> data = ((Map<String, Object>) json(r).get("data"));
        assertNull(data.get("factorRef"), "零载列必 null，禁造值");
        assertEquals(Boolean.FALSE, data.get("factorRefApplicable"));
        assertNull(data.get("projectId"));
        assertEquals(Boolean.FALSE, data.get("projectIdApplicable"));
    }

    @Test
    @DisplayName("503 底座腿：DecisionGateway 未装配 ⇒ COGNITIVE_UNAVAILABLE（禁本地造动作记录）")
    void missingGatewayIs503() throws Exception {
        stubRun("run-1", "sc-1", null);
        setSubject("subj-9");
        // 空 provider ⇒ requireGateway() 抛 503 承载
        MvcResult r = post(mvc(actionService(null, null)),
                "/api/v1/workspace/forecast-runs/run-1/actions",
                "{\"ownerUserId\":\"u-1\",\"dueDate\":\"2026-11-01\","
                        + "\"expectedImpact\":10.0,\"controlMetric\":\"mape\"}");
        assertEquals(503, r.getResponse().getStatus());
        Map<String, Object> root = json(r);
        assertEquals("COGNITIVE_UNAVAILABLE", root.get("errorCode"));
    }

    @Test
    @DisplayName("400 SANDBOX_REF_FORBIDDEN：run 锚定到演练模式 run（守诚实列，禁正式动作挂演练 run）")
    void sandboxBaselineIs400() throws Exception {
        // run-1 自身 run_mode=SANDBOX ⇒ resolveMode(forecastRunId) 护栏触发
        stubRun("run-1", "sc-1", null);
        modes.put("run-1", RunMode.SANDBOX);
        setSubject("subj-9");
        MvcResult r = post(mvc(actionService(decisionGateway, null)),
                "/api/v1/workspace/forecast-runs/run-1/actions",
                "{\"ownerUserId\":\"u-1\",\"dueDate\":\"2026-11-01\","
                        + "\"expectedImpact\":10.0,\"controlMetric\":\"mape\"}");
        assertEquals(400, r.getResponse().getStatus());
        Map<String, Object> root = json(r);
        assertEquals("SANDBOX_REF_FORBIDDEN", root.get("errorCode"));
        assertNull(gatewaySaw.get("scenarioId"), "400 后底座不应被触达（守卫先于写侧）");
    }
}
