package com.chinacreator.gzcm.gateway.controller;

import java.util.*;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 引擎任务汇聚控制器 — 对标 PMO 指令阶段4.5 V6/V7。
 *
 * <p>统一端点:
 * <ul>
 *   <li>POST /api/v1/engine/{type}/tasks — 提交引擎任务</li>
 *   <li>GET  /api/v1/engine/{type}/tasks/{taskId} — 查询任务状态</li>
 *   <li>GET  /api/v1/engine/tasks — 汇聚全部引擎任务</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/engine")
public class EngineTaskController {

    @Autowired
    private ITaskManagementService taskManagementService;

    /** 提交引擎任务 (V6) */
    @PostMapping("/{engineType}/tasks")
    public ApiResponse<Map<String, Object>> submitEngineTask(
            @PathVariable String engineType,
            @RequestBody Map<String, Object> body) {
        TaskDescription taskDesc = new TaskDescription();
        taskDesc.setTaskName((String) body.getOrDefault("taskName", engineType + "-task"));
        taskDesc.setTaskType((String) body.getOrDefault("type", "ENGINE_TASK"));
        taskDesc.setDescription((String) body.get("description"));

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.get("params");
        taskDesc.setParameters(params != null ? params : Map.of("engine", engineType));

        try {
            String taskId = taskManagementService.submitTask(taskDesc);
            return ApiResponse.success(Map.of(
                "taskId", taskId,
                "engine", engineType,
                "status", "SUBMITTED"
            ));
        } catch (Exception e) {
            return ApiResponse.error(-1, "任务提交失败: " + e.getMessage());
        }
    }

    /** 查询引擎任务状态 (V6) */
    @GetMapping("/{engineType}/tasks/{taskId}")
    public ApiResponse<Map<String, Object>> getEngineTask(
            @PathVariable String engineType,
            @PathVariable String taskId) {
        try {
            TaskDescription task = taskManagementService.getTaskDescription(taskId);
            TaskStatus status = taskManagementService.getTaskStatus(taskId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("taskId", task.getTaskId());
            result.put("taskName", task.getTaskName());
            result.put("taskType", task.getTaskType());
            result.put("engine", engineType);
            result.put("status", status.getStatus() != null ? status.getStatus().name() : "UNKNOWN");
            result.put("progress", status.getProgress());
            result.put("startedAt", status.getStartTime());
            result.put("completedAt", status.getEndTime());
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(-1, "查询任务失败: " + e.getMessage());
        }
    }

    /** 汇聚全部引擎任务 (V7) */
    @GetMapping("/tasks")
    public ApiResponse<List<Map<String, Object>>> aggregateEngineTasks() {
        try {
            List<TaskDescription> tasks = taskManagementService.queryTasks(Map.of(), 0, 100);
            List<Map<String, Object>> result = tasks.stream()
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("taskId", t.getTaskId());
                    m.put("taskName", t.getTaskName());
                    m.put("taskType", t.getTaskType());
                    try {
                        TaskStatus s = taskManagementService.getTaskStatus(t.getTaskId());
                        m.put("status", s.getStatus() != null ? s.getStatus().name() : "UNKNOWN");
                        m.put("progress", s.getProgress());
                    } catch (Exception ignored) {
                        m.put("status", "UNKNOWN");
                        m.put("progress", 0);
                    }
                    return m;
                })
                .collect(Collectors.toList());
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(-1, "汇聚任务失败: " + e.getMessage());
        }
    }
}
