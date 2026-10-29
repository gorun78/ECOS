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
 * 文档接入解析任务解析器 — 实现 ITaskParser，将 TaskDescription 解析为 TaskExecutionPlan。
 * <p>
 * supports "KB_DOC_INGEST"（#146z 附录A 第 11 条 {@code POST /api/v1/knowledge/ingest/documents}
 * 的异步执行腿）。parse 出单个 ExecutionStep：
 * {stepType="KB_DOC_INGEST", executor="KB_DOC_INGEST", config={docId, objectKey, biz_kind}}。
 * 形态对齐 {@code KbImportTaskParser} 先例（config 与 context 双挂参数，执行器兼容读取）。
 *
 * @author ECOS KB Team
 */
@Component
public class KbDocIngestTaskParser implements ITaskParser {

    private static final Logger log = LoggerFactory.getLogger(KbDocIngestTaskParser.class);

    /** 任务类型单源（Registrar/Executor/契约 Controller 均引用本常量）。 */
    public static final String TASK_TYPE = "KB_DOC_INGEST";

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
        step.setStepId("kb-doc-ingest-step-1");
        step.setStepName("执行非结构化文档解析流水线（登记原文→解析→分块→向量化→登记 CURATED）");
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

        log.info("KbDocIngestTaskParser.parse: taskId={} docId={} objectKey={}",
                taskDescription.getTaskId(), config.get("docId"), config.get("objectKey"));
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
                    + " (expected KB_DOC_INGEST)");
        }
        Object docId = taskDescription.getParameters() == null
                ? null : taskDescription.getParameters().get("docId");
        if (docId == null || String.valueOf(docId).isBlank()) {
            throw new TaskParseException("KB_DOC_INGEST requires parameter docId");
        }
    }
}
