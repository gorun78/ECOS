package com.chinacreator.gzcm.workspace.analysis.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;

/**
 * L3 研究循环异步派发器（可行性报告 v2.0 §5.2 规划的异步形态，2026-11-25）。
 *
 * <p><b>底座收口（铁律 §2.5）</b>：异步任务唯一通道 = runtime-task
 * {@link ITaskManagementService#submitTask}，返回真实 taskId；<b>禁自建任务表、禁自造 jobId</b>。
 * 门面未装配 ⇒ 抛 {@link ResearchAsyncUnavailableException}（入口转 501），
 * 绝不降级成同步冒充成功。</p>
 *
 * <p><b>为什么要异步</b>：一轮研究可能跑满 {@code maxLoops} 次只读探针加知识检索，
 * 同步 HTTP 容易触到网关/反代超时；异步后前端拿 taskId 轮询进度，谱系逐步长出。</p>
 *
 * <p><b>执行载体</b>：{@code executeTask(taskId)} 必须提交到 workspace 命名执行器
 * {@code workspaceTaskExecutor}（{@code WorkspaceTaskExecutorConfig}）——
 * 禁裸线程、禁 common pool（{@code RuntimeConvergenceArchTest} 门禁）。</p>
 *
 * <p><b>与同步路径的关系</b>：Executor 侧直接复用
 * {@link com.chinacreator.gzcm.workspace.analysis.ScenarioResearchRunService#run}，
 * 循环语义、预算钳制、评估降级、谱系写法<b>完全同一条</b>（禁双轨实现）。</p>
 */
@Component
public class ResearchRunAsyncDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ResearchRunAsyncDispatcher.class);

    /** runtime-task 任务类型字面量（Parser/Executor/Registrar 与本类共享，单源）。 */
    public static final String TASK_TYPE = "RESEARCH_RUN";

    /** 任务参数键：场景 id。 */
    public static final String PARAM_SCENARIO_ID = "scenarioId";
    /** 任务参数键：请求体原文（question/hypotheses/budget/branchKey 等）。 */
    public static final String PARAM_BODY = "researchRunBody";

    private final ObjectProvider<ITaskManagementService> taskManagementProvider;
    private final TaskExecutor workspaceTaskExecutor;

    public ResearchRunAsyncDispatcher(ObjectProvider<ITaskManagementService> taskManagementProvider,
                                      @Qualifier("workspaceTaskExecutor") TaskExecutor workspaceTaskExecutor) {
        this.taskManagementProvider = taskManagementProvider;
        this.workspaceTaskExecutor = workspaceTaskExecutor;
    }

    /**
     * 派发一次异步研究。
     *
     * @return runtime-task 真实 taskId（前端轮询用）
     * @throws ResearchAsyncUnavailableException 门面未装配或提交失败（入口转 501，禁假异步）
     */
    public String dispatch(String scenarioId, java.util.Map<String, Object> body) {
        ITaskManagementService taskService = taskManagementProvider.getIfAvailable();
        if (taskService == null) {
            throw new ResearchAsyncUnavailableException(
                    "ITaskManagementService 未装配（runtime-task 底座不在 classpath 或条件未满足），"
                            + "异步研究不可用——禁自造 taskId 假异步");
        }

        TaskDescription description = new TaskDescription();
        description.setTaskType(TASK_TYPE);
        description.setTaskName("场景研究循环 scenario=" + scenarioId);
        java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put(PARAM_SCENARIO_ID, scenarioId);
        params.put(PARAM_BODY, body);
        description.setParameters(params);
        description.setAsync(true);

        String taskId;
        try {
            taskId = taskService.submitTask(description);
        } catch (ITaskManagementService.TaskManagementException e) {
            // 依赖故障 fail-loud：不降级为同步、不自造 taskId
            throw new ResearchAsyncUnavailableException("runtime-task submitTask 失败: " + e.getMessage());
        }

        final String asyncTaskId = taskId;
        workspaceTaskExecutor.execute(() -> {
            try {
                taskService.executeTask(asyncTaskId);
            } catch (Exception e) {
                log.warn("研究循环异步执行失败 taskId={} scenario={} reason={}",
                        asyncTaskId, scenarioId, e.getMessage());
            }
        });
        log.info("研究循环已异步派发 scenario={} taskId={}", scenarioId, taskId);
        return taskId;
    }

    /** 异步入口不可用（runtime-task 未装配）：入口层据此回 501，禁静默降级。 */
    public static class ResearchAsyncUnavailableException extends RuntimeException {
        public ResearchAsyncUnavailableException(String message) {
            super(message);
        }
    }
}
