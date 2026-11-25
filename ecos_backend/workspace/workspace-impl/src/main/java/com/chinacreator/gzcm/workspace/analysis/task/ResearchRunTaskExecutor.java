package com.chinacreator.gzcm.workspace.analysis.task;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.workspace.analysis.ScenarioResearchRunService;

/**
 * RESEARCH_RUN 任务执行器：在 workspace 命名线程内跑完整研究循环。
 *
 * <p><b>复用而非重写</b>：直接调用
 * {@link ScenarioResearchRunService#run}，与同步入口是同一条实现——
 * 预算钳制、多轮收敛、评估降级、谱系写入全部共享，禁另写一套循环。</p>
 *
 * <p><b>失败语义</b>：参数缺失或循环抛错 ⇒ 上抛，runtime-task 底座把任务落 FAILED，
 * 前端进度页真实可见；禁吞异常置 SUCCEEDED（X-44）。</p>
 */
@Component
public class ResearchRunTaskExecutor implements ITaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(ResearchRunTaskExecutor.class);

    private final ObjectProvider<ScenarioResearchRunService> serviceProvider;

    public ResearchRunTaskExecutor(ObjectProvider<ScenarioResearchRunService> serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(TaskExecutionPlan executionPlan, ITaskStatusCallback statusCallback)
            throws TaskExecutionException {
        String taskId = executionPlan.getTaskId();
        Map<String, Object> params = executionPlan.getContext() == null
                ? null : executionPlan.getContext();
        if (params == null || params.isEmpty()) {
            throw new TaskExecutionException("RESEARCH_RUN 任务缺上下文参数: taskId=" + taskId);
        }
        String scenarioId = String.valueOf(params.get(ResearchRunAsyncDispatcher.PARAM_SCENARIO_ID));
        Object body = params.get(ResearchRunAsyncDispatcher.PARAM_BODY);
        if (!(body instanceof Map)) {
            throw new TaskExecutionException("RESEARCH_RUN 任务缺 body: taskId=" + taskId);
        }

        ScenarioResearchRunService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new TaskExecutionException("ScenarioResearchRunService 未装配: taskId=" + taskId);
        }

        try {
            Map<String, Object> result = service.run(scenarioId, (Map<String, Object>) body);
            Object stop = result.get("stopReason");
            log.info("研究循环异步完成 taskId={} scenario={} stop={} rounds={}",
                    taskId, scenarioId, stop,
                    result.get("rounds") instanceof java.util.List<?> l ? l.size() : 0);
            return "stop=" + stop;
        } catch (RuntimeException e) {
            // 真实失败上抛，由底座落 FAILED；禁转成「成功但没数据」
            throw new TaskExecutionException("研究循环执行失败 taskId=" + taskId + ": " + e.getMessage());
        }
    }

    /** 研究循环为只读多轮任务，不提供执行器级取消（状态以 runtime-task 底座为真值源）。 */
    @Override
    public void cancel(String taskId) throws TaskExecutionException {
        throw new TaskExecutionException("RESEARCH_RUN 不支持执行器级取消: taskId=" + taskId);
    }

    @Override
    public void pause(String taskId) throws TaskExecutionException {
        throw new TaskExecutionException("RESEARCH_RUN 不支持暂停（轮间无持久断点）: taskId=" + taskId);
    }

    @Override
    public void resume(String taskId) throws TaskExecutionException {
        throw new TaskExecutionException("RESEARCH_RUN 不支持恢复（轮间无持久断点）: taskId=" + taskId);
    }

    /** 执行器无独立状态通道：状态以 {@code td_runtime_task_status} 为真值源，禁另立一份。 */
    @Override
    public TaskStatus getStatus(String taskId) throws TaskExecutionException {
        throw new TaskExecutionException("RESEARCH_RUN 状态以 runtime-task 底座为真值源: taskId=" + taskId);
    }
}
