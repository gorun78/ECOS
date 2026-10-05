package com.chinacreator.gzcm.ai.wagent.candidate;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.CandidateStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 分册10 F10-20/F10-23 · Candidate 生命周期服务（内存态）。
 *
 * <p><b>Agent 必留痕</b>：{@code generatedByRun == null} 一律拒收（红线：无来源不留痕）。
 * 四眼原则（4-eyes）：审批人必须不同于生成 Run；仅 PENDING_APPROVAL → APPROVED；
 * 过期（expireTime != far-future）强制 EXPIRED。审计事件仅登记（不外抛）。</p>
 */
public final class CandidateService {

    private final Map<String, CandidateModel> store = new LinkedHashMap<>();
    private final List<String> audit = new ArrayList<>();

    public String create(CandidateModel m) {
        Objects.requireNonNull(m, "candidate");
        WAgentEnums.CandidateType t = m.type();
        if (t == null) {
            throw new IllegalArgumentException("E-WA-CAND: candidateType 不可为空（类型单源 WAgentEnums.CandidateType）");
        }
        if (m.generatedByRun() == null) {
            throw new IllegalStateException("E-WA-STATE: Agent 必留痕，generatedByRun 不可为空");
        }
        String hash = sha256Hex(m.payloadJson());
        CandidateModel draft = new CandidateModel(
                m.candidateId(), t, m.targetEngine(), m.targetObjectRefJson(),
                m.payloadJson(), hash, m.generatedByRun(), m.generatedByStep(),
                m.confidence(), m.basisJson(),
                m.vStructure(), m.vReference(), m.vConflict(), m.vImpact(), m.vRegression(),
                CandidateStatus.DRAFT, m.batchId(), m.engineDraftRef(),
                m.publishedRef(), m.publishedGitRef(), m.expireTime());
        store.put(draft.candidateId(), draft);
        audit.add("CREATE " + draft.candidateId());
        return draft.candidateId();
    }

    /**
     * 四眼审批：PENDING_APPROVAL → APPROVED → 待发布（返回 status=APPROVED 的模型）。
     * 过期保护：expireTime 非远未来 ⇒ EXPIRED（拒批）。
     */
    public CandidateModel approve(String id, String approvalToken, String reviewerId) {
        CandidateModel cur = get(id);
        if (cur.status() != CandidateStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("E-WA-STATE: 仅 PENDING_APPROVAL 可审批，当前 " + cur.status());
        }
        if (reviewerId == null || reviewerId.equals(cur.generatedByRun())) {
            throw new IllegalStateException("E-WA-STATE: four-eyes violated，审批人须不同于生成 Run");
        }
        if (!isFarFuture(cur.expireTime())) {
            throw new IllegalStateException("E-WA-STATE: candidate EXPIRED，不可审批");
        }
        CandidateModel approved = withStatus(cur, CandidateStatus.APPROVED);
        store.put(id, approved);
        audit.add("APPROVE " + id + " by " + reviewerId + " token=" + approvalToken);
        return approved;
    }

    public CandidateModel updateStatus(String id, CandidateStatus to) {
        CandidateModel cur = get(id);
        LifecycleState.ensureTransition(cur.status(), to);
        CandidateModel next = withStatus(cur, to);
        store.put(id, next);
        audit.add("STATUS " + id + " -> " + to);
        return next;
    }

    public List<CandidateModel> listByBatch(String batchId) {
        List<CandidateModel> out = new ArrayList<>();
        for (CandidateModel m : store.values()) {
            if (Objects.equals(m.batchId(), batchId)) out.add(m);
        }
        return out;
    }

    public CandidateModel get(String id) {
        CandidateModel m = store.get(id);
        if (m == null) throw new IllegalStateException("E-WA-STATE: candidate 不存在 " + id);
        return m;
    }

    public List<String> auditTrail() { return List.copyOf(audit); }

    // ---- helpers ----
    private static CandidateModel withStatus(CandidateModel c, CandidateStatus s) {
        return new CandidateModel(
                c.candidateId(), c.type(), c.targetEngine(), c.targetObjectRefJson(),
                c.payloadJson(), c.payloadHash(), c.generatedByRun(), c.generatedByStep(),
                c.confidence(), c.basisJson(), c.vStructure(), c.vReference(), c.vConflict(),
                c.vImpact(), c.vRegression(), s, c.batchId(), c.engineDraftRef(),
                c.publishedRef(), c.publishedGitRef(), c.expireTime());
    }

    private static boolean isFarFuture(Instant expireTime) {
        return expireTime != null && expireTime.isAfter(Instant.now().plusSeconds(3600));
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input == null ? new byte[0] : input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("E-WA-STATE: SHA-256 不可用", e);
        }
    }
}
