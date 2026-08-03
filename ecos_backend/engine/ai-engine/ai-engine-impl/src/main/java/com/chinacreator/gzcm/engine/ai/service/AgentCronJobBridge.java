package com.chinacreator.gzcm.engine.ai.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.engine.ai.entity.CronJobEntity;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.scheduling.TaskSchedulerService;

/**
 * Agent CronJob 到全局任务中心的桥接器。
 * 负责将 ai-engine 的 CronJobEntity 转换为 TaskDescription，
 * 并通过 TaskSchedulerService 注册到 runtime-task 全局任务中心。
 *
 * <p>维护 cronJobId → 调度状态的映射，支持注册、取消、重新调度等操作。</p>
 */
@Component
public class AgentCronJobBridge {

    private static final Logger log = LoggerFactory.getLogger(AgentCronJobBridge.class);

    private static final String TASK_TYPE = "AI_AGENT_CRON";
    private static final long TASK_TIMEOUT_MS = 300_000L;
    private static final List<String> TASK_TAGS = List.of("ai-engine", "cron");

    private final TaskSchedulerService taskScheduler;

    /** cronJobId → 调度状态 (scheduleId, prompt, agentId, userId) */
    private final Map<Long, BridgeState> stateByCronJobId = new ConcurrentHashMap<>();

    @Autowired
    public AgentCronJobBridge(TaskSchedulerService taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    /**
     * 注册 CronJob 到全局任务中心并开始调度。
     *
     * @param entity  CronJob 实体 (含 id, name, cronExpression, enabled 等)
     * @param prompt  任务 prompt
     * @param agentId Agent ID
     * @param userId  用户 ID
     * @return 调度 ID
     */
    public String register(CronJobEntity entity, String prompt, String agentId, String userId) {
        TaskDescription task = convert(entity, prompt, agentId, userId);

        String scheduleId;
        if (entity.getCronExpression() != null && !entity.getCronExpression().isBlank()) {
            scheduleId = taskScheduler.scheduleTask(task, entity.getCronExpression());
        } else {
            scheduleId = taskScheduler.scheduleTask(task);
        }

        stateByCronJobId.put(entity.getId(), new BridgeState(scheduleId, prompt, agentId, userId));
        log.info("AgentCronJobBridge registered: cronJobId={}, scheduleId={}", entity.getId(), scheduleId);
        return scheduleId;
    }

    /**
     * 立即执行 CronJob (一次性，不注册 cron 调度)。
     *
     * @param entity  CronJob 实体
     * @param prompt  任务 prompt
     * @param agentId Agent ID
     * @param userId  用户 ID
     * @return 调度 ID
     */
    public String executeNow(CronJobEntity entity, String prompt, String agentId, String userId) {
        TaskDescription task = convert(entity, prompt, agentId, userId);

        // delayMillis=0 表示立即执行
        String scheduleId = taskScheduler.scheduleTask(task, 0L);

        stateByCronJobId.put(entity.getId(), new BridgeState(scheduleId, prompt, agentId, userId));
        log.info("AgentCronJobBridge executeNow: cronJobId={}, scheduleId={}", entity.getId(), scheduleId);
        return scheduleId;
    }

    /**
     * 取消指定 CronJob 的调度。
     *
     * @param cronJobId CronJob ID
     */
    public void cancel(Long cronJobId) {
        BridgeState state = stateByCronJobId.remove(cronJobId);
        if (state != null) {
            taskScheduler.cancelSchedule(state.scheduleId);
            log.info("AgentCronJobBridge cancelled: cronJobId={}, scheduleId={}", cronJobId, state.scheduleId);
        }
    }

    /**
     * 重新调度 CronJob (先取消旧调度，再用 cronExpression 重新注册)。
     *
     * @param entity  CronJob 实体
     * @param prompt  任务 prompt
     * @param agentId Agent ID
     * @param userId  用户 ID
     * @return 新的调度 ID
     */
    public String reschedule(CronJobEntity entity, String prompt, String agentId, String userId) {
        cancel(entity.getId());
        return register(entity, prompt, agentId, userId);
    }

    /**
     * 获取指定 CronJob 缓存的 prompt。用于 toggle 重新启用时无需再次提供。
     *
     * @param cronJobId CronJob ID
     * @return 缓存的 prompt，若未注册则返回 null
     */
    public String getCachedPrompt(Long cronJobId) {
        BridgeState state = stateByCronJobId.get(cronJobId);
        return state != null ? state.prompt : null;
    }

    /**
     * 获取指定 CronJob 缓存的 agentId。
     */
    public String getCachedAgentId(Long cronJobId) {
        BridgeState state = stateByCronJobId.get(cronJobId);
        return state != null ? state.agentId : null;
    }

    /**
     * 获取指定 CronJob 缓存的 userId。
     */
    public String getCachedUserId(Long cronJobId) {
        BridgeState state = stateByCronJobId.get(cronJobId);
        return state != null ? state.userId : null;
    }

    // ── 内部转换 ──

    private TaskDescription convert(CronJobEntity entity, String prompt, String agentId, String userId) {
        TaskDescription task = new TaskDescription();
        task.setTaskId("agent-cron-" + entity.getId());
        task.setTaskName(entity.getName());
        task.setTaskType(TASK_TYPE);
        task.setDescription(entity.getDescription());
        task.setAsync(true);
        task.setTimeout(TASK_TIMEOUT_MS);
        task.setRetryCount(0);
        task.setTags(TASK_TAGS);

        // parameters
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("agentId", agentId);
        parameters.put("prompt", prompt);
        parameters.put("userId", userId);
        task.setParameters(parameters);

        // extensions
        Map<String, Object> extensions = new HashMap<>();
        extensions.put("cronJobId", entity.getId());
        task.setExtensions(extensions);

        task.setCreatedBy(entity.getCreatedBy());
        return task;
    }

    // ── 内部状态类 ──

    private static class BridgeState {
        final String scheduleId;
        final String prompt;
        final String agentId;
        final String userId;

        BridgeState(String scheduleId, String prompt, String agentId, String userId) {
            this.scheduleId = scheduleId;
            this.prompt = prompt;
            this.agentId = agentId;
            this.userId = userId;
        }
    }
}
