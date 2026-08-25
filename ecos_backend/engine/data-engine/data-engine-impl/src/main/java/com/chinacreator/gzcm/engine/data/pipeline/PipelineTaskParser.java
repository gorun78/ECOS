package com.chinacreator.gzcm.engine.data.pipeline;

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
 * Pipeline 任务解析器 — 实现 ITaskParser，将 TaskDescription 解析为 TaskExecutionPlan。
 * <p>
 * supports "PIPELINE"。parse 出单个 ExecutionStep：
 * {stepId, stepName, stepType="PIPELINE", executor="PIPELINE", config={definitionId}}。
 * executor 字段需与 registerExecutor("PIPELINE", pipelineTaskExecutor) 注册名一致，
 * TaskManagementServiceImpl.findExecutor() 据 step.getExecutor() 查找执行器。
 *
 * @author DataBridge Datanet Team
 */
@Component
public class PipelineTaskParser implements ITaskParser {

    private static final Logger log = LoggerFactory.getLogger(PipelineTaskParser.class);
    private static final String PIPELINE = "PIPELINE";

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
        step.setStepId("pipeline-step-1");
        step.setStepName("Execute Pipeline: " + taskDescription.getTaskName());
        step.setStepType(PIPELINE);
        step.setExecutor(PIPELINE);
        step.setOrder(0);
        step.setRequired(true);

        // config 携带 definitionId（来自 TaskDescription.parameters）
        Map<String, Object> config = new HashMap<>();
        if (taskDescription.getParameters() != null) {
            config.putAll(taskDescription.getParameters());
        }
        step.setConfig(config);
        steps.add(step);
        plan.setSteps(steps);

        // context 透传 parameters，便于执行器兼容读取
        Map<String, Object> context = new HashMap<>();
        if (taskDescription.getParameters() != null) {
            context.putAll(taskDescription.getParameters());
        }
        plan.setContext(context);

        log.info("PipelineTaskParser.parse: taskId={}, definitionId={}",
                taskDescription.getTaskId(), config.get("definitionId"));
        return plan;
    }

    @Override
    public boolean supports(String taskType) {
        return PIPELINE.equalsIgnoreCase(taskType);
    }

    @Override
    public void validate(TaskDescription taskDescription) throws TaskParseException {
        if (taskDescription == null) {
            throw new TaskParseException("TaskDescription cannot be null");
        }
        if (taskDescription.getTaskId() == null || taskDescription.getTaskId().isEmpty()) {
            throw new TaskParseException("Task ID is required");
        }
        if (taskDescription.getTaskType() == null || taskDescription.getTaskType().isEmpty()) {
            throw new TaskParseException("Task type is required");
        }
        if (!PIPELINE.equalsIgnoreCase(taskDescription.getTaskType())) {
            throw new TaskParseException("Unsupported task type: " + taskDescription.getTaskType());
        }
        if (taskDescription.getParameters() == null
                || taskDescription.getParameters().get("definitionId") == null
                || taskDescription.getParameters().get("definitionId").toString().isEmpty()) {
            throw new TaskParseException("definitionId is required in parameters");
        }
    }
}
