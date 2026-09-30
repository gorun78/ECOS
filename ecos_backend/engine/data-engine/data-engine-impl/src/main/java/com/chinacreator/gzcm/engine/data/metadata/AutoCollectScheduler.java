package com.chinacreator.gzcm.engine.data.metadata;

import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.service.MetadataRowCountService;
import com.chinacreator.gzcm.engine.data.repository.DataSourceRepository;
import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.parser.ITaskParser;
import com.chinacreator.gzcm.runtime.core.task.scheduling.TaskSchedulerService;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PMO-37 定时自动采集 —— 每 60s 扫描 strategy=ON_SCHEDULE 的数据源，
 * 到点（cron 相对 last_collect_time 的下次触发 &lt;= now）提交采集任务。
 * <p>
 * 连续失败 3 次的数据源自动静默（下一周期重置计数），避免刷屏。
 *
 * @author DataBridge Datanet Team
 */
@Component
public class AutoCollectScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoCollectScheduler.class);
    private static final int MAX_CONSECUTIVE_FAILS = 3;
    private static final String TASK_TYPE = "AUTO_COLLECT_SCAN";

    private final DataSourceRepository dsRepository;
    private final MetadataAsyncTrigger trigger;
    private final MetadataRowCountService rowCountService;
    private final ITaskManagementService taskManagementService;
    private final TaskSchedulerService taskSchedulerService;

    /** datasourceId -> 连续失败次数（触发即 +1，完成回调 markSuccess 清零） */
    private final Map<String, Integer> fails = new ConcurrentHashMap<>();

    public AutoCollectScheduler(DataSourceRepository dsRepository,
                                MetadataAsyncTrigger trigger,
                                MetadataRowCountService rowCountService,
                                ITaskManagementService taskManagementService,
                                TaskSchedulerService taskSchedulerService) {
        this.dsRepository = dsRepository;
        this.trigger = trigger;
        this.rowCountService = rowCountService;
        this.taskManagementService = taskManagementService;
        this.taskSchedulerService = taskSchedulerService;
    }

    @PostConstruct
    public void registerWithRuntimeTask() {
        taskManagementService.registerParser(TASK_TYPE, new ScanParser());
        taskManagementService.registerExecutor(TASK_TYPE, new ScanExecutor());

        TaskDescription description = new TaskDescription();
        description.setTaskName("auto-collect-scan");
        description.setTaskType(TASK_TYPE);
        description.setDescription("PMO-37 定时自动采集扫描 strategy=ON_SCHEDULE 数据源");
        description.setParameters(new HashMap<>());
        taskSchedulerService.schedulePeriodicTask(description, 30_000L, 60_000L);
    }

    public void scanDueSources() {
        Iterable<DataSourceEntity> all;
        try {
            all = dsRepository.findAll();
        } catch (Exception e) {
            log.warn("AutoCollectScheduler 扫描失败: {}", e.getMessage());
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (DataSourceEntity ds : all) {
            try {
                MetadataStrategyConfig cfg = MetadataStrategyConfig.fromJson(ds.getMetadataConfig());
                if (!MetadataAsyncTrigger.isOnSchedule(cfg)) {
                    continue;
                }
                java.sql.Timestamp last = rowCountService.getLastCollectTime(ds.getDatasourceId());
                LocalDateTime lastT = last == null ? null : last.toLocalDateTime();
                // 从未采集过 → 直接触发一次基线采集
                if (lastT == null) {
                    log.info("基线采集触发（从未采集）: datasource={}, cron={}",
                            ds.getDatasourceId(), cfg.getScheduleCron());
                    trigger.triggerManualAsync(ds.getDatasourceId());
                    fails.merge(ds.getDatasourceId(), 1, Integer::sum);
                } else {
                    LocalDateTime next = MetadataAsyncTrigger.nextFireTime(cfg.getScheduleCron(), lastT);
                    if (next != null && !next.isAfter(now)) {
                        int failStreak = fails.getOrDefault(ds.getDatasourceId(), 0);
                        if (failStreak >= MAX_CONSECUTIVE_FAILS) {
                            log.debug("定时采集静默中（连续失败 {} 次）: datasource={}",
                                    failStreak, ds.getDatasourceId());
                            continue;
                        }
                        log.info("定时采集触发: datasource={}, cron={}",
                                ds.getDatasourceId(), cfg.getScheduleCron());
                        trigger.triggerManualAsync(ds.getDatasourceId());
                        fails.merge(ds.getDatasourceId(), 1, Integer::sum);
                    }
                }
            } catch (Exception e) {
                log.debug("AutoCollectScheduler 处理 {} 失败: {}", ds.getDatasourceId(), e.getMessage());
            }
        }
    }

    /** 采集完成后由 Controller/Service 回调，重置失败计数 */
    public void markSuccess(String datasourceId) {
        if (datasourceId != null) {
            fails.remove(datasourceId);
        }
    }

    public void markFailure(String datasourceId) {
        if (datasourceId != null) {
            fails.merge(datasourceId, 1, Integer::sum);
        }
    }

    private final class ScanParser implements ITaskParser {
        @Override
        public TaskExecutionPlan parse(TaskDescription taskDescription) throws TaskParseException {
            validate(taskDescription);
            TaskExecutionPlan plan = new TaskExecutionPlan();
            plan.setTaskId(taskDescription.getTaskId());
            TaskExecutionPlan.ExecutionStep step = new TaskExecutionPlan.ExecutionStep();
            step.setStepId("step-1");
            step.setStepName("AutoCollect scanDueSources");
            step.setStepType(TASK_TYPE);
            step.setExecutor(TASK_TYPE);
            step.setConfig(taskDescription.getParameters() == null
                    ? new HashMap<>() : new HashMap<>(taskDescription.getParameters()));
            List<TaskExecutionPlan.ExecutionStep> steps = new ArrayList<>();
            steps.add(step);
            plan.setSteps(steps);
            return plan;
        }

        @Override
        public boolean supports(String taskType) {
            return TASK_TYPE.equalsIgnoreCase(taskType);
        }

        @Override
        public void validate(TaskDescription taskDescription) throws TaskParseException {
            if (taskDescription == null
                    || taskDescription.getTaskId() == null
                    || taskDescription.getTaskId().isEmpty()) {
                throw new TaskParseException("auto collect scan task id is required");
            }
            if (!supports(taskDescription.getTaskType())) {
                throw new TaskParseException("unsupported auto collect task type: "
                        + taskDescription.getTaskType());
            }
        }
    }

    private final class ScanExecutor implements ITaskExecutor {
        @Override
        public String execute(TaskExecutionPlan executionPlan, ITaskStatusCallback statusCallback)
                throws TaskExecutionException {
            try {
                scanDueSources();
                return String.format("{\"taskType\":\"%s\",\"completedAt\":\"%s\"}",
                        TASK_TYPE, java.time.Instant.now().toString());
            } catch (Exception ex) {
                log.error("AutoCollectScheduler runtime-task 执行失败: {}", ex.getMessage(), ex);
                throw new TaskExecutionException("auto collect scan failed", ex);
            }
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
