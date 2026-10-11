package com.chinacreator.gzcm.workspace.forecast.task;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.parser.ITaskParser;

/**
 * FORECAST_RUN 任务解析器（H-6，形态对齐 data-engine {@code PipelineTaskParser} 先例）：
 * 把 {@code submitTask} 落库的任务描述解析为单步执行计划（COMPUTE 步 → executor=FORECAST_RUN）。
 *
 * <p>解析器只做计划展开，不做计算；命令要素缺失在 {@link #validate} 阶段即 fail-loud
 * （禁把残缺参数带进执行器静默补默认值）。</p>
 */
@Component
public class ForecastRunTaskParser implements ITaskParser {

    static final String STEP_COMPUTE = "fc_compute";
    static final String STEP_PROGRESS = "fc_progress";

    @Override
    public TaskExecutionPlan parse(TaskDescription taskDescription) throws TaskParseException {
        Object params = taskDescription.getParameters() == null
                ? null : taskDescription.getParameters().get(ForecastRunAsyncDispatcher.PARAM_COMMAND);
        if (!(params instanceof Map<?, ?> raw)) {
            throw new TaskParseException("FORECAST_RUN 任务参数缺失 "
                    + ForecastRunAsyncDispatcher.PARAM_COMMAND + ": taskId=" + taskDescription.getTaskId());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> commandParams = (Map<String, Object>) raw;

        TaskExecutionPlan plan = new TaskExecutionPlan();
        plan.setTaskId(taskDescription.getTaskId());

        TaskExecutionPlan.ExecutionStep compute = new TaskExecutionPlan.ExecutionStep();
        compute.setStepId(STEP_COMPUTE);
        compute.setStepName("预测计算（run 级）");
        compute.setStepType("COMPUTE");
        compute.setExecutor(ForecastRunAsyncDispatcher.TASK_TYPE);
        compute.setOrder(1);
        compute.setRequired(Boolean.TRUE);
        compute.setConfig(commandParams);

        TaskExecutionPlan.ExecutionStep progress = new TaskExecutionPlan.ExecutionStep();
        progress.setStepId(STEP_PROGRESS);
        progress.setStepName("进度回写（td_runtime_task_status 消费面）");
        progress.setStepType("REPORT");
        progress.setExecutor(ForecastRunAsyncDispatcher.TASK_TYPE);
        progress.setOrder(2);
        progress.setRequired(Boolean.TRUE);
        progress.setConfig(Map.of("forecastRunId", nullSafe(commandParams.get("idempotencyKey"))));
        progress.setDependencies(List.of(STEP_COMPUTE));

        plan.setSteps(List.of(compute, progress));
        plan.setContext(Map.of(ForecastRunAsyncDispatcher.PARAM_COMMAND, commandParams));
        return plan;
    }

    @Override
    public boolean supports(String taskType) {
        return ForecastRunAsyncDispatcher.TASK_TYPE.equals(taskType);
    }

    @Override
    public void validate(TaskDescription taskDescription) throws TaskParseException {
        if (taskDescription == null || taskDescription.getParameters() == null) {
            throw new TaskParseException("FORECAST_RUN 任务描述/参数为空");
        }
        Object params = taskDescription.getParameters().get(ForecastRunAsyncDispatcher.PARAM_COMMAND);
        if (!(params instanceof Map<?, ?> m)) {
            throw new TaskParseException("FORECAST_RUN 参数须为 "
                    + ForecastRunAsyncDispatcher.PARAM_COMMAND + " 命令 Map");
        }
        // 六要素 + 幂等对缺失即拒（与 commandFrom 同一 fail-loud 口径，禁静默默认）
        try {
            ForecastRunAsyncDispatcher.commandFrom((Map<String, Object>) m);
        } catch (IllegalArgumentException e) {
            throw new TaskParseException(e.getMessage(), e);
        }
    }

    private static String nullSafe(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
