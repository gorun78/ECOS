package com.chinacreator.gzcm.engine.cognitive2.service.backtest;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.backtest.BacktestReviewRouteOutcome;
import com.chinacreator.gzcm.engine.cognitive.api.backtest.BacktestReviewRouteResult;
import com.chinacreator.gzcm.engine.cognitive2.model.OutcomeEvaluationEntity;
import com.chinacreator.gzcm.engine.cognitive2.observability.CognitiveObservability;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 复审任务路由冻结测试（详设-09 v1.2 §十一 H-4 要点 4 / W226）。
 *
 * <p>锁三条红线的<b>反面</b>：</p>
 * <ol>
 *   <li>任务只走 runtime-task：认知侧不自造 {@code taskId}、不起线程（铁律 §1.6 / §2.5）；</li>
 *   <li>复审角色只来自 security-engine 裁决：未接线 / 不可用 / DENY / 无角色一律<b>不建任务</b>
 *       （铁律 §2.4 默认 DENY，DG-6：503 与 403 严格可分）；</li>
 *   <li>{@code LOW_SAMPLE} 优先于越限：守卫触发时不得据被抑制的断言建任务（禁造假结论）。</li>
 * </ol>
 */
@DisplayName("H-4 复审任务路由（BacktestReviewRouter）")
class BacktestReviewRouterTest {

    private final ITaskManagementService taskService = mock(ITaskManagementService.class);
    private final BacktestReviewRoleDecider decider = mock(BacktestReviewRoleDecider.class);
    private final CognitiveObservability observability = mock(CognitiveObservability.class);

    private static final String K_MAPE = "ecos.fc.backtest.review_threshold.mape_ratio";

    @SuppressWarnings("unchecked")
    private BacktestReviewRouter routerWith(BacktestReviewRoleDecider bean) {
        ObjectProvider<BacktestReviewRoleDecider> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bean);
        return new BacktestReviewRouter(taskService, provider, observability);
    }

    private static OutcomeEvaluationEntity entity() {
        OutcomeEvaluationEntity e = new OutcomeEvaluationEntity();
        e.setId("eval-001");
        e.setTenantId("t-01");
        e.setOrgId("org-01");
        e.setScenarioId("sc-001");
        e.setRunId("run-001");
        e.setForecastRunId("frun-001");
        return e;
    }

    /** 一个双侧齐备样本 + MAPE 越限线：产出 1 条越限、守卫不触发。 */
    private static BacktestMetricCalculator.Result breaching(MInteger minSamples) {
        List<BacktestMetricCalculator.Sample> rows = List.of(new BacktestMetricCalculator.Sample(
                "gm.revenue", new BigDecimal("130"), new BigDecimal("100"),
                null, null, null, true));
        Map<String, String> src = new LinkedHashMap<>();
        src.put(K_MAPE, K_MAPE);
        src.put("ecos.fc.backtest.review_threshold.min_samples",
                minSamples.value == null ? "ecos.fc.backtest.review_threshold.min_samples(UNSET)"
                        : "ecos.fc.backtest.review_threshold.min_samples");
        return BacktestMetricCalculator.compute(rows, 1,
                new BacktestMetricCalculator.Thresholds(null, new BigDecimal("0.15"),
                        minSamples.value, null, null, src), new BigDecimal("0.30"));
    }

    /** 包装 Integer，避免 {@code null} 在可变参数里产生歧义。 */
    private record MInteger(Integer value) {
    }

    private static CognitiveGuardException expectGuard(Executable action) {
        return assertThrows(CognitiveGuardException.class, action);
    }

    @Test
    @DisplayName("LOW_SAMPLE ⇒ SUPPRESSED_BY_LOW_SAMPLE 且不建任务（不据被抑制断言造假结论）")
    void lowSampleSuppressesRouting() {
        BacktestReviewRouter router = routerWith(decider);
        BacktestReviewRouteResult r = router.route(entity(), breaching(new MInteger(5)), "trace-1");

        assertEquals(BacktestReviewRouteOutcome.SUPPRESSED_BY_LOW_SAMPLE.name(), r.getOutcome());
        assertFalse(r.isRouted());
        assertNull(r.getTaskId());
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("NO_REVIEW_TASK_CREATED")));
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.startsWith("LOW_SAMPLE: ")));
        verifyNoInteractions(taskService, decider);
        verify(observability).audit(org.mockito.ArgumentMatchers.eq("cognitiveBacktest.reviewRoute"),
                org.mockito.ArgumentMatchers.eq("outcome_evaluation"),
                org.mockito.ArgumentMatchers.contains("suppressedByLowSample"));
    }

    @Test
    @DisplayName("无越限 ⇒ NO_BREACH 且不建任务、不请求裁决")
    void noBreachShortCircuitsBeforeAdjudication() {
        List<BacktestMetricCalculator.Sample> rows = List.of(new BacktestMetricCalculator.Sample(
                "gm.revenue", new BigDecimal("100"), new BigDecimal("100"), null, null, null, true));
        BacktestMetricCalculator.Result ok = BacktestMetricCalculator.compute(rows, 1,
                new BacktestMetricCalculator.Thresholds(null, new BigDecimal("0.15"), 1, null, null,
                        Map.of(K_MAPE, K_MAPE)), new BigDecimal("0.05"));
        BacktestReviewRouteResult r = routerWith(decider).route(entity(), ok, "trace-2");
        assertEquals(BacktestReviewRouteOutcome.NO_BREACH.name(), r.getOutcome());
        assertFalse(r.isRouted());
        verifyNoInteractions(taskService, decider);
    }

    @Test
    @DisplayName("裁决端口未装配 ⇒ 503 ROLE_DECIDER_UNAVAILABLE，不建任务（fail-closed）")
    void absentDeciderFailsClosed() {
        CognitiveGuardException e = expectGuard(() -> routerWith(null)
                .route(entity(), breaching(new MInteger(1)), "trace-3"));
        assertEquals(CognitiveErrorCode.E502_DEPENDENCY_DOWN, e.errorCode);
        assertTrue(e.missing.contains(BacktestReviewRouteOutcome.ROLE_DECIDER_UNAVAILABLE.name()));
        verifyNoInteractions(taskService);
    }

    @Test
    @DisplayName("裁决链路不可用 ⇒ 503；policy DENY ⇒ 403；两态严格可分且都不建任务")
    void unavailableAndDenyAreDistinguishable() {
        when(decider.decide(any())).thenReturn(BacktestReviewRoleDecider.ReviewRoleDecision
                .unavailable("policy-ref=x", "security-engine REST timeout"));
        CognitiveGuardException down = expectGuard(() -> routerWith(decider)
                .route(entity(), breaching(new MInteger(1)), "trace-4"));
        assertEquals(CognitiveErrorCode.E502_DEPENDENCY_DOWN, down.errorCode);
        assertTrue(down.missing.contains("security-engine REST timeout"));

        when(decider.decide(any())).thenReturn(BacktestReviewRoleDecider.ReviewRoleDecision
                .deny("policy-ref=y", "operator lacks review clearance"));
        CognitiveGuardException denied = expectGuard(() -> routerWith(decider)
                .route(entity(), breaching(new MInteger(1)), "trace-5"));
        assertEquals(CognitiveErrorCode.E403_CLEARANCE, denied.errorCode);
        assertTrue(denied.missing.contains(BacktestReviewRouteOutcome.ROLE_DENIED_BY_POLICY.name()));
        verifyNoInteractions(taskService);
    }

    @Test
    @DisplayName("放行但没给角色 ⇒ 403 ROLE_DECIDER_RETURNED_NO_ROLE，认知侧绝不本地推断角色")
    void allowWithoutRoleIsNotBackfilledLocally() {
        when(decider.decide(any())).thenReturn(new BacktestReviewRoleDecider.ReviewRoleDecision(
                true, false, "   ", "policy-ref=z", "no role in decision payload"));
        CognitiveGuardException e = expectGuard(() -> routerWith(decider)
                .route(entity(), breaching(new MInteger(1)), "trace-6"));
        assertEquals(CognitiveErrorCode.E403_CLEARANCE, e.errorCode);
        assertTrue(e.missing.contains(BacktestReviewRouteOutcome.ROLE_DECIDER_RETURNED_NO_ROLE.name())
                && e.missing.contains("cognitive never infers a role locally"));
        verifyNoInteractions(taskService);
    }

    @Test
    @DisplayName("放行 ⇒ 经 runtime-task 建任务：taskId 由底座分配，参数含越限项与实测值")
    void allowedRouteSubmitsThroughRuntimeTask() throws Exception {
        when(decider.decide(any())).thenReturn(BacktestReviewRoleDecider.ReviewRoleDecision
                .allow("FINANCE_REVIEWER", "policy-ref=w", "role adjudicated by security-engine"));
        when(taskService.submitTask(any())).thenReturn("T-777");

        BacktestReviewRouteResult r = routerWith(decider).route(entity(), breaching(new MInteger(1)), "trace-7");

        assertEquals(BacktestReviewRouteOutcome.SUBMITTED.name(), r.getOutcome());
        assertTrue(r.isRouted());
        assertEquals("T-777", r.getTaskId());
        assertEquals(BacktestReviewRouter.TASK_TYPE, r.getTaskType());
        assertEquals("FINANCE_REVIEWER", r.getReviewerRole());
        assertEquals("policy-ref=w", r.getDecisionRef());
        assertEquals(1, r.getBreachedItems().size());
        assertTrue(r.getBreachedItems().get(0).startsWith("MAX_MAPE measured=0.30 threshold=0.15"));
        assertEquals(r.getBreachedItems(), r.getMeasuredValues(), "越限项与实测值同源同序");
        assertTrue(r.getThresholdSources().contains(K_MAPE + "=" + K_MAPE));

        ArgumentCaptor<TaskDescription> cap = ArgumentCaptor.forClass(TaskDescription.class);
        verify(taskService).submitTask(cap.capture());
        TaskDescription desc = cap.getValue();
        assertNull(desc.getTaskId(), "taskId 必须留空由 runtime-task 分配（禁自造 jobId）");
        assertEquals(BacktestReviewRouter.TASK_TYPE, desc.getTaskType());
        Map<String, Object> params = desc.getParameters();
        assertEquals("run-001", params.get("runId"));
        assertEquals("eval-001", params.get("evaluationId"));
        assertEquals("FINANCE_REVIEWER", params.get("reviewerRole"));
        assertNotNull(params.get("breachedItems"));
        assertNotNull(params.get("traceId"));
        assertEquals("t-01", desc.getTenantId());
    }

    @Test
    @DisplayName("建任务失败 ⇒ 503 TASK_SUBMISSION_FAILED，不谎称已建")
    void submissionFailureStaysFailLoud() throws Exception {
        when(decider.decide(any())).thenReturn(BacktestReviewRoleDecider.ReviewRoleDecision
                .allow("FINANCE_REVIEWER", "policy-ref=v", "ok"));
        when(taskService.submitTask(any())).thenThrow(new ITaskManagementService.TaskManagementException(
                "queue full"));
        CognitiveGuardException e = expectGuard(() -> routerWith(decider)
                .route(entity(), breaching(new MInteger(1)), "trace-8"));
        assertEquals(CognitiveErrorCode.E502_DEPENDENCY_DOWN, e.errorCode);
        assertTrue(e.missing.contains(BacktestReviewRouteOutcome.TASK_SUBMISSION_FAILED.name()));
    }

    @Test
    @DisplayName("deciderReady 如实反映接线状态（false ≠ 假绿）")
    void seamReadinessIsHonest() {
        assertFalse(routerWith(null).deciderReady());
        assertTrue(routerWith(decider).deciderReady());
    }
}
