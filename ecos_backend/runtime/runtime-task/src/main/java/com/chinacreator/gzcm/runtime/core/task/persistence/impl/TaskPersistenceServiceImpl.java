package com.chinacreator.gzcm.runtime.core.task.persistence.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.persistence.ITaskPersistenceService;
import com.chinacreator.gzcm.runtime.core.task.persistence.TaskPersistenceException;

/**
 * 任务持久化服务实现
 * 当前使用内存存储，后续可扩展为数据库持久化
 * 
 * @author CDRC Runtime Team
 */
public class TaskPersistenceServiceImpl implements ITaskPersistenceService {

    private final Map<String, TaskDescription> tasks = new ConcurrentHashMap<>();
    private final Map<String, TaskExecutionPlan> plans = new ConcurrentHashMap<>();
    private final Map<String, TaskStatus> statuses = new ConcurrentHashMap<>();

    @Override
    public void saveTask(TaskDescription task) throws TaskPersistenceException {
        if (task == null) {
            throw new TaskPersistenceException("TaskDescription cannot be null");
        }
        if (task.getTaskId() == null) {
            throw new TaskPersistenceException("Task ID cannot be null");
        }
        tasks.put(task.getTaskId(), task);
    }

    @Override
    public TaskDescription getTask(String taskId) throws TaskPersistenceException {
        if (taskId == null) {
            throw new TaskPersistenceException("Task ID cannot be null");
        }
        return tasks.get(taskId);
    }

    @Override
    public void savePlan(TaskExecutionPlan plan) throws TaskPersistenceException {
        if (plan == null) {
            throw new TaskPersistenceException("TaskExecutionPlan cannot be null");
        }
        if (plan.getTaskId() == null) {
            throw new TaskPersistenceException("Task ID cannot be null");
        }
        plans.put(plan.getTaskId(), plan);
    }

    @Override
    public TaskExecutionPlan getPlan(String taskId) throws TaskPersistenceException {
        if (taskId == null) {
            throw new TaskPersistenceException("Task ID cannot be null");
        }
        return plans.get(taskId);
    }

    @Override
    public void saveStatus(TaskStatus status) throws TaskPersistenceException {
        if (status == null) {
            throw new TaskPersistenceException("TaskStatus cannot be null");
        }
        if (status.getTaskId() == null) {
            throw new TaskPersistenceException("Task ID cannot be null");
        }
        statuses.put(status.getTaskId(), status);
    }

    @Override
    public TaskStatus getStatus(String taskId) throws TaskPersistenceException {
        if (taskId == null) {
            throw new TaskPersistenceException("Task ID cannot be null");
        }
        return statuses.get(taskId);
    }

    @Override
    public void deleteTask(String taskId) throws TaskPersistenceException {
        if (taskId == null) {
            throw new TaskPersistenceException("Task ID cannot be null");
        }
        tasks.remove(taskId);
        plans.remove(taskId);
        statuses.remove(taskId);
    }

    @Override
    public List<TaskDescription> queryTasks(Map<String, Object> condition, int offset, int limit)
            throws TaskPersistenceException {
        List<TaskDescription> result = new ArrayList<>(tasks.values());
        result = result.stream()
                .filter(task -> matchTaskCondition(task, condition))
                .sorted(Comparator.comparing(TaskDescription::getCreateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        return paginate(result, offset, limit);
    }

    @Override
    public List<TaskStatus> queryTaskStatuses(Map<String, Object> condition, int offset, int limit)
            throws TaskPersistenceException {
        List<TaskStatus> result = new ArrayList<>(statuses.values());
        result = result.stream()
                .filter(status -> matchStatusCondition(status, condition))
                .sorted(Comparator.comparing(TaskStatus::getUpdateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        return paginate(result, offset, limit);
    }

    private boolean matchTaskCondition(TaskDescription task, Map<String, Object> condition) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }
        Object taskType = condition.get("taskType");
        if (taskType != null && !taskType.equals(task.getTaskType())) {
            return false;
        }
        Object tenantId = condition.get("tenantId");
        if (tenantId != null && !tenantId.equals(task.getTenantId())) {
            return false;
        }
        Object createdBy = condition.get("createdBy");
        if (createdBy != null && !createdBy.equals(task.getCreatedBy())) {
            return false;
        }
        Object taskId = condition.get("taskId");
        if (taskId != null && !taskId.equals(task.getTaskId())) {
            return false;
        }
        return true;
    }

    private boolean matchStatusCondition(TaskStatus status, Map<String, Object> condition) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }
        Object taskId = condition.get("taskId");
        if (taskId != null && !taskId.equals(status.getTaskId())) {
            return false;
        }
        Object statusValue = condition.get("status");
        if (statusValue != null) {
            String expected = String.valueOf(statusValue);
            String actual = status.getStatus() != null ? status.getStatus().name() : null;
            if (!expected.equals(actual)) {
                return false;
            }
        }
        return true;
    }

    private <T> List<T> paginate(List<T> input, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = limit <= 0 ? Integer.MAX_VALUE : limit;
        int fromIndex = Math.min(safeOffset, input.size());
        int toIndex = Math.min(fromIndex + safeLimit, input.size());
        return new ArrayList<>(input.subList(fromIndex, toIndex));
    }
}

