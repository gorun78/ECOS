package com.chinacreator.gzcm.workspace.analysis.task;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.parser.ITaskParser;

/**
 * RESEARCH_RUN 任务解析器：把提交参数展开为单步 COMPUTE 计划。
 *
 * <p>解析器只做计划展开，不做计算；参数残缺在 {@link #validate} 阶段 fail-loud
 * （禁把残缺参数带进执行器静默补默认值）。</p>
 */
@Component
public class ResearchRunTaskParser implements ITaskParser {

    static final String STEP_RESEARCH = "rr_loop";

    @Override
    public TaskExecutionPlan parse(TaskDescription taskDescription) throws TaskParseException {
        Map<String, Object> params = taskDescription.getParameters();
        if (params == null || !(params.get(ResearchRunAsyncDispatcher.PARAM_BODY) instanceof Map)) {
            throw new TaskParseException("RESEARCH_RUN 任务参数缺失 "
                    + ResearchRunAsyncDispatcher.PARAM_BODY + ": taskId=" + taskDescription.getTaskId());
        }
        if (text(params.get(ResearchRunAsyncDispatcher.PARAM_SCENARIO_ID)).isBlank()) {
            throw new TaskParseException("RESEARCH_RUN 任务缺 scenarioId: taskId=" + taskDescription.getTaskId());
        }

        TaskExecutionPlan plan = new TaskExecutionPlan();
        plan.setTaskId(taskDescription.getTaskId());
        // Executor 从 plan.getContext() 取参数（与 FORECAST_RUN 同形态），故此处原样透传
        plan.setContext(params);

        TaskExecutionPlan.ExecutionStep step = new TaskExecutionPlan.ExecutionStep();
        step.setStepId(STEP_RESEARCH);
        step.setStepName("研究循环（run 级，多轮）");
        step.setStepType("COMPUTE");
        step.setExecutor(ResearchRunAsyncDispatcher.TASK_TYPE);
        step.setOrder(1);
        step.setRequired(Boolean.TRUE);
        plan.setSteps(new java.util.ArrayList<>(java.util.List.of(step)));
        return plan;
    }

    @Override
    public boolean supports(String taskType) {
        return ResearchRunAsyncDispatcher.TASK_TYPE.equals(taskType);
    }

    @Override
    public void validate(TaskDescription taskDescription) throws TaskParseException {
        parse(taskDescription);
    }

    private static String text(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
