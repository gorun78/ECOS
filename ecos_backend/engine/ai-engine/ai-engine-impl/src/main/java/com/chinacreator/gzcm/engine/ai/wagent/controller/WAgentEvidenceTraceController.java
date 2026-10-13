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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Claim / Evidence 端点（详细设计-10 §5.2 #21 listClaims / #22 traceEvidence，F10-24）。
 *
 * <p><b>引用型纪律（§6.3）</b>：Evidence 无内容字段，只出来源引用/定位符/内容哈希/密级，
 * 业务明细正文永不入本响应（禁在此二次组装正文）。密级过滤（classify_level）属
 * security-engine CLS 职责，本层不写策略（ST04/05），响应以
 * {@code classification_filter=pending#security-CLS} 如实标注。</p>
 *
 * <p>租户闸：Run 归属租户校验（#21）与对象租户校验（#22）均掩 404（§5.3 防枚举）。</p>
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
    public ApiResponse<Map<String, Object>> listClaims(
            @PathVariable(WAgentApiPaths.P_RUN_ID) String runId) {
        Identity identity = identityResolver.require();
        WAgentRunDO run = runDao.findById(runId)
                .filter(r -> identity.tenantId().equals(r.getTenantId()))
                .orElseThrow(WAgentOrchestrationException::notFound);

        List<Map<String, Object>> claims = new ArrayList<>();
        for (WAgentClaimDO claim : claimDao.findByRunId(run.getId())) {
            Map<String, Object> m = claimView(claim);
            List<Map<String, Object>> links = new ArrayList<>();
            for (WAgentClaimEvidenceDO rel : claimEvidenceDao.findByClaimId(claim.getId())) {
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("evidence_id", rel.getEvidenceId());
                l.put("relation", rel.getRelation());
                l.put("cover_ratio", rel.getCoverRatio());
                evidenceDao.findById(rel.getEvidenceId())
                        .filter(e -> identity.tenantId().equals(e.getTenantId()))
                        .ifPresent(e -> l.put("evidence", evidenceBrief(e)));
                links.add(l);
            }
            m.put("evidence_links", links);
            claims.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("run_id", run.getId());
        data.put("claims", claims);
        data.put("total", claims.size());
        data.put("classification_filter", CLASSIFICATION_FILTER);
        return WAgentRunController.ok(data);
    }

    /** #22 引擎端追溯：证据来源四元组 + 快照引用 + 内容哈希 + 反向 Claim 索引（引用型，无正文）。 */
    @GetMapping(WAgentApiPaths.EVIDENCE_BY_ID)
    public ApiResponse<Map<String, Object>> traceEvidence(
            @PathVariable(WAgentApiPaths.P_EVIDENCE_ID) String evidenceId) {
        Identity identity = identityResolver.require();
        WAgentEvidenceDO evidence = evidenceDao.findById(evidenceId)
                .filter(e -> identity.tenantId().equals(e.getTenantId()))
                .orElseThrow(WAgentOrchestrationException::notFound);

        Map<String, Object> data = evidenceView(evidence);
        List<Map<String, Object>> usedBy = new ArrayList<>();
        for (WAgentClaimEvidenceDO rel : claimEvidenceDao.findByEvidenceId(evidence.getId())) {
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("claim_id", rel.getClaimId());
            u.put("relation", rel.getRelation());
            u.put("cover_ratio", rel.getCoverRatio());
            claimDao.findById(rel.getClaimId()).ifPresent(c -> u.put("run_id", c.getRunId()));
            usedBy.add(u);
        }
        data.put("used_by_claims", usedBy);
        data.put("classification_filter", CLASSIFICATION_FILTER);
        // 引擎端深追（按 source_engine/source_object_id 拉属主引擎现值）未接线：如实标注，禁伪造追溯结果
        data.put("engine_trace", "pending#owner-engine-read-endpoint(本响应只出引用型登记面)");
        return WAgentRunController.ok(data);
    }

    // ── 视图 ────────────────────────────────────────────────────────

    static Map<String, Object> claimView(WAgentClaimDO c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("claim_id", c.getId());
        m.put("run_id", c.getRunId());
        m.put("step_id", c.getStepId());
        m.put("claim_text", c.getClaimText());
        m.put("claim_type", c.getClaimType());
        m.put("numeric_value", c.getNumericValue());
        m.put("numeric_unit", c.getNumericUnit());
        m.put("value_source", c.getValueSource());
        m.put("evidence_grade", c.getEvidenceGrade());
        m.put("verify_status", c.getVerifyStatus());
        return m;
    }

    private static Map<String, Object> evidenceBrief(WAgentEvidenceDO e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("evidence_type", e.getEvidenceType());
        m.put("source_engine", e.getSourceEngine());
        m.put("classify_level", e.getClassifyLevel());
        m.put("is_valid", e.getIsValid());
        return m;
    }

    static Map<String, Object> evidenceView(WAgentEvidenceDO e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("evidence_id", e.getId());
        m.put("evidence_type", e.getEvidenceType());
        m.put("source_engine", e.getSourceEngine());
        m.put("source_object_type", e.getSourceObjectType());
        m.put("source_object_id", e.getSourceObjectId());
        m.put("source_version", e.getSourceVersion());
        m.put("snapshot_ref_json", e.getSnapshotRefJson());
        m.put("locator_text", e.getLocatorText());
        m.put("content_hash", e.getContentHash());
        m.put("classify_level", e.getClassifyLevel());
        m.put("is_valid", e.getIsValid());
        m.put("create_time", e.getCreateTime() == null ? null : e.getCreateTime().toString());
        return m;
    }
}
