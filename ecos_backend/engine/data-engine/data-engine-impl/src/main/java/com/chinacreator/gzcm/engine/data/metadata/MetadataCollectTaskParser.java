package com.chinacreator.gzcm.engine.data.metadata;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.parser.ITaskParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PMO-37 METADATA_COLLECT 任务解析器。
 * <p>
 * 将 TaskDescription(taskType=METADATA_COLLECT, parameters={datasourceId}) 解析为
 * 单步 TaskExecutionPlan，executor 名 = "METADATA_COLLECT"。
 *
 * @author DataBridge Datanet Team
 */
@Component
public class MetadataCollectTaskParser implements ITaskParser {

    private static final Logger log = LoggerFactory.getLogger(MetadataCollectTaskParser.class);
    public static final String TASK_TYPE = "METADATA_COLLECT";

    @Override
    public TaskExecutionPlan parse(TaskDescription taskDescription) throws TaskParseException {
        if (taskDescription == null) {
            throw new TaskParseException("TaskDescription cannot be null");
        }
        validate(taskDescription);

        TaskExecutionPlan plan = new TaskExecutionPlan();
        plan.setTaskId(taskDescription.getTaskId());

        TaskExecutionPlan.ExecutionStep step = new TaskExecutionPlan.ExecutionStep();
        step.setStepId("metadata-collect-1");
        step.setStepName("采集元数据: " + taskDescription.getTaskName());
        step.setStepType(TASK_TYPE);
        step.setExecutor(TASK_TYPE);
        step.setOrder(0);
        step.setRequired(true);

        Map<String, Object> config = new HashMap<>();
        if (taskDescription.getParameters() != null) {
            config.putAll(taskDescription.getParameters());
        }
        step.setConfig(config);

        List<TaskExecutionPlan.ExecutionStep> steps = Arrays.asList(step);
        plan.setSteps(steps);
        plan.setContext(config);

        log.info("MetadataCollectTaskParser.parse: taskId={}, datasourceId={}",
                taskDescription.getTaskId(), config.get("datasourceId"));
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
        if (!TASK_TYPE.equalsIgnoreCase(taskDescription.getTaskType())) {
            throw new TaskParseException("Unsupported task type: " + taskDescription.getTaskType());
        }
        Map<String, Object> params = taskDescription.getParameters();
        if (params == null || params.get("datasourceId") == null
                || params.get("datasourceId").toString().isEmpty()) {
            throw new TaskParseException("datasourceId is required in parameters");
        }
    }
}
