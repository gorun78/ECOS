package com.chinacreator.gzcm.runtime.core.task.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;

/**
 * 默认任务解析器
 * 提供基础的任务解析功能，支持常见任务类型
 */
public class DefaultTaskParser implements ITaskParser {

    private static final String[] SUPPORTED_TYPES = {
        "DATA_TRANSFER",
        "DATA_SYNC",
        "DATA_EXPORT",
        "DATA_IMPORT",
        "DEFAULT"
    };

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
        step.setStepId("step-1");
        step.setStepName("执行" + taskDescription.getTaskType() + "任务");
        step.setStepType(taskDescription.getTaskType());
        step.setExecutor("DefaultTaskExecutor");
        
        Map<String, Object> config = new HashMap<>();
        if (taskDescription.getParameters() != null) {
            config.putAll(taskDescription.getParameters());
        }
        step.setConfig(config);
        
        steps.add(step);
        plan.setSteps(steps);
        
        return plan;
    }

    @Override
    public boolean supports(String taskType) {
        if (taskType == null) {
            return false;
        }
        for (String type : SUPPORTED_TYPES) {
            if (type.equalsIgnoreCase(taskType)) {
                return true;
            }
        }
        return false;
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
    }
}
