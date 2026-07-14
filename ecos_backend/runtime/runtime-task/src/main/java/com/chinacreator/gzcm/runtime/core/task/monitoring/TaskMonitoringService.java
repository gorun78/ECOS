package com.chinacreator.gzcm.runtime.core.task.monitoring;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;

/**
 * 任务监控服务接口
 * 提供任务执行状态监控和告警功能
 * 
 * @author CDRC Runtime Team
 */
public interface TaskMonitoringService {
    
    /**
     * 监控任务状态
     * 
     * @param taskId 任务ID
     * @return 任务状态
     */
    TaskStatus monitorTask(String taskId);
    
    /**
     * 检测长时间运行的任务
     * 
     * @param timeoutMillis 超时时间（毫秒）
     * @return 超时任务ID列表
     */
    List<String> detectLongRunningTasks(long timeoutMillis);
    
    /**
     * 检测失败任务
     * 
     * @return 失败任务ID列表
     */
    List<String> detectFailedTasks();
    
    /**
     * 检测卡住的任务（长时间处于RUNNING状态）
     * 
     * @param timeoutMillis 超时时间（毫秒）
     * @return 卡住的任务ID列表
     */
    List<String> detectStuckTasks(long timeoutMillis);
    
    /**
     * 注册任务状态监听器
     * 
     * @param listener 监听器
     */
    void registerListener(TaskStatusListener listener);
    
    /**
     * 移除任务状态监听器
     * 
     * @param listener 监听器
     */
    void removeListener(TaskStatusListener listener);
    
    /**
     * 任务状态监听器
     */
    interface TaskStatusListener {
        /**
         * 任务状态变化回调
         * 
         * @param taskId 任务ID
         * @param oldStatus 旧状态
         * @param newStatus 新状态
         */
        void onStatusChanged(String taskId, String oldStatus, String newStatus);
        
        /**
         * 任务超时回调
         * 
         * @param taskId 任务ID
         * @param timeoutMillis 超时时间（毫秒）
         */
        void onTaskTimeout(String taskId, long timeoutMillis);
        
        /**
         * 任务失败回调
         * 
         * @param taskId 任务ID
         * @param errorMessage 错误消息
         */
        void onTaskFailed(String taskId, String errorMessage);
    }
}

