package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.CandidateStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.CandidateType;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReviewDecision;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.engine.ai.wagent.approval.ApprovalTokenService;
import com.chinacreator.gzcm.engine.ai.wagent.approval.WAgentApprovalScope;
import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.archive.ArchiveOutcome;
import com.chinacreator.gzcm.runtime.access.git.archive.DesignAssetArchiver;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentCandidateDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentCandidateReviewDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentCandidateDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentCandidateReviewDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import com.fasterxml.jackson.annotation.JsonAlias;
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
    private final ApprovalTokenService approvalTokenService;
    private final AiSecurityEngineClient securityClient;
    private final GitRepositoryService gitRepositoryService;
    private final DesignAssetArchiver designAssetArchiver;

    public WAgentCandidateController(WAgentCandidateDao candidateDao,
                                     WAgentCandidateReviewDao reviewDao,
                                     WAgentIdentityResolver identityResolver,
                                     ApprovalTokenService approvalTokenService,
                                     AiSecurityEngineClient securityClient,
                                     GitRepositoryService gitRepositoryService,
                                     DesignAssetArchiver designAssetArchiver) {
        this.candidateDao = candidateDao;
        this.reviewDao = reviewDao;
        this.identityResolver = identityResolver;
        this.approvalTokenService = approvalTokenService;
        this.securityClient = securityClient;
        this.gitRepositoryService = gitRepositoryService;
        this.designAssetArchiver = designAssetArchiver;
    }

    /** #19 评审请求体（decision 取 ReviewDecision 三值；operator 一律服务端身份，禁自报）。 */
    public record CandidateReviewRequest(String decision, String commentText, String changesJson,
                                         String approvalToken) {
    }

    /** #20 发布请求体（approval_token 必填，§5.2 表）。 */
    public record CandidatePublishRequest(
            @JsonProperty("approvalToken") @JsonAlias("approval_token") String approvalToken) {
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

    /** 建候选请求体（knowledge extract 的落点；只落候选，不直写知识库）。 */
    public record CandidateCreateRequest(String candidateType, String targetEngine, String payloadJson,
                                         String basisJson, String generatedByRun, String batchId,
                                         BigDecimal confidence) {
    }

    /**
     * 建候选（#17 前置面，详设-10 §5.2 表外能力位）：knowledge extract 的产出落点。
     *
     * <p>初始状态固定 {@code GENERATED}（四元校验与评审各自推进状态机，禁直接置 approved）；
     * 载荷哈希由服务端计算，调用方不得自报（防「哈希与载荷不一致」）。</p>
     */
    @PostMapping(WAgentApiPaths.CANDIDATES_COLLECTION)
    public ResponseEntity<ApiResponse<CandidateDetailVO>> createCandidate(
            @RequestBody(required = false) CandidateCreateRequest req) {
        Identity identity = identityResolver.require();
        if (req == null) {
            throw WAgentOrchestrationException.validation(
                    "请求体必填：candidateType + payloadJson（knowledge extract 产物）");
        }
        String type = validateEnum(req.candidateType(), CandidateType.ALL::contains,
                "candidate_type 越界（CandidateType 十二类单源）");
        if (type == null) {
            throw WAgentOrchestrationException.validation("candidate_type 必填（CandidateType 十二类单源）");
        }
        if (req.payloadJson() == null || req.payloadJson().isBlank()) {
            throw WAgentOrchestrationException.validation("payload_json 必填（候选载荷不得为空）");
        }

        WAgentCandidateDO candidate = new WAgentCandidateDO();
        candidate.setId(UUID.randomUUID().toString());
        candidate.setCandidateType(type);
        candidate.setTargetEngine(req.targetEngine());
        candidate.setPayloadJson(req.payloadJson());
        candidate.setPayloadHash(sha256(req.payloadJson()));
        candidate.setBasisJson(req.basisJson());
        candidate.setGeneratedByRun(req.generatedByRun());
        candidate.setBatchId(req.batchId());
        candidate.setConfidence(req.confidence());
        candidate.setStatus(CandidateStatus.GENERATED.value());
        candidate.setTenantId(identity.tenantId());
        candidate.setOrgId(identity.orgId());
        candidate.setDomain("default");
        candidate.setVersionNo("1");
        candidate.setCreateBy(identity.operator());
        candidate.setUpdateBy(identity.operator());
        candidate.setIsDeleted(0);
        candidateDao.insert(candidate);

        return ResponseEntity.status(201).body(WAgentRunController.ok(
                CandidateDetailVO.of(CandidateViewVO.of(candidate), List.of())));
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

    /** 发布结果（强类型载荷；controller 不产出无类型 Map）。 */
    public record CandidatePublishResult(@JsonProperty("candidateId") String candidateId,
                                         @JsonProperty("status") String status,
                                         @JsonProperty("publishedRef") String publishedRef,
                                         @JsonProperty("gitRef") String gitRef,
                                         @JsonProperty("gitTag") String gitTag,
                                         @JsonProperty("repositoryId") String repositoryId,
                                         @JsonProperty("traceId") String traceId) {
    }

    /**
     * #20 发布（L3 真闭环，批 F）：凭证一次性消费 → pre-commit 裁决（fail-closed）→
     * Git 归档（runtime 单源）→ 落库 PUBLISHED。
     *
     * <p><b>顺序与代价</b>：状态闸先行（非 approved 直接 409，不烧凭证）→ 凭证 CAS 消费
     * （重放 409）→ 安全裁决 DENY 即 403 → 归档 → 落库。任一步在消费之后失败，
     * 凭证已作废需重新审批（单向凭证的保守取舍，审计留痕）。</p>
     *
     * <p><b>禁伪成功</b>：归档不可用（仓未注册/冻结/path 越界）一律 503 或 409 并带原因，
     * 绝不把「没归档成功」写成 published。</p>
     */
    @PostMapping(WAgentApiPaths.CANDIDATE_PUBLISH)
    public ResponseEntity<ApiResponse<CandidatePublishResult>> publishCandidate(
            @PathVariable(WAgentApiPaths.P_CANDIDATE_ID) String candidateId,
            @RequestBody(required = false) CandidatePublishRequest req) {
        Identity identity = identityResolver.require();
        WAgentCandidateDO candidate = loadForTenant(candidateId, identity.tenantId());
        // 凭证先校验（不消费）：无效凭证一律 403，避免用状态码暴露对象状态
        approvalTokenService.verify(req == null ? null : req.approvalToken(),
                WAgentApprovalScope.CANDIDATE_PUBLISH.name(), candidate.getId(), identity.operator());
        if (!CandidateStatus.APPROVED.value().equals(candidate.getStatus())) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_WA_STATE,
                    "仅 approved 候选可发布：当前 status=" + candidate.getStatus(),
                    Map.of("candidateId", candidate.getId(), "status", String.valueOf(candidate.getStatus())));
        }
        String traceId = AiTraceSupport.currentTraceId();

        // ① pre-commit 安全裁决（security-engine 不可用即 DENY，fail-closed）。
        // 批 A 授权墙双修：真传 identityResolver 解析的角色集（admin/ROLE_SUPER_ADMIN），
        // 不再让 AiSecurityEngineClient 落硬编码 role="user" ⇒ rbac.rego admin 规则可达。
        var rolesIdentity = identityResolver.requireWithRoles();
        var attrs = AiSecurityEngineClient.candidatePublishAttrs(
                candidate.getCandidateType(), candidate.getTargetEngine());
        if (!securityClient.evaluate(identity.operator(), identity.tenantId(),
                rolesIdentity.roles(), "candidate:publish", attrs)) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_POLICY,
                    "pre-commit 安全裁决未通过（含 security-engine 不可用的 fail-closed 情形）",
                    Map.of("candidateId", candidate.getId(), "traceId", traceId));
        }

        // ② Git 仓库定位（按候选类型映射到已注册仓；先于凭证消费，仓库不可用不该烧掉审批凭证）
        String repositoryId;
        try {
            repositoryId = repositoryIdFor(candidate.getCandidateType());
            gitRepositoryService.getRepositoryById(repositoryId); // 存在性/启用态校验（不猜默认仓）
        } catch (WAgentOrchestrationException e) {
            throw e;
        } catch (Exception e) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_WA_DEP,
                    "Git 仓库不可用（ecos_control.ecos_git_repo 未登记或已停用）: " + repositoryIdFor(candidate.getCandidateType()),
                    Map.of("service", "runtime-access/git", "traceId", traceId));
        }

        // ③ 凭证校验 + 一次性消费（CAS；并发/重放 ⇒ 409）
        approvalTokenService.verifyAndConsume(req == null ? null : req.approvalToken(),
                WAgentApprovalScope.CANDIDATE_PUBLISH.name(), candidate.getId(), identity.operator(), traceId);

        // ④ Git 归档（唯一出口 = runtime DesignAssetArchiver）
        String content = candidate.getPayloadJson() == null ? "{}" : candidate.getPayloadJson();
        ArchiveOutcome outcome = designAssetArchiver.archive(repositoryId, "candidates", candidate.getId(),
                null, content, "publish candidate " + candidate.getId(), identity.operator());
        if (!outcome.isArchived()) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_WA_DEP,
                    "Git 归档未完成: " + outcome.reason(),
                    Map.of("service", "runtime-access/git", "candidateId", candidate.getId()));
        }

        // ⑤ 落库（WHERE status='approved' 兜底，防并发双发布）
        int updated = candidateDao.updatePublished(candidate.getId(),
                CandidateStatus.PUBLISHED.value(), outcome.commitId(), outcome.commitId(), identity.operator());
        if (updated != 1) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_WA_STATE,
                    "发布落库未生效（并发状态变化），请重查候选状态",
                    Map.of("candidateId", candidate.getId()));
        }

        return ResponseEntity.ok(WAgentRunController.ok(new CandidatePublishResult(
                candidate.getId(), CandidateStatus.PUBLISHED.value(), outcome.commitId(),
                outcome.commitId(), outcome.tag(), repositoryId, traceId)));
    }

    // ── 小件 ────────────────────────────────────────────────────────

    /**
     * 候选类型 → 归档仓（§2.9.5① 六枚注册仓的语义归属）。
     *
     * <p>不取「默认仓」：注册表允许并存多仓时取默认等于猜测（runtime 侧明确拒绝猜）。
     * 未登记的类型一律 400，禁落到某个仓冒充归档成功。</p>
     */
    static String repositoryIdFor(String candidateType) {
        if (candidateType == null || candidateType.isBlank()) {
            throw WAgentOrchestrationException.validation("候选缺 candidate_type，无法确定归档仓");
        }
        return switch (candidateType.trim().toUpperCase(Locale.ROOT)) {
            case "ENTITY", "RELATIONSHIP", "METRIC", "DQ_RULE", "DATA_MAPPING" -> "ontology-model";
            case "KNOWLEDGE_ITEM", "ASSUMPTION" -> "kb-asset-tree";
            case "COGNITIVE_MODEL_DEF", "MODEL_ROUTING" -> "cognitive-model";
            case "SCENARIO" -> "scenario-def";
            case "DECISION_OPTION", "ACTION_DRAFT" -> "agent-collab";
            default -> throw WAgentOrchestrationException.validation(
                    "未登记的候选类型，无法确定归档仓: " + candidateType);
        };
    }

    private static String sha256(String text) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

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
