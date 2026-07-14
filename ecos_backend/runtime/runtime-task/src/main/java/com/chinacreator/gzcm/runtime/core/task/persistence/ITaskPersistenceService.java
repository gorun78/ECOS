package com.chinacreator.gzcm.runtime.core.task.persistence;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;

/**
 * 任务持久化服务接口。
 * 提供任务描述、执行计划、状态的保存与查询能力。
 */
public interface ITaskPersistenceService {

    void saveTask(TaskDescription task) throws TaskPersistenceException;

    TaskDescription getTask(String taskId) throws TaskPersistenceException;

    void savePlan(TaskExecutionPlan plan) throws TaskPersistenceException;

    TaskExecutionPlan getPlan(String taskId) throws TaskPersistenceException;

    void saveStatus(TaskStatus status) throws TaskPersistenceException;

    TaskStatus getStatus(String taskId) throws TaskPersistenceException;

    void deleteTask(String taskId) throws TaskPersistenceException;

    /**
     * Query task descriptions with optional condition and pagination.
     */
    List<TaskDescription> queryTasks(Map<String, Object> condition, int offset, int limit) throws TaskPersistenceException;

    /**
     * Query task statuses with optional condition and pagination.
     */
    List<TaskStatus> queryTaskStatuses(Map<String, Object> condition, int offset, int limit) throws TaskPersistenceException;
}

