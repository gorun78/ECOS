package com.chinacreator.gzcm.engine.ontology.controller;

import java.time.LocalDateTime;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowInstanceService;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowService;

/**
 * Workflow Designer REST API — 工作流 CRUD + 发布/测试/验证/预览/克隆/导出 + 实例管理。
 *
 * <pre>
 * 工作流定义:
 * GET    /api/v1/ecos/workflows                      — 列表
 * GET    /api/v1/ecos/workflows/{id}                 — 详情（含 definition）
 * POST   /api/v1/ecos/workflows                      — 创建
 * PUT    /api/v1/ecos/workflows/{id}                 — 更新
 * DELETE /api/v1/ecos/workflows/{id}                 — 删除
 * PATCH  /api/v1/ecos/workflows/{id}/publish         — 发布
 * POST   /api/v1/ecos/workflows/{id}/test            — 测试运行
 * POST   /api/v1/ecos/workflows/validate             — 验证定义
 * POST   /api/v1/ecos/workflows/{id}/preview         — 预览展开
 * POST   /api/v1/ecos/workflows/{id}/clone           — 克隆
 * GET    /api/v1/ecos/workflows/{id}/export          — 导出
 *
 * 流程实例:
 * POST   /api/v1/ecos/workflows/{id}/start           — 启动流程实例
 * GET    /api/v1/ecos/workflows/instances            — 实例列表
 * GET    /api/v1/ecos/workflows/instances/{instanceId} — 实例详情
 * POST   /api/v1/ecos/workflows/instances/{instanceId}/suspend  — 挂起
 * POST   /api/v1/ecos/workflows/instances/{instanceId}/resume   — 恢复
 * POST   /api/v1/ecos/workflows/instances/{instanceId}/terminate — 终止
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/ecos/workflows")
public class WorkflowController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowController.class);

    private final WorkflowService workflowService;
    private final WorkflowInstanceService instanceService;

    public WorkflowController(WorkflowService workflowService,
                              WorkflowInstanceService instanceService) {
        this.workflowService = workflowService;
        this.instanceService = instanceService;
    }

    // ═══════════════ 列表 ═══════════════════

    @GetMapping
    public ApiResponse<Map<String, Object>> listWorkflows(
            @RequestParam(defaultValue = "50") int pageSize) {
        List<Map<String, Object>> list = workflowService.listWorkflows(pageSize);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", list);
        result.put("total", workflowService.totalCount());
        return ApiResponse.success(result);
    }

    // ═══════════════ 详情 ═══════════════════

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getWorkflow(@PathVariable String id) {
        return workflowService.getWorkflow(id)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在"));
    }

    // ═══════════════ 创建 ═══════════════════

    @PostMapping
    public ApiResponse<Map<String, Object>> createWorkflow(@RequestBody Map<String, Object> body) {
        Map<String, Object> wf = workflowService.createWorkflow(body);
        log.info("Workflow created via DB: {} [{}]", wf.get("id"), wf.get("name"));
        return ApiResponse.success(wf);
    }

    // ═══════════════ 更新 ═══════════════════

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateWorkflow(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return workflowService.updateWorkflow(id, body)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在"));
    }

    // ═══════════════ 删除 ═══════════════════

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> deleteWorkflow(@PathVariable String id) {
        if (workflowService.deleteWorkflow(id)) {
            return ApiResponse.success(Map.of("id", id, "deleted", true));
        }
        return ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在");
    }

    // ═══════════════ 发布 ═══════════════════

    @PatchMapping("/{id}/publish")
    public ApiResponse<Map<String, Object>> publishWorkflow(@PathVariable String id) {
        return workflowService.publishWorkflow(id)
            .map(wf -> {
                log.info("Workflow published via DB: {}", id);
                return ApiResponse.success(wf);
            })
            .orElseGet(() -> ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在"));
    }

    // ═══════════════ 测试 ═══════════════════

    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> testWorkflow(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return workflowService.testWorkflow(id, body)
            .map(result -> {
                log.info("Workflow test completed via state machine: {}", id);
                return ApiResponse.success(result);
            })
            .orElseGet(() -> ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在"));
    }

    // ═══════════════ 验证 ═══════════════════

    @PostMapping("/validate")
    public ApiResponse<Map<String, Object>> validateWorkflow(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = workflowService.validateWorkflow(body);
        log.info("Workflow validation: valid={}", result.get("valid"));
        return ApiResponse.success(result);
    }

    // ═══════════════ 预览 ═══════════════════

    @PostMapping("/{id}/preview")
    public ApiResponse<Map<String, Object>> previewWorkflow(
            @PathVariable String id,
            @RequestBody Map<String, Object> context) {
        return workflowService.previewWorkflow(id, context)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在"));
    }

    // ═══════════════ 克隆 ═══════════════════

    @PostMapping("/{id}/clone")
    public ApiResponse<Map<String, Object>> cloneWorkflow(@PathVariable String id) {
        return workflowService.cloneWorkflow(id)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在"));
    }

    // ═══════════════ 导出 ═══════════════════

    @GetMapping("/{id}/export")
    public ApiResponse<Map<String, Object>> exportWorkflow(@PathVariable String id) {
        return workflowService.exportWorkflow(id)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在"));
    }

    // ═══════════════ 流程实例 ═══════════════════

    @PostMapping("/{id}/start")
    public ApiResponse<Map<String, Object>> startWorkflow(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return instanceService.startInstance(id, body)
            .map(result -> {
                log.info("Workflow instance started: {} for workflow {}", result.get("id"), id);
                return ApiResponse.success(result);
            })
            .orElseGet(() -> ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在"));
    }

    @GetMapping("/instances")
    public ApiResponse<Map<String, Object>> listInstances(
            @RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> list = instanceService.listInstances(limit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", list);
        result.put("total", list.size());
        return ApiResponse.success(result);
    }

    @GetMapping("/instances/{instanceId}")
    public ApiResponse<Map<String, Object>> getInstance(@PathVariable String instanceId) {
        return instanceService.getInstance(instanceId)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("WF-009: 实例 " + instanceId + " 不存在"));
    }

    @PostMapping("/instances/{instanceId}/suspend")
    public ApiResponse<Map<String, Object>> suspendInstance(@PathVariable String instanceId) {
        return instanceService.suspendInstance(instanceId)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("WF-009: 实例 " + instanceId + " 不存在"));
    }

    @PostMapping("/instances/{instanceId}/resume")
    public ApiResponse<Map<String, Object>> resumeInstance(@PathVariable String instanceId) {
        return instanceService.resumeInstance(instanceId)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("WF-009: 实例 " + instanceId + " 不存在"));
    }

    @PostMapping("/instances/{instanceId}/terminate")
    public ApiResponse<Map<String, Object>> terminateInstance(@PathVariable String instanceId) {
        return instanceService.terminateInstance(instanceId)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("WF-009: 实例 " + instanceId + " 不存在"));
    }
}
