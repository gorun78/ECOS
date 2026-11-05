package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.CandidateStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.CandidateType;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReviewDecision;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentCandidateDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentCandidateReviewDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentCandidateDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentCandidateReviewDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Candidate 端点（详细设计-10 §5.2 #17 listCandidates / #18 getCandidate /
 * #19 reviewCandidate / #20 publishCandidate，F10-20/23）。
 *
 * <p><b>#19 四眼纪律（词表单源 {@link ReviewDecision}）</b>：评审人 = 认证主体操作者，
 * 禁请求体自报；评审人 == 候选生成者（create_by）⇒ 403 {@code E-POLICY} fail-closed；
 * 同人重复评审 ⇒ 409 {@code E-WA-STATE}；仅 {@code validated/review_pending} 可审、
 * 过期（TTL，F10-20）即 409；APPROVE 必携 approval_token（凭证校验登记 R-40，
 * 本批先做非空闸）。本端点为单条面 ⇒ 天然满足"语义/口径/知识禁批量"
 * （{@code CandidateType.rejectsBulkApprove} 的批量禁项在此无批量入口可越）。</p>
 *
 * <p><b>#20 明确降级</b>：pre-commit 裁决（security-engine）+ Git 归档（runtime Git 单源）
 * 均未接线 ⇒ approval_token 非空 + 状态机（approved）校验通过后如实 501，
 * <b>禁</b>置 published 伪状态。</p>
 */
@RestController
public class WAgentCandidateController {

    private final WAgentCandidateDao candidateDao;
    private final WAgentCandidateReviewDao reviewDao;
    private final WAgentIdentityResolver identityResolver;

    public WAgentCandidateController(WAgentCandidateDao candidateDao,
                                     WAgentCandidateReviewDao reviewDao,
                                     WAgentIdentityResolver identityResolver) {
        this.candidateDao = candidateDao;
        this.reviewDao = reviewDao;
        this.identityResolver = identityResolver;
    }

    /** #19 评审请求体（decision 取 ReviewDecision 三值；operator 一律服务端身份，禁自报）。 */
    public record CandidateReviewRequest(String decision, String commentText, String changesJson,
                                         String approvalToken) {
    }

    /** #20 发布请求体（approval_token 必填，§5.2 表）。 */
    public record CandidatePublishRequest(String approvalToken) {
    }

    /** 五段校验视图（旧 Map 内嵌 {@code validation}，键序不变）。 */
    public record CandidateValidationVO(@JsonProperty("structure") String structure,
                                        @JsonProperty("reference") String reference,
                                        @JsonProperty("conflict") String conflict,
                                        @JsonProperty("impact") String impact,
                                        @JsonProperty("regression") String regression) {
    }

    /** 候选视图（19 键；#17 列表项 / #18 #19 详情基底共用）。 */
    public record CandidateViewVO(@JsonProperty("candidate_id") String candidateId,
                                  @JsonProperty("candidate_type") String candidateType,
                                  @JsonProperty("target_engine") String targetEngine,
                                  @JsonProperty("target_object_ref_json") String targetObjectRefJson,
                                  @JsonProperty("payload_hash") String payloadHash,
                                  @JsonProperty("generated_by_run") String generatedByRun,
                                  @JsonProperty("generated_by_step") String generatedByStep,
                                  @JsonProperty("confidence") BigDecimal confidence,
                                  @JsonProperty("basis_json") String basisJson,
                                  @JsonProperty("validation") CandidateValidationVO validation,
                                  @JsonProperty("status") String status,
                                  @JsonProperty("batch_id") String batchId,
                                  @JsonProperty("engine_draft_ref_text") String engineDraftRefText,
                                  @JsonProperty("published_ref_text") String publishedRefText,
                                  @JsonProperty("published_git_ref") String publishedGitRef,
                                  @JsonProperty("expire_time") String expireTime,
                                  @JsonProperty("expired") boolean expired,
                                  @JsonProperty("version_no") String versionNo,
                                  @JsonProperty("create_time") String createTime) {

        static CandidateViewVO of(WAgentCandidateDO c) {
            return new CandidateViewVO(c.getId(), c.getCandidateType(), c.getTargetEngine(),
                    c.getTargetObjectRefJson(), c.getPayloadHash(), c.getGeneratedByRun(),
                    c.getGeneratedByStep(), c.getConfidence(), c.getBasisJson(),
                    new CandidateValidationVO(c.getValidationStructure(), c.getValidationReference(),
                            c.getValidationConflict(), c.getValidationImpact(), c.getValidationRegression()),
                    c.getStatus(), c.getBatchId(), c.getEngineDraftRefText(), c.getPublishedRefText(),
                    c.getPublishedGitRef(), c.getExpireTime() == null ? null : c.getExpireTime().toString(),
                    isExpired(c), c.getVersionNo(),
                    c.getCreateTime() == null ? null : c.getCreateTime().toString());
        }

        CandidateViewVO withStatus(String newStatus) {
            return new CandidateViewVO(candidateId, candidateType, targetEngine, targetObjectRefJson,
                    payloadHash, generatedByRun, generatedByStep, confidence, basisJson, validation,
                    newStatus, batchId, engineDraftRefText, publishedRefText, publishedGitRef,
                    expireTime, expired, versionNo, createTime);
        }
    }

    /** 评审视图（7 键；approval_token 只出存在位，凭证不外泄）。 */
    public record CandidateReviewVO(@JsonProperty("review_id") String reviewId,
                                    @JsonProperty("reviewer_id") String reviewerId,
                                    @JsonProperty("decision") String decision,
                                    @JsonProperty("comment_text") String commentText,
                                    @JsonProperty("changes_json") String changesJson,
                                    @JsonProperty("approval_token_present") boolean approvalTokenPresent,
                                    @JsonProperty("review_time") String reviewTime) {
    }

    /** #17 列表响应（candidates + total）。 */
    public record CandidateListVO(@JsonProperty("candidates") List<CandidateViewVO> candidates,
                                  @JsonProperty("total") int total) {
    }

    /** #18/#19 详情响应（候选 19 键平铺后追加 reviews）。 */
    public record CandidateDetailVO(@JsonProperty("candidate_id") String candidateId,
                                    @JsonProperty("candidate_type") String candidateType,
                                    @JsonProperty("target_engine") String targetEngine,
                                    @JsonProperty("target_object_ref_json") String targetObjectRefJson,
                                    @JsonProperty("payload_hash") String payloadHash,
                                    @JsonProperty("generated_by_run") String generatedByRun,
                                    @JsonProperty("generated_by_step") String generatedByStep,
                                    @JsonProperty("confidence") BigDecimal confidence,
                                    @JsonProperty("basis_json") String basisJson,
                                    @JsonProperty("validation") CandidateValidationVO validation,
                                    @JsonProperty("status") String status,
                                    @JsonProperty("batch_id") String batchId,
                                    @JsonProperty("engine_draft_ref_text") String engineDraftRefText,
                                    @JsonProperty("published_ref_text") String publishedRefText,
                                    @JsonProperty("published_git_ref") String publishedGitRef,
                                    @JsonProperty("expire_time") String expireTime,
                                    @JsonProperty("expired") boolean expired,
                                    @JsonProperty("version_no") String versionNo,
                                    @JsonProperty("create_time") String createTime,
                                    @JsonProperty("reviews") List<CandidateReviewVO> reviews) {

        static CandidateDetailVO of(CandidateViewVO v, List<CandidateReviewVO> reviews) {
            return new CandidateDetailVO(v.candidateId(), v.candidateType(), v.targetEngine(),
                    v.targetObjectRefJson(), v.payloadHash(), v.generatedByRun(), v.generatedByStep(),
                    v.confidence(), v.basisJson(), v.validation(), v.status(), v.batchId(),
                    v.engineDraftRefText(), v.publishedRefText(), v.publishedGitRef(), v.expireTime(),
                    v.expired(), v.versionNo(), v.createTime(), reviews);
        }
    }

    /** #17 列表：租户隔离 DAO 收口，类型/状态/批次/Run 为受控内存过滤（词表越界 400）。 */
    @GetMapping(WAgentApiPaths.CANDIDATES_COLLECTION)
    public ApiResponse<CandidateListVO> listCandidates(
            @RequestParam(value = "candidateType", required = false) String candidateType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "batchId", required = false) String batchId,
            @RequestParam(value = "runId", required = false) String runId) {
        Identity identity = identityResolver.require();
        String type = validateEnum(candidateType, CandidateType.ALL::contains, "candidate_type 越界（CandidateType 十二类单源）");
        String st = validateEnum(status, CandidateStatus.ALL::contains, "status 越界（CandidateStatus 十值单源）");

        List<CandidateViewVO> items = new ArrayList<>();
        for (WAgentCandidateDO c : candidateDao.findByTenant(identity.tenantId())) {
            if (type != null && !type.equals(c.getCandidateType())) {
                continue;
            }
            if (st != null && !st.equals(c.getStatus())) {
                continue;
            }
            if (batchId != null && !batchId.isBlank() && !batchId.trim().equals(c.getBatchId())) {
                continue;
            }
            if (runId != null && !runId.isBlank() && !runId.trim().equals(c.getGeneratedByRun())) {
                continue;
            }
            items.add(CandidateViewVO.of(c));
        }
        return WAgentRunController.ok(new CandidateListVO(items, items.size()));
    }

    /** #18 详情：含 basis/五段校验 + 评审链（跨租户掩 404，§5.3 防枚举）。 */
    @GetMapping(WAgentApiPaths.CANDIDATE_BY_ID)
    public ApiResponse<CandidateDetailVO> getCandidate(
            @PathVariable(WAgentApiPaths.P_CANDIDATE_ID) String candidateId) {
        Identity identity = identityResolver.require();
        WAgentCandidateDO candidate = loadForTenant(candidateId, identity.tenantId());
        return WAgentRunController.ok(CandidateDetailVO.of(CandidateViewVO.of(candidate),
                reviewView(reviewDao.findByCandidateId(candidate.getId()))));
    }

    /** #19 四眼评审（在位实装，见类注释）。 */
    @PostMapping(WAgentApiPaths.CANDIDATE_REVIEW)
    public ApiResponse<CandidateDetailVO> reviewCandidate(
            @PathVariable(WAgentApiPaths.P_CANDIDATE_ID) String candidateId,
            @RequestBody CandidateReviewRequest req) {
        Identity identity = identityResolver.require();
        WAgentCandidateDO candidate = loadForTenant(candidateId, identity.tenantId());

        if (req == null || req.decision() == null
                || !ReviewDecision.ALL.contains(req.decision().trim().toUpperCase(Locale.ROOT))) {
            throw WAgentOrchestrationException.validation(
                    "decision 必填且取 APPROVE|REJECT|REVISE（ReviewDecision 单源）");
        }
        String decision = req.decision().trim().toUpperCase(Locale.ROOT);
        if ("APPROVE".equals(decision) && (req.approvalToken() == null || req.approvalToken().isBlank())) {
            throw WAgentOrchestrationException.validation("APPROVE 必须携带 approval_token（凭证校验 R-40）");
        }

        CandidateStatus status = toStatus(candidate.getStatus());
        if (isExpired(candidate)) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_WA_STATE,
                    "候选已过期（TTL，F10-20），不得评审: status=" + candidate.getStatus(),
                    Map.of("candidateId", candidate.getId(), "status", candidate.getStatus()));
        }
        if (status == null || !status.canReview()) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_WA_STATE,
                    "当前状态不允许评审（仅 validated/review_pending）: status=" + candidate.getStatus(),
                    Map.of("candidateId", candidate.getId(), "status", String.valueOf(candidate.getStatus())));
        }
        // 四眼：评审人不得是候选生成者（词表单源注释：reviewer ≠ generator）
        if (identity.operator().equals(candidate.getCreateBy())) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_POLICY,
                    "四眼原则：候选生成者不得评审自己的候选（fail-closed）", null);
        }
        List<WAgentCandidateReviewDO> reviews = reviewDao.findByCandidateId(candidate.getId());
        if (reviews.stream().anyMatch(r -> identity.operator().equals(r.getReviewerId()))) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_WA_STATE,
                    "同一评审人对同一候选仅一次裁决（四眼链防重复）",
                    Map.of("candidateId", candidate.getId()));
        }

        WAgentCandidateReviewDO review = new WAgentCandidateReviewDO();
        review.setId(UUID.randomUUID().toString());
        review.setCandidateId(candidate.getId());
        review.setReviewerId(identity.operator());
        review.setDecision(decision);
        review.setCommentText(req.commentText());
        review.setChangesJson(req.changesJson());
        review.setApprovalToken(req.approvalToken());
        review.setReviewTime(LocalDateTime.now());
        review.setTenantId(candidate.getTenantId());
        review.setOrgId(candidate.getOrgId());
        review.setVersionNo("1");
        review.setCreateBy(identity.operator());
        review.setUpdateBy(identity.operator());
        reviewDao.insert(review);

        String next = switch (decision) {
            case "APPROVE" -> CandidateStatus.APPROVED.value();
            case "REJECT" -> CandidateStatus.REJECTED.value();
            default -> CandidateStatus.REVIEW_PENDING.value(); // REVISE：留在评审面等修订稿
        };
        candidateDao.updateStatus(candidate.getId(), next, identity.operator());

        return WAgentRunController.ok(CandidateDetailVO.of(CandidateViewVO.of(candidate).withStatus(next),
                reviewView(reviewDao.findByCandidateId(candidate.getId()))));
    }

    /** #20 发布（L3）：凭证/状态闸在位；pre-commit 裁决 + Git 归档未接线 ⇒ 如实 501。 */
    @PostMapping(WAgentApiPaths.CANDIDATE_PUBLISH)
    public ResponseEntity<ApiResponse<WAgentDegradation.NotImplementedVO>> publishCandidate(
            @PathVariable(WAgentApiPaths.P_CANDIDATE_ID) String candidateId,
            @RequestBody(required = false) CandidatePublishRequest req) {
        Identity identity = identityResolver.require();
        WAgentCandidateDO candidate = loadForTenant(candidateId, identity.tenantId());
        if (req == null || req.approvalToken() == null || req.approvalToken().isBlank()) {
            throw WAgentOrchestrationException.validation("approval_token 必填（§5.2 #20 L3 发布凭证）");
        }
        if (!CandidateStatus.APPROVED.value().equals(candidate.getStatus())) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_WA_STATE,
                    "仅 approved 候选可发布：当前 status=" + candidate.getStatus(),
                    Map.of("candidateId", candidate.getId(), "status", String.valueOf(candidate.getStatus())));
        }
        return WAgentDegradation.notImplemented(
                "F10-23 pre-commit 裁决(security-engine OPA)与 Git 归档(runtime 单源)未接线");
    }

    // ── 小件 ────────────────────────────────────────────────────────

    private WAgentCandidateDO loadForTenant(String candidateId, String tenantId) {
        return candidateDao.findById(candidateId)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .orElseThrow(WAgentOrchestrationException::notFound);
    }

    private static boolean isExpired(WAgentCandidateDO c) {
        return c.getExpireTime() != null && c.getExpireTime().isBefore(LocalDateTime.now());
    }

    private static CandidateStatus toStatus(String raw) {
        if (raw == null) {
            return null;
        }
        for (CandidateStatus s : CandidateStatus.values()) {
            if (s.value().equals(raw)) {
                return s;
            }
        }
        return null;
    }

    private static String validateEnum(String raw, java.util.function.Predicate<String> valid,
                                       String message) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        if (!valid.test(v)) {
            throw WAgentOrchestrationException.validation(message + ": " + raw);
        }
        return v;
    }

    private static List<CandidateReviewVO> reviewView(List<WAgentCandidateReviewDO> reviews) {
        List<CandidateReviewVO> list = new ArrayList<>();
        for (WAgentCandidateReviewDO r : reviews) {
            list.add(new CandidateReviewVO(r.getId(), r.getReviewerId(), r.getDecision(),
                    r.getCommentText(), r.getChangesJson(),
                    r.getApprovalToken() != null && !r.getApprovalToken().isBlank(),
                    r.getReviewTime() == null ? null : r.getReviewTime().toString()));
        }
        return list;
    }
}
