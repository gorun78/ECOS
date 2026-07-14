package com.chinacreator.gzcm.runtime.core.task.scheduling;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;

/**
 * 任务调度服务接口
 * 提供定时任务和任务依赖管理功能
 * 
 * @author CDRC Runtime Team
 */
public interface TaskSchedulerService {
    
    /**
     * 调度任务（立即执行）
     * 
     * @param taskDescription 任务描述
     * @return 调度ID
     */
    String scheduleTask(TaskDescription taskDescription);
    
    /**
     * 调度定时任务（使用Cron表达式）
     * 
     * @param taskDescription 任务描述
     * @param cronExpression Cron表达式
     * @return 调度ID
     */
    String scheduleTask(TaskDescription taskDescription, String cronExpression);
    
    /**
     * 调度延迟任务
     * 
     * @param taskDescription 任务描述
     * @param delayMillis 延迟时间（毫秒）
     * @return 调度ID
     */
    String scheduleTask(TaskDescription taskDescription, long delayMillis);
    
    /**
     * 调度周期性任务
     * 
     * @param taskDescription 任务描述
     * @param initialDelayMillis 初始延迟（毫秒）
     * @param periodMillis 执行周期（毫秒）
     * @return 调度ID
     */
    String schedulePeriodicTask(TaskDescription taskDescription, long initialDelayMillis, long periodMillis);
    
    /**
     * 取消任务调度
     * 
     * @param scheduleId 调度ID
     */
    void cancelSchedule(String scheduleId);
    
    /**
     * 检查任务依赖
     * 
     * @param taskDescription 任务描述
     * @return true表示所有依赖任务已完成，false表示有依赖任务未完成
     */
    boolean checkDependencies(TaskDescription taskDescription);
    
    /**
     * 获取任务的所有依赖任务ID
     * 
     * @param taskId 任务ID
     * @return 依赖任务ID列表
     */
    List<String> getDependencies(String taskId);
    
    /**
     * 获取所有已调度的任务
     * 
     * @return 调度ID列表
     */
    List<String> getScheduledTasks();
}

