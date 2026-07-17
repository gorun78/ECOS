package com.chinacreator.gzcm.services.agent.runtime.executor;

import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionResult;
import com.chinacreator.gzcm.services.agent.runtime.model.ExecutionTask;
import com.chinacreator.gzcm.services.agent.runtime.model.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ExecutorServiceImpl implements ExecutorService {
    private static final Logger log = LoggerFactory.getLogger(ExecutorServiceImpl.class);

    @Override
    public ExecutionResult execute(ExecutionTask task) {
        log.info("Executing task: {}", task.getId());
        task.setStatus(TaskStatus.RUNNING);
        ExecutionResult result = new ExecutionResult();
        result.setId(UUID.randomUUID().toString());
        result.setTaskId(task.getId());
        result.setSuccess(true);
        result.setOutput("Task completed: " + task.getInstruction());
        task.setStatus(TaskStatus.COMPLETED);
        task.setResult(result);
        return result;
    }

    @Override
    public ExecutionResult executeWithTools(ExecutionTask task, List<String> toolIds) {
        log.info("Executing task {} with tools: {}", task.getId(), toolIds);
        ExecutionResult result = execute(task);
        result.getMetrics().put("toolsUsed", toolIds.size());
        return result;
    }
}
