package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.governance.OntologyCandidateService;
import com.chinacreator.gzcm.engine.ontology.governance.dto.OntologyCandidateCreateDTO;

/**
 * 本体 AI 候选 REST API  --  《ECOS 认知域与智能域补充详细设计 v1.0》§7.3/§7.4/§7.5。
 *
 * <p>能力归属 ontology-engine(金 I)。路径前缀 {@code /api/v1/ontology/candidates}
 * (route-manifest {@code /api/v1/ontology} → buszhi 已登记,命中 service :18083;
 * 新增子路径只增不改,不触碰既有 {@code /api/v1/ontology/proposals} 等路径)。
 *
 * <h3>端点(§7.3 五能力 + §7.4 状态机)</h3>
 * <pre>
 * POST   /api/v1/ontology/candidates                     --  创建候选(幂等;ENTITY/PROPERTY/RELATION 证据强制)
 * GET    /api/v1/ontology/candidates                      --  候选列表(tenantId 必填)
 * GET    /api/v1/ontology/candidates/{id}                  --  候选详情(含 basis;404 语义)
 * POST   /api/v1/ontology/candidates/gap-analyze          --  ONTOLOGY_GAP_ANALYZE(只读;不写候选表)
 * POST   /api/v1/ontology/candidates/{id}/validate        --  ONTOLOGY_CANDIDATE_VALIDATE(自检 + CAS 推进)
 * POST   /api/v1/ontology/candidates/{id}/diff-preview    --  ONTOLOGY_DIFF_PREVIEW(只读快照;committed 恒 false)
 * POST   /api/v1/ontology/candidates/{id}/submit-proposal --  ONTOLOGY_PROPOSAL_CREATE(唯一桥接出口;VALIDATED 专有)
 * POST   /api/v1/ontology/candidates/{id}/checked         --  GENERATED → EVIDENCE_CHECKED
 * POST   /api/v1/ontology/candidates/{id}/approve         --  PENDING_APPROVAL → APPROVED(approvalRef 必填;ABAC fail-closed)
 * POST   /api/v1/ontology/candidates/{id}/reject          --  → REJECTED
 * POST   /api/v1/ontology/candidates/{id}/need-evidence   --  → NEEDS_EVIDENCE
 * POST   /api/v1/ontology/candidates/{id}/conflict        --  → CONFLICTED
 * POST   /api/v1/ontology/candidates/{id}/expire          --  → EXPIRED
 * POST   /api/v1/ontology/candidates/{id}/apply           --  APPROVED → APPLIED(回执态,不执行本体变更)
 * </pre>
 *
 * <h3>红线(§7.3/§7.4/§7.5)</h3>
 * <ul>
 *   <li>候选 != 正式资产:本 Controller 只创建/推进候选;<b>无任何直接 publish 端点</b>
 *       (SUP-ONTO-02:AI 禁直发);正式本体变更仅经
 *       {@code /api/v1/ontology/proposals} 审批链执行。</li>
 *   <li>GENERATED/VALIDATED 不得当 APPROVED:状态迁移 CAS;approve 必须
 *       PENDING_APPROVAL 迁入且带 approvalRef 且过 security-engine ABAC
 *       (fail-closed DENY → 403)。</li>
 *   <li>生成(create)与审批(approve)是两个端点,两个审计动作(§7.4 末段)。</li>
 * </ul>
 *
 * @author ECOS-PM 认知域与智能域补充设计 §7.3/§7.4/§7.5
 */
@RestController("ontologyCandidateController")
@RequestMapping("/api/v1/ontology/candidates")
public class OntologyCandidateController {

    private static final Logger log = LoggerFactory.getLogger(OntologyCandidateController.class);

    private final OntologyCandidateService service;

    public OntologyCandidateController(OntologyCandidateService service) {
        this.service = service;
    }

    // ═══════════════ 候选 CRUD ═══════════════

    /**
     * 创建候选(幂等)。{@code idempotencyKey} 相同(同租户)→ 直接返回既有候选
     * 不重复创建(§7.5)。ENTITY/PROPERTY/RELATION 候选证据强制;
     * LLM 自报置信度强制 UNCALIBRATED_MODEL_ESTIMATE + calibrated=false。
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> createCandidate(
            @RequestHeader(value = "X-Request-Id", required = false) String traceId,
            @RequestBody OntologyCandidateCreateDTO dto) {
        if (dto.getTraceId() == null && traceId != null && !traceId.isBlank()) {
            dto.setTraceId(traceId);
        }
        return wrap(() -> service.createCandidate(dto), "create-candidate");
    }

    /** 候选列表:tenantId 必填(空白 → 400),status / candidateType / batchId 可选。 */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listCandidates(
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "candidateType", required = false) String candidateType,
            @RequestParam(value = "batchId", required = false) String batchId) {
        return wrap(() -> service.listCandidates(tenantId, status, candidateType, batchId),
                "list-candidates");
    }

    /** 候选详情(含 basis:confidence 三元组 / 证据计数 / 冲突标记 / 状态);不存在 → 404。 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getCandidate(@PathVariable String id) {
        Map<String, Object> row = service.getCandidate(id);
        if (row == null) {
            return ApiResponse.notFound("候选不存在: " + id);
        }
        return ApiResponse.success(row);
    }

    // ═══════════════ ONTOLOGY_GAP_ANALYZE(只读) ═══════════════

    /**
     * ONTOLOGY_GAP_ANALYZE:请求方实体类型 vs 在线本体实体 code 比对(只读)。
     * 请求体 JSON:{@code targetEntityTypes[]} + 可选 {@code relationshipIntents[]}
     * ({subject, predicate, object})。不写候选表。
     */
    @PostMapping("/gap-analyze")
    public ApiResponse<Map<String, Object>> gapAnalyze(
            @RequestBody(required = false) Object body) {
        return wrap(() -> service.gapAnalyze(body), "gap-analyze");
    }

    // ═══════════════ ONTOLOGY_CANDIDATE_VALIDATE / DIFF_PREVIEW ═══════════════

    /**
     * ONTOLOGY_CANDIDATE_VALIDATE:候选自检(命名/端点/冲突/兼容四段),
     * 回写 validation_text 并 CAS 推进
     * (passed → VALIDATED;端点/冲突 → CONFLICTED;其余 → NEEDS_EVIDENCE)。
     */
    @PostMapping("/{id}/validate")
    public ApiResponse<Map<String, Object>> validate(@PathVariable String id) {
        return wrap(() -> service.validate(id), "validate");
    }

    /**
     * ONTOLOGY_DIFF_PREVIEW:结构差异只读快照(候选行自身列回写;正式资产永不触碰;
     * committed 恒 false  --  禁应用 SUP-ONTO-01/02)。
     */
    @PostMapping("/{id}/diff-preview")
    public ApiResponse<Map<String, Object>> diffPreview(@PathVariable String id) {
        return wrap(() -> service.diffPreview(id), "diff-preview");
    }

    // ═══════════════ ONTOLOGY_PROPOSAL_CREATE(唯一桥接出口) ═══════════════

    /**
     * ONTOLOGY_PROPOSAL_CREATE:VALIDATED → PENDING_APPROVAL + 桥接正式提案表
     *(唯一桥接出口;SUP-ONTO-02 AI 禁直发  --  无直接 publish 端点,正式资产变更
     * 仅经 /api/v1/ontology/proposals 审批链执行)。
     */
    @PostMapping("/{id}/submit-proposal")
    public ApiResponse<Map<String, Object>> submitProposal(@PathVariable String id) {
        return wrap(() -> service.submitProposal(id), "submit-proposal");
    }

    // ═══════════════ 状态推进(§7.4;生成/审批两步分离) ═══════════════

    /** 证据核验:GENERATED → EVIDENCE_CHECKED。 */
    @PostMapping("/{id}/checked")
    public ApiResponse<Map<String, Object>> checked(@PathVariable String id) {
        return wrap(() -> service.markEvidenceChecked(id), "checked");
    }

    /** 审批通过:PENDING_APPROVAL → APPROVED(approvalRef 必填;ABAC fail-closed DENY → 403)。 */
    @PostMapping("/{id}/approve")
    public ApiResponse<Map<String, Object>> approve(
            @PathVariable String id,
            @RequestParam("approvalRef") String approvalRef) {
        return wrap(() -> service.approve(id, approvalRef, null), "approve");
    }

    /** 拒绝:→ REJECTED。 */
    @PostMapping("/{id}/reject")
    public ApiResponse<Map<String, Object>> reject(
            @PathVariable String id,
            @RequestParam(value = "reason", required = false) String reason) {
        return wrap(() -> service.transition(id,
                OntologyCandidateService.STATUS_REJECTED, null, reason), "reject");
    }

    /** 补证据:→ NEEDS_EVIDENCE。 */
    @PostMapping("/{id}/need-evidence")
    public ApiResponse<Map<String, Object>> needEvidence(
            @PathVariable String id,
            @RequestParam(value = "reason", required = false) String reason) {
        return wrap(() -> service.transition(id,
                OntologyCandidateService.STATUS_NEEDS_EVIDENCE, null, reason), "need-evidence");
    }

    /** 冲突:→ CONFLICTED。 */
    @PostMapping("/{id}/conflict")
    public ApiResponse<Map<String, Object>> conflict(
            @PathVariable String id,
            @RequestParam(value = "reason", required = false) String reason) {
        return wrap(() -> service.transition(id,
                OntologyCandidateService.STATUS_CONFLICTED, null, reason), "conflict");
    }

    /** 过期:→ EXPIRED。 */
    @PostMapping("/{id}/expire")
    public ApiResponse<Map<String, Object>> expire(@PathVariable String id) {
        return wrap(() -> service.transition(id,
                OntologyCandidateService.STATUS_EXPIRED, null, "expire"), "expire");
    }

    /**
     * 应用回执:APPROVED → APPLIED。仅标记候选应用态 + 安全裁决 + 审计;
     * <b>不执行任何本体变更</b>(正式变更严格经 proposals 审批链)。
     */
    @PostMapping("/{id}/apply")
    public ApiResponse<Map<String, Object>> apply(@PathVariable String id) {
        return wrap(() -> service.applyCandidate(id), "apply");
    }

    // ═══════════════ 统一包装 ═══════════════

    private <T> ApiResponse<T> wrap(java.util.function.Supplier<T> op, String action) {
        try {
            T data = op.get();
            log.debug("OntologyCandidate {} succeeded", action);
            return ApiResponse.success(data);
        } catch (com.chinacreator.gzcm.common.exception.DataBridgeException e) {
            // 业务异常族(NotFound/Forbidden/Validation)按各自 httpStatus 透出
            throw e;
        } catch (IllegalArgumentException e) {
            throw new com.chinacreator.gzcm.common.exception.ValidationException(action, e.getMessage());
        } catch (IllegalStateException e) {
            // CAS 冲突 / 前置状态不满足(409 语义由 GlobalExceptionHandler 承载)
            throw e;
        }
    }
}
