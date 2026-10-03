package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.PipelineTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Pipeline Task Controller — Pipeline 2.0 YAML 任务管理 API（已只读归档）。
 * <p>
 * F02-03（详细设计-02，R-2 a 已批准）业务管道单模型收敛：
 * 承流模型 = JSON DAG（{@code /api/v1/pipeline/definitions}），
 * 本控制器的 6 个写端点全部返回 <b>HTTP 409 {@code ECOS-DATA-011}</b>，
 * 6 个读端点保留只读（响应体带 {@code deprecated=true} 标识，兼容 ≥2 迭代）。
 *
 * <p>端点清单 (14个):
 * <pre>
 * POST   /tasks             — 创建任务   （已停用 → 409 ECOS-DATA-011）
 * GET    /tasks             — 任务列表   （只读保留，deprecated=true）
 * GET    /tasks/{id}        — 任务详情   （只读保留，deprecated=true）
 * PUT    /tasks/{id}        — 更新任务   （已停用 → 409 ECOS-DATA-011）
 * DELETE /tasks/{id}        — 删除任务   （已停用 → 409 ECOS-DATA-011）
 * POST   /tasks/{id}/run    — 触发执行   （已停用 → 409 ECOS-DATA-011）
 * POST   /tasks/{id}/cancel — 取消执行   （已停用 → 409 ECOS-DATA-011）
 * GET    /tasks/{id}/runs   — 执行历史   （只读保留，deprecated=true）
 * GET    /runs/{runId}      — 执行详情   （只读保留，deprecated=true）
 * GET    /runs/{runId}/steps — 步骤详情  （只读保留，deprecated=true）
 * </pre>
 *
 * @author ECOS Pipeline 2.0 Team (F02-03 单模型收敛改造)
 */
@RestController
@RequestMapping("/api/v1/engine/data/pipeline")
public class PipelineTaskController {

    private static final Logger log = LoggerFactory.getLogger(PipelineTaskController.class);

    static final String CODE_TASK_READONLY = "ECOS-DATA-011";
    static final String TASK_MODEL_READONLY_MSG =
            "YAML 任务模型已只读归档，请改用 /api/v1/pipeline/definitions（JSON DAG 模型）";

    private final PipelineTaskService taskService;

    public PipelineTaskController(PipelineTaskService taskService) {
        this.taskService = taskService;
    }

    /** F02-03：写端点统一 409 拒绝。 */
    private ResponseEntity<ApiResponse<Void>> readonlyReject() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, CODE_TASK_READONLY, TASK_MODEL_READONLY_MSG));
    }

    /** F02-03：只读端点在响应体打 deprecated 标识（仅当响应为成功 Map 时可用；List/详情类型保留原结构）。 */
    static Map<String, Object> markDeprecated(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>(data);
        copy.put("deprecated", true);
        return copy;
    }

    // ── 1. 创建任务（已停用）──
    @Deprecated
    @PostMapping("/tasks")
    public ResponseEntity<ApiResponse<Void>> createTask(@RequestBody Map<String, Object> body) {
        log.warn("F02-03: POST /tasks rejected (YAML task model read-only)");
        return readonlyReject();
    }

    // ── 2. 任务列表（只读保留）──
    @Deprecated
    @GetMapping("/tasks")
    public ApiResponse<Map<String, Object>> listTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            return ApiResponse.success(markDeprecated(taskService.listTasks(page, pageSize)));
        } catch (Exception e) {
            log.error("列出任务失败", e);
            return ApiResponse.internalError("列出任务失败");
        }
    }

    // ── 3. 任务详情（只读保留）──
    @Deprecated
    @GetMapping("/tasks/{id}")
    public ApiResponse<Map<String, Object>> getTask(@PathVariable String id) {
        try {
            return ApiResponse.success(taskService.getTask(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("获取任务详情失败: id={}", id, e);
            return ApiResponse.internalError("获取任务详情失败");
        }
    }

    // ── 4. 更新任务（已停用）──
    @Deprecated
    @PutMapping("/tasks/{id}")
    public ResponseEntity<ApiResponse<Void>> updateTask(@PathVariable String id,
                                                         @RequestBody Map<String, Object> body) {
        log.warn("F02-03: PUT /tasks/{} rejected (YAML task model read-only)", id);
        return readonlyReject();
    }

    // ── 5. 删除任务（已停用）──
    @Deprecated
    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable String id) {
        log.warn("F02-03: DELETE /tasks/{} rejected (YAML task model read-only)", id);
        return readonlyReject();
    }

    // ── 6. 触发执行（已停用）──
    @Deprecated
    @PostMapping("/tasks/{id}/run")
    public ResponseEntity<ApiResponse<Void>> triggerRun(@PathVariable String id,
                                                         @RequestParam(defaultValue = "manual") String triggeredBy) {
        log.warn("F02-03: POST /tasks/{}/run rejected (YAML task model read-only)", id);
        return readonlyReject();
    }

    // ── 7. 取消执行（已停用）──
    @Deprecated
    @PostMapping("/tasks/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelRun(@PathVariable String id,
                                                        @RequestParam String runId) {
        log.warn("F02-03: POST /tasks/{}/cancel rejected (YAML task model read-only)", id);
        return readonlyReject();
    }

    // ── 8. 执行历史（只读保留）──
    @Deprecated
    @GetMapping("/tasks/{id}/runs")
    public ApiResponse<List<Map<String, Object>>> getRuns(@PathVariable String id) {
        try {
            return ApiResponse.success(taskService.getRuns(id));
        } catch (Exception e) {
            log.error("获取执行历史失败: taskId={}", id, e);
            return ApiResponse.internalError("获取执行历史失败");
        }
    }

    // ── 9. 执行详情（只读保留）──
    @Deprecated
    @GetMapping("/runs/{runId}")
    public ApiResponse<Map<String, Object>> getRun(@PathVariable String runId) {
        try {
            return ApiResponse.success(taskService.getRun(runId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("获取执行详情失败: runId={}", runId, e);
            return ApiResponse.internalError("获取执行详情失败");
        }
    }

    // ── 10. 步骤详情（只读保留）──
    @Deprecated
    @GetMapping("/runs/{runId}/steps")
    public ApiResponse<List<Map<String, Object>>> getRunSteps(@PathVariable String runId) {
        try {
            return ApiResponse.success(taskService.getRunSteps(runId));
        } catch (Exception e) {
            log.error("获取执行步骤失败: runId={}", runId, e);
            return ApiResponse.internalError("获取执行步骤失败");
        }
    }
}
