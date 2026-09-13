package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowApprovalService;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowInstanceService;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowService;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.engine.ontology.dto.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作流端点（T16-2 重构）。
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/engine/ontology/workflow/definitions        — 工作流定义列表（分页 + 兼容）</li>
 *   <li>POST   /api/v1/engine/ontology/workflow/definitions        — 创建工作流</li>
 *   <li>GET    /api/v1/engine/ontology/workflow/instances          — 实例列表</li>
 *   <li>POST   /api/v1/engine/ontology/workflow/instances          — 启动实例</li>
 *   <li>GET    /api/v1/engine/ontology/workflow/instances/{id}     — 实例详情</li>
 *   <li>POST   /api/v1/engine/ontology/workflow/instances/{id}/approve — 审批</li>
 *   <li>POST   /api/v1/engine/ontology/workflow/instances/{id}/reject   — 驳回</li>
 * </ul>
 *
 * <p><b>T16-2 说明</b>：本控制器底层 service（{@code WorkflowService} /
 * {@code WorkflowInstanceService} / {@code WorkflowApprovalService}）
 * 位于 {@code services/buszhi-impl}（PMO-51 残留范围）。
 * 由于 buszhi 是红线（见 T16-2 指令 blacklist），
 * 本控制器**不动 service 签名**，仅在 Controller 内做 Map → VO 的 Jackson 转换，
 * API 输出契约严格等价于既有 Map 形态（字段名/序列化格式保持一致）。
 *
 * <p>转换使用 {@link com.fasterxml.jackson.databind.ObjectMapper#convertValue}：
 * 比手工字段枚举更紧凑，且对 service 后续新增字段自动传播。
 */
@RestController
@RequestMapping({"/api/v1/engine/ontology/workflow", "/api/engine/ontology/workflow"})
public class OntologyWorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowInstanceService instanceService;
    private final WorkflowApprovalService approvalService;
    private final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
        new com.fasterxml.jackson.databind.ObjectMapper();

    public OntologyWorkflowController(WorkflowService workflowService,
                                      WorkflowInstanceService instanceService,
                                      WorkflowApprovalService approvalService) {
        this.workflowService = workflowService;
        this.instanceService = instanceService;
        this.approvalService = approvalService;
    }

    /**
     * 工作流定义列表（强类型包装 VO；兼容 data 全量字段 + records 当前页切片）。
     *
     * <p>结构：{@code records: List<OntologyWorkflowVO>} (当前页) +
     * {@code data: List<OntologyWorkflowVO>} (全量) +
     * {@code total: long} + {@code pageNum: int} + {@code pageSize: int}。
     */
    @GetMapping("/definitions")
    public ApiResponse<OntologyWorkflowPageVO> listDefinitions(
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(defaultValue = "1") int pageNum) {
        List<Map<String, Object>> list = workflowService.listWorkflows(pageSize);
        int from = Math.min((pageNum - 1) * pageSize, list.size());
        int to = Math.min(from + pageSize, list.size());
        OntologyWorkflowPageVO page = new OntologyWorkflowPageVO();
        page.setRecords(toVOList(list.subList(from, to)));
        page.setData(toVOList(list));
        page.setTotal(workflowService.totalCount());
        page.setPageNum(pageNum);
        page.setPageSize(pageSize);
        return ApiResponse.success(page);
    }

    /**
     * 创建工作流（强类型 VO）。
     */
    @PostMapping("/definitions")
    public ApiResponse<OntologyWorkflowVO> createDefinition(
            @RequestBody OntologyWorkflowSaveDTO dto) {
        Map<String, Object> body = MAPPER.convertValue(dto, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        // service 内 String.valueOf(body.getOrDefault("nodes", "[]")) 行为兼容
        // （Object nodes / edges 字段在 Service 内被 toJsonString 进一步序列化）
        Map<String, Object> wf = workflowService.createWorkflow(body);
        return ApiResponse.success(toVO(wf));
    }

    /**
     * 实例列表（强类型包装 VO；{@code data: List<OntologyWorkflowInstanceVO>} + total）。
     */
    @GetMapping("/instances")
    public ApiResponse<OntologyWorkflowInstanceListVO> listInstances(
            @RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> list = instanceService.listInstances(limit);
        OntologyWorkflowInstanceListVO vo = new OntologyWorkflowInstanceListVO();
        vo.setData(toInstanceVOList(list));
        vo.setTotal(list.size());
        return ApiResponse.success(vo);
    }

    /**
     * 启动实例（强类型 VO）。
     */
    @PostMapping("/instances")
    public ApiResponse<OntologyWorkflowInstanceVO> startInstance(
            @RequestBody OntologyWorkflowInstanceSaveDTO dto) {
        String workflowId = dto.getWorkflowId();
        if (workflowId == null || workflowId.isBlank()) {
            return ApiResponse.badRequest("workflowId is required");
        }
        Map<String, Object> body = MAPPER.convertValue(dto,
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        java.util.Optional<Map<String, Object>> resultOpt = instanceService.startInstance(workflowId, body);
        if (resultOpt.isEmpty()) {
            return ApiResponse.notFound("Workflow " + workflowId + " not found");
        }
        return ApiResponse.success(toInstanceVO(resultOpt.get()));
    }

    /**
     * 实例详情（强类型 VO）。
     */
    @GetMapping("/instances/{id}")
    public ApiResponse<OntologyWorkflowInstanceVO> getInstance(@PathVariable String id) {
        java.util.Optional<Map<String, Object>> resultOpt = instanceService.getInstance(id);
        if (resultOpt.isEmpty()) {
            return ApiResponse.notFound("Instance " + id + " not found");
        }
        return ApiResponse.success(toInstanceVO(resultOpt.get()));
    }

    /**
     * 审批通过（强类型 VO）。
     * <p>body 使用 {@link OntologyWorkflowApprovalDTO}（含 @JsonAnySetter 接收
     * formData / userId / opinion，避免前端 payload 字段被丢）。
     */
    @PostMapping("/instances/{id}/approve")
    public ApiResponse<OntologyWorkflowApprovalVO> approve(
            @PathVariable String id,
            @RequestBody OntologyWorkflowApprovalDTO dto) {
        Map<String, Object> body = MAPPER.convertValue(dto,
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        Map<String, Object> result = approvalService.approve(id, body);
        return ApiResponse.success(toApprovalVO(result));
    }

    /**
     * 审批驳回（强类型 VO）。
     */
    @PostMapping("/instances/{id}/reject")
    public ApiResponse<OntologyWorkflowApprovalVO> reject(
            @PathVariable String id,
            @RequestBody OntologyWorkflowApprovalDTO dto) {
        Map<String, Object> body = MAPPER.convertValue(dto,
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        Map<String, Object> result = approvalService.reject(id, body);
        return ApiResponse.success(toApprovalVO(result));
    }

    // ═══════════════ 内部 Map → VO 转换（Jackson convertValue）═══════════════
    // buszhi service 签名不动；本控制器在 controller 层做"Map 行 → 强类型 VO"
    // 包装，对外契约等价（POJO 序列化 == Map 序列化）。

    private OntologyWorkflowVO toVO(Map<String, Object> row) {
        return MAPPER.convertValue(row, OntologyWorkflowVO.class);
    }

    private List<OntologyWorkflowVO> toVOList(List<Map<String, Object>> rows) {
        return rows.stream().map(this::toVO).collect(Collectors.toList());
    }

    private OntologyWorkflowInstanceVO toInstanceVO(Map<String, Object> row) {
        return MAPPER.convertValue(row, OntologyWorkflowInstanceVO.class);
    }

    private List<OntologyWorkflowInstanceVO> toInstanceVOList(List<Map<String, Object>> rows) {
        return rows.stream().map(this::toInstanceVO).collect(Collectors.toList());
    }

    private OntologyWorkflowApprovalVO toApprovalVO(Map<String, Object> row) {
        return MAPPER.convertValue(row, OntologyWorkflowApprovalVO.class);
    }
}
