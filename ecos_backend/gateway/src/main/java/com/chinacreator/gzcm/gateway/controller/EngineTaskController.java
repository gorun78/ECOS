package com.chinacreator.gzcm.gateway.controller;

import java.util.*;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(EngineTaskController.class);

    @Autowired
    private ITaskManagementService taskManagementService;

    /** 提交引擎任务 (V6) */
    @PostMapping("/{engineType}/tasks")
    public ApiResponse<EngineTaskSubmitVO> submitEngineTask(
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
            return ApiResponse.success(new EngineTaskSubmitVO(taskId, engineType, "SUBMITTED"));
        } catch (Exception e) {
            log.error("引擎任务提交失败", e);
            return ApiResponse.error(-1, "任务提交失败");
        }
    }

    /** 查询引擎任务状态 (V6) */
    @GetMapping("/{engineType}/tasks/{taskId}")
    public ApiResponse<EngineTaskDetailVO> getEngineTask(
            @PathVariable String engineType,
            @PathVariable String taskId) {
        try {
            TaskDescription task = taskManagementService.getTaskDescription(taskId);
            TaskStatus status = taskManagementService.getTaskStatus(taskId);

            return ApiResponse.success(new EngineTaskDetailVO(task.getTaskId(), task.getTaskName(),
                    task.getTaskType(), engineType,
                    status.getStatus() != null ? status.getStatus().name() : "UNKNOWN",
                    status.getProgress(), status.getStartTime(), status.getEndTime()));
        } catch (Exception e) {
            log.error("引擎任务查询失败 taskId={}", taskId, e);
            return ApiResponse.error(-1, "查询任务失败");
        }
    }

    /** 汇聚全部引擎任务 (V7) */
    @GetMapping("/tasks")
    public ApiResponse<List<EngineTaskRowVO>> aggregateEngineTasks() {
        try {
            List<TaskDescription> tasks = taskManagementService.queryTasks(Map.of(), 0, 100);
            List<EngineTaskRowVO> result = tasks.stream()
                .map(t -> {
                    try {
                        TaskStatus s = taskManagementService.getTaskStatus(t.getTaskId());
                        return new EngineTaskRowVO(t.getTaskId(), t.getTaskName(), t.getTaskType(),
                                s.getStatus() != null ? s.getStatus().name() : "UNKNOWN",
                                s.getProgress());
                    } catch (Exception ignored) {
                        return new EngineTaskRowVO(t.getTaskId(), t.getTaskName(), t.getTaskType(),
                                "UNKNOWN", 0);
                    }
                })
                .collect(Collectors.toList());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("引擎任务汇聚失败", e);
            return ApiResponse.error(-1, "汇聚任务失败");
        }
    }

    /** 提交回执（3 键）：taskId/engine/status（status 恒 SUBMITTED）。 */
    public record EngineTaskSubmitVO(String taskId, String engine, String status) {
    }

    /** 任务详情（8 键）：taskId/taskName/taskType/engine/status/progress/startedAt/completedAt。 */
    public record EngineTaskDetailVO(String taskId, String taskName, String taskType, String engine,
                                     String status, Object progress, Object startedAt,
                                     Object completedAt) {
    }

    /** 汇聚行（5 键）：taskId/taskName/taskType/status/progress（取状态失败时 UNKNOWN/0）。 */
    public record EngineTaskRowVO(String taskId, String taskName, String taskType, String status,
                                  Object progress) {
    }
}
