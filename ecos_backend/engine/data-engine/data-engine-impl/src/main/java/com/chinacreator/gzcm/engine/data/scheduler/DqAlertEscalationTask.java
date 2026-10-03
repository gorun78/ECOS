package com.chinacreator.gzcm.engine.data.scheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.engine.data.quality.service.DqAlertEscalationService;
import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.parser.ITaskParser;
import com.chinacreator.gzcm.runtime.core.task.scheduling.TaskSchedulerService;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import jakarta.annotation.PostConstruct;

/**
 * F02-10 — DQ 告警升级周期任务注册（升级驱动归 runtime-task，禁 data-engine 自建 @Scheduled）。
 * <p>
 * 每 1min 触发一次 {@link DqAlertEscalationService#runEscalation()}：扫描未 ack 且超响应窗口
 * 的 {@code dq_alert_record}，按 {@code P2 >5min → P1} / {@code P1 >15min → P0} 升级，经
 * {@code IAlertService} 推送并 {@code escalated_to} 留痕。
 *
 * <p>结构与 {@link DqScheduledTask} 相同：parser + executor 注册给 runtime-task，
 * cron 触发由 runtime-task 统一调度。
 */
@Component
public class DqAlertEscalationTask {

    private static final Logger log = LoggerFactory.getLogger(DqAlertEscalationTask.class);
    private static final String CRON_EVERY_MIN = "0 * * * * ?";

    private final DqAlertEscalationService escalationService;
    private final TaskSchedulerService taskScheduler;
    private final ITaskManagementService taskManagementService;

    public DqAlertEscalationTask(DqAlertEscalationService escalationService,
                                 TaskSchedulerService taskScheduler,
                                 ITaskManagementService taskManagementService) {
        this.escalationService = escalationService;
        this.taskScheduler = taskScheduler;
        this.taskManagementService = taskManagementService;
    }

    @PostConstruct
    public void init() {
        taskManagementService.registerParser(DqAlertEscalationService.TASK_TYPE, new EscalationParser());
        taskManagementService.registerExecutor(DqAlertEscalationService.TASK_TYPE, new EscalationExecutor());

        TaskDescription desc = new TaskDescription();
        desc.setTaskId(DqAlertEscalationService.TASK_ID);
        desc.setTaskName("DQ告警升级时限扫描");
        desc.setTaskType(DqAlertEscalationService.TASK_TYPE);
        desc.setDescription("每分钟扫描未ack且超时告警：P2>5min→P1 / P1>15min→P0；经 IAlertService 推送并 escalated_to 留痕");
        desc.setAsync(true);
        desc.setTimeout(30_000L);
        desc.setRetryCount(0);
        desc.setParameters(Map.of("cron", CRON_EVERY_MIN));
        desc.setTags(List.of("data-engine", "quality", "escalation"));
        taskScheduler.scheduleTask(desc, CRON_EVERY_MIN);
        log.info("DQ 告警升级周期任务已注册到 runtime-task: cron={}", CRON_EVERY_MIN);
    }

    private final class EscalationParser implements ITaskParser {
        @Override
        public TaskExecutionPlan parse(TaskDescription td) throws TaskParseException {
            validate(td);
            TaskExecutionPlan plan = new TaskExecutionPlan();
            plan.setTaskId(td.getTaskId());
            TaskExecutionPlan.ExecutionStep step = new TaskExecutionPlan.ExecutionStep();
            step.setStepId("step-1");
            step.setStepName("DQ 告警升级扫描");
            step.setStepType(DqAlertEscalationService.TASK_TYPE);
            step.setExecutor(DqAlertEscalationService.TASK_TYPE);
            step.setConfig(td.getParameters() == null ? new HashMap<>() : new HashMap<>(td.getParameters()));
            List<TaskExecutionPlan.ExecutionStep> steps = new ArrayList<>();
            steps.add(step);
            plan.setSteps(steps);
            return plan;
        }

        @Override
        public boolean supports(String taskType) {
            return DqAlertEscalationService.TASK_TYPE.equalsIgnoreCase(taskType);
        }

        @Override
        public void validate(TaskDescription td) throws TaskParseException {
            if (td == null || td.getTaskId() == null || td.getTaskId().isEmpty()) {
                throw new TaskParseException("DQ alert escalation task id is required");
            }
            if (!supports(td.getTaskType())) {
                throw new TaskParseException("unsupported task type: " + td.getTaskType());
            }
        }
    }

    private final class EscalationExecutor implements ITaskExecutor {
        @Override
        public String execute(TaskExecutionPlan plan, ITaskStatusCallback cb) throws TaskExecutionException {
            try {
                int escalated = escalationService.runEscalation();
                return String.format("{\"taskType\":\"%s\",\"escalated\":%d}",
                        DqAlertEscalationService.TASK_TYPE, escalated);
            } catch (Exception ex) {
                log.error("DQ 告警升级 runtime-task 执行失败: {}", ex.getMessage(), ex);
                throw new TaskExecutionException("DQ alert escalation failed", ex);
            }
        }

        @Override public void cancel(String taskId)   { }
        @Override public void pause(String taskId)   { }
        @Override public void resume(String taskId)  { }
        @Override public TaskStatus getStatus(String taskId) { return null; }
    }
}
