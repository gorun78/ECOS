package com.chinacreator.gzcm.ai.wagent.candidate;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 分册10 F10-23 · Candidate 发布五步（附件 §6.2 / 铁律 §十 版本×Git 归档）。
 *
 * <ol>
 *   <li>校验 token + 四眼 + 未过期（委托 {@link CandidateService}）</li>
 *   <li>{@link CandidateCommitPort#commit} — 引擎侧写 draft（Port，**本类只定义、不实现**）</li>
 *   <li>{@link GitArchivePort#commitAndTag} — Git 归档 + 打 tag {@code wagent-<type>-<id>-<ver>}</li>
 *   <li>{@link AuditEventPublisher#emit} — 审计事件（topic=eecos.audit / wagent.candidate / CandidatePublished）</li>
 *   <li>回写 {@code publishedRef} + {@code publishedGitRef}</li>
 * </ol>
 *
 * <p>Port 若未注入（默认）⇒ 抛 {@link UnsupportedOperationException}，由 wiring 层替换为真实现。
 * 无 double/float 进入签名；无 Spring 依赖。</p>
 */
public final class CommitService {

    /** 发布入参（F10-23）。 */
    public record PublishRiser(String candidateId, String approvalToken, String actorUser) {}

    // ---- Port 接口（本包内嵌，wiring 期注入实现） ----

    public interface CandidateCommitPort {
        PublishResult commit(CandidateModel m, String actor);
    }

    public interface GitArchivePort {
        GitRef commitAndTag(String repo, String branch, String message);
    }

    public interface AuditEventPublisher {
        void emit(String topic, String aggregator, String eventType, Map<String, Object> payload);
    }

    public record PublishResult(String gitRef, String ownerEngineRef) {}
    public record GitRef(String repo, String branch, String commitHash, String tag) {}

    private final CandidateService candidates;
    private final CandidateCommitPort commitPort;
    private final GitArchivePort gitPort;
    private final AuditEventPublisher audit;
    private final String repo;
    private final String branch;

    public CommitService(CandidateService candidates, CandidateCommitPort commitPort,
                         GitArchivePort gitPort, AuditEventPublisher audit,
                         String repo, String branch) {
        this.candidates = candidates;
        this.commitPort = commitPort;
        this.gitPort = gitPort;
        this.audit = audit;
        this.repo = repo;
        this.branch = branch;
    }

    /** 五步发布。返回带 publishedRef/publishedGitRef 的最终模型。 */
    public CandidateModel publish(PublishRiser riser) {
        // Step 1：四眼 + 状态 + 过期（委托 CandidateService）
        CandidateModel approved = candidates.approve(
                riser.candidateId(), riser.approvalToken(), actorOrDerived(riser));
        // Step 2：引擎侧写 draft
        PublishResult cr = requirePort(commitPort, "CandidateCommitPort").commit(approved, riser.actorUser());
        // Step 3：Git 归档 + tag
        String tag = "wagent-" + approved.type() + "-" + approved.candidateId() + "-" + shortId(approved.candidateId());
        GitRef ref = requirePort(gitPort, "GitArchivePort").commitAndTag(repo, branch, "publish " + approved.candidateId() + " tag " + tag);
        // Step 4：审计
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("candidateId", approved.candidateId());
        payload.put("type", String.valueOf(approved.type()));
        payload.put("tag", tag);
        requirePort(audit, "AuditEventPublisher").emit("ecos.audit", "wagent.candidate", "CandidatePublished", payload);
        // Step 5：回写 publishedRef + publishedGitRef
        CandidateModel published = withRefs(approved, cr.ownerEngineRef(), ref);
        candidates.updateStatus(approved.candidateId(),
                WAgentEnums.CandidateStatus.PUBLISHED);
        return published;
    }

    private String actorOrDerived(PublishRiser riser) {
        // 四眼：取 actorUser 作为审批人（须 != generatedByRun，由 CandidateService 校验）。
        return riser.actorUser();
    }

    private static <T> T requirePort(T port, String name) {
        if (port == null) {
            throw new UnsupportedOperationException("E-WA-COMMIT: " + name + " 未注入，需 wiring 层提供实现");
        }
        return port;
    }

    private static String shortId(String id) {
        return id == null ? "0" : (id.length() > 8 ? id.substring(0, 8) : id);
    }

    private static CandidateModel withRefs(CandidateModel c, String publishedRef, GitRef ref) {
        return new CandidateModel(
                c.candidateId(), c.type(), c.targetEngine(), c.targetObjectRefJson(),
                c.payloadJson(), c.payloadHash(), c.generatedByRun(), c.generatedByStep(),
                c.confidence(), c.basisJson(), c.vStructure(), c.vReference(), c.vConflict(),
                c.vImpact(), c.vRegression(), c.status(), c.batchId(), c.engineDraftRef(),
                publishedRef, ref == null ? null : ref.commitHash(), c.expireTime());
    }
}
