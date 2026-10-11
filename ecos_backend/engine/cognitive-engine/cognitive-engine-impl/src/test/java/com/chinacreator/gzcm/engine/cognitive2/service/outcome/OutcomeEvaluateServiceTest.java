package com.chinacreator.gzcm.engine.cognitive2.service.outcome;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.context.CalculationResultRef;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.ActualRef;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.EvaluationWindow;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.MetricCalculationBinding;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.OutcomeEvaluateRequest;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.OutcomeEvaluateResult;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.OutcomeEvaluationBlocker;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.OutcomeEvaluationStatus;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.UpdateCandidateRef;
import com.chinacreator.gzcm.engine.cognitive2.dao.BeliefDao;
import com.chinacreator.gzcm.engine.cognitive2.dao.HypothesisDao;
import com.chinacreator.gzcm.engine.cognitive2.dao.OutcomeEvaluationDao;
import com.chinacreator.gzcm.engine.cognitive2.model.OutcomeEvaluationEntity;
import com.chinacreator.gzcm.engine.cognitive2.observability.CognitiveObservability;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SUP-OUT-01 / §9 W→D 结果评估编排服务冻结测试
 * （《认知域与智能域补充详细设计 v1.0》§9 / §9.4 / §16 验收矩阵）。
 *
 * <p>验收对应：
 * <ul>
 *   <li><b>SUP-OUT-01</b>（单次偏差不得自动升格生产资产）：
 *       {@link #guardsPassButNoProductionWriteHappens()} —— 门禁全过也零生产写，
 *       updateCandidates 恒为候选引用（status=GENERATED），warnings 带 AUTO_UPDATE_NOT_PERFORMED；</li>
 *   <li><b>§9.4 fail-loud</b>：ACTUALS_NOT_CLOSED 命中 ⇒ NEEDS_RECONCILE + blockers 非空，
 *       但评估结论<b>仍落库</b>（DG-6：业务结论不是错误码）
 *       （{@link #blockerActualsNotClosedPersistedAsNeedsReconcile()}）；</li>
 *   <li><b>DG-9</b>（接缝缺位禁止伪成功）：{@link #valueSeamAbsentMarksAllMetricsNotApplicable()}；</li>
 *   <li>结构校验 400 / 幂等回放 / 记录回查 404、400。</li>
 * </ul>
 * 装配口径：DAO/observability 全部 mock；{@link OutcomeGuardEvaluator} 用真实实例
 * （纯判定无依赖，§9.4 门禁裁决不 mock）；ObjectProvider 均无 bean（确定性接缝未交付形态）。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SUP-OUT-01 / §9.4 W→D 结果评估 (OutcomeEvaluateService)")
class OutcomeEvaluateServiceTest {

    @Mock
    private OutcomeEvaluationDao outcomeDao;
    @Mock
    private HypothesisDao hypothesisDao;
    @Mock
    private BeliefDao beliefDao;
    @Mock
    private CognitiveObservability observability;
    @Mock
    private ObjectProvider<OutcomeMetricValueReader> valueReaderProvider;
    @Mock
    private ObjectProvider<EventBusService> eventBusProvider;

    private OutcomeEvaluateService service;

    @BeforeEach
    void setUp() {
        // OutcomeGuardEvaluator 为无依赖纯判定组件：用真实实例锁 §9.4 七门禁语义，禁 mock 掉裁决本身
        service = new OutcomeEvaluateService(outcomeDao, new OutcomeGuardEvaluator(),
                hypothesisDao, beliefDao, valueReaderProvider, eventBusProvider, observability);
    }

    // ── 1. 结构性非法 ⇒ 400 ECOS-COG-400O（禁建评估记录）──

    @Test
    @DisplayName("§9.3 结构校验: 请求为 null ⇒ 400 ECOS-COG-400O，0 落库")
    void nullRequestIs400() {
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.evaluate(null));
        assertEquals(CognitiveErrorCode.E400_OUTCOME_INVALID, e.errorCode);
        assertEquals(400, e.httpStatus);
        verify(outcomeDao, never()).insert(any(OutcomeEvaluationEntity.class));
    }

    @Test
    @DisplayName("§9.3 结构校验: metricCalculations 缺失 ⇒ 400 ECOS-COG-400O，0 落库")
    void missingMetricCalculationsIs400() {
        OutcomeEvaluateRequest req = validRequest();
        req.setMetricCalculations(new ArrayList<>());

        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.evaluate(req));
        assertEquals(CognitiveErrorCode.E400_OUTCOME_INVALID, e.errorCode);
        assertEquals(400, e.httpStatus);
        verify(outcomeDao, never()).insert(any(OutcomeEvaluationEntity.class));
    }

    // ── 2. 幂等回放（同键重试回读既有记录，禁重复计算/建记录）──

    @Test
    @DisplayName("§7.5 幂等: 同 idempotencyKey 命中既有记录 ⇒ idempotentReplay=true，0 新写")
    void idempotentReplayReturnsExistingRecord() {
        OutcomeEvaluateRequest req = validRequest();
        OutcomeEvaluationEntity existing = new OutcomeEvaluationEntity();
        existing.setId("eval-9");
        existing.setTenantId("tenant-1");
        existing.setScenarioId("scenario-1");
        existing.setIdempotencyKey("idem-1");
        existing.setStatus(OutcomeEvaluationStatus.NEEDS_RECONCILE.name());
        when(outcomeDao.findByIdempotencyKey(eq("tenant-1"), eq("idem-1"))).thenReturn(existing);

        OutcomeEvaluateResult r = service.evaluate(req);

        assertTrue(r.isIdempotentReplay(), "幂等命中必须回显 idempotentReplay=true");
        assertEquals("eval-9", r.getEvaluationId());
        verify(outcomeDao, never()).insert(any(OutcomeEvaluationEntity.class));
    }

    // ── 3. §9.4 门禁命中 ⇒ NEEDS_RECONCILE 且结论仍落库（DG-6：业务结论 ≠ 错误码）──

    @Test
    @DisplayName("§9.4: 实际未关账(closed=FALSE) ⇒ NEEDS_RECONCILE + blockers 含 ACTUALS_NOT_CLOSED，evaluate 不抛错且记录仍落库一次")
    void blockerActualsNotClosedPersistedAsNeedsReconcile() {
        OutcomeEvaluateRequest req = validRequest();
        req.getActualRef().setClosed(Boolean.FALSE); // 未关账（§9.4 情形一）

        OutcomeEvaluateResult r = service.evaluate(req); // 不得抛异常（DG-6：门禁命中是业务结论）

        assertEquals(OutcomeEvaluationStatus.NEEDS_RECONCILE.name(), r.getStatus(),
                "门禁命中禁返回 EVALUATED");
        assertTrue(r.getBlockers().contains(OutcomeEvaluationBlocker.ACTUALS_NOT_CLOSED),
                "blockers 必须含 ACTUALS_NOT_CLOSED（词表单源枚举名）");
        verify(outcomeDao, times(1)).insert(any(OutcomeEvaluationEntity.class));
    }

    // ── 4. SUP-OUT-01 核心：门禁全过也零生产写（候选 ≠ 升格）──

    @Test
    @DisplayName("SUP-OUT-01: 七门禁全过 ⇒ BeliefDao/HypothesisDao 零写调用；updateCandidates 恒为 GENERATED 候选引用；warnings 带 AUTO_UPDATE_NOT_PERFORMED")
    void guardsPassButNoProductionWriteHappens() {
        OutcomeEvaluateResult r = service.evaluate(validRequest());

        // 生产资产 0 写：belief 表 insert / lifecycle UPDATE 全禁
        verify(beliefDao, never()).insert(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyInt(), anyString(), anyBoolean(), anyString(), anyString());
        verify(beliefDao, never()).updateLifecycleState(anyString(), anyString(), anyString());
        // hypothesis 表 insert 同样 0 调用
        verify(hypothesisDao, never()).insert(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());

        // updateCandidates 只可能是候选引用（§7.4 状态机起点 GENERATED），非空也不代表已改生产
        assertFalse(r.getUpdateCandidates().isEmpty(),
                "存在不适用指标 ⇒ 必须产 UPDATE_EVIDENCE 候选（禁静默无建议）");
        for (UpdateCandidateRef c : r.getUpdateCandidates()) {
            assertEquals("GENERATED", c.getStatus(), "候选状态机起点必须是 GENERATED");
            assertNotNull(c.getCandidateRef(), "候选必须带稳定引用");
            assertTrue(UpdateCandidateRef.CANDIDATE_TYPES.contains(c.getCandidateType()));
        }
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.startsWith("AUTO_UPDATE_NOT_PERFORMED")),
                "门禁全过也必须明示本端点从不写生产资产");
    }

    // ── 5. DG-9：确定性数值接缝未交付 ⇒ 全指标不适用 + 明示缺谁（禁伪成功）──

    @Test
    @DisplayName("DG-9: OutcomeMetricValueReader 无 bean ⇒ 全部指标 applicable=false + MISSING_VALUE_SEAM 告警 + NEEDS_RECONCILE")
    void valueSeamAbsentMarksAllMetricsNotApplicable() {
        OutcomeEvaluateResult r = service.evaluate(validRequest());

        assertEquals(OutcomeEvaluationStatus.NEEDS_RECONCILE.name(), r.getStatus(),
                "接缝缺位禁返回 EVALUATED");
        assertFalse(r.getMetrics().isEmpty());
        for (var m : r.getMetrics()) {
            assertFalse(m.isApplicable(), "无接缝时任何指标不得适用");
            assertNotNull(m.getNotApplicableReason());
        }
        assertTrue(r.getWarnings().contains(OutcomeEvaluateService.MISSING_VALUE_SEAM),
                "必须逐字登记接缝缺位定位串（禁笼统告警）");
        assertEquals(0, r.getApplicableSampleCount());
    }

    // ── 6. 接缝健康面 ──

    @Test
    @DisplayName("valueSeamReady(): provider 无 bean ⇒ false（false ≠ 假绿）")
    void valueSeamFalseWhenProviderEmpty() {
        assertFalse(service.valueSeamReady());
    }

    // ── 7. 记录回查 get() ──

    @Test
    @DisplayName("§9.2 步骤 10 回查: 记录不存在 ⇒ 404 ECOS-COG-404O（不降级空数据）")
    void getMissIs404() {
        when(outcomeDao.findById("eval-missing")).thenReturn(null);
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.get("eval-missing"));
        assertEquals(CognitiveErrorCode.E404_OUTCOME, e.errorCode);
        assertEquals(404, e.httpStatus);
    }

    @Test
    @DisplayName("§9.2 步骤 10 回查: evaluationId 空白 ⇒ 400 ECOS-COG-400O")
    void getBlankIdIs400() {
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.get("   "));
        assertEquals(CognitiveErrorCode.E400_OUTCOME_INVALID, e.errorCode);
        assertEquals(400, e.httpStatus);
        verify(outcomeDao, never()).findById(anyString());
    }

    // ── fixtures ──

    /** 结构合法 + §9.4 七门禁声明齐备（全过）的最简请求。 */
    private OutcomeEvaluateRequest validRequest() {
        OutcomeEvaluateRequest req = new OutcomeEvaluateRequest();
        req.setTenantId("tenant-1");
        req.setScenarioId("scenario-1");
        req.setIdempotencyKey("idem-1");
        req.setCaliberId("cal-blue");

        ActualRef actual = new ActualRef();
        actual.setAssetId("asset-1");
        actual.setVersion("v7");
        actual.setAsOfTime("2026-09-30T00:00:00Z");
        actual.setClosed(Boolean.TRUE);
        actual.setCaliberId("cal-blue");
        req.setActualRef(actual);

        req.setMetricRefs(new ArrayList<>(List.of("M_FC_PROFIT")));
        EvaluationWindow w = new EvaluationWindow();
        w.setFrom("2026-09-01");
        w.setTo("2026-09-30");
        req.setEvaluationWindow(w);

        MetricCalculationBinding b = new MetricCalculationBinding();
        b.setMetricRef("M_FC_PROFIT");
        b.setForecastRef(deterministicRef("calc-fc", "snap-fc", "out-fc"));
        b.setActualRef(deterministicRef("calc-act", "snap-act", "out-act"));
        req.setMetricCalculations(new ArrayList<>(List.of(b)));

        // §9.4 七门禁全声明满足（null = 未满足口径）
        req.setMaturityWindowReached(Boolean.TRUE);
        req.setDqGatePassed(Boolean.TRUE);
        req.setDqGateRef("dq-1");
        req.setEvidenceRefs(new ArrayList<>(List.of("ev-1")));
        req.setBaselineRegressionRef("reg-1");
        return req;
    }

    private static CalculationResultRef deterministicRef(String calcId, String snapshot, String output) {
        CalculationResultRef ref = new CalculationResultRef();
        ref.setDeterministic(true);
        ref.setCalculationId(calcId);
        ref.setInputSnapshotRef(snapshot);
        ref.setOutputRef(output);
        return ref;
    }
}
