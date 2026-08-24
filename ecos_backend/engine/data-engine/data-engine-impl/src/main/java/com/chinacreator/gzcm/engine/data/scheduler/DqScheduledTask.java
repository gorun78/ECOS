package com.chinacreator.gzcm.engine.data.scheduler;

import com.chinacreator.gzcm.engine.data.QualityService;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.scheduling.TaskSchedulerService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

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

    public DqScheduledTask(JdbcTemplate jdbc, QualityService qualityService,
                           TaskSchedulerService taskScheduler) {
        this.jdbc = jdbc;
        this.qualityService = qualityService;
        this.taskScheduler = taskScheduler;
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

        log.info("开始巡检 {} 条DQ规则", enabledCount);

        // B2 fix: 只调一次 evaluateAll()（内部已遍历全部 enabled 规则），不再 per-rule 循环
        int successCount = 0;
        int failCount = 0;
        try {
            Map<String, Object> result = qualityService.evaluateAll();
            Object evaluated = result.get("total");
            int total = evaluated instanceof Number ? ((Number) evaluated).intValue() : enabledCount;
            Object failed = result.get("failed");
            failCount = failed instanceof Number ? ((Number) failed).intValue() : 0;
            successCount = total - failCount;
            log.info("DQ巡检完成: 共{}条规则, 成功{}条, 失败{}条", total, successCount, failCount);
        } catch (Exception e) {
            failCount = enabledCount;
            log.error("DQ巡检执行失败: {}", e.getMessage(), e);
        }
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("DQ巡检完成: 成功={}, 失败={}, 总计={}, 耗时={}ms",
                successCount, failCount, enabledCount, elapsed);

        updateLastRunTime();
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
