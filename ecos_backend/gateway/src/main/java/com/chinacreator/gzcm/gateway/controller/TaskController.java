package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.service.IAnalyticsService;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService.TaskManagementException;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务管理端点（runtime-task 单源；详细设计-10 F10-11 调度面）。
 *
 * <p><b>读写载体（#147CV）</b>：任务/状态/列表/回执全部改 record，键名与旧 {@code Map} 时代
 * 逐个一致（{@code taskId/category/statusMessage/startedAt/...}）。仍保留 {@code Map} 的两处是
 * <b>属主契约</b>：{@code taskManagementService.queryTasks(Map condition, ...)} 的查询条件入参、
 * {@code getTaskStats()} 与 {@code analyticsService.health()} 的自由统计结果；以及任务
 * {@code params}（自由 JSON 配置，键面由提交方定义）。</p>
 */
@RestController
@RequestMapping("/api/v1/task")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    @Autowired
    private ITaskManagementService taskManagementService;

    @Autowired
    @Qualifier("pgAnalyticsService")
    private IAnalyticsService analyticsService;

    /** taskType → 分类 key（大小写不敏感；未知类型返回 null）。 */
    private static final Map<String, String> TASK_TYPE_CATEGORY = new LinkedHashMap<>();

    /** 任务类型分类目录（诊断/表单面单源）。 */
    private static final List<TaskTypeCategoryVO> TASK_TYPE_CATEGORIES = new ArrayList<>();

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

    /** 任务类型分类条目（3 键）。 */
    public record TaskTypeCategoryVO(@JsonProperty("key") String key,
                                     @JsonProperty("label") String label,
                                     @JsonProperty("types") List<String> types) {
    }

    /** 任务描述视图（9 键；{@code parameters} 为自由 JSON 配置）。 */
    public record TaskVO(@JsonProperty("taskId") String taskId,
                         @JsonProperty("taskName") String taskName,
                         @JsonProperty("taskType") String taskType,
                         @JsonProperty("category") String category,
                         @JsonProperty("description") String description,
                         @JsonProperty("priority") Integer priority,
                         @JsonProperty("createTime") String createTime,
                         @JsonProperty("createdBy") String createdBy,
                         @JsonProperty("parameters") Map<String, Object> parameters) {
    }

    /** 任务状态视图（6 键）。 */
    public record TaskStatusVO(@JsonProperty("taskId") String taskId,
                               @JsonProperty("status") String status,
                               @JsonProperty("statusMessage") String statusMessage,
                               @JsonProperty("progress") Integer progress,
                               @JsonProperty("startedAt") String startedAt,
                               @JsonProperty("completedAt") String completedAt) {
    }

    /** 列表响应（4 键）。 */
    public record TaskListVO(@JsonProperty("items") List<TaskVO> items,
                             @JsonProperty("total") int total,
                             @JsonProperty("offset") int offset,
                             @JsonProperty("limit") int limit) {
    }

    /** 详情响应（task + status）。 */
    public record TaskDetailVO(@JsonProperty("task") TaskVO task,
                               @JsonProperty("status") TaskStatusVO status) {
    }

    /** 提交响应（2 键）。 */
    public record SubmitResultVO(@JsonProperty("taskId") String taskId,
                                 @JsonProperty("status") String status) {
    }

    /** 执行响应（2 键）。 */
    public record ExecuteResultVO(@JsonProperty("taskId") String taskId,
                                  @JsonProperty("result") String result) {
    }

    /** 操作回执（1 键）。 */
    public record MessageVO(@JsonProperty("message") String message) {
    }

    /** 批量操作回执（2 键）。 */
    public record BatchResultVO(@JsonProperty("successCount") int successCount,
                                @JsonProperty("errors") List<String> errors) {
    }

    /** 任务类型目录（1 键）。 */
    public record TaskTypesVO(@JsonProperty("categories") List<TaskTypeCategoryVO> categories) {
    }

    /** 提交请求体（params 为自由 JSON 配置，键面由提交方定义）。 */
    public record SubmitTaskRequest(String taskName, String taskType, String description,
                                    Map<String, Object> params, Integer priority) {
    }

    /** 批量操作请求体。 */
    public record BatchOperationRequest(List<String> taskIds, String action) {
    }

    private static TaskTypeCategoryVO categoryEntry(String key, String label, String... types) {
        return new TaskTypeCategoryVO(key, label, Arrays.asList(types));
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
    public ApiResponse<TaskListVO> listTasks(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String createdBy) {

        Map<String, Object> condition = new LinkedHashMap<>();
        if (taskType != null) condition.put("taskType", taskType);
        if (status != null) condition.put("status", status);
        if (createdBy != null) condition.put("createdBy", createdBy);

        try {
            List<TaskDescription> tasks = taskManagementService.queryTasks(condition, offset, limit);
            List<TaskVO> items = tasks.stream()
                .map(this::toVO)
                .collect(Collectors.toList());

            return ApiResponse.success(new TaskListVO(items, items.size(), offset, limit));
        } catch (TaskManagementException e) {
            return ApiResponse.error(-1, e.getMessage());
        }
    }

    @GetMapping("/{taskId}")
    public ApiResponse<TaskDetailVO> getTask(@PathVariable String taskId) {
        try {
            TaskDescription task = taskManagementService.getTaskDescription(taskId);
            if (task == null) {
                return ApiResponse.notFound("任务不存在: " + taskId);
            }
            TaskStatus status = taskManagementService.getTaskStatus(taskId);

            return ApiResponse.success(new TaskDetailVO(toVO(task), toStatusVO(status)));
        } catch (TaskManagementException e) {
            return ApiResponse.error(-1, e.getMessage());
        }
    }

    @PostMapping("/submit")
    public ApiResponse<SubmitResultVO> submitTask(@RequestBody SubmitTaskRequest body) {
        TaskDescription taskDesc = new TaskDescription();
        taskDesc.setTaskName(body.taskName());
        taskDesc.setTaskType(body.taskType());
        taskDesc.setDescription(body.description());
        taskDesc.setParameters(body.params());
        if (body.priority() != null) {
            taskDesc.setPriority(body.priority());
        }

        try {
            String taskId = taskManagementService.submitTask(taskDesc);
            return ApiResponse.success(new SubmitResultVO(taskId, "SUBMITTED"));
        } catch (TaskManagementException e) {
            return ApiResponse.error(-1, e.getMessage());
        }
    }

    @PostMapping("/{taskId}/execute")
    public ApiResponse<ExecuteResultVO> executeTask(@PathVariable String taskId) {
        try {
            String execResult = taskManagementService.executeTask(taskId);
            return ApiResponse.success(new ExecuteResultVO(taskId,
                    execResult != null ? execResult : "ASYNC_EXECUTING"));
        } catch (TaskManagementException e) {
            return ApiResponse.error(-1, e.getMessage());
        }
    }

    @GetMapping("/{taskId}/status")
    public ApiResponse<TaskStatusVO> getTaskStatus(@PathVariable String taskId) {
        try {
            TaskStatus status = taskManagementService.getTaskStatus(taskId);
            if (status == null) {
                return ApiResponse.notFound("任务状态不存在: " + taskId);
            }
            return ApiResponse.success(toStatusVO(status));
        } catch (TaskManagementException e) {
            return ApiResponse.error(-1, e.getMessage());
        }
    }

    @PostMapping("/{taskId}/cancel")
    public ApiResponse<MessageVO> cancelTask(@PathVariable String taskId) {
        try {
            taskManagementService.cancelTask(taskId);
            return ApiResponse.success(new MessageVO("Task " + taskId + " cancelled"));
        } catch (TaskManagementException e) {
            return ApiResponse.error(-1, e.getMessage());
        }
    }

    @PostMapping("/{taskId}/pause")
    public ApiResponse<MessageVO> pauseTask(@PathVariable String taskId) {
        try {
            taskManagementService.pauseTask(taskId);
            return ApiResponse.success(new MessageVO("Task " + taskId + " paused"));
        } catch (TaskManagementException e) {
            return ApiResponse.error(-1, e.getMessage());
        }
    }

    @PostMapping("/{taskId}/resume")
    public ApiResponse<MessageVO> resumeTask(@PathVariable String taskId) {
        try {
            taskManagementService.resumeTask(taskId);
            return ApiResponse.success(new MessageVO("Task " + taskId + " resumed"));
        } catch (TaskManagementException e) {
            return ApiResponse.error(-1, e.getMessage());
        }
    }

    @PostMapping("/{taskId}/archive")
    public ApiResponse<MessageVO> archiveTask(@PathVariable String taskId) {
        try {
            taskManagementService.cancelTask(taskId);
            return ApiResponse.success(new MessageVO("Task " + taskId + " archived"));
        } catch (TaskManagementException e) {
            return ApiResponse.error(-1, e.getMessage());
        }
    }

    @PostMapping("/batch")
    public ApiResponse<BatchResultVO> batchOperation(@RequestBody BatchOperationRequest body) {
        List<String> taskIds = body.taskIds();
        String action = body.action();

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

        return ApiResponse.success(new BatchResultVO(successCount, errors));
    }

    // ─── 任务统计 ──────────────────────────────────────────

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> taskStats() {
        try {
            Map<String, Object> stats = taskManagementService.getTaskStats();
            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("任务统计查询失败", e);
            return ApiResponse.error(-1, "获取任务统计失败");
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
    public ApiResponse<TaskTypesVO> taskTypes() {
        return ApiResponse.success(new TaskTypesVO(TASK_TYPE_CATEGORIES));
    }

    // ─── 辅助方法 ─────────────────────────────────────────

    private TaskVO toVO(TaskDescription task) {
        return new TaskVO(task.getTaskId(), task.getTaskName(), task.getTaskType(),
                getCategory(task.getTaskType()), task.getDescription(), task.getPriority(),
                task.getCreateTime() != null ? task.getCreateTime().toString() : null,
                task.getCreatedBy(), task.getParameters());
    }

    private TaskStatusVO toStatusVO(TaskStatus status) {
        return new TaskStatusVO(status.getTaskId(),
                status.getStatus() != null ? status.getStatus().name() : "UNKNOWN",
                status.getStatusMessage(), status.getProgress(),
                status.getStartTime() != null ? status.getStartTime().toString() : null,
                status.getEndTime() != null ? status.getEndTime().toString() : null);
    }
}
