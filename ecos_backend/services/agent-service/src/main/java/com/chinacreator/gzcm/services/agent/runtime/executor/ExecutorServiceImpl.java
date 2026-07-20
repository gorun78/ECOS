package com.chinacreator.gzcm.services.agent.runtime.executor;

import com.chinacreator.gzcm.services.agent.runtime.memory.MemoryService;
import com.chinacreator.gzcm.services.agent.runtime.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ExecutorServiceImpl implements ExecutorService {
    private static final Logger log = LoggerFactory.getLogger(ExecutorServiceImpl.class);

    private final MemoryService memoryService;

    public ExecutorServiceImpl(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public ExecutionResult execute(ExecutionTask task) {
        log.info("Executing task: {}", task.getId());

        MemoryContext ctx = memoryService.buildContext(task.getAgentId(), task.getId());
        log.debug("Loaded memory context for agent: {}, working={}, session={}, longTerm={}, enterprise={}",
            task.getAgentId(),
            ctx.getWorkingMemory().size(),
            ctx.getSessionMemory().size(),
            ctx.getLongTermMemory().size(),
            ctx.getEnterpriseMemory().size());

        task.setStatus(TaskStatus.RUNNING);
        ExecutionResult result = new ExecutionResult();
        result.setId(UUID.randomUUID().toString());
        result.setTaskId(task.getId());
        result.setSuccess(true);
        result.setOutput("Task completed: " + task.getInstruction());
        task.setStatus(TaskStatus.COMPLETED);
        task.setResult(result);

        MemoryRecord record = new MemoryRecord();
        record.setAgentId(task.getAgentId());
        record.setSessionId(task.getId());
        record.setLayer(MemoryLayer.WORKING);
        record.setContent(task.getInstruction() + " -> " + result.getOutput());
        memoryService.store(record);

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
