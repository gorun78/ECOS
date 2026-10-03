package com.chinacreator.gzcm.engine.kb.task;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.parser.ITaskParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史画像生成任务解析器（F04-06 / F04-14 收口 runtime-task）。
 * <p>
 * supports {@code KB_PROFILE_GENERATE}。parse 出单个 ExecutionStep：
 * {@code {stepId, stepName, stepType="KB_PROFILE_GENERATE", executor="KB_PROFILE_GENERATE",
 * config={metricCodes, dims, windowFrom, windowTo}}}；executor 名与
 * {@code registerExecutor("KB_PROFILE_GENERATE", executor)} 一致。
 *
 * @author ECOS KB Team
 */
@Component
public class KbProfileTaskParser implements ITaskParser {

    private static final Logger log = LoggerFactory.getLogger(KbProfileTaskParser.class);
    public static final String TASK_TYPE = "KB_PROFILE_GENERATE";

    @Override
    public TaskExecutionPlan parse(TaskDescription taskDescription) throws TaskParseException {
        if (taskDescription == null) {
            throw new TaskParseException("TaskDescription cannot be null");
        }
        validate(taskDescription);
        TaskExecutionPlan plan = new TaskExecutionPlan();
        plan.setTaskId(taskDescription.getTaskId());

        List<TaskExecutionPlan.ExecutionStep> steps = new ArrayList<>();
        TaskExecutionPlan.ExecutionStep step = new TaskExecutionPlan.ExecutionStep();
        step.setStepId("kb-profile-step-1");
        step.setStepName("执行画像分布统计与退化链发布");
        step.setStepType(TASK_TYPE);
        step.setExecutor(TASK_TYPE);
        step.setOrder(0);
        step.setRequired(true);
        Map<String, Object> config = new HashMap<>();
        if (taskDescription.getParameters() != null) {
            config.putAll(taskDescription.getParameters());
        }
        step.setConfig(config);
        steps.add(step);
        plan.setSteps(steps);

        Map<String, Object> context = new HashMap<>();
        if (taskDescription.getParameters() != null) {
            context.putAll(taskDescription.getParameters());
        }
        plan.setContext(context);

        log.info("KbProfileTaskParser.parse: taskId={} metricCodes={} window={}~{}",
                taskDescription.getTaskId(), config.get("metricCodes"),
                config.get("windowFrom"), config.get("windowTo"));
        return plan;
    }

    @Override
    public boolean supports(String taskType) {
        return TASK_TYPE.equalsIgnoreCase(taskType);
    }

    @Override
    public void validate(TaskDescription taskDescription) throws TaskParseException {
        if (taskDescription == null) {
            throw new TaskParseException("TaskDescription cannot be null");
        }
        if (taskDescription.getTaskId() == null || taskDescription.getTaskId().isEmpty()) {
            throw new TaskParseException("Task ID is required");
        }
        if (taskDescription.getTaskType() == null || !TASK_TYPE.equalsIgnoreCase(taskDescription.getTaskType())) {
            throw new TaskParseException("Unsupported task type: " + taskDescription.getTaskType()
                    + " (expected KB_PROFILE_GENERATE)");
        }
    }
}
