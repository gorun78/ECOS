package com.chinacreator.gzcm.runtime.core.task.scheduling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.persistence.ITaskPersistenceService;
import com.chinacreator.gzcm.runtime.core.task.persistence.impl.TaskPersistenceServiceImpl;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.chinacreator.gzcm.runtime.core.task.service.impl.TaskManagementServiceImpl;

/**
 * 任务调度服务实现
 * 使用ScheduledExecutorService实现定时任务和任务依赖管理
 * 
 * @author CDRC Runtime Team
 */
public class TaskSchedulerServiceImpl implements TaskSchedulerService {
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new HashMap<>();
    private final Map<String, TaskDescription> taskDescriptions = new HashMap<>();
    private final ITaskPersistenceService taskPersistenceService;
    private final ITaskManagementService taskManagementService;
    
    public TaskSchedulerServiceImpl() {
        this.taskPersistenceService = new TaskPersistenceServiceImpl();
        TaskManagementServiceImpl managementService = new TaskManagementServiceImpl();
        managementService.setPersistenceService(this.taskPersistenceService);
        this.taskManagementService = managementService;
    }
    
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
        // 简化实现：Cron表达式解析和调度
        // 实际应使用Quartz等调度框架
        String scheduleId = UUID.randomUUID().toString();
        taskDescriptions.put(scheduleId, taskDescription);
        
        // 简化实现：将Cron表达式转换为固定延迟（实际应使用Cron解析器）
        long delay = parseCronToDelay(cronExpression);
        if (delay > 0) {
            ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        if (checkDependencies(taskDescription)) {
                            executeTask(taskDescription);
                        }
                    } catch (Exception e) {
                        // 记录错误
                    }
                },
                delay,
                delay,
                TimeUnit.MILLISECONDS
            );
            scheduledTasks.put(scheduleId, future);
        }
        
        return scheduleId;
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
        } catch (Exception e) {
            // 记录错误
        }
    }
    
    /**
     * 将Cron表达式转换为延迟时间（简化实现）
     * 实际应使用Cron解析库（如Quartz的CronExpression）
     */
    private long parseCronToDelay(String cronExpression) {
        // 简化实现：假设Cron表达式格式为 "秒 分 时 日 月 周"
        // 这里只做简单解析，实际应使用专业的Cron解析库
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return 0;
        }
        
        // 简化实现：如果Cron表达式包含数字，尝试解析为秒数
        // 实际应使用Quartz等库进行完整解析
        try {
            // 这里只是占位实现，实际应使用Cron解析库
            return 60000; // 默认1分钟
        } catch (Exception e) {
            return 0;
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

