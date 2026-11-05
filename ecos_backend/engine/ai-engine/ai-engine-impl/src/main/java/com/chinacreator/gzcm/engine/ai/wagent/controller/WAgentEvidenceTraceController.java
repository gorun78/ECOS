package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentClaimDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentClaimEvidenceDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentEvidenceDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentClaimDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentClaimEvidenceDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentEvidenceDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Claim / Evidence 端点（详细设计-10 §5.2 #21 listClaims / #22 traceEvidence，F10-24）。
 *
 * <p><b>引用型纪律（§6.3）</b>：Evidence 无内容字段，只出来源引用/定位符/内容哈希/密级，
 * 业务明细正文永不入本响应（禁在此二次组装正文）。密级过滤（classify_level）属
 * security-engine CLS 职责，本层不写策略（ST04/05），响应以
 * {@code classification_filter=pending#security-CLS} 如实标注。</p>
 *
 * <p>租户闸：Run 归属租户校验（#21）与对象租户校验（#22）均掩 404（§5.3 防枚举）。</p>
 *
 * <p><b>#147CO（DoD DI-2 第十八批）</b>：响应视图由 Map 改 record（snake 键用
 * {@code @JsonProperty} 钉住），两处**条件键**保持「缺失而非 null」：证据关联里的
 * {@code evidence} 仅在跨租户过滤后仍可取时出现，反向索引里的 {@code run_id} 仅在 Claim
 * 可取时出现（属性级 NON_NULL）。</p>
 */
@RestController
public class WAgentEvidenceTraceController {

    /** 密级过滤形态标注（security-engine CLS 通道接线后改发其裁决）。 */
    static final String CLASSIFICATION_FILTER = "pending#security-CLS(本层不写策略, ST04/05)";

    private final WAgentRunDao runDao;
    private final WAgentClaimDao claimDao;
    private final WAgentClaimEvidenceDao claimEvidenceDao;
    private final WAgentEvidenceDao evidenceDao;
    private final WAgentIdentityResolver identityResolver;

    public WAgentEvidenceTraceController(WAgentRunDao runDao,
                                         WAgentClaimDao claimDao,
                                         WAgentClaimEvidenceDao claimEvidenceDao,
                                         WAgentEvidenceDao evidenceDao,
                                         WAgentIdentityResolver identityResolver) {
        this.runDao = runDao;
        this.claimDao = claimDao;
        this.claimEvidenceDao = claimEvidenceDao;
        this.evidenceDao = evidenceDao;
        this.identityResolver = identityResolver;
    }

    /** #21 Run 的 Claim 清单 + 逐条 Evidence 关联与等级（只读）。 */
    @GetMapping(WAgentApiPaths.CLAIMS_BY_RUN)
    public ApiResponse<ClaimsResponseVO> listClaims(
            @PathVariable(WAgentApiPaths.P_RUN_ID) String runId) {
        Identity identity = identityResolver.require();
        WAgentRunDO run = runDao.findById(runId)
                .filter(r -> identity.tenantId().equals(r.getTenantId()))
                .orElseThrow(WAgentOrchestrationException::notFound);

        List<ClaimVO> claims = new ArrayList<>();
        for (WAgentClaimDO claim : claimDao.findByRunId(run.getId())) {
            ClaimBaseVO m = claimView(claim);
            List<EvidenceLinkVO> links = new ArrayList<>();
            for (WAgentClaimEvidenceDO rel : claimEvidenceDao.findByClaimId(claim.getId())) {
                EvidenceBriefVO brief = evidenceDao.findById(rel.getEvidenceId())
                        .filter(e -> identity.tenantId().equals(e.getTenantId()))
                        .map(WAgentEvidenceTraceController::evidenceBrief)
                        .orElse(null);
                links.add(new EvidenceLinkVO(rel.getEvidenceId(), rel.getRelation(),
                        rel.getCoverRatio(), brief));
            }
            claims.add(ClaimVO.of(m, links));
        }
        return WAgentRunController.ok(new ClaimsResponseVO(run.getId(), claims, claims.size(),
                CLASSIFICATION_FILTER));
    }

    /** #22 引擎端追溯：证据来源四元组 + 快照引用 + 内容哈希 + 反向 Claim 索引（引用型，无正文）。 */
    @GetMapping(WAgentApiPaths.EVIDENCE_BY_ID)
    public ApiResponse<EvidenceTraceVO> traceEvidence(
            @PathVariable(WAgentApiPaths.P_EVIDENCE_ID) String evidenceId) {
        Identity identity = identityResolver.require();
        WAgentEvidenceDO evidence = evidenceDao.findById(evidenceId)
                .filter(e -> identity.tenantId().equals(e.getTenantId()))
                .orElseThrow(WAgentOrchestrationException::notFound);

        List<UsedByClaimVO> usedBy = new ArrayList<>();
        for (WAgentClaimEvidenceDO rel : claimEvidenceDao.findByEvidenceId(evidence.getId())) {
            String claimRunId = claimDao.findById(rel.getClaimId())
                    .map(WAgentClaimDO::getRunId).orElse(null);
            usedBy.add(new UsedByClaimVO(rel.getClaimId(), rel.getRelation(), rel.getCoverRatio(),
                    claimRunId));
        }
        return WAgentRunController.ok(EvidenceTraceVO.of(evidenceView(evidence), usedBy,
                CLASSIFICATION_FILTER,
                "pending#owner-engine-read-endpoint(本响应只出引用型登记面)"));
    }

    // ── 视图（#147CO：键名＝旧 Map 键，键序不变） ──────────────────

    static ClaimBaseVO claimView(WAgentClaimDO c) {
        return new ClaimBaseVO(c.getId(), c.getRunId(), c.getStepId(), c.getClaimText(),
                c.getClaimType(), c.getNumericValue(), c.getNumericUnit(), c.getValueSource(),
                c.getEvidenceGrade(), c.getVerifyStatus());
    }

    private static EvidenceBriefVO evidenceBrief(WAgentEvidenceDO e) {
        return new EvidenceBriefVO(e.getEvidenceType(), e.getSourceEngine(), e.getClassifyLevel(),
                e.getIsValid());
    }

    static EvidenceVO evidenceView(WAgentEvidenceDO e) {
        return new EvidenceVO(e.getId(), e.getEvidenceType(), e.getSourceEngine(),
                e.getSourceObjectType(), e.getSourceObjectId(), e.getSourceVersion(),
                e.getSnapshotRefJson(), e.getLocatorText(), e.getContentHash(), e.getClassifyLevel(),
                e.getIsValid(), e.getCreateTime() == null ? null : e.getCreateTime().toString());
    }

    /** Claim 基视图（10 键，与既有关键面门禁名录一致；{@code evidence_links} 由端点追加）。 */
    public record ClaimBaseVO(@JsonProperty("claim_id") String claimId,
                              @JsonProperty("run_id") String runId,
                              @JsonProperty("step_id") String stepId,
                              @JsonProperty("claim_text") String claimText,
                              @JsonProperty("claim_type") String claimType,
                              @JsonProperty("numeric_value") BigDecimal numericValue,
                              @JsonProperty("numeric_unit") String numericUnit,
                              @JsonProperty("value_source") String valueSource,
                              @JsonProperty("evidence_grade") String evidenceGrade,
                              @JsonProperty("verify_status") String verifyStatus) {
    }

    /** Claim 视图（11 键，{@code evidence_links} 在尾，键序与旧 Map 追加顺序一致）。 */
    public record ClaimVO(@JsonProperty("claim_id") String claimId,
                          @JsonProperty("run_id") String runId,
                          @JsonProperty("step_id") String stepId,
                          @JsonProperty("claim_text") String claimText,
                          @JsonProperty("claim_type") String claimType,
                          @JsonProperty("numeric_value") BigDecimal numericValue,
                          @JsonProperty("numeric_unit") String numericUnit,
                          @JsonProperty("value_source") String valueSource,
                          @JsonProperty("evidence_grade") String evidenceGrade,
                          @JsonProperty("verify_status") String verifyStatus,
                          @JsonProperty("evidence_links") List<EvidenceLinkVO> evidenceLinks) {

        static ClaimVO of(ClaimBaseVO b, List<EvidenceLinkVO> links) {
            return new ClaimVO(b.claimId(), b.runId(), b.stepId(), b.claimText(), b.claimType(),
                    b.numericValue(), b.numericUnit(), b.valueSource(), b.evidenceGrade(),
                    b.verifyStatus(), links);
        }
    }

    /** Claim 关联（4 键；{@code evidence} 仅在可取时出现）。 */
    public record EvidenceLinkVO(@JsonProperty("evidence_id") String evidenceId,
                                 @JsonProperty("relation") String relation,
                                 @JsonProperty("cover_ratio") BigDecimal coverRatio,
                                 @JsonInclude(JsonInclude.Include.NON_NULL)
                                 @JsonProperty("evidence") EvidenceBriefVO evidence) {
    }

    /** 证据摘要（4 键）。 */
    public record EvidenceBriefVO(@JsonProperty("evidence_type") String evidenceType,
                                  @JsonProperty("source_engine") String sourceEngine,
                                  @JsonProperty("classify_level") String classifyLevel,
                                  @JsonProperty("is_valid") Integer isValid) {
    }

    /** 证据登记面（12 键）。 */
    public record EvidenceVO(@JsonProperty("evidence_id") String evidenceId,
                             @JsonProperty("evidence_type") String evidenceType,
                             @JsonProperty("source_engine") String sourceEngine,
                             @JsonProperty("source_object_type") String sourceObjectType,
                             @JsonProperty("source_object_id") String sourceObjectId,
                             @JsonProperty("source_version") String sourceVersion,
                             @JsonProperty("snapshot_ref_json") String snapshotRefJson,
                             @JsonProperty("locator_text") String locatorText,
                             @JsonProperty("content_hash") String contentHash,
                             @JsonProperty("classify_level") String classifyLevel,
                             @JsonProperty("is_valid") Integer isValid,
                             @JsonProperty("create_time") String createTime) {
    }

    /** 反向索引（4 键；{@code run_id} 仅在 Claim 可取时出现）。 */
    public record UsedByClaimVO(@JsonProperty("claim_id") String claimId,
                                @JsonProperty("relation") String relation,
                                @JsonProperty("cover_ratio") BigDecimal coverRatio,
                                @JsonInclude(JsonInclude.Include.NON_NULL)
                                @JsonProperty("run_id") String runId) {
    }

    /** #21 响应（4 键）。 */
    public record ClaimsResponseVO(@JsonProperty("run_id") String runId,
                                   @JsonProperty("claims") List<ClaimVO> claims,
                                   @JsonProperty("total") int total,
                                   @JsonProperty("classification_filter") String classificationFilter) {
    }

    /** #22 响应（证据 12 键平铺 + 3 键，共 15 键）。 */
    public record EvidenceTraceVO(@JsonProperty("evidence_id") String evidenceId,
                                  @JsonProperty("evidence_type") String evidenceType,
                                  @JsonProperty("source_engine") String sourceEngine,
                                  @JsonProperty("source_object_type") String sourceObjectType,
                                  @JsonProperty("source_object_id") String sourceObjectId,
                                  @JsonProperty("source_version") String sourceVersion,
                                  @JsonProperty("snapshot_ref_json") String snapshotRefJson,
                                  @JsonProperty("locator_text") String locatorText,
                                  @JsonProperty("content_hash") String contentHash,
                                  @JsonProperty("classify_level") String classifyLevel,
                                  @JsonProperty("is_valid") Integer isValid,
                                  @JsonProperty("create_time") String createTime,
                                  @JsonProperty("used_by_claims") List<UsedByClaimVO> usedByClaims,
                                  @JsonProperty("classification_filter") String classificationFilter,
                                  @JsonProperty("engine_trace") String engineTrace) {

        static EvidenceTraceVO of(EvidenceVO e, List<UsedByClaimVO> usedBy,
                                  String classificationFilter, String engineTrace) {
            return new EvidenceTraceVO(e.evidenceId(), e.evidenceType(), e.sourceEngine(),
                    e.sourceObjectType(), e.sourceObjectId(), e.sourceVersion(), e.snapshotRefJson(),
                    e.locatorText(), e.contentHash(), e.classifyLevel(), e.isValid(), e.createTime(),
                    usedBy, classificationFilter, engineTrace);
        }
    }
}
