package com.chinacreator.gzcm.engine.kb.task;

import com.chinacreator.gzcm.engine.kb.service.KnowledgeExtractionService;
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
 * 知识抽取解析任务解析器（F04-14 收口 runtime-task）。
 * <p>
 * supports {@code KB_EXTRACT_PARSE}（即 {@link KnowledgeExtractionService#TASK_TYPE_EXTRACT_PARSE}）。
 * parse 出单个 ExecutionStep：{@code stepType=executor="KB_EXTRACT_PARSE",
 * config={extractionId}}。
 *
 * @author ECOS KB Team
 */
@Component
public class KbExtractParseTaskParser implements ITaskParser {

    private static final Logger log = LoggerFactory.getLogger(KbExtractParseTaskParser.class);
    public static final String TASK_TYPE = KnowledgeExtractionService.TASK_TYPE_EXTRACT_PARSE;

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
        step.setStepId("kb-extract-parse-step-1");
        step.setStepName("解析文档并执行 LLM 抽取");
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

        log.info("KbExtractParseTaskParser.parse: taskId={} extractionId={}",
                taskDescription.getTaskId(), config.get("extractionId"));
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
                    + " (expected " + TASK_TYPE + ")");
        }
        if (taskDescription.getParameters() == null
                || taskDescription.getParameters().get("extractionId") == null) {
            throw new TaskParseException("参数 extractionId 必填");
        }
    }
}
