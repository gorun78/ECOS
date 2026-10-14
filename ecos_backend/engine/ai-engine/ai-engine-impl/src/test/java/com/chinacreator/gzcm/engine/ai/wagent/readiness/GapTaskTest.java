package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentReadinessGapDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessGapDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.StepExecutor;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentBudgetService;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 具名门禁 {@code GapTaskTest#taskCreatedViaRuntimeTaskOnly}（详细设计-10 §5.3 F10-09 验收项原文；
 * 差距分析「F10-09 缺口派发零实现」的收口点之一）。
 *
 * <p><b>本类存在的理由</b>：F10-09 规格写死「{@code type=task} 经 {@code ITaskManagementService}
 * 建任务，<b>不新建任务表</b>」，而铁律 v2.0 §2.5 把调度能力收敛在 runtime-task。
 * 一个"看起来能派发"的实现很容易滑向"自己在 ecos_wagent_* 里插一行当任务"——
 * 那种假绿既有数据又有返回值，只有从<b>调用面</b>断言才能挡住。
 * 故本类的核心断言是：{@code submitTask} 被调、<b>且</b>除 gapDao 读以外没有任何写通道被触碰。</p>
 *
 * <p>其余分支都是"缺口不谎称已派发"的差额可见性：任务台缺席 / 任务台拒单 ⇒ 进 deferred 并点名原因。</p>
 */
class GapTaskTest {

    private static final String TENANT = "t-1";
    private static final String OPERATOR = "u-1";

    private WAgentReadinessGapDao gapDao;
    private WAgentRunDao runDao;
    private ITaskManagementService taskService;
    private ObjectProvider<ITaskManagementService> taskProvider;
    private ReadinessGapService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        gapDao = mock(WAgentReadinessGapDao.class);
        runDao = mock(WAgentRunDao.class);
        taskService = mock(ITaskManagementService.class);
        taskProvider = mock(ObjectProvider.class);
        service = new ReadinessGapService(gapDao, runDao,
                mock(WAgentBudgetService.class), mock(StepExecutor.class), taskProvider);
        when(taskProvider.getIfAvailable()).thenReturn(taskService);
    }

    @Test
    @DisplayName("具名门禁：type=task 的缺口只经 runtime-task submitTask 建任务，引擎侧零写表")
    void taskCreatedViaRuntimeTaskOnly() throws ITaskManagementService.TaskManagementException {
        when(taskService.submitTask(any())).thenReturn("task-101");
        List<WAgentReadinessGapDO> gaps = List.of(gap("gap-1", "C", "task", "cog.cm_ready"));

        ReadinessGapService.TaskDispatch dispatch =
                service.dispatchResolutionTasks(gaps, null, context(), OPERATOR);

        assertEquals(List.of("task-101"), dispatch.taskIds());
        assertTrue(dispatch.dispatchedAnything());
        assertTrue(dispatch.deferred().isEmpty());

        ArgumentCaptor<TaskDescription> captor = ArgumentCaptor.forClass(TaskDescription.class);
        verify(taskService, times(1)).submitTask(captor.capture());
        TaskDescription submitted = captor.getValue();
        assertEquals(ReadinessGapService.GAP_TASK_TYPE, submitted.getTaskType());
        assertEquals(TENANT, submitted.getTenantId(), "任务必须带租户，否则任务台无法做隔离");
        assertEquals(OPERATOR, submitted.getCreatedBy());
        assertEquals(Boolean.TRUE, submitted.getAsync(), "缺口补齐是异步动作，禁占评估线程");

        // 引擎侧不建任务表：派发路径除读缺口外，不得触碰任何写通道
        verify(gapDao, never()).insert(any());
        verify(gapDao, never()).update(any());
        verify(runDao, never()).update(any());
        verify(runDao, never()).insert(any());
    }

    @Test
    @DisplayName("任务参数只带锚点与档位，禁塞度量数值/金额（硬规则③同族纪律）")
    void taskParametersCarryAnchorsOnly() throws ITaskManagementService.TaskManagementException {
        when(taskService.submitTask(any())).thenReturn("task-102");
        WAgentReadinessGapDO gap = gap("gap-9", "I", "task", "onto.conflict_count");
        gap.setUnlockFrom("D");
        gap.setUnlockTo("C");
        gap.setSuggestedOwner("ontology-owner");

        service.dispatchResolutionTasks(List.of(gap), null, context(), OPERATOR);

        ArgumentCaptor<TaskDescription> captor = ArgumentCaptor.forClass(TaskDescription.class);
        verify(taskService).submitTask(captor.capture());
        Map<String, Object> params = captor.getValue().getParameters();
        assertEquals(List.of("gap_id", "assessment_id", "question_id", "run_id", "check_code",
                        "layer", "severity", "unlock_from", "unlock_to", "suggested_owner"),
                List.copyOf(params.keySet()));
        assertEquals("gap-9", params.get("gap_id"));
        assertEquals("onto.conflict_count", params.get("check_code"));
        assertEquals("q-1", params.get("question_id"));
        assertEquals("run-1", params.get("run_id"));
        assertEquals("C", params.get("unlock_to"));
        assertFalse(params.containsKey("measured_value"),
                "度量值属评估快照，抄进任务参数即两份真相");
    }

    @Test
    @DisplayName("runtime-task 未装配 ⇒ deferred 点名 runtime-task-unavailable（禁静默丢弃、禁自建队列）")
    void taskHubAbsentIsDeferredNotSilentlyDropped() {
        when(taskProvider.getIfAvailable()).thenReturn(null);

        ReadinessGapService.TaskDispatch dispatch = service.dispatchResolutionTasks(
                List.of(gap("gap-2", "C", "task", "cog.backtest_ready")), null, context(), OPERATOR);

        assertTrue(dispatch.taskIds().isEmpty());
        assertEquals(1, dispatch.deferred().size());
        assertEquals(ReadinessGapService.DEFERRED_TASK_HUB_ABSENT, dispatch.deferred().get(0).get("reason"));
        assertEquals("gap-2", dispatch.deferred().get(0).get("gap_id"));
    }

    @Test
    @DisplayName("任务台拒单（TaskManagementException）⇒ 折成 deferred，不打断复评主链")
    void taskHubRejectionBecomesDeferredRow() throws ITaskManagementService.TaskManagementException {
        when(taskService.submitTask(any())).thenThrow(
                new ITaskManagementService.TaskManagementException("任务台配额已满"));

        ReadinessGapService.TaskDispatch dispatch = service.dispatchResolutionTasks(
                List.of(gap("gap-3", "C", "task", "cog.cm_ready")), null, context(), OPERATOR);

        assertTrue(dispatch.taskIds().isEmpty());
        assertEquals("runtime-task-rejected", dispatch.deferred().get(0).get("reason"));
    }

    @Test
    @DisplayName("显式 action_type 覆盖缺口自带动作：approve 请求不会偷偷建成 task")
    void requestedActionTypeOverridesRowAction() throws ITaskManagementService.TaskManagementException {
        ReadinessGapService.TaskDispatch dispatch = service.dispatchResolutionTasks(
                List.of(gap("gap-4", "C", "task", "cog.cm_ready")), "approve", context(), OPERATOR);

        assertTrue(dispatch.taskIds().isEmpty(), "approve 的引擎侧端口未接线 ⇒ 不得谎称已派发");
        assertEquals(ReadinessGapService.DEFERRED_ENGINE_PORT, dispatch.deferred().get(0).get("reason"));
        assertEquals("approve", dispatch.deferred().get(0).get("action_type"));
        verify(taskService, never()).submitTask(any());
    }

    @Test
    @DisplayName("混合批次：task 行建任务、fetch 行进 deferred，两条差额都看得见")
    void mixedBatchShowsBothDispatchedAndDeferred() throws ITaskManagementService.TaskManagementException {
        when(taskService.submitTask(any())).thenReturn("task-103");

        ReadinessGapService.TaskDispatch dispatch = service.dispatchResolutionTasks(List.of(
                gap("gap-5", "C", "task", "cog.cm_ready"),
                gap("gap-6", "D", "fetch", "data.check_coverage"),
                gap("gap-7", "I", "approve", "onto.caliber_defined")), null, context(), OPERATOR);

        assertEquals(List.of("task-103"), dispatch.taskIds());
        assertEquals(2, dispatch.deferred().size());
        verify(taskService, times(1)).submitTask(any());
    }

    // ── 夹具 ────────────────────────────────────────────────

    private static WAgentReadinessGapDO gap(String id, String layer, String actionType, String checkCode) {
        WAgentReadinessGapDO gap = new WAgentReadinessGapDO();
        gap.setId(id);
        gap.setAssessmentId("ra-1");
        gap.setTenantId(TENANT);
        gap.setLayer(layer);
        gap.setSeverity("blocker");
        gap.setDescriptionText(checkCode + ": fixture");
        gap.setSuggestedActionText(ReadinessGapService.encodeSuggestedAction(actionType, checkCode));
        gap.setStatus("open");
        return gap;
    }

    private static ReadinessProbeContext context() {
        return new ReadinessProbeContext("q-1", "m-1", "goal-1", "run-1", null, "FORECAST",
                TENANT, "o-1", Map.of());
    }
}
