package com.chinacreator.gzcm.engine.data.pipeline;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Pipeline Controller — Pipeline 定义管理与执行。
 * <p>
 * 执行入口走 runtime-task（ITaskManagementService.submitTask + executeTask 分步调用），
 * 返回 taskId 供前端轮询 getTaskStatus(taskId)。
 *
 * @author DataBridge Datanet Team
 */
@RestController
@RequestMapping("/api/v1/pipeline")
public class PipelineController {

    private static final Logger log = LoggerFactory.getLogger(PipelineController.class);
    private final PipelineService pipelineService;
    private final PipelineRepository repository;
    private final ITaskManagementService taskManagementService;

    public PipelineController(PipelineService pipelineService,
                               PipelineRepository repository,
                               ITaskManagementService taskManagementService) {
        this.pipelineService = pipelineService;
        this.repository = repository;
        this.taskManagementService = taskManagementService;
    }

    // ── 1. 创建 Pipeline 定义 ──────────────────────────

    @PostMapping("/definitions")
    public ApiResponse<Map<String, Object>> createDefinition(@RequestBody Map<String, Object> body) {
        try {
            PipelineDefinition def = pipelineService.createDefinition(body);
            List<PipelineNode> nodes;
            try {
                nodes = repository.findNodesByDefinitionId(def.getId());
            } catch (Exception e) {
                log.warn("查询 Pipeline 节点失败（表可能未初始化）: {}", e.getMessage());
                nodes = Collections.emptyList();
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", def.getId());
            result.put("name", def.getName());
            result.put("description", def.getDescription());
            result.put("status", def.getStatus());
            result.put("nodes", nodes);
            result.put("createdAt", def.getCreatedAt());
            result.put("updatedAt", def.getUpdatedAt());

            return ApiResponse.success("Pipeline 定义创建成功", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("创建 Pipeline 定义失败", e);
            return ApiResponse.internalError("创建 Pipeline 定义失败: " + e.getMessage());
        }
    }

    // ── 2. Pipeline 定义列表 ────────────────────────────

    @GetMapping("/definitions")
    public ApiResponse<List<Map<String, Object>>> listDefinitions() {
        try {
            List<PipelineDefinition> defs = pipelineService.listDefinitions();
            List<Map<String, Object>> result = new ArrayList<>();
            for (PipelineDefinition def : defs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", def.getId());
                m.put("name", def.getName());
                m.put("description", def.getDescription());
                m.put("status", def.getStatus());
                m.put("createdAt", def.getCreatedAt());
                m.put("updatedAt", def.getUpdatedAt());
                result.add(m);
            }
            return ApiResponse.success("查询成功", result);
        } catch (Exception e) {
            log.error("查询 Pipeline 定义列表失败", e);
            return ApiResponse.internalError("查询 Pipeline 定义列表失败: " + e.getMessage());
        }
    }

    // ── 3. Pipeline 定义详情 ────────────────────────────

    @GetMapping("/definitions/{id}")
    public ApiResponse<Map<String, Object>> getDefinition(@PathVariable String id) {
        try {
            PipelineDefinition def = pipelineService.getDefinition(id);
            List<PipelineNode> nodes = repository.findNodesByDefinitionId(id);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", def.getId());
            result.put("name", def.getName());
            result.put("description", def.getDescription());
            result.put("status", def.getStatus());
            result.put("nodes", nodes);
            result.put("createdAt", def.getCreatedAt());
            result.put("updatedAt", def.getUpdatedAt());

            return ApiResponse.success("查询成功", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("查询 Pipeline 定义详情失败: id={}", id, e);
            return ApiResponse.internalError("查询 Pipeline 定义详情失败: " + e.getMessage());
        }
    }

    // ── 4. 更新 Pipeline 定义 ────────────────────────────

    @PutMapping("/definitions/{id}")
    public ApiResponse<Map<String, Object>> updateDefinition(@PathVariable String id,
                                                              @RequestBody Map<String, Object> body) {
        try {
            PipelineDefinition def = pipelineService.updateDefinition(id, body);
            List<PipelineNode> nodes = repository.findNodesByDefinitionId(id);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", def.getId());
            result.put("name", def.getName());
            result.put("description", def.getDescription());
            result.put("status", def.getStatus());
            result.put("nodes", nodes);
            result.put("createdAt", def.getCreatedAt());
            result.put("updatedAt", def.getUpdatedAt());

            return ApiResponse.success("Pipeline 定义更新成功", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("更新 Pipeline 定义失败: id={}", id, e);
            return ApiResponse.internalError("更新 Pipeline 定义失败: " + e.getMessage());
        }
    }

    // ── 5. 删除 Pipeline 定义 ────────────────────────────

    @DeleteMapping("/definitions/{id}")
    public ApiResponse<Map<String, Object>> deleteDefinition(@PathVariable String id) {
        try {
            pipelineService.deleteDefinition(id);
            return ApiResponse.success("Pipeline 定义已删除", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("删除 Pipeline 定义失败: id={}", id, e);
            return ApiResponse.internalError("删除 Pipeline 定义失败: " + e.getMessage());
        }
    }

    // ── 6. 执行 Pipeline（走 runtime-task 全闭环） ──────

    @PostMapping("/definitions/{id}/execute")
    public ApiResponse<Map<String, Object>> executeDefinition(@PathVariable String id) {
        try {
            // 构造 runtime-task 任务描述，分步 submitTask + executeTask（确保 taskId 先返回）
            TaskDescription desc = new TaskDescription();
            desc.setTaskType("PIPELINE");
            desc.setTaskName("Pipeline-" + id);
            Map<String, Object> params = new HashMap<>();
            params.put("definitionId", id);
            desc.setParameters(params);
            desc.setAsync(false); // 同步执行返回结果，前端可改 true 异步轮询

            String taskId = taskManagementService.submitTask(desc);
            taskManagementService.executeTask(taskId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("taskId", taskId);
            // 兼容旧字段：从 runtime-task 状态取最终状态
            try {
                TaskStatus status = taskManagementService.getTaskStatus(taskId);
                if (status != null) {
                    result.put("status", status.getStatus() != null ? status.getStatus().name() : "UNKNOWN");
                    result.put("progress", status.getProgress());
                    result.put("result", status.getResult());
                    result.put("errorMessage", status.getErrorMessage());
                }
            } catch (Exception se) {
                log.warn("查询 runtime-task 状态失败: taskId={}", taskId, se);
            }
            return ApiResponse.success("Pipeline execution submitted via runtime-task", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("执行 Pipeline 失败: id={}", id, e);
            return ApiResponse.internalError("执行 Pipeline 失败: " + e.getMessage());
        }
    }

    // ── 7. 查询 runtime-task 状态（前端轮询） ────────────

    @GetMapping("/tasks/{taskId}/status")
    public ApiResponse<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        try {
            TaskStatus status = taskManagementService.getTaskStatus(taskId);
            if (status == null) {
                return ApiResponse.notFound("任务状态不存在: " + taskId);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("taskId", status.getTaskId());
            result.put("status", status.getStatus() != null ? status.getStatus().name() : "UNKNOWN");
            result.put("statusMessage", status.getStatusMessage());
            result.put("progress", status.getProgress());
            result.put("currentStepId", status.getCurrentStepId());
            result.put("startTime", status.getStartTime());
            result.put("endTime", status.getEndTime());
            result.put("result", status.getResult());
            result.put("errorMessage", status.getErrorMessage());
            return ApiResponse.success("查询成功", result);
        } catch (Exception e) {
            log.error("查询任务状态失败: taskId={}", taskId, e);
            return ApiResponse.internalError("查询任务状态失败: " + e.getMessage());
        }
    }

    // ── 8. 查询执行状态（旧端点，按 executionId 查 ecos_pipeline_execution） ──

    @GetMapping("/executions/{id}")
    public ApiResponse<PipelineExecution> getExecution(@PathVariable String id) {
        try {
            PipelineExecution exec = repository.findExecutionById(id);
            if (exec == null) {
                return ApiResponse.notFound("执行记录不存在: " + id);
            }
            return ApiResponse.success("查询成功", exec);
        } catch (Exception e) {
            log.error("查询执行状态失败: id={}", id, e);
            return ApiResponse.internalError("查询执行状态失败: " + e.getMessage());
        }
    }
}
