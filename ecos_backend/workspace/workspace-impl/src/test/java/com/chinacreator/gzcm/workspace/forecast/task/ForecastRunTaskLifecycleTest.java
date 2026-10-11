package com.chinacreator.gzcm.workspace.forecast.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.parser.ITaskParser.TaskParseException;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunCommand;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunGateway;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunStatus;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunView;
import com.chinacreator.gzcm.workspace.forecast.observability.ForecastObservability;
import com.chinacreator.gzcm.workspace.scenario.RunMode;

/**
 * H-6 runtime-task 生命周期三段（Parser → Executor → Registrar）验收（详设-09 v1.2 §十一 H-6）。
 *
 * <p>锁三件事：①参数缺失在解析/校验期即 fail-loud（禁把残缺参数带进执行器补默认值）；
 * ②执行器只在真算成功时才回 SUCCEEDED（SPI 缺席 ⇒ TaskExecutionException，禁 mock 成功）；
 * ③取消/暂停/自报状态一律拒绝 —— run 状态真值源是 {@code td_runtime_task_status} + run 视图。</p>
 */
@DisplayName("H-6 FORECAST_RUN 任务解析/执行/注册")
class ForecastRunTaskLifecycleTest {

    private static Map<String, Object> commandParams() {
        ForecastRunCommand command = new ForecastRunCommand("sc001", "ROLLING", "cal-1", "v3",
                "2026-09-30T00:00:00", "snap-1", "f-2", "idem-1", "hash-1", RunMode.FORMAL,
                null, null, 60, 200_000L);
        return ForecastRunAsyncDispatcher.paramsOf(command);
    }

    private static TaskDescription description(Map<String, Object> params) {
        TaskDescription description = new TaskDescription();
        description.setTaskId("T-1");
        description.setTaskType(ForecastRunAsyncDispatcher.TASK_TYPE);
        Map<String, Object> wrapper = new LinkedHashMap<>();
        if (params != null) {
            wrapper.put(ForecastRunAsyncDispatcher.PARAM_COMMAND, params);
        }
        description.setParameters(wrapper);
        return description;
    }

    private static TaskExecutionPlan planOf(Map<String, Object> params) throws Exception {
        return new ForecastRunTaskParser().parse(description(params));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(bean);
        return p;
    }

    // ── Parser ──

    @Test
    @DisplayName("解析器只承接 FORECAST_RUN，展开 COMPUTE→REPORT 两步计划并透传命令")
    void parserExpandsTwoStepPlanAndCarriesCommand() throws Exception {
        ForecastRunTaskParser parser = new ForecastRunTaskParser();
        assertTrue(parser.supports("FORECAST_RUN"));
        assertFalse(parser.supports("PIPELINE"), "禁抢其它域任务类型");

        TaskExecutionPlan plan = parser.parse(description(commandParams()));
        assertEquals("T-1", plan.getTaskId());
        assertEquals(2, plan.getSteps().size());

        TaskExecutionPlan.ExecutionStep compute = plan.getSteps().get(0);
        assertEquals(ForecastRunTaskParser.STEP_COMPUTE, compute.getStepId());
        assertEquals(ForecastRunAsyncDispatcher.TASK_TYPE, compute.getExecutor());
        assertEquals(Integer.valueOf(1), compute.getOrder());
        assertEquals(Boolean.TRUE, compute.getRequired());
        assertEquals("idem-1", compute.getConfig().get("idempotencyKey"), "命令要素须原样进计划配置");

        TaskExecutionPlan.ExecutionStep report = plan.getSteps().get(1);
        assertEquals(ForecastRunTaskParser.STEP_PROGRESS, report.getStepId());
        assertEquals(List.of(ForecastRunTaskParser.STEP_COMPUTE), report.getDependencies(),
                "进度步必须依赖计算步，禁早报完成");
        assertSame(plan.getContext().get(ForecastRunAsyncDispatcher.PARAM_COMMAND), compute.getConfig(),
                "context 与步配置须同一份命令 Map，禁两处各拷一遍");
    }

    @Test
    @DisplayName("参数缺失：parse/validate 双段都拒绝（禁静默补默认值）")
    void parserRejectsMissingOrMalformedCommandParams() {
        ForecastRunTaskParser parser = new ForecastRunTaskParser();

        TaskParseException e = assertThrows(TaskParseException.class, () -> parser.parse(description(null)));
        assertTrue(e.getMessage().contains(ForecastRunAsyncDispatcher.PARAM_COMMAND), e.getMessage());

        assertThrows(TaskParseException.class, () -> parser.validate(null));
        assertThrows(TaskParseException.class, () -> parser.validate(description(null)));

        Map<String, Object> blankHash = new LinkedHashMap<>(commandParams());
        blankHash.put("contentHash", "  ");
        TaskParseException ve = assertThrows(TaskParseException.class,
                () -> parser.validate(description(blankHash)));
        assertTrue(ve.getMessage().contains("contentHash"), ve.getMessage());
    }

    // ── Executor ──

    @Test
    @DisplayName("真算成功才回 SUCCEEDED，并如实记 run 耗时与 DATA_CHECK 通过")
    void executorReportsSuccessOnlyAfterRealComputation() throws Exception {
        ForecastRunGateway gateway = mock(ForecastRunGateway.class);
        when(gateway.submit(any(ForecastRunCommand.class))).thenReturn(
                ForecastRunView.builder("fc_run_1").status(ForecastRunStatus.SUCCEEDED).build());
        ForecastObservability observability = mock(ForecastObservability.class);
        ITaskStatusCallback callback = mock(ITaskStatusCallback.class);
        ForecastRunTaskExecutor executor = new ForecastRunTaskExecutor(provider(gateway), observability);

        String result = executor.execute(planOf(commandParams()), callback);

        assertTrue(result.contains("\"status\":\"SUCCEEDED\""), result);
        assertTrue(result.contains("idem-1"), result);
        verify(gateway).submit(any(ForecastRunCommand.class));
        verify(observability).recordRunDuration(anyLong());
        verify(observability).recordDataCheck(true);
        verify(callback).onStepStart("T-1", ForecastRunTaskParser.STEP_COMPUTE, "预测计算（run 级）");
        verify(callback).onProgressUpdate(eq("T-1"), anyInt(), contains("fc_run_1"));
        verify(callback).onStepComplete(eq("T-1"), eq(ForecastRunTaskParser.STEP_PROGRESS),
                anyString(), anyBoolean(), anyString());
    }

    @Test
    @DisplayName("计算终态 FAILED ⇒ TaskExecutionException + DATA_CHECK 记失败（禁吞异常置成功）")
    void executorFailsLoudlyOnFailedRun() throws Exception {
        ForecastRunGateway gateway = mock(ForecastRunGateway.class);
        when(gateway.submit(any(ForecastRunCommand.class))).thenReturn(
                ForecastRunView.builder("fc_run_2").status(ForecastRunStatus.FAILED).build());
        ForecastObservability observability = mock(ForecastObservability.class);
        ForecastRunTaskExecutor executor = new ForecastRunTaskExecutor(provider(gateway), observability);

        ITaskExecutor.TaskExecutionException e = assertThrows(ITaskExecutor.TaskExecutionException.class,
                () -> executor.execute(planOf(commandParams()), null));
        assertTrue(e.getMessage().contains("fc_run_2"), e.getMessage());
        verify(observability).recordDataCheck(false);
    }

    @Test
    @DisplayName("RUNNING（非终态）也不算成功：底座未给终态即抛错")
    void executorTreatsNonTerminalRunAsFailure() throws Exception {
        ForecastRunGateway gateway = mock(ForecastRunGateway.class);
        when(gateway.submit(any(ForecastRunCommand.class))).thenReturn(
                ForecastRunView.builder("fc_run_3").status(ForecastRunStatus.RUNNING).build());
        ForecastObservability observability = mock(ForecastObservability.class);
        ForecastRunTaskExecutor executor = new ForecastRunTaskExecutor(provider(gateway), observability);

        assertThrows(ITaskExecutor.TaskExecutionException.class,
                () -> executor.execute(planOf(commandParams()), null));
        verify(observability).recordDataCheck(false);
    }

    @Test
    @DisplayName("SPI 未装配 ⇒ TaskExecutionException 且不产生任何计数（异步面禁 mock 成功）")
    void executorRefusesToFakeSuccessWithoutGateway() throws Exception {
        ForecastObservability observability = mock(ForecastObservability.class);
        ForecastRunGateway gateway = mock(ForecastRunGateway.class);
        ForecastRunTaskExecutor executor = new ForecastRunTaskExecutor(provider(null), observability);

        ITaskExecutor.TaskExecutionException e = assertThrows(ITaskExecutor.TaskExecutionException.class,
                () -> executor.execute(planOf(commandParams()), null));
        assertTrue(e.getMessage().contains("禁 mock 成功"), e.getMessage());
        verifyNoInteractions(observability);
        verify(gateway, never()).submit(any(ForecastRunCommand.class));
    }

    @Test
    @DisplayName("残缺计划 ⇒ TaskExecutionException（不在执行器内补默认值）")
    void executorRejectsIncompletePlanParams() {
        ForecastRunTaskExecutor executor = new ForecastRunTaskExecutor(
                provider(mock(ForecastRunGateway.class)), mock(ForecastObservability.class));
        TaskExecutionPlan bare = new TaskExecutionPlan();
        bare.setTaskId("T-3");
        bare.setSteps(List.of());
        bare.setContext(Map.of());

        ITaskExecutor.TaskExecutionException e = assertThrows(ITaskExecutor.TaskExecutionException.class,
                () -> executor.execute(bare, null));
        assertTrue(e.getMessage().contains("计划参数非法"), e.getMessage());
    }

    @Test
    @DisplayName("取消/暂停/恢复/自报状态全拒（run 只读语义 C210，状态真值源在底座）")
    void executorDoesNotOwnLifecycleOverrides() {
        ForecastRunTaskExecutor executor = new ForecastRunTaskExecutor(
                provider(mock(ForecastRunGateway.class)), mock(ForecastObservability.class));

        assertThrows(ITaskExecutor.TaskExecutionException.class, () -> executor.cancel("T-4"));
        assertThrows(ITaskExecutor.TaskExecutionException.class, () -> executor.pause("T-4"));
        assertThrows(ITaskExecutor.TaskExecutionException.class, () -> executor.resume("T-4"));
        ITaskExecutor.TaskExecutionException e = assertThrows(
                ITaskExecutor.TaskExecutionException.class, () -> executor.getStatus("T-4"));
        assertTrue(e.getMessage().contains("真值源"), e.getMessage());
    }

    // ── Registrar ──

    @Test
    @DisplayName("底座在位：executor 与 parser 双双注册到 FORECAST_RUN")
    void registrarWiresBothSides() {
        ITaskManagementService taskService = mock(ITaskManagementService.class);
        ForecastRunTaskExecutor executor = mock(ForecastRunTaskExecutor.class);
        ForecastRunTaskParser parser = new ForecastRunTaskParser();

        new ForecastRunTaskRegistrar(provider(taskService), executor, parser).register();

        verify(taskService).registerExecutor(ForecastRunAsyncDispatcher.TASK_TYPE, executor);
        verify(taskService).registerParser(ForecastRunAsyncDispatcher.TASK_TYPE, parser);
    }

    @Test
    @DisplayName("底座缺席：注册器只留痕不抛错（异步入口另行诚实 501，禁阻断启动）")
    void registrarDegradesWithoutBlockingStartup() {
        ForecastRunTaskExecutor executor = mock(ForecastRunTaskExecutor.class);
        ForecastRunTaskParser parser = mock(ForecastRunTaskParser.class);

        new ForecastRunTaskRegistrar(provider(null), executor, parser).register();

        verifyNoInteractions(executor, parser);
    }
}
