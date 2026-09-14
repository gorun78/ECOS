package com.chinacreator.gzcm.buszhi.workflow.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowTaskService;

/**
 * 任务中心 REST API — 我的任务/签收/完成/转签/驳回。
 *
 * <pre>
 * GET    /api/v1/ecos/tasks                  — 我的任务列表 (?assignee=&limit=)
 * GET    /api/v1/ecos/tasks/{taskId}         — 任务详情
 * POST   /api/v1/ecos/tasks/{taskId}/claim   — 签收任务
 * POST   /api/v1/ecos/tasks/{taskId}/complete — 完成任务
 * POST   /api/v1/ecos/tasks/{taskId}/transfer — 转签
 * POST   /api/v1/ecos/tasks/{taskId}/reject  — 驳回
 * GET    /api/v1/ecos/tasks/statistics       — 任务统计
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/ecos/tasks")
public class WorkflowTaskController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTaskController.class);
    private final WorkflowTaskService taskService;

    public WorkflowTaskController(WorkflowTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> listTasks(
            @RequestParam(required = false) String assignee,
            @RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> tasks = taskService.listTasks(assignee, limit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", tasks);
        result.put("total", tasks.size());
        return ApiResponse.success(result);
    }

    @GetMapping("/{taskId}")
    public ApiResponse<Map<String, Object>> getTask(@PathVariable String taskId) {
        return taskService.getTask(taskId)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("WF-009: 任务 " + taskId + " 不存在"));
    }

    @PostMapping("/{taskId}/claim")
    public ApiResponse<Map<String, Object>> claimTask(
            @PathVariable String taskId,
            @RequestBody Map<String, Object> body) {
        String userId = String.valueOf(body.getOrDefault("userId", "system"));
        return taskService.claimTask(taskId, userId)
            .map(task -> {
                log.info("Task claimed: {} by {}", taskId, userId);
                return ApiResponse.success(task);
            })
            .orElseGet(() -> ApiResponse.badRequest("WF-006: 无法签收任务 " + taskId));
    }

    @PostMapping("/{taskId}/complete")
    public ApiResponse<Map<String, Object>> completeTask(
            @PathVariable String taskId,
            @RequestBody Map<String, Object> body) {
        return taskService.completeTask(taskId, body)
            .map(task -> {
                log.info("Task completed: {}", taskId);
                return ApiResponse.success(task);
            })
            .orElseGet(() -> ApiResponse.notFound("WF-009: 任务 " + taskId + " 不存在"));
    }

    @PostMapping("/{taskId}/transfer")
    public ApiResponse<Map<String, Object>> transferTask(
            @PathVariable String taskId,
            @RequestBody Map<String, Object> body) {
        String targetUserId = String.valueOf(body.getOrDefault("targetUserId", ""));
        if (targetUserId.isBlank()) {
            return ApiResponse.badRequest("WF-003: 转签目标用户不能为空");
        }
        return taskService.transferTask(taskId, targetUserId)
            .map(task -> {
                log.info("Task transferred: {} → {}", taskId, targetUserId);
                return ApiResponse.success(task);
            })
            .orElseGet(() -> ApiResponse.notFound("WF-009: 任务 " + taskId + " 不存在"));
    }

    @PostMapping("/{taskId}/reject")
    public ApiResponse<Map<String, Object>> rejectTask(
            @PathVariable String taskId,
            @RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null ? String.valueOf(body.getOrDefault("reason", "")) : "用户驳回";
        return taskService.rejectTask(taskId, reason)
            .map(task -> {
                log.info("Task rejected: {}", taskId);
                return ApiResponse.success(task);
            })
            .orElseGet(() -> ApiResponse.notFound("WF-009: 任务 " + taskId + " 不存在"));
    }

    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getStatistics(
            @RequestParam(required = false) String userId) {
        Map<String, Object> stats = taskService.getStatistics(userId);
        return ApiResponse.success(stats);
    }
}
