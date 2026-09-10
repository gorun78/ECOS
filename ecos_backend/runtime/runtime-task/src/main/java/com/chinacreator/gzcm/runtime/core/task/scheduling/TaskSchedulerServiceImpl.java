package com.chinacreator.gzcm.runtime.core.task.scheduling;

import java.util.ArrayList;
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
 * 任务调度服务实现
 * 使用ScheduledExecutorService实现定时任务和任务依赖管理
 * 
 * @author CDRC Runtime Team
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
                executeTask(taskDescription);
            } catch (Exception e) {
                // 记录错误
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

        // 真实 cron 周期调度：计算当前时刻的下次触发时间，触发后按 next() 重排下一次
        rescheduleCron(scheduleId, taskDescription, cron);
        log.info("定时任务已注册 scheduleId={} cron={} taskId={}",
                scheduleId, normalized, taskDescription.getTaskId());
        return scheduleId;
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
                        executeTask(taskDescription);
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
                    executeTask(taskDescription);
                }
            } catch (Exception e) {
                // 记录错误
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
                    executeTask(taskDescription);
                }
            } catch (Exception e) {
                // 记录错误
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
     * 执行任务
     */
    private void executeTask(TaskDescription taskDescription) {
        try {
            taskManagementService.submitAndExecute(taskDescription);
            log.info("定时任务已提交执行 task={} taskType={}",
                    taskDescription.getTaskId(), taskDescription.getTaskType());
        } catch (Exception e) {
            log.warn("定时任务提交失败 task={}: {}", taskDescription.getTaskId(), e.getMessage());
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

