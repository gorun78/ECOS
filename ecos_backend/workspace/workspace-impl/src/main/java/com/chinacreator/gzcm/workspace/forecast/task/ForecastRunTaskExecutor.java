package com.chinacreator.gzcm.workspace.forecast.task;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunCommand;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunGateway;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunView;
import com.chinacreator.gzcm.workspace.forecast.observability.ForecastObservability;

/**
 * FORECAST_RUN 任务执行器（H-6，形态对齐 data-engine {@code PipelineTaskExecutor} 先例）。
 *
 * <p>在 {@code workspaceTaskExecutor} 命名线程内（由 {@code ForecastRunAsyncDispatcher} 经
 * {@code ITaskManagementService.executeTask} 触发）执行真实计算：从计划上下文还原
 * {@link ForecastRunCommand} → 经 {@link ForecastRunGateway#submit} 走与同步路径<b>同一条</b>
 * 幂等/落库通道（占位 run 由 submitDeferred 先行落位，同 idempotencyKey 回链，禁重复落新行）。</p>
 *
 * <p><b>失败语义</b>：SPI 未装配 / 计算抛错 ⇒ {@link TaskExecutionException} 上抛，
 * runtime-task 底座把状态落 {@code FAILED}（{@code td_runtime_task_status}），
 * 前端进度页真实可见——禁吞异常置 SUCCEEDED（X-44）。</p>
 */
@Component
public class ForecastRunTaskExecutor implements ITaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(ForecastRunTaskExecutor.class);

    private final ObjectProvider<ForecastRunGateway> gatewayProvider;
    private final ForecastObservability observability;

    public ForecastRunTaskExecutor(ObjectProvider<ForecastRunGateway> gatewayProvider,
                                   ForecastObservability observability) {
        this.gatewayProvider = gatewayProvider;
        this.observability = observability;
    }

    @Override
    public String execute(TaskExecutionPlan executionPlan, ITaskStatusCallback statusCallback)
            throws TaskExecutionException {
        String taskId = executionPlan.getTaskId();
        Map<String, Object> commandParams = commandParamsOf(executionPlan);
        ForecastRunCommand command;
        try {
            command = ForecastRunAsyncDispatcher.commandFrom(commandParams);
        } catch (IllegalArgumentException e) {
            throw new TaskExecutionException("FORECAST_RUN 计划参数非法: " + e.getMessage(), e);
        }

        ForecastRunGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) {
            throw new TaskExecutionException("ForecastRunGateway 未装配，异步 run 无法真算"
                    + "（禁 mock 成功）: taskId=" + taskId);
        }

        long startedAt = System.currentTimeMillis();
        for (TaskExecutionPlan.ExecutionStep step : stepsOf(executionPlan)) {
            if (statusCallback != null) {
                statusCallback.onStepStart(taskId, step.getStepId(), step.getStepName());
            }
            if (ForecastRunTaskParser.STEP_COMPUTE.equals(step.getStepId())) {
                ForecastRunView view = gateway.submit(command);
                boolean passed = view != null && view.getStatus() != null && view.getStatus().isTerminal()
                        && view.getStatus() != com.chinacreator.gzcm.workspace.forecast.ForecastRunStatus.FAILED;
                // H-6 监控记录缝（台账①②）：run 真实耗时 + 终态成败（诚实计数，非造数）
                observability.recordRunDuration(System.currentTimeMillis() - startedAt);
                observability.recordDataCheck(passed);
                if (!passed) {
                    throw new TaskExecutionException("FORECAST_RUN 计算终态失败: runId="
                            + (view == null ? "?" : view.getForecastRunId()));
                }
                if (statusCallback != null) {
                    statusCallback.onProgressUpdate(taskId, 90,
                            "预测计算完成 runId=" + view.getForecastRunId());
                }
                if (statusCallback != null) {
                    statusCallback.onStepComplete(taskId, step.getStepId(), step.getStepName(), true,
                            "runId=" + view.getForecastRunId());
                }
            } else {
                // REPORT 步：进度消费面（td_runtime_task_status 由底座维护），此处仅回执
                if (statusCallback != null) {
                    statusCallback.onStepComplete(taskId, step.getStepId(), step.getStepName(), true, "ok");
                }
            }
        }
        return "{\"taskId\":\"" + taskId + "\",\"idempotencyKey\":\""
                + command.getIdempotencyKey() + "\",\"status\":\"SUCCEEDED\"}";
    }

    @Override
    public void cancel(String taskId) throws TaskExecutionException {
        throw new TaskExecutionException("FORECAST_RUN 不支持执行器级取消（run 只读语义，C210）: taskId=" + taskId);
    }

    @Override
    public void pause(String taskId) throws TaskExecutionException {
        throw new TaskExecutionException("FORECAST_RUN 不支持暂停（单步计算任务）: taskId=" + taskId);
    }

    @Override
    public void resume(String taskId) throws TaskExecutionException {
        throw new TaskExecutionException("FORECAST_RUN 不支持恢复（单步计算任务）: taskId=" + taskId);
    }

    /** 执行器无独立状态通道：run/任务状态以 {@code td_runtime_task_status} + run 视图为真值源。 */
    @Override
    public TaskStatus getStatus(String taskId) throws TaskExecutionException {
        throw new TaskExecutionException("FORECAST_RUN 状态以 runtime-task 底座为真值源，执行器不另立状态: taskId=" + taskId);
    }

    private static Map<String, Object> commandParamsOf(TaskExecutionPlan plan) {
        Object direct = plan.getContext() == null
                ? null : plan.getContext().get(ForecastRunAsyncDispatcher.PARAM_COMMAND);
        if (direct instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) m;
            return cast;
        }
        // 兜底：取 COMPUTE 步 config（Parser 单源写入同一命令 Map）
        for (TaskExecutionPlan.ExecutionStep step : stepsOf(plan)) {
            if (ForecastRunTaskParser.STEP_COMPUTE.equals(step.getStepId()) && step.getConfig() != null) {
                return step.getConfig();
            }
        }
        return Map.of();
    }

    private static List<TaskExecutionPlan.ExecutionStep> stepsOf(TaskExecutionPlan plan) {
        return plan.getSteps() == null ? List.of() : plan.getSteps();
    }
}
