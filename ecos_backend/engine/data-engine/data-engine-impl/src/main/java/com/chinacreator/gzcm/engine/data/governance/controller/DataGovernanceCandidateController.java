package com.chinacreator.gzcm.engine.data.governance.controller;

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
import com.chinacreator.gzcm.engine.data.governance.DataGovernanceCandidateService;
import com.chinacreator.gzcm.engine.data.governance.dto.DataGovernanceCandidateCreateDTO;

/**
 * 数据治理辅助 REST API — 《ECOS 认知域与智能域补充详细设计 v1.0》§7.2 / §13。
 *
 * <p>能力归属 data-engine（土 D）。路径前缀 {@code /api/v1/data/governance}
 * （route-manifest {@code /api/v1/data} → datanet 已登记，命中 service :18082）。
 * 新增子路径只增不改，不触碰既有 {@code /api/v1/datanet}、{@code /api/v1/dq} 路径。
 *
 * <h3>端点</h3>
 * <pre>
 * POST   /api/v1/data/governance/profile                              — DATA_PROFILE（只读剖析 assetRef）
 * GET    /api/v1/data/governance/field-semantic-suggest?assetRef=&amp;field=  — FIELD_SEMANTIC_SUGGEST（只读建议）
 * GET    /api/v1/data/governance/field-mapping-suggest?sourceField=&amp;targetRef= — FIELD_MAPPING_SUGGEST（只读建议）
 * GET    /api/v1/data/governance/dq-rule-suggest?assetRef=              — DQ_RULE_SUGGEST（只读建议）
 * GET    /api/v1/data/governance/profile?assetRef=&amp;capability=PROFILE  — 组合只读取值（见 profile 端点）
 * POST   /api/v1/data/governance/candidates                            — 创建候选
 *         (FIELD_SEMANTIC/FIELD_MAPPING/DQ_RULE/DATA_CHANGE_PROPOSAL;
 *          idempotencyKey 重试幂等 — 同 tenant 返既有行)
 * POST   /api/v1/data/governance/candidates/{id}/validate             — VALIDATED（校验通过；GENERATED/证据通过→VALIDATED）
 * POST   /api/v1/data/governance/candidates/{id}/submit-to-approval    — VALIDATED → PENDING_APPROVAL（DATA_CHANGE_PROPOSAL 必经）
 * POST   /api/v1/data/governance/candidates/{id}/approve               — PENDING_APPROVAL → APPROVED（需 approvalRef；security 裁决）
 * POST   /api/v1/data/governance/candidates/{id}/reject                 — → REJECTED
 * POST   /api/v1/data/governance/candidates/{id}/need-evidence          — → NEEDS_EVIDENCE
 * POST   /api/v1/data/governance/candidates/{id}/conflict               — → CONFLICTED
 * POST   /api/v1/data/governance/candidates/{id}/expire                 — → EXPIRED
 * POST   /api/v1/data/governance/candidates/{id}/apply                   — APPROVED → APPLIED（DATA_CHANGE_APPLY，security 裁决 + 人工审批；fail-closed DENY）
 * GET    /api/v1/data/governance/dq-preview/{id}                         — DQ_PREVIEW（只读预览；SUP-DATA-01 预览不改正式资产）
 * GET    /api/v1/data/governance/candidates                              — 候选列表
 * GET    /api/v1/data/governance/candidates/{id}                         — 候选详情
 * </pre>
 *
 * <h3>红线（§7.1 / §7.4 / §7.5）</h3>
 * <ul>
 *   <li>候选 != 正式资产：本 Controller 只创建/推进候选，<b>不直接改正式资产</b>；
 *       DATA_CHANGE_PROPOSAL 是 data-engine 变更提案引用，正式执行经既有 DQ/审批通道。</li>
 *   <li>GENERATED/VALIDATED ≠ APPROVED：状态迁移 CAS，approve 必须 PENDING_APPROVAL 迁入
 *       且带 approvalRef；apply 必须 APPROVED 且过 security 裁决（SUP-DATA-02）。</li>
 *   <li>生成（create）与审批（approve）是两个端点、两个审计事件。</li>
 * </ul>
 *
 * @author ECOS-PM 认知域与智能域补充设计 §7.2/§13
 */
@RestController("dataGovernanceCandidateController")
@RequestMapping("/api/v1/data/governance")
public class DataGovernanceCandidateController {

    private static final Logger log = LoggerFactory.getLogger(DataGovernanceCandidateController.class);

    private final DataGovernanceCandidateService service;

    public DataGovernanceCandidateController(DataGovernanceCandidateService service) {
        this.service = service;
    }

    // ═══════════════ 只读能力（DATA_PROFILE / SUGGEST 族） ═══════════════

    /** DATA_PROFILE — 数据剖析（只读）。 */
    @PostMapping("/profile")
    public ApiResponse<Map<String, Object>> profile(@RequestParam("assetRef") String assetRef) {
        return wrap(() -> service.profileAsset(assetRef), "profile");
    }

    /** FIELD_SEMANTIC_SUGGEST — 字段语义候选（只读建议）。 */
    @GetMapping("/field-semantic-suggest")
    public ApiResponse<Map<String, Object>> semanticSuggest(
            @RequestParam("assetRef") String assetRef,
            @RequestParam(value = "field", required = false) String field) {
        return wrap(() -> service.suggestFieldSemantic(assetRef, field), "semantic-suggest");
    }

    /** FIELD_MAPPING_SUGGEST — 字段映射候选（只读建议）。 */
    @GetMapping("/field-mapping-suggest")
    public ApiResponse<Map<String, Object>> mappingSuggest(
            @RequestParam("sourceField") String sourceField,
            @RequestParam(value = "targetRef") String targetRef) {
        return wrap(() -> service.suggestFieldMapping(sourceField, targetRef), "mapping-suggest");
    }

    /** DQ_RULE_SUGGEST — DQ 规则候选建议（只读建议）。 */
    @GetMapping("/dq-rule-suggest")
    public ApiResponse<Map<String, Object>> dqRuleSuggest(@RequestParam("assetRef") String assetRef) {
        return wrap(() -> service.suggestDqRules(assetRef), "dq-rule-suggest");
    }

    // ═══════════════ 候选 CRUD ═══════════════

    /**
     * 创建候选（幂等）。{@code idempotencyKey} 相同（同租户）→ 直接返回既有候选，不重复创建（§7.5）。
     * DATA_CHANGE_PROPOSAL 必带 rollbackPlan；LLM 自报置信度强制 UNCALIBRATED_MODEL_ESTIMATE。
     */
    @PostMapping("/candidates")
    public ApiResponse<Map<String, Object>> createCandidate(
            @RequestHeader(value = "X-Request-Id", required = false) String traceId,
            @RequestBody DataGovernanceCandidateCreateDTO dto) {
        if (dto.getTraceId() == null && traceId != null) {
            dto.setTraceId(traceId);
        }
        return wrap(() -> service.createCandidate(dto), "create-candidate");
    }

    @GetMapping("/candidates")
    public ApiResponse<List<Map<String, Object>>> listCandidates(
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "candidateType", required = false) String candidateType,
            @RequestParam(value = "batchId", required = false) String batchId) {
        return wrap(() -> service.listCandidates(tenantId, status, candidateType, batchId), "list-candidates");
    }

    @GetMapping("/candidates/{id}")
    public ApiResponse<Map<String, Object>> getCandidate(@PathVariable String id) {
        Map<String, Object> row = service.getCandidate(id);
        if (row == null) {
            return ApiResponse.notFound("候选不存在: " + id);
        }
        return ApiResponse.success(row);
    }

    // ═══════════════ 状态推进（§7.4；生成/审批两步分离） ═══════════════

    /** 校验通过：GENERATED/EVIDENCE_CHECKED → VALIDATED。 */
    @PostMapping("/candidates/{id}/validate")
    public ApiResponse<Map<String, Object>> validate(@PathVariable String id) {
        return wrap(() -> service.transition(id,
                DataGovernanceCandidateService.STATUS_VALIDATED, null, "validate"), "validate");
    }

    /** 提交审批：VALIDATED → PENDING_APPROVAL（DATA_CHANGE_PROPOSAL 必经）。 */
    @PostMapping("/candidates/{id}/submit-to-approval")
    public ApiResponse<Map<String, Object>> submitToApproval(@PathVariable String id) {
        return wrap(() -> service.transition(id,
                DataGovernanceCandidateService.STATUS_PENDING_APPROVAL, null, "submit"), "submit");
    }

    /** 审批通过：PENDING_APPROVAL → APPROVED（需 approvalRef；生成/验证严禁当审批）。 */
    @PostMapping("/candidates/{id}/approve")
    public ApiResponse<Map<String, Object>> approve(
            @PathVariable String id,
            @RequestParam("approvalRef") String approvalRef) {
        return wrap(() -> service.approve(id, approvalRef, null), "approve");
    }

    @PostMapping("/candidates/{id}/reject")
    public ApiResponse<Map<String, Object>> reject(
            @PathVariable String id,
            @RequestParam(value = "reason", required = false) String reason) {
        return wrap(() -> service.transition(id,
                DataGovernanceCandidateService.STATUS_REJECTED, null, reason), "reject");
    }

    @PostMapping("/candidates/{id}/need-evidence")
    public ApiResponse<Map<String, Object>> needsEvidence(
            @PathVariable String id,
            @RequestParam(value = "reason", required = false) String reason) {
        return wrap(() -> service.transition(id,
                DataGovernanceCandidateService.STATUS_NEEDS_EVIDENCE, null, reason), "need-evidence");
    }

    @PostMapping("/candidates/{id}/conflict")
    public ApiResponse<Map<String, Object>> conflict(
            @PathVariable String id,
            @RequestParam(value = "reason", required = false) String reason) {
        return wrap(() -> service.transition(id,
                DataGovernanceCandidateService.STATUS_CONFLICTED, null, reason), "conflict");
    }

    @PostMapping("/candidates/{id}/expire")
    public ApiResponse<Map<String, Object>> expire(@PathVariable String id) {
        return wrap(() -> service.transition(id,
                DataGovernanceCandidateService.STATUS_EXPIRED, null, "expire"), "expire");
    }

    /**
     * DATA_CHANGE_APPLY：APPROVED → APPLIED。高风险写操作，先过 security-engine 裁决，
     * 裁决 FAILED / 不可用 → fail-closed DENY（403，SUP-DATA-02）。正式变更由既有
     * DQ/数据源审批执行，本端点仅回执候选应用态。
     */
    @PostMapping("/candidates/{id}/apply")
    public ApiResponse<Map<String, Object>> apply(@PathVariable String id) {
        return wrap(() -> service.applyApprovedProposal(id), "apply");
    }

    // ═══════════════ DQ_PREVIEW（只读） ═══════════════

    /** DQ 规则候选只读预览：SUP-DATA-01 — 预览前后正式版本/水位线/行数无变化。 */
    @GetMapping("/dq-preview/{id}")
    public ApiResponse<Map<String, Object>> dqPreview(@PathVariable String id) {
        return wrap(() -> service.previewDqRule(id), "dq-preview");
    }

    // ═══════════════ 统一包装 ═══════════════

    private <T> ApiResponse<T> wrap(java.util.function.Supplier<T> op, String action) {
        try {
            T data = op.get();
            log.debug("DataGovernance {} succeeded", action);
            return ApiResponse.success(data);
        } catch (com.chinacreator.gzcm.common.exception.DataBridgeException e) {
            // 业务异常族（NotFound/Forbidden/Validation/SecurityDeny）按各自 httpStatus 透出
            throw e;
        } catch (IllegalArgumentException e) {
            throw new com.chinacreator.gzcm.common.exception.ValidationException(action, e.getMessage());
        } catch (IllegalStateException e) {
            // CAS 冲突 / 前置状态不满足（409 语义由 GlobalExceptionHandler 承载）
            throw e;
        }
    }
}
