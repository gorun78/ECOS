package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowInstanceService;
import com.chinacreator.gzcm.buszhi.workflow.WorkflowService;
import com.chinacreator.gzcm.engine.ontology.dto.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
 *
 * <p><b>T16-4 说明</b>：本控制器（{@code /api/v1/ecos/workflows}，旧版 Designer 端点）
 * 与 {@link OntologyWorkflowController}（{@code /api/v1/engine/ontology/workflow}，T16-2）
 * 路由不冲突、互为同域不同端点组（均委托 buszhi-impl 的 WorkflowService/WorkflowInstanceService）。
 *
 * <p>入参/出参由 Map 改强类型，跟随 T16-1/2/3 命名先例、复用已有 VO/DTO
 * （{@link OntologyWorkflowVO}/{@link OntologyWorkflowInstanceVO}/{@link OntologyWorkflowSaveDTO}/
 * {@link OntologyWorkflowInstanceSaveDTO} + T16-4 新增 {@link WorkflowListVO}/
 * {@link WorkflowValidationSaveDTO}/{@link WorkflowValidationVO}/{@link WorkflowTestVO}/
 * {@link WorkflowPreviewVO}/{@link WorkflowExportVO}）。
 * buszhi-impl service 签名不动（Wave31 C1 兼容）：DTO 请求体经
 * {@link ObjectMapper#convertValue} 还原 Map 调旧 service（不跨文件改 buszhi-impl）。
 *
 * <p>动态结构豁免（标 {@code // T16-4: ... 动态结构豁免 Map}）：
 * 测试/预览的执行步迹与运行时上下文、导出 blob（$schema/nodes/edges）。
 *
 * <p><b>Wave D T18: 双路径收敛登记</b> — 本 Controller 与
 * {@link com.chinacreator.gzcm.buszhi.workflow.controller.WorkflowController}
 * （services/buszhi/impl）映射同一路由 {@code /api/v1/ecos/workflows}，
 * 属双跑并存期（ADR-7 兼容期）。gateway fat-JAR 当前走
 * GatewayApplication excludeFilters 排除 buszhi 侧副本（ontology 副本存活），
 * buszhi 微服务走 BuszhiServiceApplication excludeFilters 排除本副本（buszhi 副本存活）。
 * 物理下线待 buszhi 聚合 E2E + gateway live check 后执行；
 * 跟踪: docs/11-运维/t18-route-consolidation-checklist.md
 */
@RestController
@RequestMapping("/api/v1/ecos/workflows")
public class WorkflowController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowController.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 启动实例 DTO → Map 的 TypeReference（T16-4: C1 兼容 —
     * buszhi-impl service 保留 Map 入参，与 T16-2 startInstance 同转换路径）。
     */
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final WorkflowService workflowService;
    private final WorkflowInstanceService instanceService;

    public WorkflowController(WorkflowService workflowService,
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
    public ApiResponse<OntologyWorkflowVO> getWorkflow(@PathVariable String id) {
        Optional<Map<String, Object>> wf = workflowService.getWorkflow(id);
        if (wf.isEmpty()) {
            return ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在");
        }
        return ApiResponse.success(toVO(wf.get()));
    }

    // ═══════════════ 创建 ═══════════════════

    @PostMapping
    public ApiResponse<OntologyWorkflowVO> createWorkflow(@RequestBody OntologyWorkflowSaveDTO dto) {
        // T16-4: buszhi-impl service 保留 Map 入参（C1 兼容）；DTO 仅非 null 字段转 Map，
        // 等价原先 body.containsKey 默认值语义（name 缺省 → service 默认 "新工作流"）
        Map<String, Object> wf = workflowService.createWorkflow(toMap(dto));
        log.info("Workflow created via DB: {} [{}]", wf.get("id"), wf.get("name"));
        return ApiResponse.success(toVO(wf));
    }

    // ═══════════════ 更新 ═══════════════════

    @PutMapping("/{id}")
    public ApiResponse<OntologyWorkflowVO> updateWorkflow(
            @PathVariable String id,
            @RequestBody OntologyWorkflowSaveDTO dto) {
        Optional<Map<String, Object>> wf = workflowService.updateWorkflow(id, toMap(dto));
        if (wf.isEmpty()) {
            return ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在");
        }
        return ApiResponse.success(toVO(wf.get()));
    }

    // ═══════════════ 删除 ═══════════════════

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteResultVO> deleteWorkflow(@PathVariable String id) {
        if (workflowService.deleteWorkflow(id)) {
            DeleteResultVO vo = new DeleteResultVO();
            vo.setId(id);
            vo.setDeleted(true);
            return ApiResponse.success(vo);
        }
        return ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在");
    }

    // ═══════════════ 发布 ═══════════════════

    @PatchMapping("/{id}/publish")
    public ApiResponse<OntologyWorkflowVO> publishWorkflow(@PathVariable String id) {
        Optional<Map<String, Object>> wf = workflowService.publishWorkflow(id);
        if (wf.isEmpty()) {
            return ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在");
        }
        log.info("Workflow published via DB: {}", id);
        return ApiResponse.success(toVO(wf.get()));
    }

    // ═══════════════ 测试 ═══════════════════

    @PostMapping("/{id}/test")
    public ApiResponse<WorkflowTestVO> testWorkflow(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        // T16-4: workflow 实例运行时 payload 动态结构豁免 Map（测试输入 context，service 直透 WorkflowEngine）
        Optional<Map<String, Object>> result = workflowService.testWorkflow(id, body);
        if (result.isEmpty()) {
            return ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在");
        }
        log.info("Workflow test completed via state machine: {}", id);
        return ApiResponse.success(toTestVO(result.get()));
    }

    // ═══════════════ 验证 ═══════════════════

    @PostMapping("/validate")
    public ApiResponse<WorkflowValidationVO> validateWorkflow(@RequestBody WorkflowValidationSaveDTO dto) {
        // T16-4: workflow 定义动态结构豁免 — 透传全字段 payload 调旧 service
        Map<String, Object> definition = dto.getPayload();
        if (definition == null || definition.isEmpty()) {
            return ApiResponse.badRequest("WF-003: 验证定义不能为空");
        }
        Map<String, Object> result = workflowService.validateWorkflow(definition);
        log.info("Workflow validation: valid={}", result.get("valid"));
        return ApiResponse.success(toValidationVO(result));
    }

    // ═══════════════ 预览 ═══════════════════

    @PostMapping("/{id}/preview")
    public ApiResponse<WorkflowPreviewVO> previewWorkflow(
            @PathVariable String id,
            @RequestBody Map<String, Object> context) {
        // T16-4: workflow 实例运行时 payload 动态结构豁免 Map（预览上下文，service 直透 WorkflowEngine）
        Optional<Map<String, Object>> preview = workflowService.previewWorkflow(id, context);
        if (preview.isEmpty()) {
            return ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在");
        }
        return ApiResponse.success(toPreviewVO(preview.get()));
    }

    // ═══════════════ 克隆 ═══════════════════

    @PostMapping("/{id}/clone")
    public ApiResponse<OntologyWorkflowVO> cloneWorkflow(@PathVariable String id) {
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
    public ApiResponse<OntologyWorkflowInstanceVO> startWorkflow(
            @PathVariable String id,
            @RequestBody OntologyWorkflowInstanceSaveDTO body) {
        // T16-4: buszhi-impl service 保留 Map 入参（C1 兼容），DTO → Map 全字段透传（含 extras payload）
        Optional<Map<String, Object>> inst = instanceService.startInstance(id, toMap(body));
        if (inst.isEmpty()) {
            return ApiResponse.notFound("WF-001: 工作流 " + id + " 不存在");
        }
        log.info("Workflow instance started: {} for workflow {}", inst.get().get("id"), id);
        return ApiResponse.success(toInstanceVO(inst.get()));
    }

    @GetMapping("/instances")
    public ApiResponse<OntologyWorkflowInstanceListVO> listInstances(
            @RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> list = instanceService.listInstances(limit);
        OntologyWorkflowInstanceListVO vo = new OntologyWorkflowInstanceListVO();
        vo.setData(toInstanceVOList(list));
        vo.setTotal(list.size());
        return ApiResponse.success(vo);
    }

    @GetMapping("/instances/{instanceId}")
    public ApiResponse<OntologyWorkflowInstanceVO> getInstance(@PathVariable String instanceId) {
        Optional<Map<String, Object>> inst = instanceService.getInstance(instanceId);
        if (inst.isEmpty()) {
            return ApiResponse.notFound("WF-009: 实例 " + instanceId + " 不存在");
        }
        return ApiResponse.success(toInstanceVO(inst.get()));
    }

    @PostMapping("/instances/{instanceId}/suspend")
    public ApiResponse<OntologyWorkflowInstanceVO> suspendInstance(@PathVariable String instanceId) {
        Optional<Map<String, Object>> inst = instanceService.suspendInstance(instanceId);
        if (inst.isEmpty()) {
            return ApiResponse.notFound("WF-009: 实例 " + instanceId + " 不存在");
        }
        return ApiResponse.success(toInstanceVO(inst.get()));
    }

    @PostMapping("/instances/{instanceId}/resume")
    public ApiResponse<OntologyWorkflowInstanceVO> resumeInstance(@PathVariable String instanceId) {
        Optional<Map<String, Object>> inst = instanceService.resumeInstance(instanceId);
        if (inst.isEmpty()) {
            return ApiResponse.notFound("WF-009: 实例 " + instanceId + " 不存在");
        }
        return ApiResponse.success(toInstanceVO(inst.get()));
    }

    @PostMapping("/instances/{instanceId}/terminate")
    public ApiResponse<OntologyWorkflowInstanceVO> terminateInstance(@PathVariable String instanceId) {
        Optional<Map<String, Object>> inst = instanceService.terminateInstance(instanceId);
        if (inst.isEmpty()) {
            return ApiResponse.notFound("WF-009: 实例 " + instanceId + " 不存在");
        }
        return ApiResponse.success(toInstanceVO(inst.get()));
    }

    // ═══════════════ 内部 Map → VO 转换（Jackson convertValue）═══════════════
    // buszhi service 签名不动；本控制器在 controller 层做"Map 行 → 强类型 VO"
    // 包装，对外契约等价（POJO 序列化 == Map 序列化）。

    private OntologyWorkflowVO toVO(Map<String, Object> row) {
        return MAPPER.convertValue(row, OntologyWorkflowVO.class);
    }

    private List<OntologyWorkflowVO> toVOList(List<Map<String, Object>> rows) {
        return rows.stream().map(this::toVO).collect(java.util.stream.Collectors.toList());
    }

    private OntologyWorkflowInstanceVO toInstanceVO(Map<String, Object> row) {
        return MAPPER.convertValue(row, OntologyWorkflowInstanceVO.class);
    }

    private List<OntologyWorkflowInstanceVO> toInstanceVOList(List<Map<String, Object>> rows) {
        return rows.stream().map(this::toInstanceVO).collect(java.util.stream.Collectors.toList());
    }

    /**
     * 测试运行结果 Map（WorkflowExecutionResult.toMap）→ WorkflowTestVO。
     * steps / context / activeNodes 动态结构直接托底（已是任意 JSON 兼容结构）。
     */
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

    /**
     * 验证结果 Map（ValidationResult.toMap）→ WorkflowValidationVO。
     * errors / warnings / suggestions 为 {code, nodeId, message} 动态条目集合
     * （T16-4: workflow 校验条目动态结构豁免）。
     */
    private WorkflowValidationVO toValidationVO(Map<String, Object> result) {
        WorkflowValidationVO vo = new WorkflowValidationVO();
        Object valid = result.get("valid");
        vo.setValid(Boolean.TRUE.equals(valid) || "true".equals(String.valueOf(valid)));
        vo.setErrors(asList(result.get("errors")));
        vo.setWarnings(asList(result.get("warnings")));
        vo.setSuggestions(asList(result.get("suggestions")));
        return vo;
    }

    /**
     * 预览展开结果 Map → WorkflowPreviewVO（字段名 1:1 对齐 service 输出）。
     */
    private WorkflowPreviewVO toPreviewVO(Map<String, Object> result) {
        WorkflowPreviewVO vo = new WorkflowPreviewVO();
        vo.setWorkflowId(result.get("workflowId") == null ? null : String.valueOf(result.get("workflowId")));
        vo.setName(result.get("name") == null ? null : String.valueOf(result.get("name")));
        vo.setExpandedSteps(result.get("expandedSteps"));
        vo.setEstimatedPath(result.get("estimatedPath") == null ? null : String.valueOf(result.get("estimatedPath")));
        vo.setContext(result.get("context"));
        return vo;
    }

    /**
     * 导出结果 Map → WorkflowExportVO。
     * <p>$schema 经 @JsonProperty("$schema") 映射到 schema 字段；
     * nodes / edges / 其他 $ 开头扩展键收敛到 extras
     * （T16-4: 导出 blob 动态结构豁免 Map）。
     */
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

    /** 创建/更新 DTO → service Map（仅保留非 null 字段，等价原先 body.containsKey 语义） */
    private Map<String, Object> toMap(OntologyWorkflowSaveDTO dto) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (dto.getName() != null) map.put("name", dto.getName());
        if (dto.getDescription() != null) map.put("description", dto.getDescription());
        if (dto.getMode() != null) map.put("mode", dto.getMode());
        if (dto.getNodes() != null) map.put("nodes", dto.getNodes());
        if (dto.getEdges() != null) map.put("edges", dto.getEdges());
        return map;
    }

    /**
     * 启动实例 DTO → service Map。
     * <p>Jackson convertValue（与 T16-2 {@code OntologyWorkflowController.startInstance}
     * 同路径）：命名字段 + @JsonAnyGetter extras 扁平化全字段透传，
     * 与原先 Map 全字段透传行为一致（service 内 {@code context = toJson(body)} 不受影响）。
     */
    private Map<String, Object> toMap(OntologyWorkflowInstanceSaveDTO dto) {
        return MAPPER.convertValue(dto, MAP_TYPE);
    }
}
