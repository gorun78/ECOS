package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessCheckStatus;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentReadinessGapDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentQuestionDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessGapDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.StepExecutor;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentBudgetService;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentBudgetService.BudgetSnapshot;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentBudgetService.Consumption;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F10-09 缺口面门禁（详细设计-10 §4.1 {@code readiness/GapService} + 硬规则⑤「循环 ≤3」）。
 *
 * <p>钉三件事：
 * ① 缺口行<b>只由缺项产生</b>且形态受控（V232 无 check_code 列 ⇒ {@code suggested_action_text}
 *    写读同源，禁调用点自拼；{@code effort_estimate} 恒 null＝禁编造工时）；
 * ② 轮次闸以 Run 的 {@code consumption_json} 为<b>单源</b>，无 Run ⇒ fail-closed 拒绝
 *    （允许"没 Run 也能 refill"等于允许无限循环）；
 * ③ 派发<b>只经 runtime-task</b>，未接线的动作一律进 deferred 清单点名，禁静默丢弃。</p>
 */
class ReadinessGapServiceTest {

    private static final String TENANT = "t-1";
    private static final String OPERATOR = "u-1";
    private static final String ASSESSMENT_ID = "ra-1";

    private WAgentReadinessGapDao gapDao;
    private WAgentRunDao runDao;
    private WAgentBudgetService budgetService;
    private StepExecutor stepExecutor;
    private ObjectProvider<ITaskManagementService> taskProvider;
    private ITaskManagementService taskService;
    private ReadinessGapService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        gapDao = mock(WAgentReadinessGapDao.class);
        runDao = mock(WAgentRunDao.class);
        budgetService = mock(WAgentBudgetService.class);
        stepExecutor = mock(StepExecutor.class);
        taskProvider = mock(ObjectProvider.class);
        taskService = mock(ITaskManagementService.class);
        service = new ReadinessGapService(gapDao, runDao, budgetService, stepExecutor, taskProvider);

        when(gapDao.insert(any())).thenReturn(1);
        when(taskProvider.getIfAvailable()).thenReturn(taskService);
        when(budgetService.parseSnapshot(any())).thenReturn(new BudgetSnapshot(60, 40, 30, 30, 3, 3));
        when(stepExecutor.parseConsumption(any())).thenReturn(Consumption.zero());
        when(budgetService.toConsumptionJson(any())).thenReturn("{\"refill_rounds\":1}");
        when(runDao.findById(anyString())).thenReturn(Optional.of(run("run-1")));
    }

    // ── ① 缺口落库形态 ────────────────────────────────────────

    @Test
    @DisplayName("缺口行只由缺项产生：pass 项不建行，severity=状态字面，工时留 null（禁编造）")
    void onlyMissingOutcomesBecomeGapRows() {
        List<CheckOutcome> outcomes = List.of(
                outcome(ReadinessCheckCode.DATA_COVERAGE, ReadinessCheckStatus.PASS, "coverage>=0.7"),
                outcome(ReadinessCheckCode.COG_CM_READY, ReadinessCheckStatus.BLOCKER, "cm-unavailable"),
                outcome(ReadinessCheckCode.KNOW_APPLICABILITY, ReadinessCheckStatus.UNKNOWN, "metric-absent"));

        List<WAgentReadinessGapDO> written = service.openGaps(ASSESSMENT_ID, context(), outcomes,
                "D", Map.of("cog.cm_ready", "C"), OPERATOR);

        assertEquals(2, written.size(), "非缺项不得建行");
        ArgumentCaptor<WAgentReadinessGapDO> captor = ArgumentCaptor.forClass(WAgentReadinessGapDO.class);
        verify(gapDao, times(2)).insert(captor.capture());
        for (WAgentReadinessGapDO row : captor.getAllValues()) {
            assertEquals(ASSESSMENT_ID, row.getAssessmentId());
            assertEquals("open", row.getStatus());
            assertNull(row.getEffortEstimate(), "工时是运营承诺，代码写默认值即编造");
            assertNull(row.getResolvedTime(), "本批无 RESOLVED 写入方");
            assertEquals(TENANT, row.getTenantId());
            assertEquals("D", row.getUnlockFrom());
            String checkCode = ReadinessGapService.checkCodeOf(row);
            assertTrue(row.getDescriptionText().startsWith(checkCode + ": "),
                    "描述必须以检查码开头（否则看板无法定位缺口来源项）: " + row.getDescriptionText());
        }
        WAgentReadinessGapDO blocker = captor.getAllValues().get(0);
        assertEquals("blocker", blocker.getSeverity(), "severity 复用状态字面（裁决 D-B2④-4）");
        assertEquals("C", blocker.getUnlockTo());
        assertTrue(blocker.getImpactText().contains("grade D -> C"), blocker.getImpactText());
        // UNKNOWN 项无解锁映射 ⇒ unlock_to 留 null（禁猜一个档）
        assertNull(captor.getAllValues().get(1).getUnlockTo());
    }

    @Test
    @DisplayName("suggested_action_text 受控形态写读同源：形态不符即判'不可派发'，禁猜")
    void actionTextRoundTripsAndRejectsFreeForm() {
        String encoded = ReadinessGapService.encodeSuggestedAction(
                ReadinessGapService.ACTION_APPROVE, "onto.caliber_defined");
        assertEquals("type=approve;check_code=onto.caliber_defined", encoded);
        WAgentReadinessGapDO gap = new WAgentReadinessGapDO();
        gap.setSuggestedActionText(encoded);
        assertEquals("onto.caliber_defined", ReadinessGapService.checkCodeOf(gap));

        // 自由文案（例如未来有人让 LLM 直接写这列）必须解析失败，而不是被"宽容"成一个动作
        gap.setSuggestedActionText("请尽快补齐口径定义");
        assertNull(ReadinessGapService.checkCodeOf(gap));
        gap.setSuggestedActionText("check_code=data.check_coverage");
        assertNull(ReadinessGapService.checkCodeOf(gap), "缺 type 段即形态不符");
        gap.setSuggestedActionText("type=delete;check_code=cog.cm_ready");
        assertNull(ReadinessGapService.checkCodeOf(gap), "动作越界即形态不符");
    }

    @Test
    @DisplayName("缺 assessmentId ⇒ 拒写（无主明细禁落库）")
    void orphanGapRowsAreRejected() {
        List<CheckOutcome> outcomes = List.of(
                outcome(ReadinessCheckCode.COG_CM_READY, ReadinessCheckStatus.BLOCKER, "cm-unavailable"));
        assertThrows(WAgentOrchestrationException.class,
                () -> service.openGaps("  ", context(), outcomes, "D", Map.of(), OPERATOR));
        verify(gapDao, never()).insert(any());
    }

    @Test
    @DisplayName("建议动作映射定版：UNKNOWN 一律 task（探测未回是去接线，不是去补数据）")
    void actionMappingFollowsRuling() {
        assertEquals(ReadinessGapService.ACTION_FETCH,
                ReadinessGapService.suggestedActionOf(ReadinessCheckCode.DATA_COVERAGE, ReadinessCheckStatus.FAIL));
        assertEquals(ReadinessGapService.ACTION_APPROVE,
                ReadinessGapService.suggestedActionOf(ReadinessCheckCode.ONTO_CALIBER_DEFINED,
                        ReadinessCheckStatus.FAIL));
        assertEquals(ReadinessGapService.ACTION_CONFIRM,
                ReadinessGapService.suggestedActionOf(ReadinessCheckCode.KNOW_APPLICABILITY,
                        ReadinessCheckStatus.WARN), "DG-K1 requires_ack ⇒ 确认而非阻断");
        assertEquals(ReadinessGapService.ACTION_TASK,
                ReadinessGapService.suggestedActionOf(ReadinessCheckCode.COG_CM_READY, ReadinessCheckStatus.BLOCKER));
        // UNKNOWN 覆盖检查项自带的动作语义
        assertEquals(ReadinessGapService.ACTION_TASK,
                ReadinessGapService.suggestedActionOf(ReadinessCheckCode.DATA_STALENESS, ReadinessCheckStatus.UNKNOWN));
    }

    // ── ② 补齐轮次闸（≤3，单源 consumption_json） ─────────────

    @Test
    @DisplayName("硬规则⑤：0→1→2→3 轮可补，第 4 次调用即越限（闸在预算侧，不在调用方自觉）")
    void refillLoopCapsAtThreeRounds() {
        WAgentReadinessDO assessment = assessment(ASSESSMENT_ID, "run-1");
        WAgentQuestionDO question = question();

        for (int consumed = 0; consumed <= 3; consumed++) {
            when(stepExecutor.parseConsumption(any())).thenReturn(new Consumption(10, 5, 0, consumed));
            ReadinessGapService.RefillLedger ledger = service.loadRefillLedger(assessment, question);
            assertEquals(consumed, ledger.refillRound());
            assertEquals(3, ledger.maxRefillRounds());
            assertEquals(consumed >= 3, ledger.limitReached(),
                    "已消耗 " + consumed + " 轮的越限判定");
        }
    }

    @Test
    @DisplayName("无 Run 可解析 ⇒ fail-closed 拒绝 refill（禁无台账补槽把 ≤3 变成无限循环）")
    void loadRefillLedgerFailsClosedWithoutRun() {
        WAgentReadinessDO headless = assessment("ra-1", null);
        WAgentQuestionDO question = question();
        question.setLatestRunId(null);

        WAgentOrchestrationException failure = assertThrows(WAgentOrchestrationException.class,
                () -> service.loadRefillLedger(headless, question));
        assertTrue(failure.getMessage().contains("consumption_json"), failure.getMessage());
        verify(runDao, never()).findById(anyString());
    }

    @Test
    @DisplayName("Run 取不到 ⇒ 404 语义（禁新建 Run 兜底：Run 是编排产物，不是缺口服务的产物）")
    void missingRunRowIsNotFound() {
        when(runDao.findById("run-ghost")).thenReturn(Optional.empty());
        assertThrows(WAgentOrchestrationException.class,
                () -> service.loadRefillLedger(assessment("ra-1", "run-ghost"), question()));
    }

    @Test
    @DisplayName("轮次先落账：consumption_json +1 且此时 readiness_id 尚未回挂（两段式写，D-B2④-13）")
    void recordRefillRoundCountsBeforeAssessmentIsAttached() {
        when(stepExecutor.parseConsumption(any())).thenReturn(new Consumption(10, 5, 0, 1));
        WAgentRunDO run = run("run-1");
        when(runDao.findById("run-1")).thenReturn(Optional.of(run));
        ReadinessGapService.RefillLedger ledger =
                service.loadRefillLedger(assessment("ra-1", "run-1"), question());

        Consumption after = service.recordRefillRound(ledger, null, OPERATOR);

        assertEquals(2, after.refillRounds());
        assertEquals("{\"refill_rounds\":1}", run.getConsumptionJson());
        assertEquals("ra-old", run.getReadinessId(), "本段只 +1 轮次，指针留给 attachAssessmentToRun");

        service.attachAssessmentToRun(run, "ra-new", OPERATOR);
        assertEquals("ra-new", run.getReadinessId());
        verify(runDao, times(2)).update(run);
    }

    @Test
    @DisplayName("旧缺口只置 superseded 不置 resolved（进展不是由新评估推断出来的）")
    void previousOpenGapsBecomeSupersededNotResolved() {
        when(gapDao.supersedeOpen("ra-old", "superseded", "open", OPERATOR)).thenReturn(3);

        assertEquals(3, service.supersedeOpenGaps("ra-old", OPERATOR));
        verify(gapDao).supersedeOpen(eq("ra-old"), eq("superseded"), eq("open"), eq(OPERATOR));
        verify(gapDao, never()).update(any());
    }

    @Test
    @DisplayName("supersede 空 ID ⇒ 直接返 0（禁把 null 当'替代所有评估'）")
    void blankAssessmentIdSupersedesNothing() {
        assertEquals(0, service.supersedeOpenGaps(null, OPERATOR));
        assertEquals(0, service.supersedeOpenGaps("  ", OPERATOR));
        verify(gapDao, never()).supersedeOpen(anyString(), anyString(), anyString(), anyString());
    }

    // ── ③ 派发面与动作词表 ────────────────────────────────────

    @Test
    @DisplayName("action_type 越界即拒（禁宽容成默认动作）；空串与缺位同义")
    void actionTypesAreClosedSet() {
        ReadinessGapService.assertActionType(null);
        ReadinessGapService.assertActionType("  ");
        ReadinessGapService.assertActionType("fetch");
        assertTrue(ReadinessGapService.ACTION_TYPES.containsAll(
                List.of("fetch", "task", "confirm", "approve")));
        assertEquals(4, ReadinessGapService.ACTION_TYPES.size());
        assertThrows(WAgentOrchestrationException.class,
                () -> ReadinessGapService.assertActionType("drop_table"));
    }

    @Test
    @DisplayName("未接线动作/无任务台的缺口都进 deferred 并点名原因，禁静默丢弃")
    void unparsableAndUnwiredActionsAreDeferredWithReasons() {
        WAgentReadinessGapDO unparsable = new WAgentReadinessGapDO();
        unparsable.setId("gap-0");
        unparsable.setSuggestedActionText("自由文案");
        WAgentReadinessGapDO fetch = gapRow("gap-1", "fetch", "data.check_coverage");

        ReadinessGapService.TaskDispatch dispatch =
                service.dispatchResolutionTasks(List.of(unparsable, fetch), null, context(), OPERATOR);

        assertTrue(dispatch.taskIds().isEmpty());
        assertFalse(dispatch.dispatchedAnything());
        assertEquals(2, dispatch.deferred().size());
        assertEquals("suggested-action-unparsable", dispatch.deferred().get(0).get("reason"));
        assertEquals(ReadinessGapService.DEFERRED_ENGINE_PORT, dispatch.deferred().get(1).get("reason"));
        assertEquals("data.check_coverage", dispatch.deferred().get(1).get("check_code"));
    }

    @Test
    @DisplayName("无缺口 ⇒ 空派发（禁为'没有缺口'编一个 deferred 行冒充处理过）")
    void emptyGapListDispatchesNothing() {
        ReadinessGapService.TaskDispatch dispatch =
                service.dispatchResolutionTasks(List.of(), null, context(), OPERATOR);
        assertTrue(dispatch.taskIds().isEmpty());
        assertTrue(dispatch.deferred().isEmpty());
    }

    // ── 夹具 ────────────────────────────────────────────────

    private static CheckOutcome outcome(ReadinessCheckCode code, ReadinessCheckStatus status, String reason) {
        return new CheckOutcome(code, MetricValue.number(new BigDecimal("1")),
                ThresholdSet.unset("default"), ReadinessCheckCode.Judgement.of(status, reason),
                List.of(), null);
    }

    private static WAgentReadinessGapDO gapRow(String id, String actionType, String checkCode) {
        WAgentReadinessGapDO gap = new WAgentReadinessGapDO();
        gap.setId(id);
        gap.setAssessmentId("ra-1");
        gap.setTenantId(TENANT);
        gap.setLayer("D");
        gap.setSeverity("fail");
        gap.setSuggestedActionText(ReadinessGapService.encodeSuggestedAction(actionType, checkCode));
        gap.setStatus("open");
        return gap;
    }

    private static ReadinessProbeContext context() {
        return new ReadinessProbeContext("q-1", "m-1", "goal-1", "run-1", null, "DIAGNOSE",
                TENANT, "o-1", Map.of());
    }

    private static WAgentReadinessDO assessment(String id, String runId) {
        WAgentReadinessDO assessment = new WAgentReadinessDO();
        assessment.setId(id);
        assessment.setQuestionId("q-1");
        assessment.setRunId(runId);
        assessment.setTenantId(TENANT);
        assessment.setGrade("C");
        return assessment;
    }

    private static WAgentQuestionDO question() {
        WAgentQuestionDO question = new WAgentQuestionDO();
        question.setId("q-1");
        question.setTenantId(TENANT);
        question.setLatestRunId("run-1");
        return question;
    }

    private static WAgentRunDO run(String id) {
        WAgentRunDO run = new WAgentRunDO();
        run.setId(id);
        run.setQuestionId("q-1");
        run.setTenantId(TENANT);
        run.setReadinessId("ra-old");
        run.setBudgetJson("{}");
        run.setConsumptionJson("{}");
        return run;
    }
}
