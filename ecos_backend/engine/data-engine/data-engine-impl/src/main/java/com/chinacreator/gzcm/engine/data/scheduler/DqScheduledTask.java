package com.chinacreator.gzcm.engine.data.scheduler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.engine.data.QualityService;
import com.chinacreator.gzcm.engine.data.quality.DqScheduleService;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqScheduleMapper;
import com.chinacreator.gzcm.engine.data.quality.model.DqScheduleVO;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.scheduling.TaskSchedulerService;
import jakarta.annotation.PostConstruct;

/**
 * DQ 定时巡检任务 — 每天8:00全量执行所有启用的质量规则。
 * <p>
 * 双重调度：runtime-task 注册（可见性/管理）+ Spring @Scheduled（实际执行）。
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

    private final JdbcTemplate jdbc;
    private final QualityService qualityService;
    private final TaskSchedulerService taskScheduler;
    private final DqScheduleMapper scheduleMapper;
    private final DqScheduleService scheduleService;

    public DqScheduledTask(JdbcTemplate jdbc, QualityService qualityService,
                           TaskSchedulerService taskScheduler,
                           DqScheduleMapper scheduleMapper,
                           DqScheduleService scheduleService) {
        this.jdbc = jdbc;
        this.qualityService = qualityService;
        this.taskScheduler = taskScheduler;
        this.scheduleMapper = scheduleMapper;
        this.scheduleService = scheduleService;
    }

    @PostConstruct
    public void init() {
        // 1. 注册到 runtime-task 全局调度（满足架构铁律2.3）
        TaskDescription desc = new TaskDescription();
        desc.setTaskId("dq-daily-scan");
        desc.setTaskName("DQ每日全量巡检");
        desc.setTaskType("DQ_SCAN");
        desc.setDescription("每天8:00全量执行所有启用的DQ规则，超时120s，结果写dq_evaluation_results");
        desc.setAsync(true);
        desc.setTimeout(120_000L);
        desc.setRetryCount(0);
        desc.setParameters(Map.of("cron", CRON_DAILY_8AM));
        desc.setTags(List.of("data-engine", "quality"));
        taskScheduler.scheduleTask(desc, CRON_DAILY_8AM);
        log.info("DQ定时巡检已注册到runtime-task: cron={}", CRON_DAILY_8AM);
    }

    /**
     * 每天8:00全量巡检所有启用的DQ规则。
     * Spring @Scheduled 负责实际执行，runtime-task 负责可见性管理。
     */
    @Scheduled(cron = "0 0 8 * * ?")
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
}
