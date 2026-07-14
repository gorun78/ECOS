package com.chinacreator.gzcm.runtime.core.task.monitoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.chinacreator.gzcm.runtime.core.alert.IAlertService;
import com.chinacreator.gzcm.runtime.core.alert.service.impl.AlertServiceImpl;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.persistence.ITaskPersistenceService;
import com.chinacreator.gzcm.runtime.core.task.persistence.impl.TaskPersistenceServiceImpl;

/**
 * Task monitoring service implementation.
 */
public class TaskMonitoringServiceImpl implements TaskMonitoringService {

    private final ITaskPersistenceService taskPersistenceService;
    private final IAlertService alertService;
    private final List<TaskStatusListener> listeners = new CopyOnWriteArrayList<>();

    public TaskMonitoringServiceImpl() {
        this.taskPersistenceService = new TaskPersistenceServiceImpl();
        this.alertService = new AlertServiceImpl();
    }

    public TaskMonitoringServiceImpl(ITaskPersistenceService taskPersistenceService, IAlertService alertService) {
        this.taskPersistenceService = taskPersistenceService;
        this.alertService = alertService;
    }

    @Override
    public TaskStatus monitorTask(String taskId) {
        if (taskId == null) {
            return null;
        }

        try {
            return taskPersistenceService.getStatus(taskId);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<String> detectLongRunningTasks(long timeoutMillis) {
        List<String> longRunningTasks = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (TaskStatus status : queryStatusesByStatus(TaskStatus.Status.RUNNING)) {
            if (status.getTaskId() == null || status.getStartTime() == null) {
                continue;
            }
            if (now - status.getStartTime().getTime() > timeoutMillis) {
                longRunningTasks.add(status.getTaskId());
            }
        }

        return longRunningTasks;
    }

    @Override
    public List<String> detectFailedTasks() {
        List<String> failedTasks = new ArrayList<>();

        for (TaskStatus status : queryStatusesByStatus(TaskStatus.Status.FAILED)) {
            if (status.getTaskId() != null) {
                failedTasks.add(status.getTaskId());
            }
        }

        return failedTasks;
    }

    @Override
    public List<String> detectStuckTasks(long timeoutMillis) {
        List<String> stuckTasks = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (TaskStatus status : queryStatusesByStatus(TaskStatus.Status.RUNNING)) {
            if (status.getTaskId() == null) {
                continue;
            }
            long anchor = status.getUpdateTime() != null
                    ? status.getUpdateTime().getTime()
                    : (status.getStartTime() != null ? status.getStartTime().getTime() : -1L);
            if (anchor > 0 && now - anchor > timeoutMillis) {
                stuckTasks.add(status.getTaskId());
            }
        }

        return stuckTasks;
    }

    @Override
    public void registerListener(TaskStatusListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(TaskStatusListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notify listeners on status change.
     */
    public void notifyStatusChanged(String taskId, String oldStatus, String newStatus) {
        for (TaskStatusListener listener : listeners) {
            try {
                listener.onStatusChanged(taskId, oldStatus, newStatus);
            } catch (Exception e) {
                // ignore listener exceptions
            }
        }
    }

    /**
     * Notify listeners on timeout and trigger alert.
     */
    public void notifyTaskTimeout(String taskId, long timeoutMillis) {
        for (TaskStatusListener listener : listeners) {
            try {
                listener.onTaskTimeout(taskId, timeoutMillis);
            } catch (Exception e) {
                // ignore listener exceptions
            }
        }

        try {
            alertService.triggerAlert("TASK_TIMEOUT", "TASK_TIMEOUT", null, taskId, "Task timeout: " + taskId);
        } catch (Exception e) {
            // ignore alert exceptions
        }
    }

    /**
     * Notify listeners on failure and trigger alert.
     */
    public void notifyTaskFailed(String taskId, String errorMessage) {
        for (TaskStatusListener listener : listeners) {
            try {
                listener.onTaskFailed(taskId, errorMessage);
            } catch (Exception e) {
                // ignore listener exceptions
            }
        }

        try {
            alertService.triggerAlert("TASK_FAILED", "TASK_FAILED", null, taskId,
                    "Task failed: " + taskId + ", error: " + errorMessage);
        } catch (Exception e) {
            // ignore alert exceptions
        }
    }

    private List<TaskStatus> queryStatusesByStatus(TaskStatus.Status status) {
        try {
            Map<String, Object> condition = new HashMap<>();
            condition.put("status", status.name());
            return taskPersistenceService.queryTaskStatuses(condition, 0, Integer.MAX_VALUE);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
