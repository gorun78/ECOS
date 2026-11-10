package com.chinacreator.gzcm.engine.ai.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor.TaskExecutionException;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.parser.ITaskParser;
import com.chinacreator.gzcm.runtime.core.task.parser.ITaskParser.TaskParseException;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;

/**
 * AI_AGENT_CRON 任务类型的解析器/执行器注册（P1-10）。
 *
 * <p>链路闭环：{@code CronJobServiceImpl#createCronJob} → {@code AgentCronJobBridge.register}
 * → runtime-task {@code scheduleTask(cron)} → 触发时按 stepType=AI_AGENT_CRON 找到本类执行器 →
 * 经 {@code AgentLoopService} 真实执行 prompt。本类补上此前缺失的最后一环（执行器曾未注册，
 * 触发侧找不到执行器只能记失败）。</p>
 *
 * <p>注册于 @PostConstruct，单源一处，与 DqScheduledTask 同形态。</p>
 */
@Component
public class AgentCronTaskRegistrar {

    private static final Logger log = LoggerFactory.getLogger(AgentCronTaskRegistrar.class);

    /** 任务类型词（与 {@code AgentCronJobBridge.TASK_TYPE} 同字面量，两侧同时改）。 */
    public static final String TASK_TYPE = "AI_AGENT_CRON";

    private final ITaskManagementService taskManagementService;
    private final AgentLoopService agentLoopService;

    public AgentCronTaskRegistrar(ITaskManagementService taskManagementService,
                                  AgentLoopService agentLoopService) {
        this.taskManagementService = taskManagementService;
        this.agentLoopService = agentLoopService;
    }

    @PostConstruct
    public void init() {
        taskManagementService.registerParser(TASK_TYPE, new AgentCronTaskParser());
        taskManagementService.registerExecutor(TASK_TYPE, new AgentCronTaskExecutor());
        log.info("AI_AGENT_CRON 任务执行器已注册到 runtime-task（cron 执行链闭环）");
    }

    // ── Parser：parameters → step.config ────────────────────────────────

    private static final class AgentCronTaskParser implements ITaskParser {
        @Override
        public TaskExecutionPlan parse(TaskDescription taskDescription) throws TaskParseException {
            validate(taskDescription);
            TaskExecutionPlan plan = new TaskExecutionPlan();
            plan.setTaskId(taskDescription.getTaskId());
            TaskExecutionPlan.ExecutionStep step = new TaskExecutionPlan.ExecutionStep();
            step.setStepId("step-1");
            step.setStepName("执行" + taskDescription.getTaskType() + "任务");
            step.setStepType(taskDescription.getTaskType());
            step.setExecutor(TASK_TYPE);
            Map<String, Object> config = new HashMap<>();
            if (taskDescription.getParameters() != null) {
                config.putAll(taskDescription.getParameters());
            }
            step.setConfig(config);
            plan.setSteps(List.of(step));
            return plan;
        }

        @Override
        public boolean supports(String taskType) {
            return TASK_TYPE.equalsIgnoreCase(taskType);
        }

        @Override
        public void validate(TaskDescription taskDescription) throws TaskParseException {
            if (taskDescription == null || taskDescription.getTaskId() == null
                    || taskDescription.getTaskId().isEmpty()) {
                throw new TaskParseException("Task ID is required");
            }
        }
    }

    // ── Executor：cron 触发 → AgentLoop 真实执行 ─────────────────────────

    private final class AgentCronTaskExecutor implements ITaskExecutor {
        @Override
        public String execute(TaskExecutionPlan executionPlan, ITaskStatusCallback statusCallback)
                throws TaskExecutionException {
            Map<String, Object> config = executionPlan.getSteps() == null || executionPlan.getSteps().isEmpty()
                    ? Map.of()
                    : executionPlan.getSteps().get(0).getConfig();
            String prompt = config.get("prompt") == null ? "" : String.valueOf(config.get("prompt"));
            String agentId = config.get("agentId") == null ? "default" : String.valueOf(config.get("agentId"));
            if (prompt.isBlank()) {
                throw new TaskExecutionException("AI_AGENT_CRON prompt 为空（cronJobId="
                        + config.get("cronJobId") + "）");
            }

            AgentLoopConfig loopConfig = new AgentLoopConfig();
            loopConfig.setModel("deepseek-chat");
            loopConfig.setTemperature(0.3);
            loopConfig.setMaxTokens(2048);

            com.chinacreator.gzcm.runtime.llm.session.AgentSession session =
                    new com.chinacreator.gzcm.runtime.llm.session.AgentSession();
            session.setSessionId("cron-" + executionPlan.getTaskId());
            session.setSubsystem("ai-engine");
            session.setProfileName(agentId);

            log.info("AI_AGENT_CRON 开始执行: taskId={} agentId={}", executionPlan.getTaskId(), agentId);
            AgentLoopResult result = agentLoopService.run(loopConfig, prompt, session);
            boolean success = result != null && result.isSuccess();
            log.info("AI_AGENT_CRON 执行结束: taskId={} success={} turns={}",
                    executionPlan.getTaskId(), success, result == null ? 0 : result.getTurns());
            return "{\"taskType\":\"" + TASK_TYPE + "\",\"success\":" + success
                    + ",\"taskId\":\"" + executionPlan.getTaskId() + "\""
                    + (success && result.getContent() != null
                        ? ",\"answerLength\":" + result.getContent().length() : "")
                    + "}";
        }

        @Override
        public void cancel(String taskId) { }

        @Override
        public void pause(String taskId) { }

        @Override
        public void resume(String taskId) { }

        @Override
        public TaskStatus getStatus(String taskId) {
            return null;
        }
    }
}
