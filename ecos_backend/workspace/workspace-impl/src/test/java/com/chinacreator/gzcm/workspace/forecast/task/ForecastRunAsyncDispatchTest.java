package com.chinacreator.gzcm.workspace.forecast.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunCommand;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunNotLiveException;
import com.chinacreator.gzcm.workspace.scenario.RunMode;

/**
 * H-6 异步派发验收（详设-09 v1.2 §十一 / PRD-09 §七-1）：阈值分流 + runtime-task 真实 taskId +
 * 底座未装配/提交故障的诚实 501（禁自造 jobId 假异步，X-44/X-50）。
 */
@DisplayName("H-6 预测 run 异步派发（runtime-task 唯一通道）")
class ForecastRunAsyncDispatchTest {

    /** 记录型执行器：只收集 Runnable，不真起线程（断言派发落点而非并发行为）。 */
    private static final class RecordingExecutor implements TaskExecutor {
        final List<Runnable> queued = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            queued.add(task);
        }
    }

    private static ForecastRunCommand command(int projectCount, long rowEstimate) {
        return new ForecastRunCommand("sc001", "ROLLING", "cal-1", "v3", "2026-09-30T00:00:00",
                "snap-1", "f-2", "idem-1", "hash-1", RunMode.FORMAL, null, null, projectCount, rowEstimate);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(bean);
        return p;
    }

    @Test
    @DisplayName("阈值边界：>50 项目 或 >10 万行才转异步（PRD 原文严格大于）")
    void asyncThresholdIsStrictlyGreaterThanPrdNumbers() {
        ForecastRunAsyncDispatcher dispatcher =
                new ForecastRunAsyncDispatcher(provider(mock(ITaskManagementService.class)), new RecordingExecutor());

        assertTrue(dispatcher.isAsyncEligible(command(51, 0L)), "51 项目应转异步");
        assertFalse(dispatcher.isAsyncEligible(command(50, 0L)), "50 项目=阈值下，应同步");
        assertTrue(dispatcher.isAsyncEligible(command(0, 100_001L)), "10 万 1 行应转异步");
        assertFalse(dispatcher.isAsyncEligible(command(0, 100_000L)), "10 万行=阈值下，应同步");
        assertFalse(dispatcher.isAsyncEligible(command(0, 0L)), "缺省 0/0（同步语义）不应误判异步");
    }

    @Test
    @DisplayName("派发返回底座真实 taskId，并把 executeTask 提交到命名执行器")
    void dispatchUsesRealTaskIdAndNamedExecutor() throws Exception {
        ITaskManagementService taskService = mock(ITaskManagementService.class);
        when(taskService.submitTask(any(TaskDescription.class))).thenReturn("T-REAL-77");
        RecordingExecutor executor = new RecordingExecutor();
        ForecastRunAsyncDispatcher dispatcher =
                new ForecastRunAsyncDispatcher(provider(taskService), executor);

        String taskId = dispatcher.dispatch(command(60, 0L));

        assertEquals("T-REAL-77", taskId, "taskId 必须是 submitTask 返回值，禁自造");
        ArgumentCaptor<TaskDescription> captor = ArgumentCaptor.forClass(TaskDescription.class);
        verify(taskService).submitTask(captor.capture());
        TaskDescription description = captor.getValue();
        assertEquals(ForecastRunAsyncDispatcher.TASK_TYPE, description.getTaskType());
        assertEquals(Boolean.TRUE, description.getAsync());
        assertEquals("forecast-run:sc001", description.getTaskName());
        // taskId 留空由底座分配（与认知域复审路由同一纪律）
        assertEquals(null, description.getTaskId());
        assertTrue(description.getParameters().containsKey(ForecastRunAsyncDispatcher.PARAM_COMMAND));

        assertEquals(1, executor.queued.size(), "执行派发只能经命名执行器，禁裸线程");
        executor.queued.get(0).run();
        verify(taskService).executeTask("T-REAL-77");
    }

    @Test
    @DisplayName("命名执行器内 executeTask 抛错不外溢（异步线程异常由底座状态承接，禁崩派发线程）")
    void executeFailureInsideNamedExecutorDoesNotEscape() throws Exception {
        ITaskManagementService taskService = mock(ITaskManagementService.class);
        when(taskService.submitTask(any(TaskDescription.class))).thenReturn("T-2");
        when(taskService.executeTask("T-2")).thenThrow(new RuntimeException("engine down"));
        RecordingExecutor executor = new RecordingExecutor();
        ForecastRunAsyncDispatcher dispatcher =
                new ForecastRunAsyncDispatcher(provider(taskService), executor);

        dispatcher.dispatch(command(60, 0L));
        executor.queued.get(0).run();   // 不抛即通过；抛错则本断言失败

        verify(taskService).executeTask("T-2");
    }

    @Test
    @DisplayName("门面未装配 ⇒ 501 未上线，禁假异步且禁提交执行器")
    void unassembledFacadeIsHonestNotLive() {
        RecordingExecutor executor = new RecordingExecutor();
        ForecastRunAsyncDispatcher dispatcher =
                new ForecastRunAsyncDispatcher(provider(null), executor);

        ForecastRunNotLiveException e =
                assertThrows(ForecastRunNotLiveException.class, () -> dispatcher.dispatch(command(999, 0L)));
        assertTrue(e.getMessage().contains("该能力尚未上线"), e.getMessage());
        assertTrue(e.getTechnicalReason().contains("未装配"), e.getTechnicalReason());
        assertTrue(executor.queued.isEmpty(), "未上线时禁把计算塞进执行器冒充异步");
    }

    @Test
    @DisplayName("submitTask 故障 ⇒ 501 fail-loud，禁降级自造 taskId")
    void submitFailureNeverFabricatesTaskId() throws Exception {
        ITaskManagementService taskService = mock(ITaskManagementService.class);
        when(taskService.submitTask(any(TaskDescription.class)))
                .thenThrow(new ITaskManagementService.TaskManagementException("runtime-task unavailable"));
        RecordingExecutor executor = new RecordingExecutor();
        ForecastRunAsyncDispatcher dispatcher =
                new ForecastRunAsyncDispatcher(provider(taskService), executor);

        ForecastRunNotLiveException e =
                assertThrows(ForecastRunNotLiveException.class, () -> dispatcher.dispatch(command(60, 0L)));
        assertTrue(e.getTechnicalReason().contains("submitTask 失败"), e.getTechnicalReason());
        assertTrue(executor.queued.isEmpty());
    }

    @Test
    @DisplayName("命令 ⇄ 任务参数往返：要素无损，缺失即 fail-loud（禁默认值吞掉）")
    void commandParamsRoundTripLosslesslyAndRejectMissing() {
        ForecastRunCommand original = command(60, 123_456L);
        Map<String, Object> params = ForecastRunAsyncDispatcher.paramsOf(original);

        ForecastRunCommand restored = ForecastRunAsyncDispatcher.commandFrom(params);
        assertEquals(original.getScenarioId(), restored.getScenarioId());
        assertEquals(original.getCaliberId(), restored.getCaliberId());
        assertEquals(original.getCaliberVersion(), restored.getCaliberVersion());
        assertEquals(original.getAsOfTime(), restored.getAsOfTime());
        assertEquals(original.getSnapshotId(), restored.getSnapshotId());
        assertEquals(original.getFormulaVersion(), restored.getFormulaVersion());
        assertEquals(original.getIdempotencyKey(), restored.getIdempotencyKey());
        assertEquals(original.getContentHash(), restored.getContentHash());
        assertEquals(RunMode.FORMAL, restored.getRunMode());
        assertEquals(60, restored.getProjectCount());
        assertEquals(123_456L, restored.getRowEstimate());

        Map<String, Object> broken = new java.util.LinkedHashMap<>(params);
        broken.remove("snapshotId");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ForecastRunAsyncDispatcher.commandFrom(broken));
        assertTrue(e.getMessage().contains("snapshotId"), e.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> ForecastRunAsyncDispatcher.commandFrom(null));
    }

    @Test
    @DisplayName("taskDescriptionOf 的 taskType 字面量单源（Parser/Executor/Registrar 共用）")
    void taskTypeLiteralIsSingleSourced() {
        assertEquals("FORECAST_RUN", ForecastRunAsyncDispatcher.TASK_TYPE);
        assertTrue(new ForecastRunTaskParser().supports(ForecastRunAsyncDispatcher.TASK_TYPE),
                "解析器必须承接派发器写出的 taskType，两处禁各写一份字面量");
    }
}
