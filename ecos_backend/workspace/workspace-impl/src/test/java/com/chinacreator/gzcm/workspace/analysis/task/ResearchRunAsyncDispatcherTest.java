package com.chinacreator.gzcm.workspace.analysis.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.chinacreator.gzcm.workspace.analysis.ScenarioResearchRunService;

/**
 * L3 研究异步派发单测（2026-11-25，报告 §5.2 异步形态）。
 *
 * <p>覆盖三条不能让步的语义：①底座未装配 ⇒ 501 语义异常，<b>禁</b>降级同步/自造 taskId；
 * ②提交失败 ⇒ fail-loud，不自造；③成功 ⇒ 返回底座真实 taskId 且把 executeTask
 * 交给 workspace 命名执行器（禁裸线程）。</p>
 */
class ResearchRunAsyncDispatcherTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ITaskManagementService> providerOf(ITaskManagementService svc) {
        ObjectProvider<ITaskManagementService> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(svc);
        return p;
    }

    private static TaskExecutor directExecutor() {
        return Runnable::run;
    }

    @Test
    @DisplayName("底座未装配 ⇒ 抛不可用异常，禁降级同步或自造 taskId")
    void unavailableWhenTaskServiceMissing() {
        ResearchRunAsyncDispatcher d = new ResearchRunAsyncDispatcher(
                providerOf(null), directExecutor());
        ResearchRunAsyncDispatcher.ResearchAsyncUnavailableException e = assertThrows(
                ResearchRunAsyncDispatcher.ResearchAsyncUnavailableException.class,
                () -> d.dispatch("sc-1", Map.of("question", "q")));
        assertTrue(String.valueOf(e.getMessage()).contains("未装配"), e.getMessage());
    }

    @Test
    @DisplayName("submitTask 失败 ⇒ fail-loud 上抛，不自造 taskId")
    void submitFailureIsLoud() throws Exception {
        ITaskManagementService svc = mock(ITaskManagementService.class);
        when(svc.submitTask(any())).thenThrow(
                new ITaskManagementService.TaskManagementException("db down"));
        ResearchRunAsyncDispatcher d = new ResearchRunAsyncDispatcher(
                providerOf(svc), directExecutor());
        assertThrows(ResearchRunAsyncDispatcher.ResearchAsyncUnavailableException.class,
                () -> d.dispatch("sc-1", Map.of("question", "q")));
    }

    @Test
    @DisplayName("成功 ⇒ 返回底座真实 taskId，并经命名执行器触发 executeTask")
    void successReturnsRealTaskIdAndExecutesViaNamedExecutor() throws Exception {
        ITaskManagementService svc = mock(ITaskManagementService.class);
        when(svc.submitTask(any())).thenReturn("task-real-001");
        ResearchRunAsyncDispatcher d = new ResearchRunAsyncDispatcher(
                providerOf(svc), directExecutor());

        String taskId = d.dispatch("sc-1", Map.of("question", "q"));

        assertEquals("task-real-001", taskId);
        // 命名执行器（此处用直跑替身）确实触发了底座执行，而非自造线程
        org.mockito.Mockito.verify(svc).executeTask("task-real-001");
    }

    @Test
    @DisplayName("解析器：缺 body 或 scenarioId ⇒ fail-loud；正常参数展开单步 COMPUTE 并透传上下文")
    void parserValidatesAndExpandsPlan() throws Exception {
        ResearchRunTaskParser parser = new ResearchRunTaskParser();

        TaskDescription missing = new TaskDescription();
        missing.setTaskId("t1");
        assertThrows(ResearchRunTaskParser.TaskParseException.class, () -> parser.parse(missing));

        Map<String, Object> params = new LinkedHashMap<>();
        TaskDescription noScenario = new TaskDescription();
        noScenario.setTaskId("t2");
        noScenario.setParameters(Map.of(ResearchRunAsyncDispatcher.PARAM_BODY, Map.of("question", "q")));
        assertThrows(ResearchRunTaskParser.TaskParseException.class, () -> parser.parse(noScenario));

        TaskDescription ok = new TaskDescription();
        ok.setTaskId("t3");
        Map<String, Object> body = Map.of("question", "q");
        params.put(ResearchRunAsyncDispatcher.PARAM_SCENARIO_ID, "sc-1");
        params.put(ResearchRunAsyncDispatcher.PARAM_BODY, body);
        ok.setParameters(params);

        TaskExecutionPlan plan = parser.parse(ok);
        assertEquals("t3", plan.getTaskId());
        assertEquals(1, plan.getSteps().size());
        assertEquals(ResearchRunAsyncDispatcher.TASK_TYPE, plan.getSteps().get(0).getExecutor());
        // Executor 侧从 context 取参数，故解析器必须透传
        assertEquals("sc-1", plan.getContext().get(ResearchRunAsyncDispatcher.PARAM_SCENARIO_ID));
        assertTrue(parser.supports(ResearchRunAsyncDispatcher.TASK_TYPE));
        assertTrue(!parser.supports("FORECAST_RUN"));
    }

    @Test
    @DisplayName("执行器：循环复用同步实现（禁双轨），失败上抛不吞成成功")
    void executorReusesSyncServiceAndPropagatesFailure() throws Exception {
        ScenarioResearchRunService service = mock(ScenarioResearchRunService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ScenarioResearchRunService> sp = mock(ObjectProvider.class);
        when(sp.getIfAvailable()).thenReturn(service);
        when(service.run(any(), any())).thenReturn(Map.of("stopReason", "ACCEPTED", "rounds", List.of()));

        ResearchRunTaskExecutor executor = new ResearchRunTaskExecutor(sp);
        TaskExecutionPlan plan = new TaskExecutionPlan();
        plan.setTaskId("t9");
        plan.setContext(Map.of(
                ResearchRunAsyncDispatcher.PARAM_SCENARIO_ID, "sc-1",
                ResearchRunAsyncDispatcher.PARAM_BODY, Map.of("question", "q")));

        String out = executor.execute(plan, null);
        assertTrue(out.contains("ACCEPTED"), out);

        when(service.run(any(), any())).thenThrow(new IllegalStateException("boom"));
        assertThrows(ResearchRunTaskExecutor.TaskExecutionException.class,
                () -> executor.execute(plan, null));
    }
}
