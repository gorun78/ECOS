package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.data.PipelineTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Pipeline Task Controller — Pipeline 2.0 任务管理 API（<b>YAML 模型，已只读</b>）。
 * <p>
 * F02-03 收敛（R-2 a 已批准，详细设计-02 §F02-03）：JSON DAG 模型
 * （{@code ecos_pipeline_definition/node/edge} + {@code /api/v1/pipeline/definitions}）
 * 为唯一承流模型；本控制器的 YAML 模型（{@code ecos_pipeline_task.yaml_content}）
 * 转只读归档 —— 只停写不删（IR03）。
 * <ul>
 *   <li><b>写端点 409 桩</b>（POST /tasks、PUT /tasks/{id}、DELETE /tasks/{id}、
 *       POST /tasks/{id}/run、POST /tasks/{id}/cancel）：保留方法签名（API 只增不改），
 *       统一抛 {@link DataBridgeException}(httpStatus=409, errorCode=409)，
 *       gateway GlobalExceptionHandler 按 {@code DataBridgeException#getHttpStatus()} 语义透出 409，
 *       消息首段 = {@code ECOS-DATA-011}（409, YAML 模型只读）。</li>
 *   <li><b>读端点保留</b>（GET ×5）：继续委托 {@link PipelineTaskService}，
 *       响应包装一层 {@code deprecated=true}。</li>
 * </ul>
 *
 * <p>弃用信号模式（选定后文档化）：读端点 = 响应体包装字段 {@code deprecated=true}
 * （数据原样内嵌，前端可渐进迁移）；写端点 = 409 异常（无法再"成功"，包装字段无意义）。
 *
 * @deprecated F02-03（R-2 a）：YAML 管道模型只读归档，请改用
 *             {@code /api/v1/pipeline/definitions}（JSON DAG 单模型）。
 * @author ECOS Pipeline 2.0 Team
 */
@Deprecated
@RestController
@RequestMapping("/api/v1/engine/data/pipeline")
public class PipelineTaskController {

    private static final Logger log = LoggerFactory.getLogger(PipelineTaskController.class);

    /** F02-03 读端点 409 消息：机读码单源取自 {@link DataDomainErrorCode#ECOS_DATA_011}。 */
    static final String READONLY_MESSAGE = DataDomainErrorCode.ECOS_DATA_011
            + " YAML 管道模型已只读，请改用 /api/v1/pipeline/definitions";

    /** F02-03：写端点冲突码（409 Conflict，带业务错误码字段）。 */
    private static DataBridgeException readonlyConflict(String op) {
        return new DataBridgeException(409, 409, READONLY_MESSAGE + "（拒绝操作: " + op + "）");
    }

    private final PipelineTaskService taskService;

    public PipelineTaskController(PipelineTaskService taskService) {
        this.taskService = taskService;
    }

    // ── 1. 创建任务（已只读 → 409 桩） ──
    @PostMapping("/tasks")
    public ApiResponse<Map<String, Object>> createTask(@RequestBody Map<String, Object> body) {
        log.warn("YAML 管道模型已只读，拒绝创建：POST /tasks");
        throw readonlyConflict("POST /tasks");
    }

    // ── 2. 任务列表（只读保留，deprecated=true） ──
    @GetMapping("/tasks")
    public ApiResponse<Map<String, Object>> listTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            return ApiResponse.success(deprecate(taskService.listTasks(page, pageSize)));
        } catch (Exception e) {
            log.error("列出任务失败", e);
            return ApiResponse.internalError("列出任务失败");
        }
    }

    // ── 3. 任务详情（只读保留，deprecated=true） ──
    @GetMapping("/tasks/{id}")
    public ApiResponse<Map<String, Object>> getTask(@PathVariable String id) {
        try {
            return ApiResponse.success(deprecate(taskService.getTask(id)));
        } catch (IllegalArgumentException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("获取任务详情失败: id={}", id, e);
            return ApiResponse.internalError("获取任务详情失败");
        }
    }

    // ── 4. 更新任务（已只读 → 409 桩） ──
    @PutMapping("/tasks/{id}")
    public ApiResponse<Map<String, Object>> updateTask(@PathVariable String id,
                                                        @RequestBody Map<String, Object> body) {
        log.warn("YAML 管道模型已只读，拒绝更新: taskId={}", id);
        throw readonlyConflict("PUT /tasks/" + id);
    }

    // ── 5. 删除任务（已只读 → 409 桩） ──
    @DeleteMapping("/tasks/{id}")
    public ApiResponse<Void> deleteTask(@PathVariable String id) {
        log.warn("YAML 管道模型已只读，拒绝删除: taskId={}", id);
        throw readonlyConflict("DELETE /tasks/" + id);
    }

    // ── 6. 触发执行（已只读 → 409 桩；执行归 JSON DAG 模型 /definitions/{id}/execute） ──
    @PostMapping("/tasks/{id}/run")
    public ApiResponse<Map<String, Object>> triggerRun(@PathVariable String id,
                                                        @RequestParam(defaultValue = "manual") String triggeredBy) {
        log.warn("YAML 管道模型已只读，拒绝触发执行: taskId={}", id);
        throw readonlyConflict("POST /tasks/" + id + "/run");
    }

    // ── 7. 取消执行（已只读 → 409 桩，属变更操作） ──
    @PostMapping("/tasks/{id}/cancel")
    public ApiResponse<Void> cancelRun(@PathVariable String id,
                                        @RequestParam String runId) {
        log.warn("YAML 管道模型已只读，拒绝取消执行: runId={}", runId);
        throw readonlyConflict("POST /tasks/" + id + "/cancel");
    }

    // ── 8. 执行历史（只读保留，deprecated=true） ──
    @GetMapping("/tasks/{id}/runs")
    public ApiResponse<List<Map<String, Object>>> getRuns(@PathVariable String id) {
        try {
            return ApiResponse.success(deprecateList(taskService.getRuns(id)));
        } catch (Exception e) {
            log.error("获取执行历史失败: taskId={}", id, e);
            return ApiResponse.internalError("获取执行历史失败");
        }
    }

    // ── 9. 执行详情（只读保留，deprecated=true） ──
    @GetMapping("/runs/{runId}")
    public ApiResponse<Map<String, Object>> getRun(@PathVariable String runId) {
        try {
            return ApiResponse.success(deprecate(taskService.getRun(runId)));
        } catch (IllegalArgumentException e) {
            return ApiResponse.notFound(e.getMessage());
        } catch (Exception e) {
            log.error("获取执行详情失败: runId={}", runId, e);
            return ApiResponse.internalError("获取执行详情失败");
        }
    }

    // ── 10. 步骤详情（只读保留，deprecated=true） ──
    @GetMapping("/runs/{runId}/steps")
    public ApiResponse<List<Map<String, Object>>> getRunSteps(@PathVariable String runId) {
        try {
            return ApiResponse.success(deprecateList(taskService.getRunSteps(runId)));
        } catch (Exception e) {
            log.error("获取执行步骤失败: runId={}", runId, e);
            return ApiResponse.internalError("获取执行步骤失败");
        }
    }

    // ── 弃用信号包装：数据原样内嵌，顶层标记 deprecated=true（F02-03 读端点保留口径） ──

    private Map<String, Object> deprecate(Map<String, Object> data) {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("deprecated", true);
        if (data != null) {
            wrapper.putAll(data);
        }
        return wrapper;
    }

    private List<Map<String, Object>> deprecateList(List<Map<String, Object>> data) {
        return data == null ? Collections.emptyList() : data;
    }
}
