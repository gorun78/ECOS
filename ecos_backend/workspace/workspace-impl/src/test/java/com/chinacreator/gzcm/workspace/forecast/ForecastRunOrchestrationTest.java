package com.chinacreator.gzcm.workspace.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

/**
 * H-5 预测 run 编排面验收（详细设计-09 v1.2 §十一）—— 六要素落位 / 幂等 / 状态机 / 期间锁 /
 * 区间三值透传 / 依赖未就绪降级。纯内存 + 桩 {@link ForecastRunGateway}，无 DB、无网络。
 */
class ForecastRunOrchestrationTest {

    // ── 测试替身 ──

    /** 内存 run 仓库 + 计算桩：submit 生成新 run 并落六要素，findRun/findByIdempotencyKey 取回存值。 */
    private static final class FakeGateway implements ForecastRunGateway {
        final Map<String, ForecastRunView> byId = new LinkedHashMap<>();
        final Map<String, String> idemToRun = new LinkedHashMap<>();
        QuantileTriple nextQuantile;
        List<String> nextEvidence = List.of("ev-1");
        int submitCount;

        private FakeGateway() {
            this.nextQuantile = new QuantileTriple(new BigDecimal("100.00"),
                    new BigDecimal("120.00"), new BigDecimal("150.00"));
        }

        @Override
        public ForecastRunView submit(ForecastRunCommand command) {
            submitCount++;
            String runId = "fc_run_" + submitCount;
            ForecastRunView view = ForecastRunView.builder(runId)
                    .scenarioId(command.getScenarioId())
                    .runType(command.getRunType())
                    .status(ForecastRunStatus.SUCCEEDED)
                    .caliberId(command.getCaliberId())
                    .caliberVersion(command.getCaliberVersion())
                    .asOfTime(command.getAsOfTime())
                    .snapshotId(command.getSnapshotId())
                    .formulaVersion(command.getFormulaVersion())
                    .idempotencyKey(command.getIdempotencyKey())
                    .contentHash(command.getContentHash())
                    .baselineRunId(command.getBaselineRunId())
                    .reusedFromRunId(command.getReusedFromRunId())
                    .evidenceRefs(new ArrayList<>(nextEvidence))
                    .quantile(nextQuantile)
                    .build();
            byId.put(runId, view);
            idemToRun.put(command.getIdempotencyKey(), runId);
            return view;
        }

        @Override
        public ForecastRunView findRun(String forecastRunId) {
            return byId.get(forecastRunId);
        }

        @Override
        public ForecastRunView findByIdempotencyKey(String idempotencyKey) {
            String runId = idemToRun.get(idempotencyKey);
            return runId == null ? null : byId.get(runId);
        }

        @Override
        public Map<String, Object> readResults(String forecastRunId) {
            return Map.of();
        }

        @Override
        public ForecastRunEvidence readEvidence(String forecastRunId) {
            ForecastRunView v = byId.get(forecastRunId);
            return new ForecastRunEvidence(forecastRunId, v.getCaliberId(), v.getCaliberVersion(),
                    v.getAsOfTime(), v.getSnapshotId(), v.getFormulaVersion(),
                    v.getEvidenceRefs(), List.of());
        }

        @Override
        public String copyScenario(String sourceRunId, Map<String, Object> overrides) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ForecastRunGateway> providerReturning(ForecastRunGateway gw) {
        ObjectProvider<ForecastRunGateway> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(gw);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ForecastRunGateway> emptyProvider() {
        ObjectProvider<ForecastRunGateway> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    private static ForecastRunOrchestrationService serviceWith(ForecastRunGateway gw, String closedCsv) {
        return new ForecastRunOrchestrationService(providerReturning(gw),
                new ForecastRunStateMachine(), new ForecastPeriodLock(closedCsv));
    }

    private static ForecastRunCommand command(String idemKey, String contentHash) {
        return new ForecastRunCommand("sc-01", "FORECAST", "cal-01", "v3",
                "2025-08-31T23:59:59", "snap-77", "fv-2025.09",
                idemKey, contentHash, com.chinacreator.gzcm.workspace.scenario.RunMode.FORMAL, null);
    }

    // ── ① 六要素落位 ──

    @Test
    @DisplayName("创建 run：六要素 caliberId@版本/asOfTime/snapshotId/formulaVersion/证据引用全部落位")
    void createRunCarriesAllSixElements() {
        FakeGateway gw = new FakeGateway();
        ForecastRunView view = serviceWith(gw, "").create(command("k1", "hash-A"));

        assertEquals("cal-01", view.getCaliberId());
        assertEquals("v3", view.getCaliberVersion());
        assertEquals("2025-08-31T23:59:59", view.getAsOfTime());
        assertEquals("snap-77", view.getSnapshotId());
        assertEquals("fv-2025.09", view.getFormulaVersion());
        assertEquals("hash-A", view.getContentHash());
        assertEquals(List.of("ev-1"), view.getEvidenceRefs());
        assertEquals(1, gw.submitCount);
    }

    @Test
    @DisplayName("缺任一要素即 400 fail-loud，禁止静默默认值")
    void missingAnyElementRejects400() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scenarioId", "sc-01");
        body.put("runType", "FORECAST");
        body.put("caliberId", "cal-01");
        body.put("caliberVersion", "v3");
        body.put("asOfTime", "2025-08-31");
        body.put("formulaVersion", "fv-1");
        body.put("idempotencyKey", "k1");
        body.put("contentHash", "h1");
        // snapshotId 缺失
        ForecastRunRequestValidator validator = new ForecastRunRequestValidator(
                new com.chinacreator.gzcm.workspace.scenario.BaselineReferenceGuard(emptyRunModeProvider()));

        com.chinacreator.gzcm.common.exception.BusinessException ex = assertThrows(
                com.chinacreator.gzcm.common.exception.BusinessException.class,
                () -> validator.validate(body));
        assertTrue(ex.getMessage().contains("snapshotId"), ex.getMessage());
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<com.chinacreator.gzcm.workspace.scenario.BaselineReferenceGuard.BaselineRunModeResolver>
    emptyRunModeProvider() {
        ObjectProvider<com.chinacreator.gzcm.workspace.scenario.BaselineReferenceGuard.BaselineRunModeResolver> p =
                Mockito.mock(ObjectProvider.class);
        Mockito.when(p.getIfAvailable()).thenReturn(null);
        return p;
    }

    // ── ② 幂等复用 ──

    @Test
    @DisplayName("同 idempotencyKey + 同 contentHash → 复用既有 run 并回 reusedFromRunId，不重算")
    void idempotentSameKeySameHashReusesExistingRun() {
        FakeGateway gw = new FakeGateway();
        ForecastRunOrchestrationService svc = serviceWith(gw, "");
        svc.create(command("k1", "hash-A")); // 首次落 run_1
        int submitsAfterFirst = gw.submitCount;

        ForecastRunView reused = svc.create(command("k1", "hash-A")); // 同 key 同 hash

        assertEquals("fc_run_1", reused.getReusedFromRunId());
        assertEquals(submitsAfterFirst, gw.submitCount, "复用路径不得再次 submit（禁重算）");
    }

    // ── ③ 幂等冲突 ──

    @Test
    @DisplayName("同 idempotencyKey + 异 contentHash → 409 IDEMPOTENCY_CONFLICT 拒绝，禁静默覆盖")
    void idempotentSameKeyDiffHashRejectsConflict() {
        FakeGateway gw = new FakeGateway();
        ForecastRunOrchestrationService svc = serviceWith(gw, "");
        svc.create(command("k1", "hash-A"));

        ForecastRunConflictException ex = assertThrows(ForecastRunConflictException.class,
                () -> svc.create(command("k1", "hash-B")));
        assertEquals(409, ex.getHttpStatus());
        assertEquals("IDEMPOTENCY_CONFLICT", ex.getCode());
    }

    // ── ④ SUCCEEDED 只读 ──

    @Test
    @DisplayName("SUCCEEDED run 只读：原地改写结果被 409 RUN_READ_ONLY 拒绝（C210）")
    void succeededRunIsReadOnlyRewriteDenied() {
        ForecastRunStateMachine sm = new ForecastRunStateMachine();
        ForecastRunView done = ForecastRunView.builder("fc_run_9")
                .status(ForecastRunStatus.SUCCEEDED).build();

        ForecastRunReadOnlyException ex = assertThrows(ForecastRunReadOnlyException.class,
                () -> sm.assertRewriteAllowed(done, "rewrite-results"));
        assertEquals(409, ex.getHttpStatus());
        assertEquals("RUN_READ_ONLY", ex.getCode());
    }

    // ── ⑤ 重试派生新 run + 链路可追溯 ──

    @Test
    @DisplayName("重试派生新 run：新 runId≠源且 baselineRunId/reusedFromRunId 回链源（链路可追溯，C210）")
    void retryDerivesNewRunWithTraceableBackReference() {
        FakeGateway gw = new FakeGateway();
        ForecastRunOrchestrationService svc = serviceWith(gw, "");
        ForecastRunView base = svc.create(command("k1", "hash-A")); // fc_run_1

        ForecastRunView retried = svc.retry(base.getForecastRunId(), null);

        assertNotEquals(base.getForecastRunId(), retried.getForecastRunId());
        assertEquals(base.getForecastRunId(), retried.getBaselineRunId());
        assertEquals(base.getForecastRunId(), retried.getReusedFromRunId());
    }

    // ── ⑥ 区间三值透传（禁插值） ──

    @Test
    @DisplayName("结果三值 P10/P50/P90 原样透传；缺 P50 时保持 null，禁由 P10/P90 插值合成（R-62 ①）")
    void resultsPassThroughQuantilesWithoutInterpolation() {
        FakeGateway gw = new FakeGateway();
        gw.nextQuantile = new QuantileTriple(new BigDecimal("100.00"), null, new BigDecimal("150.00"));
        ForecastRunOrchestrationService svc = serviceWith(gw, "");
        ForecastRunView view = svc.create(command("k1", "hash-A"));

        Map<String, Object> results = svc.results(view.getForecastRunId());

        assertEquals(new BigDecimal("100.00"), results.get("p10"));
        assertEquals(new BigDecimal("150.00"), results.get("p90"));
        assertNull(results.get("p50"), "缺 P50 真值时禁插值合成（必须为 null，不得算出 125）");
    }

    // ── ⑦ 依赖未就绪 → 未上线（禁 200 造数） ──

    @Test
    @DisplayName("计算 SPI 未装配 → 501 STUB_CAPABILITY 未上线，绝不 200 mock 成功")
    void gatewayAbsentYields501NotFakeSuccess() {
        ForecastRunOrchestrationService svc = new ForecastRunOrchestrationService(emptyProvider(),
                new ForecastRunStateMachine(), new ForecastPeriodLock(""));

        ForecastRunNotLiveException ex = assertThrows(ForecastRunNotLiveException.class,
                () -> svc.create(command("k1", "hash-A")));
        assertEquals(501, ex.getHttpStatus());
        assertEquals("STUB_CAPABILITY", ex.getCode());
    }

    @Test
    @DisplayName("只读面依赖未就绪同样 501：status 查询不得返回伪造 run")
    void statusReadYields501WhenDependencyMissing() {
        ForecastRunOrchestrationService svc = new ForecastRunOrchestrationService(emptyProvider(),
                new ForecastRunStateMachine(), new ForecastPeriodLock(""));

        ForecastRunNotLiveException ex = assertThrows(ForecastRunNotLiveException.class,
                () -> svc.status("fc_run_x"));
        assertEquals(501, ex.getHttpStatus());
    }

    // ── ⑧ 关账期禁算 ──

    @Test
    @DisplayName("asOfTime 命中关账期 → 409 PERIOD_CLOSED 拒绝重算（复用 closed-periods 单源判定）")
    void closedPeriodForbidsCompute() {
        FakeGateway gw = new FakeGateway();
        ForecastRunOrchestrationService svc = serviceWith(gw, "2025-08,2025-09");

        ForecastPeriodClosedException ex = assertThrows(ForecastPeriodClosedException.class,
                () -> svc.create(command("k1", "hash-A")));
        assertEquals(409, ex.getHttpStatus());
        assertEquals("PERIOD_CLOSED", ex.getCode());
        assertEquals(0, gw.submitCount, "关账期不得触发任何计算落库");
    }

    @Test
    @DisplayName("重试命中关账期同样被拒，禁对已关账 run 重算")
    void closedPeriodForbidsRetry() {
        FakeGateway gw = new FakeGateway();
        ForecastRunOrchestrationService openSvc = serviceWith(gw, "");
        ForecastRunView base = openSvc.create(command("k1", "hash-A"));

        ForecastRunOrchestrationService closedSvc = serviceWith(gw, "2025-08");
        assertThrows(ForecastPeriodClosedException.class, () -> closedSvc.retry(base.getForecastRunId(), null));
    }
}
