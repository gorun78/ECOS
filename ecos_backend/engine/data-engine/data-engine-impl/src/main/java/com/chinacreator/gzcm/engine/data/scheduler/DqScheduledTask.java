package com.chinacreator.gzcm.engine.data.scheduler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.engine.data.QualityService;
import com.chinacreator.gzcm.engine.data.quality.DqScheduleService;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqScheduleMapper;
import com.chinacreator.gzcm.engine.data.quality.model.DqScheduleVO;
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
 * DQ 定时巡检任务 — 每天8:00全量执行所有启用的质量规则。
 * <p>
 * 单一调度：runtime-task 注册 + cron 触发（H8-T3 收口，已移除 Spring @Scheduled 双轨）。
 * </p>
 *
 * @author ECOS Data Engine Team
 * @since 2026-08-07
 */
@Component
public class DqScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(DqScheduledTask.class);
    private static final String CRON_DAILY_8AM = "0 0 8 * * ?";
    private static final long TIMEOUT_SECONDS = 120;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String TASK_TYPE = "DQ_SCAN";

    private final JdbcTemplate jdbc;
    private final QualityService qualityService;
    private final TaskSchedulerService taskScheduler;
    private final ITaskManagementService taskManagementService;
    private final DqScheduleMapper scheduleMapper;
    private final DqScheduleService scheduleService;

    public DqScheduledTask(JdbcTemplate jdbc, QualityService qualityService,
                           TaskSchedulerService taskScheduler,
                           ITaskManagementService taskManagementService,
                           DqScheduleMapper scheduleMapper,
                           DqScheduleService scheduleService) {
        this.jdbc = jdbc;
        this.qualityService = qualityService;
        this.taskScheduler = taskScheduler;
        this.taskManagementService = taskManagementService;
        this.scheduleMapper = scheduleMapper;
        this.scheduleService = scheduleService;
    }

    @PostConstruct
    public void init() {
        taskManagementService.registerParser(TASK_TYPE, new DqScanParser());
        taskManagementService.registerExecutor(TASK_TYPE, new DqScanExecutor());

        TaskDescription desc = new TaskDescription();
        desc.setTaskId("dq-daily-scan");
        desc.setTaskName("DQ每日全量巡检");
        desc.setTaskType(TASK_TYPE);
        desc.setDescription("每天8:00全量执行所有启用的DQ规则，超时120s，结果写dq_evaluation_results");
        desc.setAsync(true);
        desc.setTimeout(120_000L);
        desc.setRetryCount(0);
        desc.setParameters(Map.of("cron", CRON_DAILY_8AM));
        desc.setTags(List.of("data-engine", "quality"));
        taskScheduler.scheduleTask(desc, CRON_DAILY_8AM);
        log.info("DQ定时巡检已注册到runtime-task: cron={}", CRON_DAILY_8AM);
    }

    public void executeDailyScan() {
        log.info("DQ定时巡检开始");
        long startTime = System.currentTimeMillis();

        // B1 fix: 查真实表 ecos_quality_rule（字段: rule_id/rule_name/rule_type/parameters/enabled）
        Integer enabledCount;
        try {
            enabledCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_quality_rule WHERE enabled = true", Integer.class);
        } catch (Exception e) {
            log.warn("查询DQ规则失败(表ecos_quality_rule可能不存在): {}", e.getMessage());
            return;
        }

        if (enabledCount == null || enabledCount == 0) {
            log.info("无启用的DQ规则，跳过巡检");
            return;
        }

        // PMO-48-C T11: 优先走 dq_schedule 调度批执行（enabled + trigger_type='SCHEDULE' 的计划）
        // 兜底: 无 SCHEDULE 调度计划时回落旧逻辑 qualityService.evaluateAll()（legacy 兼容）
        List<DqScheduleVO> schedulePlans = scheduleMapper.listEnabled().stream()
                .filter(p -> "SCHEDULE".equalsIgnoreCase(p.getTriggerType()))
                .toList();

        if (schedulePlans.isEmpty()) {
            log.info("无 SCHEDULE 型调度计划，回落旧逻辑 evaluateAll 全量巡检");
            evaluateLegacy();
            updateLastRunTime();
            return;
        }

        log.info("开始 DQ 调度巡检，{} 个 SCHEDULE 计划", schedulePlans.size());
        int totalExecuted = 0;
        for (DqScheduleVO plan : schedulePlans) {
            try {
                totalExecuted += scheduleService.runRuleBatch(plan.getId());
            } catch (Exception e) {
                log.error("DQ 调度计划执行失败 id={}: {}", plan.getId(), e.getMessage(), e);
            }
        }
        log.info("DQ 调度巡检完成: 评估 {} 条规则", totalExecuted);
        updateLastRunTime();
    }

    /** 旧逻辑兜底：无 SCHEDULE 调度计划时全量评估（legacy 兼容，Phase 4 可下线）。 */
    private void evaluateLegacy() {
        Integer enabledCount;
        try {
            enabledCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_quality_rule WHERE enabled = true", Integer.class);
        } catch (Exception e) {
            log.warn("查询DQ规则失败(表ecos_quality_rule可能不存在): {}", e.getMessage());
            return;
        }
        if (enabledCount == null || enabledCount == 0) {
            log.info("无启用的DQ规则，跳过巡检");
            return;
        }
        log.info("开始巡检 {} 条DQ规则（legacy path）", enabledCount);
        try {
            Map<String, Object> result = qualityService.evaluateAll();
            Object evaluated = result.get("total");
            int total = evaluated instanceof Number ? ((Number) evaluated).intValue() : enabledCount;
            Object failed = result.get("failed");
            int failCount = failed instanceof Number ? ((Number) failed).intValue() : 0;
            log.info("DQ巡检完成(legacy): 共{}条规则, 成功{}条, 失败{}条",
                    total, total - failCount, failCount);
        } catch (Exception e) {
            log.error("DQ巡检执行失败(legacy): {}", e.getMessage(), e);
        }
    }

    private void updateLastRunTime() {
        try {
            String now = LocalDateTime.now().format(DT_FMT);
            int updated = jdbc.update(
                    "UPDATE sys_config SET config_value = ?, updated_at = NOW() WHERE config_key = ?",
                    now, "dq_last_run_time");
            if (updated == 0) {
                jdbc.update(
                        "INSERT INTO sys_config (config_key, config_value, created_at, updated_at) VALUES (?, ?, NOW(), NOW())",
                        "dq_last_run_time", now);
            }
        } catch (Exception e) {
            log.warn("更新dq_last_run_time失败: {}", e.getMessage());
        }
    }

    private final class DqScanParser implements ITaskParser {
        @Override
        public TaskExecutionPlan parse(TaskDescription taskDescription) throws TaskParseException {
            validate(taskDescription);
            TaskExecutionPlan plan = new TaskExecutionPlan();
            plan.setTaskId(taskDescription.getTaskId());
            TaskExecutionPlan.ExecutionStep step = new TaskExecutionPlan.ExecutionStep();
            step.setStepId("step-1");
            step.setStepName("DQ 每日巡检");
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
                throw new TaskParseException("DQ scan task id is required");
            }
            if (!supports(taskDescription.getTaskType())) {
                throw new TaskParseException("unsupported DQ scan task type: "
                        + taskDescription.getTaskType());
            }
        }
    }

    private final class DqScanExecutor implements ITaskExecutor {
        @Override
        public String execute(TaskExecutionPlan executionPlan, ITaskStatusCallback statusCallback)
                throws TaskExecutionException {
            try {
                executeDailyScan();
                return String.format("{\"taskType\":\"%s\",\"completedAt\":\"%s\"}",
                        TASK_TYPE, java.time.Instant.now().toString());
            } catch (Exception ex) {
                log.error("DQ 巡检 runtime-task 执行失败: {}", ex.getMessage(), ex);
                throw new TaskExecutionException("DQ scan failed", ex);
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
