package com.chinacreator.gzcm.gateway.controller;

import java.util.*;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.security.RequirePermission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService.TaskManagementException;
import com.chinacreator.gzcm.common.service.IAnalyticsService;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * 任务中心 REST API
 * 
 * @author ECOS Sprint 5.3
 */
@RestController
@RequestMapping("/api/v1/task")
public class TaskController {

    @Autowired
    private ITaskManagementService taskManagementService;

    @Autowired
    @Qualifier("pgAnalyticsService")
    private IAnalyticsService analyticsService;

    // ─── 任务类型分类 ─────────────────────────────────────────

    private static final Map<String, String> TASK_TYPE_CATEGORY = new LinkedHashMap<>();
    private static final List<Map<String, Object>> TASK_TYPE_CATEGORIES = new ArrayList<>();

    static {
        TASK_TYPE_CATEGORY.put("DORIS_SQL", "pipeline");
        TASK_TYPE_CATEGORY.put("ETL", "pipeline");
        TASK_TYPE_CATEGORY.put("DATA_SYNC", "pipeline");
        TASK_TYPE_CATEGORY.put("DATA_INGEST", "pipeline");
        TASK_TYPE_CATEGORY.put("PIPELINE", "pipeline");

        TASK_TYPE_CATEGORY.put("AGENT", "agent");
        TASK_TYPE_CATEGORY.put("AI_AGENT", "agent");
        TASK_TYPE_CATEGORY.put("LLM_TASK", "agent");
        TASK_TYPE_CATEGORY.put("KG_SYNC", "agent");

        TASK_TYPE_CATEGORY.put("REALTIME", "realtime");
        TASK_TYPE_CATEGORY.put("STREAMING", "realtime");
        TASK_TYPE_CATEGORY.put("MONITOR", "realtime");
        TASK_TYPE_CATEGORY.put("ALERT", "realtime");
        TASK_TYPE_CATEGORY.put("TELEMETRY", "realtime");

        TASK_TYPE_CATEGORY.put("DATA_QUALITY", "management");
        TASK_TYPE_CATEGORY.put("REPORT", "management");
        TASK_TYPE_CATEGORY.put("MAINTENANCE", "management");
        TASK_TYPE_CATEGORY.put("BACKUP", "management");
        TASK_TYPE_CATEGORY.put("CONFIG", "management");
        TASK_TYPE_CATEGORY.put("ADMIN", "management");

        TASK_TYPE_CATEGORIES.add(categoryEntry("pipeline", "管道",
                "DORIS_SQL", "ETL", "DATA_SYNC", "DATA_INGEST", "PIPELINE"));
        TASK_TYPE_CATEGORIES.add(categoryEntry("agent", "Agent",
                "AGENT", "AI_AGENT", "LLM_TASK", "KG_SYNC"));
        TASK_TYPE_CATEGORIES.add(categoryEntry("realtime", "实时",
                "REALTIME", "STREAMING", "MONITOR", "ALERT", "TELEMETRY"));
        TASK_TYPE_CATEGORIES.add(categoryEntry("management", "管理",
                "DATA_QUALITY", "REPORT", "MAINTENANCE", "BACKUP", "CONFIG", "ADMIN"));
    }

    private static Map<String, Object> categoryEntry(String key, String label, String... types) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("key", key);
        entry.put("label", label);
        entry.put("types", Arrays.asList(types));
        return entry;
    }

    /**
     * 根据 taskType 返回所属分类 key
     */
    public static String getCategory(String taskType) {
        if (taskType == null) return null;
        return TASK_TYPE_CATEGORY.getOrDefault(taskType.toUpperCase(), null);
    }

    // ─── 任务 CRUD ──────────────────────────────────────────

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> listTasks(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String createdBy) {

        Map<String, Object> condition = new HashMap<>();
        if (taskType != null) condition.put("taskType", taskType);
        if (status != null) condition.put("status", status);
        if (createdBy != null) condition.put("createdBy", createdBy);

        try {
            List<TaskDescription> tasks = taskManagementService.queryTasks(condition, offset, limit);
            List<Map<String, Object>> items = tasks.stream()
                .map(this::toMap)
                .collect(Collectors.toList());

            return ApiResponse.success(Map.of(
                "items", items,
                "total", items.size(),
                "offset", offset,
                "limit", limit
            ));
        } catch (TaskManagementException e) {
            return ApiResponse.error(-1, e.getMessage());
        }
    }

    @GetMapping("/{taskId}")
    public ApiResponse<Map<String, Object>> getTask(@PathVariable String taskId) {
        try {
            TaskDescription task = taskManagementService.getTaskDescription(taskId);
            TaskStatus status = taskManagementService.getTaskStatus(taskId);

            return ApiResponse.success(Map.of(
                "task", toMap(task),
                "status", toStatusMap(status)
            ));
        } catch (TaskManagementException e) {
            return ApiResponse.error(-1, e.getMessage());
        }
    }

    @PostMapping("/submit")
    public ApiResponse<Map<String, Object>> submitTask(@RequestBody Map<String, Object> body) {
        TaskDescription taskDesc = new TaskDescription();
        taskDesc.setTaskName((String) body.get("taskName"));
        taskDesc.setTaskType((String) body.get("taskType"));
        taskDesc.setDescription((String) body.get("description"));

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.get("params");
        taskDesc.setParameters(params);

        if (body.get("priority") != null) {
            taskDesc.setPriority(((Number) body.get("priority")).intValue());
        }

        try {
            String taskId = taskManagementService.submitTask(taskDesc);
            return ApiResponse.success(Map.of(
                "taskId", taskId,
                "status", "SUBMITTED"
            ));
        } catch (TaskManagementException e) {
            return ApiResponse.error(-1, e.getMessage());
        }
    }

    @PostMapping("/{taskId}/execute")
    public ApiResponse<Map<String, Object>> executeTask(@PathVariable String taskId) {
        try {
            String execResult = taskManagementService.executeTask(taskId);
            return ApiResponse.success(Map.of(
                "taskId", taskId,
                "result", execResult != null ? execResult : "ASYNC_EXECUTING"
            ));
        } catch (TaskManagementException e) {
            return ApiResponse.error(-1, e.getMessage());
        }
    }

    @GetMapping("/{taskId}/status")
    public ApiResponse<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        try {
            TaskStatus status = taskManagementService.getTaskStatus(taskId);
            return ApiResponse.success(toStatusMap(status));
        } catch (TaskManagementException e) {
            return ApiResponse.error(-1, e.getMessage());
        }
    }

    @PostMapping("/{taskId}/cancel")
    public ApiResponse<Map<String, Object>> cancelTask(@PathVariable String taskId) {
        try {
            taskManagementService.cancelTask(taskId);
            return ApiResponse.success(Map.of("message", "Task " + taskId + " cancelled"));
        } catch (TaskManagementException e) {
            return ApiResponse.error(-1, e.getMessage());
        }
    }

    @PostMapping("/{taskId}/pause")
    public ApiResponse<Map<String, Object>> pauseTask(@PathVariable String taskId) {
        try {
            taskManagementService.pauseTask(taskId);
            return ApiResponse.success(Map.of("message", "Task " + taskId + " paused"));
        } catch (TaskManagementException e) {
            return ApiResponse.error(-1, e.getMessage());
        }
    }

    @PostMapping("/{taskId}/resume")
    public ApiResponse<Map<String, Object>> resumeTask(@PathVariable String taskId) {
        try {
            taskManagementService.resumeTask(taskId);
            return ApiResponse.success(Map.of("message", "Task " + taskId + " resumed"));
        } catch (TaskManagementException e) {
            return ApiResponse.error(-1, e.getMessage());
        }
    }

    @PostMapping("/{taskId}/archive")
    public ApiResponse<Map<String, Object>> archiveTask(@PathVariable String taskId) {
        try {
            taskManagementService.cancelTask(taskId);
            return ApiResponse.success(Map.of("message", "Task " + taskId + " archived"));
        } catch (TaskManagementException e) {
            return ApiResponse.error(-1, e.getMessage());
        }
    }

    @PostMapping("/batch")
    public ApiResponse<Map<String, Object>> batchOperation(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> taskIds = (List<String>) body.get("taskIds");
        String action = (String) body.get("action");
        
        if (taskIds == null || taskIds.isEmpty()) {
            return ApiResponse.error(-1, "taskIds required");
        }
        if (action == null) {
            return ApiResponse.error(-1, "action required");
        }

        int successCount = 0;
        List<String> errors = new ArrayList<>();
        for (String id : taskIds) {
            try {
                switch (action) {
                    case "cancel" -> taskManagementService.cancelTask(id);
                    case "pause" -> taskManagementService.pauseTask(id);
                    case "resume" -> taskManagementService.resumeTask(id);
                    case "archive" -> taskManagementService.cancelTask(id);
                    default -> { errors.add("Unknown action: " + action); continue; }
                }
                successCount++;
            } catch (Exception e) {
                errors.add(id + ": " + e.getMessage());
            }
        }

        return ApiResponse.success(Map.of(
            "successCount", successCount,
            "errors", errors
        ));
    }

    // ─── 任务统计 ──────────────────────────────────────────

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> taskStats() {
        try {
            Map<String, Object> stats = taskManagementService.getTaskStats();
            return ApiResponse.success(stats);
        } catch (Exception e) {
            return ApiResponse.error(-1, "获取任务统计失败: " + e.getMessage());
        }
    }

    // ─── 分析引擎健康检查 ───────────────────────────────────

    @GetMapping("/doris/health")
    public ApiResponse<Map<String, Object>> dorisHealth() {
        Map<String, Object> health = analyticsService.health();
        return ApiResponse.success(health);
    }

    // ─── 任务类型分类 ─────────────────────────────────────────

    @GetMapping("/types")
    public ApiResponse<Map<String, Object>> taskTypes() {
        return ApiResponse.success(Map.of(
            "categories", TASK_TYPE_CATEGORIES
        ));
    }

    // ─── 辅助方法 ─────────────────────────────────────────

    private Map<String, Object> toMap(TaskDescription task) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", task.getTaskId());
        m.put("taskName", task.getTaskName());
        m.put("taskType", task.getTaskType());
        m.put("category", getCategory(task.getTaskType()));
        m.put("description", task.getDescription());
        m.put("priority", task.getPriority());
        m.put("createTime", task.getCreateTime() != null ? task.getCreateTime().toString() : null);
        m.put("createdBy", task.getCreatedBy());
        m.put("parameters", task.getParameters());
        return m;
    }

    private Map<String, Object> toStatusMap(TaskStatus status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", status.getTaskId());
        m.put("status", status.getStatus() != null ? status.getStatus().name() : "UNKNOWN");
        m.put("statusMessage", status.getStatusMessage());
        m.put("progress", status.getProgress());
        m.put("startedAt", status.getStartTime() != null ? status.getStartTime().toString() : null);
        m.put("completedAt", status.getEndTime() != null ? status.getEndTime().toString() : null);
        return m;
    }
}
