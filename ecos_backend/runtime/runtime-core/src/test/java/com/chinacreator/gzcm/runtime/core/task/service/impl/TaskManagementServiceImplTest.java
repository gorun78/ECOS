package com.chinacreator.gzcm.runtime.core.task.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.parser.DefaultTaskParser;
import com.chinacreator.gzcm.runtime.core.task.executor.DefaultTaskExecutor;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService.TaskManagementException;

/**
 * TaskManagementServiceImpl 单元测试
 */
@DisplayName("任务管理服务测试")
class TaskManagementServiceImplTest {

    private TaskManagementServiceImpl taskManagementService;

    @BeforeEach
    void setUp() {
        taskManagementService = new TaskManagementServiceImpl();
        taskManagementService.registerParser("DATA_TRANSFER", new DefaultTaskParser());
        taskManagementService.registerExecutor("DefaultTaskExecutor", new DefaultTaskExecutor());
    }

    @Test
    @DisplayName("提交任务")
    void testSubmitTask() throws Exception {
        TaskDescription task = createTestTask();

        String taskId = taskManagementService.submitTask(task);
        assertNotNull(taskId);
        assertNotNull(task.getTaskId());
        assertEquals(taskId, task.getTaskId());
    }

    @Test
    @DisplayName("根据ID获取任务描述")
    void testGetTaskDescription() throws Exception {
        TaskDescription task = createTestTask();
        String taskId = taskManagementService.submitTask(task);

        TaskDescription found = taskManagementService.getTaskDescription(taskId);
        assertNotNull(found);
        assertEquals(taskId, found.getTaskId());
        assertEquals("测试任务", found.getTaskName());
    }

    @Test
    @DisplayName("查询任务列表")
    void testQueryTasks() throws Exception {
        for (int i = 1; i <= 5; i++) {
            TaskDescription task = createTestTask();
            task.setTaskName("任务" + i);
            taskManagementService.submitTask(task);
        }

        List<TaskDescription> tasks = taskManagementService.queryTasks(null, 0, 10);
        assertNotNull(tasks);
        assertTrue(tasks.size() >= 5);
    }

    @Test
    @DisplayName("获取任务状态")
    void testGetTaskStatus() throws Exception {
        TaskDescription task = createTestTask();
        String taskId = taskManagementService.submitTask(task);

        TaskStatus status = taskManagementService.getTaskStatus(taskId);
        assertNotNull(status);
        assertEquals(taskId, status.getTaskId());
        assertEquals(TaskStatus.Status.PENDING, status.getStatus());
    }

    @Test
    @DisplayName("解析任务")
    void testParseTask() throws Exception {
        TaskDescription task = createTestTask();
        String taskId = taskManagementService.submitTask(task);

        var plan = taskManagementService.parseTask(taskId);
        assertNotNull(plan);
        assertEquals(taskId, plan.getTaskId());
        
        TaskStatus status = taskManagementService.getTaskStatus(taskId);
        assertEquals(TaskStatus.Status.PARSED, status.getStatus());
    }

    @Test
    @DisplayName("执行任务")
    void testExecuteTask() throws Exception {
        TaskDescription task = createTestTask();
        String taskId = taskManagementService.submitTask(task);
        taskManagementService.parseTask(taskId);

        String result = taskManagementService.executeTask(taskId);
        assertNotNull(result);
        
        TaskStatus status = taskManagementService.getTaskStatus(taskId);
        assertEquals(TaskStatus.Status.SUCCEEDED, status.getStatus());
    }

    @Test
    @DisplayName("取消任务")
    void testCancelTask() throws Exception {
        TaskDescription task = createTestTask();
        String taskId = taskManagementService.submitTask(task);

        taskManagementService.cancelTask(taskId);
        
        TaskStatus status = taskManagementService.getTaskStatus(taskId);
        assertEquals(TaskStatus.Status.CANCELLED, status.getStatus());
    }

    @Test
    @DisplayName("暂停和恢复任务")
    void testPauseAndResumeTask() throws Exception {
        TaskDescription task = createTestTask();
        String taskId = taskManagementService.submitTask(task);

        taskManagementService.pauseTask(taskId);
        TaskStatus status = taskManagementService.getTaskStatus(taskId);
        assertEquals(TaskStatus.Status.PAUSED, status.getStatus());

        taskManagementService.resumeTask(taskId);
        status = taskManagementService.getTaskStatus(taskId);
        assertEquals(TaskStatus.Status.RUNNING, status.getStatus());
    }

    private TaskDescription createTestTask() {
        TaskDescription task = new TaskDescription();
        task.setTaskName("测试任务");
        task.setTaskType("DATA_TRANSFER");
        task.setDescription("这是一个测试任务");
        task.setPriority(1);
        Map<String, Object> params = new HashMap<>();
        params.put("source", "test-source");
        task.setParameters(params);
        return task;
    }
}

