package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.PipelineTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Pipeline Task Controller — Pipeline 2.0 任务管理 API。
 * <p>
 * 端点清单 (14个):
 * POST  /tasks          — 创建任务
 * GET   /tasks          — 任务列表
 * GET   /tasks/{id}     — 任务详情
 * PUT   /tasks/{id}     — 更新任务
 * DELETE /tasks/{id}    — 删除任务
 * POST  /tasks/{id}/run — 触发执行
 * POST  /tasks/{id}/cancel — 取消执行
 * GET   /tasks/{id}/runs   — 执行历史
 * GET   /runs/{runId}      — 执行详情
 * GET   /runs/{runId}/steps — 步骤详情
 *
 * @author ECOS Pipeline 2.0 Team
 */
@RestController
@RequestMapping("/api/v1/engine/data/pipeline")
public class PipelineTaskController {

    private static final Logger log = LoggerFactory.getLogger(PipelineTaskController.class);
    private final PipelineTaskService taskService;

    public PipelineTaskController(PipelineTaskService taskService) {
        this.taskService = taskService;
    }

    // ── 1. 创建任务 ──
    @PostMapping("/tasks")
    public ApiResponse<Map<String, Object>> createTask(@RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success("任务创建成功", taskService.createTask(body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("创建任务失败", e);
            return ApiResponse.internalError("创建任务失败: " + e.getMessage());
        }
    }

    // ── 2. 任务列表 ──
    @GetMapping("/tasks")
    public ApiResponse<Map<String, Object>> listTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            return ApiResponse.success(taskService.listTasks(page, pageSize));
        } catch (Exception e) {
            log.error("列出任务失败", e);
            return ApiResponse.internalError("列出任务失败: " + e.getMessage());
        }
    }

    // ── 3. 任务详情 ──
    @GetMapping("/tasks/{id}")
    public ApiResponse<Map<String, Object>> getTask(@PathVariable String id) {
        try {
            return ApiResponse.success(taskService.getTask(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("获取任务详情失败: id={}", id, e);
            return ApiResponse.internalError("获取任务详情失败: " + e.getMessage());
        }
    }

    // ── 4. 更新任务 ──
    @PutMapping("/tasks/{id}")
    public ApiResponse<Map<String, Object>> updateTask(@PathVariable String id,
                                                        @RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success("任务更新成功", taskService.updateTask(id, body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("更新任务失败: id={}", id, e);
            return ApiResponse.internalError("更新任务失败: " + e.getMessage());
        }
    }

    // ── 5. 删除任务 ──
    @DeleteMapping("/tasks/{id}")
    public ApiResponse<Void> deleteTask(@PathVariable String id) {
        try {
            taskService.deleteTask(id);
            return ApiResponse.success("任务已删除", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("删除任务失败: id={}", id, e);
            return ApiResponse.internalError("删除任务失败: " + e.getMessage());
        }
    }

    // ── 6. 触发执行 ──
    @PostMapping("/tasks/{id}/run")
    public ApiResponse<Map<String, Object>> triggerRun(@PathVariable String id,
                                                        @RequestParam(defaultValue = "manual") String triggeredBy) {
        try {
            return ApiResponse.success("执行已触发", taskService.triggerRun(id, triggeredBy));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("触发执行失败: id={}", id, e);
            return ApiResponse.internalError("触发执行失败: " + e.getMessage());
        }
    }

    // ── 7. 取消执行 ──
    @PostMapping("/tasks/{id}/cancel")
    public ApiResponse<Void> cancelRun(@PathVariable String id,
                                        @RequestParam String runId) {
        try {
            taskService.cancelRun(runId);
            return ApiResponse.success("执行已取消", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("取消执行失败: runId={}", runId, e);
            return ApiResponse.internalError("取消执行失败: " + e.getMessage());
        }
    }

    // ── 8. 执行历史 ──
    @GetMapping("/tasks/{id}/runs")
    public ApiResponse<List<Map<String, Object>>> getRuns(@PathVariable String id) {
        try {
            return ApiResponse.success(taskService.getRuns(id));
        } catch (Exception e) {
            log.error("获取执行历史失败: taskId={}", id, e);
            return ApiResponse.internalError("获取执行历史失败: " + e.getMessage());
        }
    }

    // ── 9. 执行详情 ──
    @GetMapping("/runs/{runId}")
    public ApiResponse<Map<String, Object>> getRun(@PathVariable String runId) {
        try {
            return ApiResponse.success(taskService.getRun(runId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("获取执行详情失败: runId={}", runId, e);
            return ApiResponse.internalError("获取执行详情失败: " + e.getMessage());
        }
    }

    // ── 10. 步骤详情 ──
    @GetMapping("/runs/{runId}/steps")
    public ApiResponse<List<Map<String, Object>>> getRunSteps(@PathVariable String runId) {
        try {
            return ApiResponse.success(taskService.getRunSteps(runId));
        } catch (Exception e) {
            log.error("获取执行步骤失败: runId={}", runId, e);
            return ApiResponse.internalError("获取执行步骤失败: " + e.getMessage());
        }
    }
}
