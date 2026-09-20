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
 * K1 结构化抽取任务解析器 — 实现 ITaskParser，将 TaskDescription 解析为 TaskExecutionPlan。
 * <p>
 * supports "KB_IMPORT"。parse 出单个 ExecutionStep：
 * {stepId, stepName, stepType="KB_IMPORT", executor="KB_IMPORT", config={ontologyId, mode, dryRun, jobId}}。
 * executor 字段需与 registerExecutor("KB_IMPORT", executor) 注册名一致。
 *
 * @author ECOS KB Team
 */
@Component
public class KbImportTaskParser implements ITaskParser {

    private static final Logger log = LoggerFactory.getLogger(KbImportTaskParser.class);
    public static final String TASK_TYPE = "KB_IMPORT";

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
        step.setStepId("kb-import-step-1");
        step.setStepName("执行结构化映射驱动实例抽取");
        step.setStepType(TASK_TYPE);
        step.setExecutor(TASK_TYPE);
        step.setOrder(0);
        step.setRequired(true);

        // config 携带全部参数（来自 TaskDescription.parameters）
        Map<String, Object> config = new HashMap<>();
        if (taskDescription.getParameters() != null) {
            config.putAll(taskDescription.getParameters());
        }
        step.setConfig(config);
        steps.add(step);
        plan.setSteps(steps);

        // context 透传参数，便于执行器兼容读取
        Map<String, Object> context = new HashMap<>();
        if (taskDescription.getParameters() != null) {
            context.putAll(taskDescription.getParameters());
        }
        plan.setContext(context);

        log.info("KbImportTaskParser.parse: taskId={} ontologyId={} mode={} dryRun={}",
                taskDescription.getTaskId(),
                config.get("ontologyId"), config.get("mode"), config.get("dryRun"));
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
        if (taskDescription.getTaskType() == null || taskDescription.getTaskType().isEmpty()) {
            throw new TaskParseException("Task type is required");
        }
        if (!TASK_TYPE.equalsIgnoreCase(taskDescription.getTaskType())) {
            throw new TaskParseException("Unsupported task type: " + taskDescription.getTaskType()
                    + " (expected KB_IMPORT)");
        }
    }
}
