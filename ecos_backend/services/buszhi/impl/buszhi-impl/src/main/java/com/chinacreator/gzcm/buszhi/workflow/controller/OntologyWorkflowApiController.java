package com.chinacreator.gzcm.buszhi.workflow.controller;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowInstanceService;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowService;
import com.chinacreator.gzcm.buszhi.workflow.dto.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * F6-07 六族规范前缀末枚（workflows 族）—— <b>additive 别名承运面</b>。
 *
 * <p>台账 F6-07 六族（objects/links/domains/versions/workflows/git-history）中
 * 前五族已有 {@code /api/v1/ontology/**} 正典承运，workflows 是唯一仍只剩
 * 旧前缀 {@code /api/v1/ecos/workflows} 的族。本类以<b>新增基址
 * {@code /api/v1/ontology/workflows}</b> 补齐第 6 族规范前缀，逐方法映射与
 * {@link WorkflowController} 完全同型（同方法体、同 404 机读码 WF-001/WF-009、
 * 同 Map→VO 转换路径、同委托属主 {@link WorkflowService}/{@link WorkflowInstanceService}），
 * 属主承运面 {@code /api/v1/ecos/workflows} <b>零改动</b>（架构铁律 API 只增不改）。
 *
 * <p><b>buszhi 落位理由</b>：PMO-57 T2 已断链 engine-impl → buszhi-impl 反向依赖，
 * 工作流端点单点收敛于本包（ontology-engine-impl 旧副本已物理删除）；
 * 同族审批别名（#146y WorkflowInstanceApprovalController）亦落本包同款。
 *
 * <p><b>网关重写面</b>：{@code VersionPrefixRewriteFilter} 正向 v1→去v1 表
 * 无 {@code /api/v1/ontology/} 条目（本基址直通不重写），反向表无命中前缀；
 * route-manifest {@code /api/v1/ontology}（owner=buszhi, rewrite=/api/*）
 * 既有条目按段边界覆盖本别名，无需补登。
 */
@RestController
@RequestMapping("/api/v1/ontology/workflows")
public class OntologyWorkflowApiController {

    private static final Logger log = LoggerFactory.getLogger(OntologyWorkflowApiController.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 启动实例 DTO → Map 的 TypeReference（service 保留 Map 入参
     * （C1 兼容）；DTO 经 convertValue 还原 Map（与属主控制器同转换路径））。
     */
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final WorkflowService workflowService;
    private final WorkflowInstanceService instanceService;

    public OntologyWorkflowApiController(WorkflowService workflowService,
                                         WorkflowInstanceService instanceService) {
        this.workflowService = workflowService;
        this.instanceService = instanceService;
    }

    // ═══════════════ 列表 ═══════════════════

    @GetMapping
    public ApiResponse<WorkflowListVO> listWorkflows(
            @RequestParam(defaultValue = "50") int pageSize) {
        List<Map<String, Object>> list = workflowService.listWorkflows(pageSize);
        WorkflowListVO vo = new WorkflowListVO();
        vo.setData(toVOList(list));
        vo.setTotal(workflowService.totalCount());
        return ApiResponse.success(vo);
    }

    // ═══════════════ 详情 ═══════════════════

    @GetMapping("/{id}")
    public ApiResponse<WorkflowVO> getWorkflow(@PathVariable String id) {
        Optional<Map<String, Object>> wf = workflowService.getWorkflow(id);
        if (wf.isEmpty()) {
            return ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在");
        }
        return ApiResponse.success(toVO(wf.get()));
    }

    // ═══════════════ 创建 ═══════════════════

    @PostMapping
    public ApiResponse<WorkflowVO> createWorkflow(@RequestBody WorkflowSaveDTO dto) {
        Map<String, Object> wf = workflowService.createWorkflow(toMap(dto));
        log.info("Workflow created via ontology alias: {} [{}]", wf.get("id"), wf.get("name"));
        return ApiResponse.success(toVO(wf));
    }

    // ═══════════════ 更新 ═══════════════════

    @PutMapping("/{id}")
    public ApiResponse<WorkflowVO> updateWorkflow(
            @PathVariable String id,
            @RequestBody WorkflowSaveDTO dto) {
        Optional<Map<String, Object>> wf = workflowService.updateWorkflow(id, toMap(dto));
        if (wf.isEmpty()) {
            return ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在");
        }
        return ApiResponse.success(toVO(wf.get()));
    }

    // ═══════════════ 删除 ═══════════════════

    @DeleteMapping("/{id}")
    public ApiResponse<WorkflowDeleteResultVO> deleteWorkflow(@PathVariable String id) {
        if (workflowService.deleteWorkflow(id)) {
            WorkflowDeleteResultVO vo = new WorkflowDeleteResultVO();
            vo.setId(id);
            vo.setDeleted(true);
            return ApiResponse.success(vo);
        }
        return ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在");
    }

    // ═══════════════ 发布 ═══════════════════

    @PatchMapping("/{id}/publish")
    public ApiResponse<WorkflowVO> publishWorkflow(@PathVariable String id) {
        Optional<Map<String, Object>> wf = workflowService.publishWorkflow(id);
        if (wf.isEmpty()) {
            return ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在");
        }
        log.info("Workflow published via ontology alias: {}", id);
        return ApiResponse.success(toVO(wf.get()));
    }

    // ═══════════════ 测试 ═══════════════════

    @PostMapping("/{id}/test")
    public ApiResponse<WorkflowTestVO> testWorkflow(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        // 动态结构豁免 Map：workflow 实例运行时 payload（测试输入 context，service 直透 WorkflowEngine）
        Optional<Map<String, Object>> result = workflowService.testWorkflow(id, body);
        if (result.isEmpty()) {
            return ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在");
        }
        return ApiResponse.success(toTestVO(result.get()));
    }

    // ═══════════════ 验证 ═══════════════════

    @PostMapping("/validate")
    public ApiResponse<WorkflowValidationVO> validateWorkflow(@RequestBody WorkflowValidationSaveDTO dto) {
        // 动态结构豁免：workflow 定义动态结构 — 透传全字段 payload 调 service
        Map<String, Object> definition = dto.getPayload();
        if (definition == null || definition.isEmpty()) {
            return ApiResponse.badRequest("WF-003: 验证定义不能为空");
        }
        Map<String, Object> result = workflowService.validateWorkflow(definition);
        return ApiResponse.success(toValidationVO(result));
    }

    // ═══════════════ 预览 ═══════════════════

    @PostMapping("/{id}/preview")
    public ApiResponse<WorkflowPreviewVO> previewWorkflow(
            @PathVariable String id,
            @RequestBody Map<String, Object> context) {
        // 动态结构豁免 Map：workflow 实例运行时 payload（预览上下文，service 直透 WorkflowEngine）
        Optional<Map<String, Object>> preview = workflowService.previewWorkflow(id, context);
        if (preview.isEmpty()) {
            return ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在");
        }
        return ApiResponse.success(toPreviewVO(preview.get()));
    }

    // ═══════════════ 克隆 ═══════════════════

    @PostMapping("/{id}/clone")
    public ApiResponse<WorkflowVO> cloneWorkflow(@PathVariable String id) {
        Optional<Map<String, Object>> clone = workflowService.cloneWorkflow(id);
        if (clone.isEmpty()) {
            return ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在");
        }
        return ApiResponse.success(toVO(clone.get()));
    }

    // ═══════════════ 导出 ═══════════════════

    @GetMapping("/{id}/export")
    public ApiResponse<WorkflowExportVO> exportWorkflow(@PathVariable String id) {
        Optional<Map<String, Object>> exp = workflowService.exportWorkflow(id);
        if (exp.isEmpty()) {
            return ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在");
        }
        return ApiResponse.success(toExportVO(exp.get()));
    }

    // ═══════════════ 流程实例 ═══════════════════

    @PostMapping("/{id}/start")
    public ApiResponse<WorkflowInstanceVO> startWorkflow(
            @PathVariable String id,
            @RequestBody WorkflowInstanceSaveDTO body) {
        // service 保留 Map 入参（C1 兼容），DTO → Map 全字段透传（含 extras payload）
        Optional<Map<String, Object>> inst = instanceService.startInstance(id, toMap(body));
        if (inst.isEmpty()) {
            return ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在");
        }
        log.info("Workflow instance started via ontology alias: {} for workflow {}", inst.get().get("id"), id);
        return ApiResponse.success(toInstanceVO(inst.get()));
    }

    @GetMapping("/instances")
    public ApiResponse<WorkflowInstanceListVO> listInstances(
            @RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> list = instanceService.listInstances(limit);
        WorkflowInstanceListVO vo = new WorkflowInstanceListVO();
        vo.setData(toInstanceVOList(list));
        vo.setTotal(list.size());
        return ApiResponse.success(vo);
    }

    @GetMapping("/instances/{instanceId}")
    public ApiResponse<WorkflowInstanceVO> getInstance(@PathVariable String instanceId) {
        Optional<Map<String, Object>> inst = instanceService.getInstance(instanceId);
        if (inst.isEmpty()) {
            return ApiResponse.notFound("WF-009: 实例 " + instanceId + " 不存在");
        }
        return ApiResponse.success(toInstanceVO(inst.get()));
    }

    @PostMapping("/instances/{instanceId}/suspend")
    public ApiResponse<WorkflowInstanceVO> suspendInstance(@PathVariable String instanceId) {
        Optional<Map<String, Object>> inst = instanceService.suspendInstance(instanceId);
        if (inst.isEmpty()) {
            return ApiResponse.notFound("WF-009: 实例 " + instanceId + " 不存在");
        }
        return ApiResponse.success(toInstanceVO(inst.get()));
    }

    @PostMapping("/instances/{instanceId}/resume")
    public ApiResponse<WorkflowInstanceVO> resumeInstance(@PathVariable String instanceId) {
        Optional<Map<String, Object>> inst = instanceService.resumeInstance(instanceId);
        if (inst.isEmpty()) {
            return ApiResponse.notFound("WF-009: 实例 " + instanceId + " 不存在");
        }
        return ApiResponse.success(toInstanceVO(inst.get()));
    }

    @PostMapping("/instances/{instanceId}/terminate")
    public ApiResponse<WorkflowInstanceVO> terminateInstance(@PathVariable String instanceId) {
        Optional<Map<String, Object>> inst = instanceService.terminateInstance(instanceId);
        if (inst.isEmpty()) {
            return ApiResponse.notFound("WF-009: 实例 " + instanceId + " 不存在");
        }
        return ApiResponse.success(toInstanceVO(inst.get()));
    }

    // ═══════════════ 内部 Map → VO 转换（与属主控制器同型）═══════════════

    private WorkflowVO toVO(Map<String, Object> row) {
        return MAPPER.convertValue(row, WorkflowVO.class);
    }

    private List<WorkflowVO> toVOList(List<Map<String, Object>> rows) {
        return rows.stream().map(this::toVO).collect(java.util.stream.Collectors.toList());
    }

    private WorkflowInstanceVO toInstanceVO(Map<String, Object> row) {
        return MAPPER.convertValue(row, WorkflowInstanceVO.class);
    }

    private List<WorkflowInstanceVO> toInstanceVOList(List<Map<String, Object>> rows) {
        return rows.stream().map(this::toInstanceVO).collect(java.util.stream.Collectors.toList());
    }

    private WorkflowTestVO toTestVO(Map<String, Object> result) {
        String workflowId = result.get("workflowId") == null ? null : String.valueOf(result.get("workflowId"));
        WorkflowTestVO vo = new WorkflowTestVO();
        vo.setWorkflowId(workflowId);
        vo.setStatus(result.get("status") == null ? null : String.valueOf(result.get("status")));
        vo.setExecutionTime(result.get("executionTime") == null ? null : String.valueOf(result.get("executionTime")));
        vo.setSteps(result.get("steps"));
        vo.setContext(result.get("context"));
        vo.setActiveNodes(result.get("activeNodes"));
        return vo;
    }

    private WorkflowValidationVO toValidationVO(Map<String, Object> result) {
        WorkflowValidationVO vo = new WorkflowValidationVO();
        Object valid = result.get("valid");
        vo.setValid(Boolean.TRUE.equals(valid) || "true".equals(String.valueOf(valid)));
        vo.setErrors(asList(result.get("errors")));
        vo.setWarnings(asList(result.get("warnings")));
        vo.setSuggestions(asList(result.get("suggestions")));
        return vo;
    }

    private WorkflowPreviewVO toPreviewVO(Map<String, Object> result) {
        WorkflowPreviewVO vo = new WorkflowPreviewVO();
        vo.setWorkflowId(result.get("workflowId") == null ? null : String.valueOf(result.get("workflowId")));
        vo.setName(result.get("name") == null ? null : String.valueOf(result.get("name")));
        vo.setExpandedSteps(result.get("expandedSteps"));
        vo.setEstimatedPath(result.get("estimatedPath") == null ? null : String.valueOf(result.get("estimatedPath")));
        vo.setContext(result.get("context"));
        return vo;
    }

    private WorkflowExportVO toExportVO(Map<String, Object> result) {
        Map<String, Object> extras = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : result.entrySet()) {
            if (e.getKey().startsWith("$")) {
                extras.put(e.getKey(), e.getValue());
            }
        }
        WorkflowExportVO vo = new WorkflowExportVO();
        Object schema = result.get("$schema");
        vo.setSchema(schema == null ? null : String.valueOf(schema));
        vo.setId(result.get("id") == null ? null : String.valueOf(result.get("id")));
        vo.setName(result.get("name") == null ? null : String.valueOf(result.get("name")));
        vo.setDescription(result.get("description") == null ? null : String.valueOf(result.get("description")));
        vo.setVersion(result.get("version") == null ? null : String.valueOf(result.get("version")));
        vo.setMode(result.get("mode") == null ? null : String.valueOf(result.get("mode")));
        vo.setStatus(result.get("status") == null ? null : String.valueOf(result.get("status")));
        vo.setNodes(result.get("nodes"));
        vo.setEdges(result.get("edges"));
        vo.setCreatedAt(result.get("createdAt") == null ? null : String.valueOf(result.get("createdAt")));
        vo.setUpdatedAt(result.get("updatedAt") == null ? null : String.valueOf(result.get("updatedAt")));
        vo.setExtras(extras);
        return vo;
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(Object value) {
        if (value instanceof List) {
            return (List<Object>) value;
        }
        return new ArrayList<>();
    }

    private Map<String, Object> toMap(WorkflowSaveDTO dto) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (dto.getName() != null) map.put("name", dto.getName());
        if (dto.getDescription() != null) map.put("description", dto.getDescription());
        if (dto.getMode() != null) map.put("mode", dto.getMode());
        if (dto.getNodes() != null) map.put("nodes", dto.getNodes());
        if (dto.getEdges() != null) map.put("edges", dto.getEdges());
        return map;
    }

    private Map<String, Object> toMap(WorkflowInstanceSaveDTO dto) {
        return MAPPER.convertValue(dto, MAP_TYPE);
    }
}
