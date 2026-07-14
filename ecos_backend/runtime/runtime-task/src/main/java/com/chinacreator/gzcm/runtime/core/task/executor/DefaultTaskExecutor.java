package com.chinacreator.gzcm.runtime.core.task.executor;

import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;

/**
 * 默认任务执行器
 * 提供基础的任务执行功能
 */
public class DefaultTaskExecutor implements ITaskExecutor {

    @Override
    public String execute(TaskExecutionPlan executionPlan, ITaskStatusCallback statusCallback) 
            throws TaskExecutionException {
        if (executionPlan == null) {
            throw new TaskExecutionException("Execution plan cannot be null");
        }
        
        String taskId = executionPlan.getTaskId();
        
        if (statusCallback != null) {
            TaskStatus status = new TaskStatus();
            status.setTaskId(taskId);
            status.setStatus(TaskStatus.Status.RUNNING);
            statusCallback.onStatusUpdate(status);
        }
        
        String result = String.format("Task %s executed successfully", taskId);
        
        if (statusCallback != null) {
            TaskStatus status = new TaskStatus();
            status.setTaskId(taskId);
            status.setStatus(TaskStatus.Status.SUCCEEDED);
            statusCallback.onStatusUpdate(status);
        }
        
        return result;
    }

    @Override
    public void cancel(String taskId) throws TaskExecutionException {
    }

    @Override
    public void pause(String taskId) throws TaskExecutionException {
    }

    @Override
    public void resume(String taskId) throws TaskExecutionException {
    }

    @Override
    public TaskStatus getStatus(String taskId) throws TaskExecutionException {
        TaskStatus status = new TaskStatus();
        status.setTaskId(taskId);
        status.setStatus(TaskStatus.Status.SUCCEEDED);
        return status;
    }
}
