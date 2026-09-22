package com.chinacreator.gzcm.runtime.core.task.scheduling;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.persistence.ITaskPersistenceService;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;

/**
 * 任务调度服务实现 — PMO-72 W4：调度元信息 (cron / next_run_at / last_run_at / last_status)
 * 全部落 PG 的 td_runtime_task_plan（铁律 §1.6 / 架构铁律 §2.5-3 单命令委托 runtime-task）。
 */
@Component
public class TaskSchedulerServiceImpl implements TaskSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(TaskSchedulerServiceImpl.class);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new HashMap<>();
    private final Map<String, TaskDescription> taskDescriptions = new HashMap<>();
    private final ITaskPersistenceService taskPersistenceService;
    private final ITaskManagementService taskManagementService;
    
    @Autowired
    public TaskSchedulerServiceImpl(ITaskPersistenceService taskPersistenceService, 
            ITaskManagementService taskManagementService) {
        this.taskPersistenceService = taskPersistenceService;
        this.taskManagementService = taskManagementService;
    }
    
    @Override
    public String scheduleTask(TaskDescription taskDescription) {
        String scheduleId = UUID.randomUUID().toString();
        taskDescriptions.put(scheduleId, taskDescription);
        
        // 立即执行
        scheduler.submit(() -> {
            try {
                executeTask(scheduleId, taskDescription, null);
            } catch (Exception e) {
                log.warn("scheduleTask(无 cron) 任务提交失败 taskId={}: {}",
                        taskDescription.getTaskId(), e.getMessage());
            }
        });
        
        return scheduleId;
    }
    
    @Override
    public String scheduleTask(TaskDescription taskDescription, String cronExpression) {
        String scheduleId = UUID.randomUUID().toString();
        taskDescriptions.put(scheduleId, taskDescription);

        // 将 5 位 cron（分 时 日 月 周）补齐为 Spring 6 位（秒 分 时 日 月 周）
        String normalized = normalizeCron(cronExpression);
        CronExpression cron;
        try {
            cron = CronExpression.parse(normalized);
        } catch (IllegalArgumentException e) {
            log.warn("非法 cron 表达式 ' {} '，无法注册定时任务: {}", cronExpression, e.getMessage());
            return scheduleId;
        }

        // 持久化调度元信息到 td_runtime_task_plan（PMO-72 W4）
        // 失败容忍：PG 不可达时 warn 不抛，调度继续（双态）
        Date nextRun = toDate(cron.next(Instant.now()));
        persistPlanCron(scheduleId, taskDescription, normalized, nextRun);

        // 真实 cron 周期调度：计算当前时刻的下次触发时间，触发后按 next() 重排下一次
        rescheduleCron(scheduleId, taskDescription, cron);
        log.info("定时任务已注册 scheduleId={} cron={} taskId={}",
                scheduleId, normalized, taskDescription.getTaskId());
        return scheduleId;
    }

    /**
     * PMO-72 W4: 注册时落 td_runtime_task_plan.cron_expression + next_run_at + task_name / task_type
     * (前置前提是 plan 行已被 submitTask 持久化；如不存在则 warn，避免唯一键缺失)。
     */
    private void persistPlanCron(String scheduleId, TaskDescription taskDescription,
            String cron, Date nextRunAt) {
        try {
            if (taskPersistenceService instanceof com.chinacreator.gzcm.runtime.core.task.persistence.impl.JdbcTaskPersistenceService) {
                com.chinacreator.gzcm.runtime.core.task.persistence.impl.JdbcTaskPersistenceService jdbc =
                        (com.chinacreator.gzcm.runtime.core.task.persistence.impl.JdbcTaskPersistenceService) taskPersistenceService;
                String planId = planKeyFor(taskDescription);
                // 双 ID 兼容：即时任务 plan 行 = task_id；定时调度同时落到 scheduleId 虚拟行
                boolean a = jdbc.saveJdbcPlanState(planId,
                        taskDescription.getTaskName(), taskDescription.getTaskType(),
                        cron, nextRunAt, null, null);
                boolean b = scheduleId.equals(planId) || jdbc.saveJdbcPlanState(scheduleId,
                        taskDescription.getTaskName(), taskDescription.getTaskType(),
                        cron, nextRunAt, null, null);
                if (!a && !b) {
                    log.warn("persistPlanCron: PG 未hit scheduleId={} taskId={}（plan 行未先持久化或 PG 异常）",
                            scheduleId, taskDescription.getTaskId());
                }
            }
        } catch (Exception e) {
            log.warn("persistPlanCron: PG 持久化失败 scheduleId={}: {}",
                    scheduleId, e.getMessage());
        }
    }

    /**
     * plan 行主键选择：优先 TaskDescription.taskId（即时任务）；
     * 定时调度场景 scheduleId 是虚拟主键（submitTask 还没发生）。
     */
    private static String planKeyFor(TaskDescription t) {
        return t != null && t.getTaskId() != null && !t.getTaskId().isEmpty()
                ? t.getTaskId() : UUID.randomUUID().toString();
    }

    /** Instant → Date 工具 */
    private static Date toDate(Instant instant) {
        return instant == null ? null : Date.from(instant);
    }

    /**
     * 按 Cron 周期调度：排定下一次触发，并在每次执行后重新计算下一轮。
     * 相比 scheduleWithFixedDelay 的固定间隔，此实现严格按 cron 语义触发（如"每日 00:00"）。
     */
    private void rescheduleCron(final String scheduleId,
                                final TaskDescription taskDescription,
                                final CronExpression cron) {
        if (scheduler.isShutdown()) {
            return;
        }
        try {
            Instant next = cron.next(Instant.now());
            if (next == null) {
                return;
            }
            long delayMs = Math.max(1L, next.toEpochMilli() - System.currentTimeMillis());
            ScheduledFuture<?> future = scheduler.schedule(() -> {
                try {
                    if (checkDependencies(taskDescription)) {
                        // 单次触发：内部捕获异常 + 落 PG, 不重排（finally 由 rescheduleCron 接管）
                        executeTask(scheduleId, taskDescription, null);
                    }
                } catch (Exception e) {
                    log.warn("定时任务执行异常 scheduleId={} task={}: {}",
                            scheduleId, taskDescription.getTaskId(), e.getMessage());
                } finally {
                    // 执行完毕后（无论成败）排定下一次，确保周期不中断
                    rescheduleCron(scheduleId, taskDescription, cron);
                }
            }, delayMs, TimeUnit.MILLISECONDS);
            // 更新句柄，便于 cancelSchedule 取消（覆盖旧 future）
            ScheduledFuture<?> prev = scheduledTasks.put(scheduleId, future);
            if (prev != null && !prev.isCancelled()) {
                prev.cancel(false);
            }
        } catch (Exception e) {
            log.warn("排定 cron 触发失败 scheduleId={}: {}", scheduleId, e.getMessage());
        }
    }

    /**
     * 将 5 位 cron 补齐为 Spring 6 位（秒位为 0）。
     * 若已是 6 位则原样返回；解析失败由调用方 CronExpression.parse 抛异常处理。
     */
    private String normalizeCron(String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return "0 0 * * * *";
        }
        String[] parts = cronExpression.trim().split("\\s+");
        if (parts.length == 5) {
            return "0 " + cronExpression.trim();
        }
        return cronExpression.trim();
    }
    
    @Override
    public String scheduleTask(TaskDescription taskDescription, long delayMillis) {
        String scheduleId = UUID.randomUUID().toString();
        taskDescriptions.put(scheduleId, taskDescription);
        
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                if (checkDependencies(taskDescription)) {
                    executeTask(scheduleId, taskDescription, null);
                }
            } catch (Exception e) {
                log.warn("scheduleTask(delay) 投递失败 scheduleId={} taskId={}: {}",
                        scheduleId, taskDescription.getTaskId(), e.getMessage());
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
        
        scheduledTasks.put(scheduleId, future);
        return scheduleId;
    }
    
    @Override
    public String schedulePeriodicTask(TaskDescription taskDescription, long initialDelayMillis, long periodMillis) {
        String scheduleId = UUID.randomUUID().toString();
        taskDescriptions.put(scheduleId, taskDescription);
        
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (checkDependencies(taskDescription)) {
                    executeTask(scheduleId, taskDescription, null);
                }
            } catch (Exception e) {
                log.warn("schedulePeriodicTask 投递失败 scheduleId={} taskId={}: {}",
                        scheduleId, taskDescription.getTaskId(), e.getMessage());
            }
        }, initialDelayMillis, periodMillis, TimeUnit.MILLISECONDS);
        
        scheduledTasks.put(scheduleId, future);
        return scheduleId;
    }
    
    @Override
    public void cancelSchedule(String scheduleId) {
        ScheduledFuture<?> future = scheduledTasks.remove(scheduleId);
        if (future != null) {
            future.cancel(false);
        }
        taskDescriptions.remove(scheduleId);
    }
    
    @Override
    public boolean checkDependencies(TaskDescription taskDescription) {
        if (taskDescription == null) {
            return true;
        }
        
        List<String> dependencies = taskDescription.getDependencies();
        if (dependencies == null || dependencies.isEmpty()) {
            return true;
        }
        
        // 检查所有依赖任务是否已完成
        for (String depTaskId : dependencies) {
            try {
                TaskStatus status = taskPersistenceService.getStatus(depTaskId);
                if (status == null) {
                    return false; // 依赖任务不存在
                }
                com.chinacreator.gzcm.runtime.core.task.model.TaskStatus.Status state = status.getStatus();
                if (state != com.chinacreator.gzcm.runtime.core.task.model.TaskStatus.Status.SUCCEEDED && 
                    state != com.chinacreator.gzcm.runtime.core.task.model.TaskStatus.Status.FAILED && 
                    state != com.chinacreator.gzcm.runtime.core.task.model.TaskStatus.Status.CANCELLED) {
                    return false; // 依赖任务未完成
                }
            } catch (Exception e) {
                return false; // 检查依赖任务状态失败
            }
        }
        
        return true;
    }
    
    @Override
    public List<String> getDependencies(String taskId) {
        if (taskId == null) {
            return new ArrayList<>();
        }
        
        try {
            TaskDescription taskDescription = taskPersistenceService.getTask(taskId);
            if (taskDescription != null) {
                List<String> dependencies = taskDescription.getDependencies();
                return dependencies != null ? new ArrayList<>(dependencies) : new ArrayList<>();
            }
        } catch (Exception e) {
            // 忽略异常
        }
        
        return new ArrayList<>();
    }
    
    @Override
    public List<String> getScheduledTasks() {
        return new ArrayList<>(scheduledTasks.keySet());
    }
    
    /**
     * 执行任务（原私有 executeTask 学习任务）— PMO-72 W4：调度循环触发
     * 前后都落 PG（pre: 创建 PENDING status row；post: SUCCEEDED/FAILED + last_run_at）。
     *
     * @param scheduleId     调度 ID（定时调度场景）；即时触发自 submitTask 时可为 null
     * @param taskDescription 任务描述
     * @param cron           当前 cron（nullable）
     */
    private void executeTask(String scheduleId, TaskDescription taskDescription,
            CronExpression cron) {
        // 启动状态 → PG：PENDING（执行记录行由 IoTaskExecution/submitAndExecute 落地; 这里只补 last_run 行）
        long startMs = System.currentTimeMillis();
        String executionId = UUID.randomUUID().toString();
        try {
            taskManagementService.submitAndExecute(taskDescription);
            log.info("定时任务已提交执行 taskId={} taskType={} scheduleId={}",
                    taskDescription.getTaskId(), taskDescription.getTaskType(), scheduleId);
            persistExecutionResult(scheduleId, executionId, taskDescription,
                    TaskStatus.Status.SUCCEEDED.name(), null, startMs);
        } catch (Exception e) {
            log.warn("定时任务提交失败 taskId={} scheduleId={}: {}",
                    taskDescription.getTaskId(), scheduleId, e.getMessage());
            persistExecutionResult(scheduleId, executionId, taskDescription,
                    TaskStatus.Status.FAILED.name(), e.getMessage(), startMs);
        } finally {
            if (scheduleId != null && cron != null) {
                // 执行完毕后（无论成败）排定下一次，确保周期不中断
                rescheduleCron(scheduleId, taskDescription, cron);
            }
        }
    }

    /**
     * PMO-72 W4: 任务执行结果落 PG（执行记录表 td_runtime_task_execution +
     *    plan 行的 last_status / last_run_at）。失败仅 warn。
     *
     * 命中策略：
     *   1) scheduleId != null → 优先 markLastRun(scheduleId)
     *   2) 没有命中再 markLastRun(planKeyFor(task))
     */
    private void persistExecutionResult(String scheduleId, String executionId,
            TaskDescription taskDescription, String status, String errorMessage, long startMs) {
        try {
            if (!(taskPersistenceService instanceof com.chinacreator.gzcm.runtime.core.task.persistence.impl.JdbcTaskPersistenceService)) {
                return;
            }
            com.chinacreator.gzcm.runtime.core.task.persistence.impl.JdbcTaskPersistenceService jdbc =
                    (com.chinacreator.gzcm.runtime.core.task.persistence.impl.JdbcTaskPersistenceService) taskPersistenceService;
            String taskId = planKeyFor(taskDescription);
            // 写 td_runtime_task_execution 执行记录
            jdbc.recordExecution(executionId, taskId, status,
                    new Date(startMs), new Date(), null,
                    errorMessage, null, null);
            // plan.last_status + last_run_at：scheduleId 优先，taskId 兜底
            boolean hit = false;
            if (scheduleId != null) {
                hit = jdbc.markLastRun(scheduleId, new Date(), status);
            }
            if (!hit) {
                hit = jdbc.markLastRun(taskId, new Date(), status);
            }
            if (!hit) {
                log.warn("persistExecutionResult: 未命中 plan 行 scheduleId={} taskId={}",
                        scheduleId, taskId);
            }
        } catch (Exception e) {
            log.warn("persistExecutionResult: 异常 taskId={}: {}",
                    taskDescription.getTaskId(), e.getMessage());
        }
    }

    /**
     * 关闭调度器
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}

