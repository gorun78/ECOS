package com.chinacreator.gzcm.engine.data.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class PipelineScheduler {

    private static final Logger log = LoggerFactory.getLogger(PipelineScheduler.class);
    private final JdbcTemplate jdbc;
    private final PipelineExecutionEngine executionEngine;
    private final ExecutorService schedulerExecutor = Executors.newFixedThreadPool(4);
    private final Set<String> activeTasks = ConcurrentHashMap.newKeySet();

    public PipelineScheduler(JdbcTemplate jdbc, PipelineExecutionEngine executionEngine) {
        this.jdbc = jdbc;
        this.executionEngine = executionEngine;
    }

    @Scheduled(fixedDelay = 60000)
    public void scanAndTrigger() {
        List<Map<String, Object>> tasks;
        try {
            tasks = jdbc.queryForList(
                "SELECT id, name, cron_expression, status FROM ecos_pipeline_task " +
                "WHERE cron_expression IS NOT NULL AND cron_expression != '' AND status IN ('DRAFT', 'SUCCEEDED', 'FAILED') AND enabled != false");
        } catch (Exception e) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> task : tasks) {
            String taskId = (String) task.get("id");
            String cronExpression = (String) task.get("cron_expression");

            if (activeTasks.contains(taskId)) continue;

            if (shouldTrigger(cronExpression, now)) {
                activeTasks.add(taskId);
                String runId = UUID.randomUUID().toString();
                try {
                    createScheduledRun(runId, taskId, "scheduler");
                    schedulerExecutor.submit(() -> {
                        try {
                            executionEngine.execute(runId);
                        } finally {
                            activeTasks.remove(taskId);
                        }
                    });
                    log.info("调度触发 Pipeline: taskId={}, runId={}, cron={}", taskId, runId, cronExpression);
                } catch (Exception e) {
                    activeTasks.remove(taskId);
                    log.error("调度触发失败: taskId={}", taskId, e);
                }
            }
        }
    }

    boolean shouldTrigger(String cronExpression, LocalDateTime now) {
        if (cronExpression == null || cronExpression.isEmpty()) return false;

        try {
            String[] parts = cronExpression.trim().split("\\s+");
            if (parts.length < 5) return false;

            String minute = parts[0];
            String hour = parts[1];
            String dayOfMonth = parts.length > 2 ? parts[2] : "*";
            String month = parts.length > 3 ? parts[3] : "*";

            if (!matchesCronPart(minute, now.getMinute())) return false;
            if (!matchesCronPart(hour, now.getHour())) return false;
            if (!dayOfMonth.equals("*") && !matchesCronPart(dayOfMonth, now.getDayOfMonth())) return false;
            if (!month.equals("*") && !matchesCronPart(month, now.getMonthValue())) return false;

            return true;
        } catch (Exception e) {
            log.warn("Cron 表达式解析失败: {}", cronExpression);
            return false;
        }
    }

    private boolean matchesCronPart(String cronPart, int actualValue) {
        if (cronPart.equals("*")) return true;

        if (cronPart.contains(",")) {
            for (String p : cronPart.split(",")) {
                if (matchesCronPart(p.trim(), actualValue)) return true;
            }
            return false;
        }

        if (cronPart.contains("/")) {
            String[] stepParts = cronPart.split("/");
            int base = stepParts[0].equals("*") ? 0 : Integer.parseInt(stepParts[0]);
            int step = Integer.parseInt(stepParts[1]);
            return actualValue >= base && (actualValue - base) % step == 0;
        }

        if (cronPart.contains("-")) {
            String[] range = cronPart.split("-");
            int start = Integer.parseInt(range[0]);
            int end = Integer.parseInt(range[1]);
            return actualValue >= start && actualValue <= end;
        }

        return Integer.parseInt(cronPart) == actualValue;
    }

    private void createScheduledRun(String runId, String taskId, String triggeredBy) {
        jdbc.update(
            "INSERT INTO ecos_pipeline_run (id, task_id, status, triggered_by, total_steps, completed_steps) " +
            "VALUES (?, ?, 'QUEUED', ?, " +
            "(SELECT COUNT(*) FROM ecos_pipeline_step WHERE task_id = ?), 0)",
            runId, taskId, triggeredBy, taskId);

        List<Map<String, Object>> steps = jdbc.queryForList(
            "SELECT id, node_id, node_type FROM ecos_pipeline_step WHERE task_id = ? ORDER BY step_order", taskId);
        for (Map<String, Object> step : steps) {
            String stepRunId = UUID.randomUUID().toString();
            jdbc.update(
                "INSERT INTO ecos_pipeline_step_run (id, run_id, step_id, node_id, node_type, status) " +
                "VALUES (?, ?, ?, ?, ?, 'QUEUED')",
                stepRunId, runId, step.get("id"), step.get("node_id"), step.get("node_type"));
        }
    }
}
