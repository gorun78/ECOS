package com.chinacreator.gzcm.engine.ontology.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.event.OntologyPublishedEvent;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.runtime.access.git.publish.DualWriteOutcome;
import com.chinacreator.gzcm.runtime.access.git.publish.LegOutcome;
import com.chinacreator.gzcm.runtime.access.git.publish.PublishReconciler;
import com.chinacreator.gzcm.runtime.access.git.publish.PublishState;
import com.chinacreator.gzcm.runtime.access.git.publish.PublishStateMachine;
import com.chinacreator.gzcm.runtime.access.git.publish.ReconciliationInput;
import com.chinacreator.gzcm.runtime.access.git.publish.ReconciliationVerdict;
import com.chinacreator.gzcm.runtime.access.git.publish.RollbackPlan;
import com.chinacreator.gzcm.engine.ontology.OntologyGitService;
import com.chinacreator.gzcm.engine.ontology.audit.PublishAuditReconciler;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveRequest;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitArchiveResult;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitReadResult;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyVersionSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyGitHistoryVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyVersionPreviousDiffVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyVersionVO;
import com.chinacreator.gzcm.engine.ontology.dto.VersionContentSource;
import com.chinacreator.gzcm.engine.ontology.model.OntologyEntity;
import com.chinacreator.gzcm.engine.ontology.model.OntologyProperty;
import com.chinacreator.gzcm.engine.ontology.model.OntologyRelationship;
import com.chinacreator.gzcm.engine.ontology.model.OntologyRule;
import com.chinacreator.gzcm.engine.ontology.model.OntologyAction;
import com.chinacreator.gzcm.engine.ontology.model.OntologyDomain;
import com.chinacreator.gzcm.engine.ontology.model.OntologyVersion;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyRuleRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyDomainRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyVersionRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyMappingStore;
import com.chinacreator.gzcm.engine.ontology.repository.GitArchivePendingRepository;

/**
 * 版本业务服务 — Snapshot / Publish / Rollback / Diff
 */
@Service
public class OntologyVersionService {

    private static final Logger log = LoggerFactory.getLogger(OntologyVersionService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OntologyVersionRepository versionRepository;
    private final OntologyRepository ontologyRepository;
    private final OntologyProposalService proposalService;
    /** 发布事件内存路径（同 JVM {@code @EventListener} 消费）。 */
    private final ApplicationEventPublisher appEventPublisher;
    /** 发布事件 Kafka/总线路径（跨 JVM 路由）；runtime-event 未装配时为空。 */
    private final Optional<EventBusService> eventBus;
    /** Git 归档通道（runtime GitService 委托）；未装配时为空，发布流程降级为不归档。 */
    private final Optional<OntologyGitService> gitService;

    /** Git 归档补偿队列入口（W83 新增）；未装配时空 = 归档失败仅日志不补偿。 */
    private final Optional<GitArchivePendingRepository> gitArchivePending;

    /** 安全引擎审计通道（#147N）；未装配时审计腿显式 NOT_APPLICABLE（禁伪成功）。 */
    private final Optional<com.chinacreator.gzcm.engine.ontology.security.SecurityEngineClient> securityClient;

    /**
     * 审计对账读回口（§2.9.4④ L95；#147AD）。可选装配：缺席时详情读面不出对账键，
     * 与「审计腿记 NOT_APPLICABLE」同口径——禁把「没装」写成「已落库」。
     * 用字段注入而非构造器参数，避免改动既有 2 个构造器的全部调用点。
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.beans.factory.ObjectProvider<com.chinacreator.gzcm.engine.ontology.audit.PublishAuditReconciler> auditReconcilerProvider;

    /**
     * DS-4 本体域第一批：已发布版本的 Git 历史读面（2026-11-08）。
     * 可选装配——缺席（单测直构/未接 Git）时回退库内读面，不伪造 Git 结果。
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.beans.factory.ObjectProvider<OntologyGitHistoryReader> gitHistoryReaderProvider;

    /** 旧 5 参构造保留（Wave31 单测直接 new；Git 归档 + 补偿均默认关闭）。 */
    public OntologyVersionService(OntologyVersionRepository versionRepository,
                                   OntologyRepository ontologyRepository,
                                   OntologyProposalService proposalService,
                                   ApplicationEventPublisher appEventPublisher,
                                   Optional<EventBusService> eventBus) {
        this(versionRepository, ontologyRepository, proposalService, appEventPublisher, eventBus,
                Optional.empty(), Optional.empty());
    }

    /** 6 参数过渡构造（API 只增不改；Wave-N / DI 兼容）。 */
    public OntologyVersionService(OntologyVersionRepository versionRepository,
                                   OntologyRepository ontologyRepository,
                                   OntologyProposalService proposalService,
                                   ApplicationEventPublisher appEventPublisher,
                                   Optional<EventBusService> eventBus,
                                   Optional<OntologyGitService> gitService) {
        this(versionRepository, ontologyRepository, proposalService, appEventPublisher, eventBus,
                gitService, Optional.empty());
    }

    /** Spring 装配入口（F03-15/W83 补齐：git + git_archive_pending 双补偿口）。 */
    @Autowired
    public OntologyVersionService(OntologyVersionRepository versionRepository,
                                   OntologyRepository ontologyRepository,
                                   OntologyProposalService proposalService,
                                   ApplicationEventPublisher appEventPublisher,
                                   Optional<EventBusService> eventBus,
                                   Optional<OntologyGitService> gitService,
                                   Optional<GitArchivePendingRepository> gitArchivePending,
                                   Optional<com.chinacreator.gzcm.engine.ontology.security.SecurityEngineClient> securityClient) {
        this.versionRepository = versionRepository;
        this.ontologyRepository = ontologyRepository;
        this.proposalService = proposalService;
        this.appEventPublisher = appEventPublisher;
        this.eventBus = eventBus;
        this.gitService = gitService != null ? gitService : Optional.empty();
        this.gitArchivePending = gitArchivePending != null ? gitArchivePending : Optional.empty();
        this.securityClient = securityClient != null ? securityClient : Optional.empty();
    }

    /** 7 参数过渡构造（#147N 审计腿接入前的装配面；API 只增不改）。 */
    public OntologyVersionService(OntologyVersionRepository versionRepository,
                                   OntologyRepository ontologyRepository,
                                   OntologyProposalService proposalService,
                                   ApplicationEventPublisher appEventPublisher,
                                   Optional<EventBusService> eventBus,
                                   Optional<OntologyGitService> gitService,
                                   Optional<GitArchivePendingRepository> gitArchivePending) {
        this(versionRepository, ontologyRepository, proposalService, appEventPublisher, eventBus,
                gitService, gitArchivePending, Optional.empty());
    }

    /**
     * 注册表 repositoryId（V300 种子 {@code ontology-model}，DQ-2 定名单源）。
     *
     * <p><b>#147E 订正</b>：本常量收口前为 {@code "ontology"}——那是仓库<b>目录名</b>
     * （{@code ecos_git_repo.repo_root}），不是注册表主键。{@code GitRepoRegistryDao.findByRepositoryId}
     * 按 {@code repository_id} 精确匹配，故每次发布归档都 404→{@code not_available}→入补偿队列，
     * 台账却按「归档已接」记账。目录名 ≠ repositoryId 的分叉由
     * {@code GitNamingAndOwnerSurfaceGateTest}（#147C 门）在路径层钉住，本常量在消费层订正。</p>
     */
    static final String GIT_REPO_ONTOLOGY = "ontology-model";

    /** 资产类型（仓内目录/标签前缀），与 repositoryId 语义不同，保持 {@code ontology}。 */
    private static final String GIT_ASSET_TYPE = "ontology";

    // ── DS-4 本体域第二层：草稿/状态事件（Git 提交即事件，库内零写）──
    private static final String GIT_DRAFT_MSG = "Draft ontology ";
    private static final String GIT_DEPRECATE_MSG = "Deprecate ontology ";
    private static final String GIT_STATUS_DEPRECATED = "{\"status\":\"Deprecated\"}";

    /**
     * 发布成功后委托 runtime Git 归档真实链路（H4-T4 / VG-2），并升级为 F03-15 可补偿语义（W83/C66）。
     *
     * <p>内容来源 = 版本快照 {@code snapshot}（{@link #generateSnapshot} 序列化产物）；
     * 仓库仅由服务端逻辑 {@code repositoryId="ontology"} 定位，禁止任何客户端 localPath/gitUrl。
     *
     * <p>三态处理（发布与归档分离，均不阻塞/回滚发布）：
     * <ol>
     *   <li>{@link OntologyGitArchiveResult#STATUS_COMMITTED}
     *       → 回填 {@code git_ref}（+ {@code commitId}），版本快照列语义降级为"在用指针"；</li>
     *   <li>非 COMMITTED（{@code not_available}）或抛异常
     *       → 写补偿队列 {@code ecos_ontology.ecos_ontology_git_archive_pending}（V170.2），
     *          由 runtime-task 指数退避重放；<b>git_ref 不回填</b>（禁伪成功），版本 UI 显"待归档"。</li>
     *   <li>compensation 队列本身写入失败 → 仅记 error 日志，不反噬发布（可用性优先，SLA 归分册 00 C20 底座）。</li>
     * </ol>
     * Git 通道未装配（monolith 早期 / 测试）时纯 no-op，与 Wave31 五参构造向后兼容。
     *
     * <p><b>#147D 起的返回语义变化</b>：本方法不再只是"发一趟归档"，而是把<b>归档腿</b>的执行结果
     * 作为三腿记录的一腿交回调用方（§2.9.6①「三者分别记录」）。返回的 {@link GitLegRecord}
     * 携带腿态、真实 {@code gitRef}、补偿队列句柄与人读原因；<b>本方法的异常仍不上抛</b>
     * （发布可用性优先，API 只增不改），但"上抛与否"与"如实记账与否"是两件事——
     * 收口前的形态是失败只落一条 warn 且对外照报已发布，自本批起该腿的结果必须进入
     * {@link PublishState}，{@code PARTIAL_FAILURE} 可被外部读出。
     */
    private GitLegRecord archiveToGit(OntologyVersion ver, String actor) {
        if (gitService.isEmpty()) {
            return new GitLegRecord(LegOutcome.NOT_APPLICABLE, null, null,
                    "Git 归档通道未装配（禁按已归档理解，§2.9.5② 全域接入未完项）");
        }
        String actorNorm = (actor == null || actor.isBlank()) ? "system" : actor;
        String commitMessage = "Publish ontology " + ver.getOntologyId() + " " + ver.getVersionNo();
        try {
            OntologyGitArchiveRequest req = new OntologyGitArchiveRequest();
            req.setRepositoryId(GIT_REPO_ONTOLOGY);
            req.setAssetType(GIT_ASSET_TYPE);
            req.setAssetId(ver.getOntologyId());
            req.setVersionNo(ver.getVersionNo());
            req.setContent(ver.getSnapshot());
            req.setCommitMessage(commitMessage);
            req.setAuthorName(actorNorm);
            OntologyGitArchiveResult r = gitService.get().archiveVersion(req);
            if (r != null && OntologyGitArchiveResult.STATUS_COMMITTED.equals(r.getStatus())) {
                String ref = r.getCommitId() != null ? r.getCommitId() : r.getTag();
                if (ref != null && !ref.isBlank()) {
                    // Git 事件化版本（id=git-<sha>）不在库内，跳过 DB 回填（DS-4 第二层）。
                    if (!isGitVersionId(ver.getId())) {
                        versionRepository.setGitRef(ver.getId(), ref);
                    }
                    return new GitLegRecord(LegOutcome.SUCCEEDED, ref, null, "已提交并回填 git_ref");
                }
                // COMMITTED 却无 ref：回执无可指向实物＝伪成功，按不成套记（§2.9.6①）
                return new GitLegRecord(LegOutcome.UNKNOWN, null, null,
                        "归档回报 COMMITTED 但无 commitId/tag 可回填");
            }
            String why = r == null ? "null result" : r.getStatus() + "/" + r.getReason();
            log.warn("Git 归档未 COMMITTED，入补偿队列: ontologyId={} version={} status={} reason={}",
                    ver.getOntologyId(), ver.getVersionNo(),
                    r == null ? "null" : r.getStatus(),
                    r == null ? "null result" : r.getReason());
            return new GitLegRecord(LegOutcome.FAILED, null,
                    enqueueArchivePending(ver, commitMessage, actorNorm, why),
                    "归档腿未成功（" + why + "）" );
        } catch (Exception e) {
            log.warn("Git 归档异常，入补偿队列: ontologyId={} version={} err={}",
                    ver.getOntologyId(), ver.getVersionNo(), e.getMessage());
            return new GitLegRecord(LegOutcome.FAILED, null,
                    enqueueArchivePending(ver, commitMessage, actorNorm,
                            e.getClass().getSimpleName() + ":" + e.getMessage()),
                    "归档腿异常（" + e.getClass().getSimpleName() + "）");
        }
    }

    /**
     * F03-15 (W83)：归档未落地时入补偿队列（不阻塞发布）。
     *
     * <p>队列表未装配或入队异常都仅记日志——补偿是<b>增强</b>而非发布前置，
     * 缺补偿不影响发布正确性（版本行依然 Published），仅"历史内容走 Git"这一 SLA 需人工补归档。
     *
     * <p>#147D 起返回<b>补偿队列行 ID</b>（可能为 {@code null}＝未受理），供三腿记录把它写成
     * 恢复腿句柄。注意该句柄是引擎自有补偿队列的 ID，<b>不是</b> runtime-task 的 {@code taskId}
     * ——句柄种类由 {@link DualWriteOutcome#KIND_COMPENSATION_QUEUE} 显式声明，
     * 台账登记为余项（§2.9.6② 要求的是 runtime-task 真实任务号）。
     *
     * @return 补偿队列行 ID；未装配或入队失败时为 {@code null}
     */
    private String enqueueArchivePending(OntologyVersion ver, String commitMessage,
                                        String actor, String reason) {
        if (gitArchivePending.isEmpty()) {
            log.error("Git 归档失败但补偿队列未装配（跳过）: ontologyId={} version={} reason={}",
                    ver.getOntologyId(), ver.getVersionNo(), reason);
            return null;
        }
        try {
            String queueId = gitArchivePending.get().enqueue(
                    ver.getOntologyId(), ver.getId(), ver.getVersionNo(),
                    GIT_REPO_ONTOLOGY, commitMessage, actor, ver.getSnapshot(), null);
            log.info("Git 归档已入补偿队列: queueId={} ontologyId={} version={} reason={}",
                    queueId, ver.getOntologyId(), ver.getVersionNo(), reason);
            return queueId;
        } catch (Exception e) {
            log.error("Git 归档入补偿队列失败（不阻断发布）: ontologyId={} version={} err={}",
                    ver.getOntologyId(), ver.getVersionNo(), e.getMessage());
            return null;
        }
    }

    // ── #147D：发布 / 双写三腿记录与状态推导（§2.9.6①③；词表单源在 runtime-access）──

    /**
     * 归档腿执行记录（{@link #archiveToGit} 的回执；三腿之一）。
     *
     * @param outcome 腿态（禁与「发布是否成功」折叠）
     * @param gitRef  真实提交号 / tag（{@code SUCCEEDED} 而无 ref＝伪成功，由状态机判不成套）
     * @param taskId  补偿队列行 ID（可为 {@code null}＝未受理）
     * @param reason  人读原因
     */
    private record GitLegRecord(LegOutcome outcome, String gitRef, String taskId, String reason) {
    }

    /** 审计腿记录（#147N；三腿之一）。 */
    private record AuditLegRecord(LegOutcome outcome, String reason) {
    }

    /**
     * 投递发布治理审计事件（§2.9.4③ / §2.9.6① 第三腿；#147N）。
     *
     * <p><b>诚实口径</b>：{@code auditGovernance} 返回 true 只代表事件<b>已被事件总线受理</b>
     * （异步落库），故把腿记为 {@link LegOutcome#SUCCEEDED} 并在原因写明「待对账」；
     * 总线未装配/投递失败记 {@link LegOutcome#UNKNOWN}（不记失败、更不记成功——UNKNOWN 会
     * 让发布态落 PARTIAL_FAILURE，禁按完全成功报）。通道未装配时记 NOT_APPLICABLE。</p>
     */
    private AuditLegRecord auditPublish(OntologyVersion ver, GitLegRecord gitLeg, int dbRows) {
        if (securityClient.isEmpty()) {
            return new AuditLegRecord(LegOutcome.NOT_APPLICABLE,
                    "安全审计通道未装配（禁按已审计理解，§2.9.6① 第三腿）");
        }
        try {
            com.chinacreator.gzcm.common.audit.AuditEventContract contract =
                    new com.chinacreator.gzcm.common.audit.AuditEventContract(
                            "ont-pub-" + ver.getId() + "-" + ver.getVersionNo(),
                            ver.getId(),
                            ver.getPublisher(),
                            List.of(),
                            "ontology",
                            "ontology_version",
                            ver.getId(),
                            com.chinacreator.gzcm.common.audit.AuditLevel.L3_HIGH_RISK.name(),
                            GIT_REPO_ONTOLOGY,
                            gitLeg.gitRef(),
                            null,
                            sha256Hex(ver.getSnapshot()),
                            null,
                            null,
                            null,
                            null,
                            null,
                            dbRows == 1 ? "APPLIED" : "FAILED",
                            "PUBLISH_" + gitLeg.outcome().name(),
                            java.time.Instant.now(),
                            java.util.UUID.randomUUID().toString());
            boolean accepted = securityClient.get().auditGovernance("ontology.publish", contract);
            return accepted
                    ? new AuditLegRecord(LegOutcome.SUCCEEDED,
                            "治理审计事件已投递 ecos.audit（异步落库，待对账）")
                    : new AuditLegRecord(LegOutcome.UNKNOWN,
                            "治理审计事件投递失败/总线未装配（审计腿未确证）");
        } catch (RuntimeException e) {
            return new AuditLegRecord(LegOutcome.UNKNOWN,
                    "治理审计事件投递异常：" + e.getClass().getSimpleName());
        }
    }

    /** 内容摘要（审计 content_hash）；无快照返回 null（不伪造空串）。 */
    private static String sha256Hex(String content) {
        if (content == null) {
            return null;
        }
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return "sha256:" + sb;
        } catch (java.security.NoSuchAlgorithmException e) {
            return null;
        }
    }

    /**
     * 一次发布的状态记录（对外只增两枚键：{@code publishState} / {@code publishStateReason}）。
     *
     * <p>{@code publishState} 取 §2.9.6① 的 16 态词表名，<b>与既有 {@code status} 列
     * （Draft/Published/Deprecated）是两套口径</b>：{@code status} 是权威库行的存储态，
     * {@code publishState} 是"这次发布三腿跑到哪、对账成不成"的执行态。二者可以不一致，
     * 且不一致正是本批要暴露的形态（行已 Published 而归档腿败 ＝ {@code PARTIAL_FAILURE}）。
     * 铁律「API 只增不改」下既有 {@code status} 语义与取值一律不动。</p>
     */
    private record PublishRecording(String publishState, String publishStateReason) {
    }

    /**
     * 由「权威库行数 ＋ 归档腿」组装三腿记录，推出发布态并对账（§2.9.6①③）。
     *
     * <p><b>审计腿的真实形态</b>（#147AB 订正旧注释）：本体发布<b>确实写</b>安全审计事件——
     * {@link #auditPublish} 经 {@code securityClient.auditGovernance} 投递 ecos.audit，事件带
     * {@code git_ref} 与内容哈希。但「总线受理」≠「异步已落库」，故腿记
     * {@code SUCCEEDED}+原因「待对账」；投递失败记 {@code UNKNOWN}（发布态随之落
     * {@code PARTIAL_FAILURE}）；通道未装配记 {@code NOT_APPLICABLE}。
     * 旧注释「本体发布路径今天不写安全审计腿」已被实证否定（该方法有 4 处调用点），
     * 保留会误导复核者把已存在的治理审计当作缺口。
     * 因 {@link PublishStateMachine#deriveState} 的上界是 {@code APPLIED}，
     * 本路径<b>没有任何一条腿组合能直接得出 {@code SUCCEEDED}</b>：报成功必须先过
     * {@link PublishReconciler}，而对账所需的仓库侧读回能力尚缺（read-at-ref 缺失，#147E 补），
     * 故真实结果恒落在 {@code VERIFYING} 或失败族。</p>
     *
     * <p><b>控制流不变</b>：本方法只读不算，不改写权威库、不上抛异常、不回滚已成功写入
     * （§2.9.6② 的回滚须经 {@link PublishStateMachine#planRollback}，禁在此现场反向写）。</p>
     */
    private PublishRecording recordPublishState(OntologyVersion ver, int dbRows, GitLegRecord gitLeg,
                                                AuditLegRecord auditLeg) {
        LegOutcome dbLeg = switch (dbRows) {
            case 1 -> LegOutcome.SUCCEEDED;
            case 0 -> LegOutcome.FAILED;
            default -> LegOutcome.UNKNOWN;
        };
        String versionRef = dbRows == 1 ? ver.getVersionNo() : null;
        DualWriteOutcome dual = DualWriteOutcome
                .builder(gitLeg.outcome(), dbLeg, auditLeg.outcome())
                .gitRef(gitLeg.gitRef())
                .versionRef(versionRef)
                .task(gitLeg.taskId(), DualWriteOutcome.KIND_COMPENSATION_QUEUE)
                .reason("审计腿：" + auditLeg.reason() + "；" + gitLeg.reason())
                .securityCritical(false)
                .build();

        PublishState state = PublishStateMachine.deriveState(dual);
        StringBuilder why = new StringBuilder(dual.legSummary());
        if (dual.reason() != null) {
            why.append(" | ").append(dual.reason());
        }

        if (state == PublishState.APPLIED) {
            // 权威库已应用 ⇒ 进对账（主链推进唯一合法走法：APPLIED → VERIFYING）
            PublishStateMachine.assertTransition(state, PublishState.VERIFYING);
            state = PublishState.VERIFYING;
            ReconciliationInput input = ReconciliationInput.builder()
                    .outcome(dual)
                    .versionRefMatched(null)
                    .dependencyMatched(null)
                    .build();
            PublishReconciler.Result verdict = PublishReconciler.reconcile(input);
            state = PublishStateMachine.stateAfterVerification(state, verdict.verdict());
            why.append(" | 对账=").append(verdict.verdict())
                    .append('(').append(verdict.reason()).append(')');
            if (verdict.verdict() != ReconciliationVerdict.CONSISTENT) {
                why.append(" | 禁报完全成功");
            }
        }
        return new PublishRecording(state.name(), why.toString());
    }

    /** 把状态记录并入 Map 形态返回（只增键，不改既有键）。 */
    private static Map<String, Object> attachPublishState(Map<String, Object> body,
                                                          PublishRecording recording) {
        if (body == null || recording == null) {
            return body;
        }
        body.put("publishState", recording.publishState());
        body.put("publishStateReason", recording.publishStateReason());
        return body;
    }

    /** 把状态记录并入强类型 VO（{@code NON_NULL} 序列化，未填即不出键）。 */
    private static OntologyVersionVO attachPublishState(OntologyVersionVO vo,
                                                        PublishRecording recording) {
        if (vo == null || recording == null) {
            return vo;
        }
        vo.setPublishState(recording.publishState());
        vo.setPublishStateReason(recording.publishStateReason());
        return vo;
    }

    /** 版本主键：库内最大后缀自增（重启安全）；方法级加锁避免并发取到同一序号。 */
    private synchronized String nextId() { return versionRepository.nextId(); }

    public List<Map<String, Object>> listVersions(String ontologyId) {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        java.util.Set<String> archived = new java.util.LinkedHashSet<>();
        for (OntologyVersion git : gitAll(ontologyId)) {
            archived.add(git.getVersionNo());
            out.add(toIndexMap(git));
        }
        // 库内只补「未归档」的行（草稿/在工作态）；已发布版本内容与元数据以 Git 为准。
        versionRepository.findByOntology(ontologyId).stream()
                .filter(v -> !archived.contains(v.getVersionNo()))
                .map(this::toIndexMap)
                .forEach(out::add);
        return out;
    }

    /**
     * 强类型 VO 版（T16-2）。
     * <p>旧 {@link #listVersions(String)} 签名保留（Wave31 C1 mock 兼容）。
     * <p>#147T：本门面按分态裁决输出<b>版本索引视图</b>（不内联快照），见 {@link #toIndexVO}。
     */
    public List<OntologyVersionVO> listVersionsVO(String ontologyId) {
        List<OntologyVersionVO> out = new java.util.ArrayList<>();
        java.util.Set<String> archived = new java.util.LinkedHashSet<>();
        for (OntologyVersion git : gitAll(ontologyId)) {
            archived.add(git.getVersionNo());
            out.add(toIndexVO(git));
        }
        versionRepository.findByOntology(ontologyId).stream()
                .filter(v -> !archived.contains(v.getVersionNo()))
                .map(this::toIndexVO)
                .forEach(out::add);
        return out;
    }

    public Map<String, Object> getVersion(String ontologyId, String versionId) {
        if (isGitVersionId(versionId)) {
            return gitVersionById(versionId).map(this::toMap).orElse(null);
        }
        return versionRepository.findById(versionId)
            .map(ver -> withResolvedSnapshot(toMap(ver), ver)).orElse(null);
    }

    /**
     * 强类型 VO 版（T16-2）；不存在返回 null。
     */
    public OntologyVersionVO getVersionVO(String ontologyId, String versionId) {
        if (isGitVersionId(versionId)) {
            return gitVersionById(versionId).map(this::toVO).map(this::withAuditReconciliation).orElse(null);
        }
        return versionRepository.findById(versionId)
            .map(ver -> withResolvedSnapshot(toVO(ver), ver))
            .map(this::withAuditReconciliation)
            .orElse(null);
    }

    /**
     * 版本详情读面补审计对账结论（§2.9.4④ L95；#147AD）。
     *
     * <p>只在「已走发布/归档」的版本上填充（{@code gitRef} 或 {@code publishState} 非空），
     * 草稿不打扰读面；对账器缺席/查询失败时按 {@code NOT_AVAILABLE}/{@code UNKNOWN} 如实写，
     * 绝不把不确定写成已落库。</p>
     */
    private OntologyVersionVO withAuditReconciliation(OntologyVersionVO vo) {
        boolean published = (vo.getGitRef() != null && !vo.getGitRef().isBlank())
                || (vo.getPublishState() != null && !vo.getPublishState().isBlank());
        if (!published) {
            return vo;
        }
        PublishAuditReconciler reconciler =
                auditReconcilerProvider == null ? null : auditReconcilerProvider.getIfAvailable();
        if (reconciler == null) {
            return vo;
        }
        PublishAuditReconciler.Reconciliation r =
                reconciler.reconcile("ont-pub-" + vo.getId() + "-" + vo.getVersionNo());
        vo.setAuditReconciliation(r.verdict().name());
        vo.setAuditReconciliationReason(r.detail());
        return vo;
    }

    /**
     * 创建新版本 Draft：从当前 ontology 生成完整快照
     */
    public Map<String, Object> createVersion(String ontologyId, Map<String, Object> body) {
        if (gitEventsAvailable()) {
            OntologyVersion ver = createDraftInGit(ontologyId,
                    String.valueOf(body.getOrDefault("changeLog", "")),
                    String.valueOf(body.getOrDefault("publisher", "")));
            return toMap(ver);
        }
        // 1. 计算新版本号
        String nextVersion = computeNextVersion(ontologyId);
        // 2. 生成 snapshot
        String snapshot = generateSnapshot(ontologyId);
        // 3. 持久化
        OntologyVersion ver = new OntologyVersion();
        ver.setId(nextId());
        ver.setOntologyId(ontologyId);
        ver.setVersionNo(nextVersion);
        ver.setStatus("Draft");
        ver.setSnapshot(snapshot);
        ver.setChangeLog(String.valueOf(body.getOrDefault("changeLog", "")));
        ver.setPublisher(String.valueOf(body.getOrDefault("publisher", "")));
        versionRepository.insert(ver);
        log.info("Version created: {} v{} for ontology {}", ver.getId(), nextVersion, ontologyId);
        return toMap(ver);
    }

    /**
     * 强类型 VO 版（T16-2）；DTO 承载 changeLog / publisher（均可选）。
     */
    public OntologyVersionVO createVersionVO(String ontologyId, OntologyVersionSaveDTO dto) {
        if (gitEventsAvailable()) {
            OntologyVersion ver = createDraftInGit(ontologyId,
                    dto.getChangeLog() != null ? dto.getChangeLog() : "",
                    dto.getPublisher() != null ? dto.getPublisher() : "");
            return toVO(ver);
        }
        String nextVersion = computeNextVersion(ontologyId);
        String snapshot = generateSnapshot(ontologyId);
        OntologyVersion ver = new OntologyVersion();
        ver.setId(nextId());
        ver.setOntologyId(ontologyId);
        ver.setVersionNo(nextVersion);
        ver.setStatus("Draft");
        ver.setSnapshot(snapshot);
        ver.setChangeLog(dto.getChangeLog() != null ? dto.getChangeLog() : "");
        ver.setPublisher(dto.getPublisher() != null ? dto.getPublisher() : "");
        versionRepository.insert(ver);
        log.info("Version created (VO): {} v{} for ontology {}", ver.getId(), nextVersion, ontologyId);
        return toVO(ver);
    }

    /**
     * 发布版本：Draft → Published
     *
     * <p>PMO-50 T4：发布成功后经 {@link #fanOutOntologyPublished(String, String, String)}
     * 事件双发，驱动 kb 侧 KG 同步（buszhi 金·I → dccheng 水·K）。
     */
    public Map<String, Object> publishVersion(String ontologyId, String versionId) {
        if (gitEventsAvailable() && isGitVersionId(versionId)) {
            GitPublishResult r = publishGitVersion(ontologyId, versionId);
            return attachPublishState(toMap(r.version()), r.recording());
        }
        OntologyVersion ver = versionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + versionId + "' not found"));
        if ("Published".equals(ver.getStatus())) {
            throw new IllegalStateException("ONT-006: Version '" + versionId + "' is already Published");
        }
        int dbRows = versionRepository.updateStatus(versionId, "Published");
        fanOutOntologyPublished(ontologyId, ver.getVersionNo(), ver.getPublisher());
        GitLegRecord gitLeg = archiveToGit(ver, ver.getPublisher());
        AuditLegRecord auditLeg = auditPublish(ver, gitLeg, dbRows);
        PublishRecording recording = recordPublishState(ver, dbRows, gitLeg, auditLeg);
        return attachPublishState(
                versionRepository.findById(versionId).map(this::toMap).orElse(null), recording);
    }

    /**
     * 强类型 VO 版（T16-2）；语义与旧版一致（Illustrated，含 PMO-50 T4 发布事件双发）。
     */
    public OntologyVersionVO publishVersionVO(String ontologyId, String versionId) {
        if (gitEventsAvailable() && isGitVersionId(versionId)) {
            GitPublishResult r = publishGitVersion(ontologyId, versionId);
            return attachPublishState(toVO(r.version()), r.recording());
        }
        OntologyVersion ver = versionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + versionId + "' not found"));
        if ("Published".equals(ver.getStatus())) {
            throw new IllegalStateException("ONT-006: Version '" + versionId + "' is already Published");
        }
        int dbRows = versionRepository.updateStatus(versionId, "Published");
        fanOutOntologyPublished(ontologyId, ver.getVersionNo(), ver.getPublisher());
        GitLegRecord gitLeg = archiveToGit(ver, ver.getPublisher());
        AuditLegRecord auditLeg = auditPublish(ver, gitLeg, dbRows);
        PublishRecording recording = recordPublishState(ver, dbRows, gitLeg, auditLeg);
        return attachPublishState(
                versionRepository.findById(versionId).map(this::toVO).orElse(null), recording);
    }

    /**
     * 发布事件双发（PMO-50 T4）— 必须覆盖全部发布路径，否则 kb 侧 KG 同步永不触发。
     *
     * <ol>
     *   <li>Spring 内存事件 {@link ApplicationEventPublisher#publishEvent} — 同 JVM
     *       {@code EcosOntologyEventConsumer#onOntologyPublished} 的 {@code @EventListener} 路径；</li>
     *   <li>Kafka 事件总线 {@link EventBusService#publish} — 跨 JVM 路由（{@code ecos.event.kafka.enabled=true}
     *       时入 Kafka）。
     *       注：内存 fallback 下本路径被包成 {@code MemoryEnvelopeEvent}、无对应监听者，属预期无害重复。</li>
     * </ol>
     *
     * <p>发布状态已提交，事件下发失败仅记日志，不阻塞主流程；载荷只含版本标识，不含本体业务文本。
     *
     * <p>PMO-B2（缺陷 D7 修复）：{@code entityCodes / relationshipCodes} 不再使用硬编码空列表占位，
     * 改为从本体引擎自有表 {@code ecos_ontology_entity} / {@code ecos_ontology_relationship}
     * 按 ontologyId 实查填充，供 kb 侧比对增量清单计算 CREATE / UPDATE / DEPRECATE（方案 §2.2）。
     * 本体确实无对象时返回空列表（数据事实），查询异常仅记 error 日志并以空列表继续下发。
     */
    private void fanOutOntologyPublished(String ontologyId, String versionNo, String actor) {
        try {
            OntologyPublishedEvent evt = OntologyPublishedEvent.of(
                    ontologyId,
                    versionNo,
                    loadEntityCodes(ontologyId),
                    loadRelationshipCodes(ontologyId),
                    actor == null || actor.isBlank() ? "system" : actor);
            if (appEventPublisher != null) {
                appEventPublisher.publishEvent(evt);
            } else {
                log.warn("publishVersion appEventPublisher null, spring-event path skipped ontologyId={}", ontologyId);
            }
            eventBus.ifPresentOrElse(
                    bus -> bus.publish(KafkaTopics.ONTOLOGY_PUBLISHED, evt),
                    () -> log.warn("publishVersion EventBusService unavailable, kafka path skipped topic={} version={}",
                            KafkaTopics.ONTOLOGY_PUBLISHED, versionNo));
        } catch (Exception e) {
            log.error("publishVersion event fan-out failed ontologyId={} version={}", ontologyId, versionNo, e);
        }
    }

    /**
     * 查本体下全部实体 code（发布事件增量清单用）。
     *
     * @param ontologyId 本体 ID
     * @return 去重去空后的实体 code 列表；本体无实体时为空列表
     */
    private List<String> loadEntityCodes(String ontologyId) {
        try {
            return ontologyRepository.findEntitiesByOntology(ontologyId).stream()
                .map(OntologyEntity::getCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("loadEntityCodes failed ontologyId={}", ontologyId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 查本体下全部关系 code（发布事件增量清单用）。
     *
     * <p>关系经 {@code source_entity_id} 归属本体（{@link OntologyRepository#findRelationshipsByOntology}）。
     *
     * @param ontologyId 本体 ID
     * @return 去重去空后的关系 code 列表；本体无关系时为空列表
     */
    private List<String> loadRelationshipCodes(String ontologyId) {
        try {
            return ontologyRepository.findRelationshipsByOntology(ontologyId).stream()
                .map(row -> row.get("code"))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(code -> !code.isBlank())
                .distinct()
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("loadRelationshipCodes failed ontologyId={}", ontologyId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 回滚：将 published version 状态设为 Deprecated，并创建新 Draft 作为回滚目标
     *
     * <p><b>#147D 加入的回滚计划面（§2.9.6②／DF-3）</b>：回滚的唯一合法机制是
     * 「发布已知历史版本（git ref 检出重应用）」，<b>不是</b>现场拼一段反向写操作把数据抹回去。
     * 本方法据此产出 {@link RollbackPlan} 并如实记入返回的 {@code publishState}
     * （恒为 {@code ROLLBACK_PENDING}——意图已登记、计划已成形，但"按 ref 检出重应用"这条腿
     * 在 {@code OntologyGitService} 面上尚不存在，属 #147E 的活，禁在此冒充已执行）。
     *
     * <p><b>本批未改的行为（须知情）</b>：新 Draft 的内容仍取自权威库的 {@code snapshot} 列
     * ——这是收口前的既有机制，且该列正是 DQ-6 判定「过渡期后随 4 张废弃版本表一并下线」的对象。
     * 把内容来源切到 git ref 属 #147E，本批只做计划与状态面。</p>
     */
    public Map<String, Object> rollback(String ontologyId, String versionId) {
        if (gitEventsAvailable() && isGitVersionId(versionId)) {
            RollbackGitResult r = rollbackGitVersion(ontologyId, versionId);
            return attachPublishState(toMap(r.version()), r.recording());
        }
        OntologyVersion ver = versionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + versionId + "' not found"));
        versionRepository.updateStatus(versionId, "Deprecated");
        RollbackContent content = rollbackContent(ver);
        OntologyVersion rollback = newRollbackDraft(ontologyId, ver, content);
        RollbackPlan plan = rollbackPlanFor(ver);
        log.info("Rollback: {} → {} (new draft) 计划={} 内容来源={}",
                versionId, rollback.getId(), plan.describe(), content.reason());
        return attachPublishState(toMap(rollback), rollbackRecording(plan, ver, content));
    }

    /**
     * 强类型 VO 版（T16-2）。
     */
    public OntologyVersionVO rollbackVO(String ontologyId, String versionId) {
        if (gitEventsAvailable() && isGitVersionId(versionId)) {
            RollbackGitResult r = rollbackGitVersion(ontologyId, versionId);
            return attachPublishState(toVO(r.version()), r.recording());
        }
        OntologyVersion ver = versionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + versionId + "' not found"));
        versionRepository.updateStatus(versionId, "Deprecated");
        RollbackContent content = rollbackContent(ver);
        OntologyVersion rollback = newRollbackDraft(ontologyId, ver, content);
        RollbackPlan plan = rollbackPlanFor(ver);
        log.info("Rollback (VO): {} → {} (new draft) 计划={} 内容来源={}",
                versionId, rollback.getId(), plan.describe(), content.reason());
        return attachPublishState(toVO(rollback), rollbackRecording(plan, ver, content));
    }

    /** 回滚 Draft 的内容来源记录（#147E：优先 git ref 读回，读不到才退回快照并写明原因）。 */
    private record RollbackContent(String snapshot, boolean fromGit, String reason) {
    }

    /**
     * 取回滚内容：<b>优先按 {@code git_ref} 从对象库读回</b>（§2.9.6②「发布已知历史版本」），
     * 读不到才退回权威库 {@code snapshot} 列，并把原因写进状态说明（禁静默降级）。
     *
     * <p>退回分支是<b>诚实的过渡态</b>：该版本未归档（{@code git_ref} 空）或仓库/引用不可用时，
     * 按 ref 检出本就不可能；此时用快照生成 Draft，同时 {@link #rollbackRecording} 明示
     * 「非按 ref 检出」，DF-3 的端到端成立仍以 ref 分支为准（台账逐次登记命中率）。</p>
     */
    private RollbackContent rollbackContent(OntologyVersion ver) {
        if (gitService.isEmpty()) {
            return new RollbackContent(ver.getSnapshot(), false,
                    "Git 归档通道未装配，退回权威库快照（非按 ref 检出）");
        }
        if (ver.getGitRef() == null || ver.getGitRef().isBlank()) {
            return new RollbackContent(ver.getSnapshot(), false,
                    "该版本缺 git_ref（未归档或归档失败），退回权威库快照（非按 ref 检出，DF-5）");
        }
        OntologyGitReadResult r = gitService.get().readVersionAtRef(
                GIT_REPO_ONTOLOGY, GIT_ASSET_TYPE, ver.getOntologyId(), ver.getVersionNo(), ver.getGitRef());
        if (r != null && r.isFound()) {
            return new RollbackContent(r.getContent(), true, "内容取自 git ref " + ver.getGitRef());
        }
        String why = r == null ? "null result" : r.getReason();
        log.warn("按 ref 读回失败，退回快照: versionId={} ref={} reason={}",
                ver.getId(), ver.getGitRef(), why);
        return new RollbackContent(ver.getSnapshot(), false,
                "read-at-ref 不可用（" + why + "），退回权威库快照（非按 ref 检出）");
    }

    private OntologyVersion newRollbackDraft(String ontologyId, OntologyVersion ver, RollbackContent content) {
        String nextVersion = computeNextVersion(ontologyId);
        OntologyVersion rollback = new OntologyVersion();
        rollback.setId(nextId());
        rollback.setOntologyId(ontologyId);
        rollback.setVersionNo(nextVersion + "-rollback");
        rollback.setStatus("Draft");
        rollback.setSnapshot(content.snapshot());
        rollback.setChangeLog("Rollback from v" + ver.getVersionNo()
                + (content.fromGit() ? "（content from git ref）" : "（content from db snapshot）"));
        versionRepository.insert(rollback);
        return rollback;
    }

    /**
     * 由被回滚版本的真实 {@code git_ref} 与 {@code version_no} 产出回滚计划（DF-3／DF-5 同形门）。
     *
     * <p>引用缺失即降级为 {@link RollbackPlan.Mode#UNAVAILABLE} 并附原因——
     * 「没有 ref 就没有计划」是设计要求的处置（待人工），不是错误；
     * 需要"必须可执行才继续"的调用方用 {@link PublishStateMachine#assertRollbackExecutable}。</p>
     */
    private RollbackPlan rollbackPlanFor(OntologyVersion ver) {
        return RollbackPlan.republishKnownVersion(GIT_REPO_ONTOLOGY, ver.getGitRef(), ver.getVersionNo());
    }

    /** 回滚侧的状态记录：意图已登记（待回滚），计划与内容来源一并写进原因。 */
    private static PublishRecording rollbackRecording(RollbackPlan plan, OntologyVersion ver,
                                                      RollbackContent content) {
        return new PublishRecording(PublishState.ROLLBACK_PENDING.name(),
                "回滚意图已登记（目标版本 " + ver.getVersionNo() + "）；计划=" + plan.describe()
                        + "；内容来源=" + content.reason()
                        + "；新 Draft 的发布（应用腿）由既有发布流程承接");
    }

    /**
     * 废弃版本
     */
    public Map<String, Object> deprecate(String ontologyId, String versionId) {
        if (gitEventsAvailable() && isGitVersionId(versionId)) {
            return toMap(deprecateGitVersion(ontologyId, versionId));
        }
        versionRepository.updateStatus(versionId, "Deprecated");
        return versionRepository.findById(versionId).map(this::toMap).orElse(null);
    }

    /**
     * 强类型 VO 版（T16-2）。
     */
    public OntologyVersionVO deprecateVO(String ontologyId, String versionId) {
        if (gitEventsAvailable() && isGitVersionId(versionId)) {
            return toVO(deprecateGitVersion(ontologyId, versionId));
        }
        versionRepository.updateStatus(versionId, "Deprecated");
        return versionRepository.findById(versionId).map(this::toVO).orElse(null);
    }

    /**
     * 快照内容解析结果（#147T）：内容 + <b>实际来源</b> + 回退原因。
     *
     * <p>来源必须与内容同批产出——只有内容没有来源，消费方就分不出「Git 读回」与「DB 兜底」，
     * 「历史只从 Git 取」这条就只能凭服务日志自证。</p>
     */
    private record SnapshotResolution(String content, VersionContentSource source, String reason) {
    }

    /**
     * 快照内容来源单源（#147R 建立，#147T 起同时给出来源）：
     * 已归档版本（git_ref 非空）优先按 ref 从对象库读回；读不到才退回 DB {@code snapshot} 列，
     * 并把退回落日志<b>且落进契约面</b>（{@link VersionContentSource#DB_FALLBACK} + 原因），
     * 禁静默把 DB 快照当 Git 历史。未归档（Draft/旧行）直接回 DB 快照。
     */
    private SnapshotResolution resolveSnapshot(OntologyVersion ver) {
        if (ver == null) {
            return new SnapshotResolution(null, VersionContentSource.DB_IN_USE, null);
        }
        if (!isGitSourced(ver)) {
            return new SnapshotResolution(ver.getSnapshot(), VersionContentSource.DB_IN_USE, null);
        }
        OntologyGitReadResult r = gitService.get().readVersionAtRef(
                GIT_REPO_ONTOLOGY, GIT_ASSET_TYPE, ver.getOntologyId(), ver.getVersionNo(), ver.getGitRef());
        if (r != null && r.isFound()) {
            return new SnapshotResolution(r.getContent(), VersionContentSource.GIT, null);
        }
        String why = r == null ? "null result" : r.getReason();
        log.warn("历史版本按 git ref 读回失败，退回 DB 快照（来源已标注）: id={} ref={} reason={}",
                ver.getId(), ver.getGitRef(), why);
        return new SnapshotResolution(ver.getSnapshot(), VersionContentSource.DB_FALLBACK, why);
    }

    /**
     * 该版本的快照内容是否「应当」取自 Git（#147T 判定谓词单源）。
     *
     * <p>谓词只有一处定义：{@code git_ref} 非空 <b>且</b> Git 通道已装配。列表索引面与单行解析面
     * 共用它，避免「列表说内容在 Git、详情说取不到」两面分叉。</p>
     */
    private boolean isGitSourced(OntologyVersion ver) {
        return ver != null && ver.getGitRef() != null && !ver.getGitRef().isBlank() && !gitService.isEmpty();
    }

    /** 内容来源（未经实际读回时的指针态，用于列表/创建等不内联内容的门面）。 */
    private VersionContentSource expectedSourceOf(OntologyVersion ver) {
        return isGitSourced(ver) ? VersionContentSource.GIT : VersionContentSource.DB_IN_USE;
    }

    /**
     * Diff 两个版本
     */
    public Map<String, Object> diff(String ontologyId, String v1, String v2) {
        OntologyVersion ver1 = versionRepository.findById(v1)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + v1 + "' not found"));
        OntologyVersion ver2 = versionRepository.findById(v2)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + v2 + "' not found"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version1", ver1.getVersionNo());
        result.put("version2", ver2.getVersionNo());
        // 每侧只解析一次：#147R 前两侧各调 2 次读，diff 端点会打 4 趟 Git 对象库。
        result.put("snapshot1", safeParseJson(resolveSnapshot(ver1).content()));
        result.put("snapshot2", safeParseJson(resolveSnapshot(ver2).content()));
        return result;
    }

    /**
     * 强类型 VO 版（T16-2）。
     *
     * <p>返回 {@link OntologyVersionVO} 的 version1/version2/snapshot1/snapshot2 字段，
     * 与既有 Map 行为（{@code diff} 端点输出）等价。
     */
    public OntologyVersionVO diffVO(String ontologyId, String v1, String v2) {
        OntologyVersion ver1 = versionRepository.findById(v1)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + v1 + "' not found"));
        OntologyVersion ver2 = versionRepository.findById(v2)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + v2 + "' not found"));
        SnapshotResolution r1 = resolveSnapshot(ver1);
        SnapshotResolution r2 = resolveSnapshot(ver2);
        OntologyVersionVO vo = new OntologyVersionVO();
        vo.setVersion1(ver1.getVersionNo());
        vo.setVersion2(ver2.getVersionNo());
        vo.setSnapshot1(safeParseJson(r1.content()));
        vo.setSnapshot2(safeParseJson(r2.content()));
        return vo;
    }

    // ── 简化端点支持方法 ──────────────────────────────────────

    /**
     * 列出所有版本（跨全部 ontology）
     */
    public List<Map<String, Object>> listAllVersions() {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        java.util.Set<String> archived = new java.util.LinkedHashSet<>();
        for (OntologyVersion git : gitAll(null)) {
            archived.add(git.getOntologyId() + "@" + git.getVersionNo());
            out.add(toIndexMap(git));
        }
        versionRepository.findAll().stream()
                .filter(v -> !archived.contains(v.getOntologyId() + "@" + v.getVersionNo()))
                .map(this::toIndexMap)
                .forEach(out::add);
        return out;
    }

    /**
     * 强类型 VO 版（T16-5，简化端点 listAll）；旧 {@link #listAllVersions()} 签名保留（Wave31 C1 mock 兼容）。
     * <p>#147T：同 {@link #listVersionsVO(String)}，输出索引视图；本面跨全部本体、行数无上界，
     * 是「列表面逐行读 Git」成本裁决的主要对象。
     */
    public List<OntologyVersionVO> listAllVersionsVO() {
        List<OntologyVersionVO> out = new java.util.ArrayList<>();
        java.util.Set<String> archived = new java.util.LinkedHashSet<>();
        for (OntologyVersion git : gitAll(null)) {
            archived.add(git.getOntologyId() + "@" + git.getVersionNo());
            out.add(toIndexVO(git));
        }
        versionRepository.findAll().stream()
                .filter(v -> !archived.contains(v.getOntologyId() + "@" + v.getVersionNo()))
                .map(this::toIndexVO)
                .forEach(out::add);
        return out;
    }

    /**
     * 按 ID 获取版本（不依赖 ontologyId）
     */
    public Map<String, Object> getVersionById(String id) {
        if (isGitVersionId(id)) {
            return gitVersionById(id).map(this::toMap).orElse(null);
        }
        return versionRepository.findById(id)
            .map(ver -> withResolvedSnapshot(toMap(ver), ver)).orElse(null);
    }

    /**
     * 强类型 VO 版（T16-5，简化端点 get）；不存在返回 null。
     */
    public OntologyVersionVO getVersionVOById(String id) {
        if (isGitVersionId(id)) {
            return gitVersionById(id).map(this::toVO).map(this::withAuditReconciliation).orElse(null);
        }
        return versionRepository.findById(id)
            .map(ver -> withResolvedSnapshot(toVO(ver), ver))
            .map(this::withAuditReconciliation)
            .orElse(null);
    }

    /** 单行读面：已归档版本内容按 git_ref 读回（#147R），来源与回退原因同批标注（#147T）。 */
    private Map<String, Object> withResolvedSnapshot(Map<String, Object> body, OntologyVersion ver) {
        SnapshotResolution r = resolveSnapshot(ver);
        body.put("snapshot", safeParseJson(r.content()));
        body.put("contentSource", r.source().name());
        if (r.reason() != null) {
            body.put("contentReason", r.reason());
        }
        return body;
    }

    /** VO 同行（单行读面）。 */
    private OntologyVersionVO withResolvedSnapshot(OntologyVersionVO vo, OntologyVersion ver) {
        SnapshotResolution r = resolveSnapshot(ver);
        vo.setSnapshot(safeParseJson(r.content()));
        vo.setContentSource(r.source());
        vo.setContentReason(r.reason());
        return vo;
    }

    /**
     * 列表行 → <b>版本索引视图</b>（#147T 分态裁决，§2.9.6④/DQ-6 与铁律 #8「版本表降级为版本索引」）。
     *
     * <p>裁决内容：列表门面<b>不内联快照内容</b>，只出索引列（版本指针 + 归档引用 + 内容所在地）。
     * 三条依据——① 成本：列表面若带内容，已归档行须逐行读 Git 对象库，一次列表 = N 次 Git 往返，
     * 且 N 无上界（{@code findAll} 面跨全部本体）；② 语义：§2.9.5② 四版本概念里列表属
     * 「运行态/索引」面，历史内容只在「按 ref 检回」的单行面出现，二者本不同源；③ 消费面实证：
     * 前端列表契约 {@code ontologyVersionApi.VersionItem} 从无 {@code snapshot} 字段，
     * 内容一律走 {@code fetchVersionDetail}（单行面），故索引化不削减任何既有能力。</p>
     *
     * <p>由此 {@code snapshot} 列在读侧只服务单行面/diff 面，列表面零消费——这是 DS-4
     * 「停写 → 读写切 Git → 备份后 DROP」里读侧的最后一块列表面。</p>
     */
    private OntologyVersionVO toIndexVO(OntologyVersion v) {
        OntologyVersionVO vo = toVO(v);
        vo.setSnapshot(null);
        return vo;
    }

    /** Map 轨同行；保留 {@code snapshot} 键置空（键集只增不改，禁结构性删除）。 */
    private Map<String, Object> toIndexMap(OntologyVersion v) {
        Map<String, Object> m = toMap(v);
        m.put("snapshot", null);
        return m;
    }

    /**
     * F6-07 规范前缀 git/history 只读读面（#146av additive）：
     * 已发布且 git_ref 非空的版本，即真实 Git 归档历史。
     *
     * @param ontologyId 本体 ID（空/null = 全量）
     * @return 发布时间倒序的归档历史行（不含 snapshot）
     */
    public List<OntologyGitHistoryVO> gitHistoryVO(String ontologyId) {
        // DS-4 本体域：已发布历史以 Git 提交为准；库内仅用于覆盖「当前状态」（弃用等写面尚未迁 Git）。
        List<OntologyVersion> source = gitHistoryReader()
                .map(r -> r.listPublished(ontologyId))
                .orElseGet(() -> {
                    List<OntologyVersion> db = (ontologyId == null || ontologyId.isBlank())
                            ? versionRepository.findAll() : versionRepository.findByOntology(ontologyId);
                    return db.stream()
                            .filter(v -> "Published".equals(v.getStatus()))
                            .filter(v -> v.getGitRef() != null && !v.getGitRef().isBlank())
                            .collect(Collectors.toList());
                });
        Map<String, String> statusByKey = new java.util.HashMap<>();
        List<OntologyVersion> dbRows = (ontologyId == null || ontologyId.isBlank())
                ? versionRepository.findAll() : versionRepository.findByOntology(ontologyId);
        for (OntologyVersion v : dbRows) {
            statusByKey.put(v.getOntologyId() + "@" + v.getVersionNo(), v.getStatus());
        }
        return source.stream()
            .sorted((a, b) -> {
                if (b.getPublishedAt() == null) return -1;
                if (a.getPublishedAt() == null) return 1;
                return b.getPublishedAt().compareTo(a.getPublishedAt());
            })
            .map(v -> {
                OntologyGitHistoryVO vo = new OntologyGitHistoryVO();
                vo.setId(v.getId());
                vo.setOntologyId(v.getOntologyId());
                vo.setVersionNo(v.getVersionNo());
                String status = statusByKey.get(v.getOntologyId() + "@" + v.getVersionNo());
                vo.setStatus(status != null ? status : v.getStatus());
                vo.setGitRef(v.getGitRef());
                vo.setPublisher(v.getPublisher());
                vo.setPublishedAt(v.getPublishedAt() != null ? v.getPublishedAt().toString() : null);
                vo.setCreatedAt(v.getCreatedAt() != null ? v.getCreatedAt().toString() : null);
                return vo;
            })
            .collect(Collectors.toList());
    }

    // ── DS-4 本体域 Git 读面辅助（2026-11-08）───────────────────────────

    private java.util.Optional<OntologyGitHistoryReader> gitHistoryReader() {
        return gitHistoryReaderProvider == null
                ? java.util.Optional.empty()
                : java.util.Optional.ofNullable(gitHistoryReaderProvider.getIfAvailable());
    }

    /** 已发布版本（Git 真源）；读面未装配时返回空，由调用方回退库内读面（不伪造）。 */
    private List<OntologyVersion> gitPublished(String ontologyId) {
        return gitHistoryReader().map(r -> r.listPublished(ontologyId)).orElse(List.of());
    }

    private java.util.Optional<OntologyVersion> gitVersionById(String id) {
        return gitHistoryReader().flatMap(r -> r.findById(id));
    }

    private static boolean isGitVersionId(String id) {
        return id != null && id.startsWith(OntologyGitHistoryReader.ID_PREFIX);
    }

    /**
     * 与前一版本 diff（按创建时间排序，取当前版本的前一个）
     */
    public Map<String, Object> diffWithPrevious(String versionId) {
        OntologyVersion current = versionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + versionId + "' not found"));
        Optional<OntologyVersion> previous = versionRepository.findPreviousVersion(
            current.getOntologyId(), current.getCreatedAt());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentVersion", current.getVersionNo());
        result.put("currentSnapshot", safeParseJson(current.getSnapshot()));
        if (previous.isPresent()) {
            OntologyVersion prev = previous.get();
            result.put("previousVersion", prev.getVersionNo());
            result.put("previousSnapshot", safeParseJson(prev.getSnapshot()));
        } else {
            result.put("previousVersion", null);
            result.put("previousSnapshot", null);
        }
        return result;
    }

    /**
     * 强类型 VO 版（T16-5，简化端点 diffWithPrevious）。
     * <p>语义与旧 {@link #diffWithPrevious(String)} 一致；
     * {@code previousVersion / previousSnapshot} 不存在时不填充
     * （VO {@code NON_NULL} 省略，与既有 Map put null 前端 undefined 行为一致）。
     */
    public OntologyVersionPreviousDiffVO diffWithPreviousVO(String versionId) {
        OntologyVersion current = versionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + versionId + "' not found"));
        Optional<OntologyVersion> previous = versionRepository.findPreviousVersion(
            current.getOntologyId(), current.getCreatedAt());

        OntologyVersionPreviousDiffVO vo = new OntologyVersionPreviousDiffVO();
        vo.setCurrentVersion(current.getVersionNo());
        vo.setCurrentSnapshot(safeParseJson(current.getSnapshot()));
        if (previous.isPresent()) {
            OntologyVersion prev = previous.get();
            vo.setPreviousVersion(prev.getVersionNo());
            vo.setPreviousSnapshot(safeParseJson(prev.getSnapshot()));
        }
        return vo;
    }

    /**
     * 按 ID 发布版本（不依赖 ontologyId）
     */
    public Map<String, Object> publishVersionById(String versionId) {
        OntologyVersion ver = versionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + versionId + "' not found"));
        if ("Published".equals(ver.getStatus())) {
            throw new IllegalStateException("ONT-006: Version '" + versionId + "' is already Published");
        }
        int dbRows = versionRepository.updateStatus(versionId, "Published");
        fanOutOntologyPublished(ver.getOntologyId(), ver.getVersionNo(), ver.getPublisher());
        GitLegRecord gitLeg = archiveToGit(ver, ver.getPublisher());
        AuditLegRecord auditLeg = auditPublish(ver, gitLeg, dbRows);
        PublishRecording recording = recordPublishState(ver, dbRows, gitLeg, auditLeg);
        return attachPublishState(
                versionRepository.findById(versionId).map(this::toMap).orElse(null), recording);
    }

    /**
     * 强类型 VO 版（T16-5，简化端点 publish）；语义与旧 {@link #publishVersionById(String)} 一致。
     */
    public OntologyVersionVO publishVersionVOById(String versionId) {
        OntologyVersion ver = versionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + versionId + "' not found"));
        if ("Published".equals(ver.getStatus())) {
            throw new IllegalStateException("ONT-006: Version '" + versionId + "' is already Published");
        }
        int dbRows = versionRepository.updateStatus(versionId, "Published");
        fanOutOntologyPublished(ver.getOntologyId(), ver.getVersionNo(), ver.getPublisher());
        GitLegRecord gitLeg = archiveToGit(ver, ver.getPublisher());
        AuditLegRecord auditLeg = auditPublish(ver, gitLeg, dbRows);
        PublishRecording recording = recordPublishState(ver, dbRows, gitLeg, auditLeg);
        return attachPublishState(
                versionRepository.findById(versionId).map(this::toVO).orElse(null), recording);
    }

    // ── PMO-28 提案联动: 审批通过→自动创建版本并发布 ─────────

    /**
     * 提案联动发布（PMO-28 T3）。
     *
     * <p>1. 把提案状态改为 APPROVED（乐观锁保护）。</p>
     * <p>2. 创建新 Draft 版本 — 从当前 ontology 产生完整快照。</p>
     * <p>3. 自动 publish Draft 版本，回填 proposal.version_id。</p>
     *
     * <p>调用方需要先把提案状态从 DRAFT/PENDING → PENDING（如果还不是），
     * 然后调用本方法审批 + 发布。</p>
     *
     * @param ontologyId     本体 ID
     * @param proposalId     提案 ID
     * @param expectedVersion 客户端持有的提案乐观锁版本号
     * @param publisher      发布人（同时作为 reviewer）
     * @return 含 versionId + versionNo 的发布结果
     */
    public Map<String, Object> publishFromProposal(String ontologyId, String proposalId,
                                                    Integer expectedVersion, String publisher) {
        // 1. 改提案 APPROVED（乐观锁）
        int updated = proposalService.optimisticTransition(
            proposalId, "APPROVED", publisher, null, expectedVersion);
        if (updated == 0) {
            throw new IllegalStateException("ONT-409: OPTIMISTIC_LOCK_CONFLICT — proposal version mismatch or not found");
        }
        // 2. 创建 Draft 版本
        java.util.Map<String, Object> newVer = createVersion(ontologyId,
            java.util.Map.of("changeLog", "auto-published from proposal " + proposalId,
                    "publisher", publisher));
        // 3. 发布
        Map<String, Object> published = publishVersion(ontologyId, newVer.get("id").toString());
        log.info("Proposal {} linked to version {}", proposalId, newVer.get("id"));
        return published;
    }

    // ── 内部方法 ──────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String generateSnapshot(String ontologyId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("entities", ontologyRepository.findEntitiesByOntology(ontologyId).stream()
            .map(this::entityToSnapshot).collect(Collectors.toList()));
        // 收集所有 entity IDs
        List<String> entityIds = ontologyRepository.findEntitiesByOntology(ontologyId).stream()
            .map(OntologyEntity::getId).collect(Collectors.toList());
        List<Map<String, Object>> allProps = new ArrayList<>();
        List<Map<String, Object>> allRels = new ArrayList<>();
        List<Map<String, Object>> allActs = new ArrayList<>();
        for (String eid : entityIds) {
            allProps.addAll(ontologyRepository.findPropertiesByEntity(eid).stream()
                .map(this::propToSnapshot).collect(Collectors.toList()));
            allRels.addAll(ontologyRepository.findRelationshipsByEntity(eid).stream()
                .map(this::relToSnapshot).collect(Collectors.toList()));
            allActs.addAll(ontologyRepository.findActionsByEntity(eid).stream()
                .map(this::actionToSnapshot).collect(Collectors.toList()));
        }
        snapshot.put("properties", allProps);
        snapshot.put("relationships", allRels);
        snapshot.put("actions", allActs);
        // rules are loaded by a separate ruleRepository if available
        try {
            return MAPPER.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.error("Failed to serialize snapshot", e);
            return "{}";
        }
    }

    /**
     * 计算该本体的下一个版本号：取全部版本（含 Draft / Published）中的最大版本号，patch 位递增。
     *
     * <p>不可只依据 Published 版本 —— 存在未发布的 Draft 版本时会重复生成同一版本号，
     * 撞唯一约束 {@code (ontology_id, version_no)}，导致提案执行失败。
     *
     * @param ontologyId 本体 ID
     * @return 下一个版本号，如 {@code 1.0.1}；无历史版本时返回 {@code 1.0.0}
     */
    private String computeNextVersion(String ontologyId) {
        List<String> versionNos = gitEventsAvailable()
                ? gitAll(ontologyId).stream().map(OntologyVersion::getVersionNo).collect(Collectors.toList())
                : versionRepository.findByOntology(ontologyId).stream()
                        .map(OntologyVersion::getVersionNo).collect(Collectors.toList());
        return versionNos.stream()
            .filter(no -> no != null && no.matches("\\d+\\.\\d+\\.\\d+"))
            .max(Comparator.comparingLong(this::versionWeight))
            .map(no -> {
                String[] parts = no.split("\\.");
                return parts[0] + "." + parts[1] + "." + (Integer.parseInt(parts[2]) + 1);
            })
            .orElse("1.0.0");
    }

    /** 版本号排序权重（major/minor/patch 加权，用于取最大值） */
    private long versionWeight(String versionNo) {
        String[] parts = versionNo.split("\\.");
        return Long.parseLong(parts[0]) * 1_000_000L
            + Long.parseLong(parts[1]) * 1_000L
            + Long.parseLong(parts[2]);
    }

    private Map<String, Object> entityToSnapshot(OntologyEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId()); m.put("code", e.getCode()); m.put("name", e.getName());
        m.put("entityType", e.getEntityType()); m.put("description", e.getDescription());
        return m;
    }

    private Map<String, Object> propToSnapshot(OntologyProperty p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId()); m.put("entityId", p.getEntityId());
        m.put("code", p.getCode()); m.put("name", p.getName());
        m.put("propertyType", p.getPropertyType()); m.put("requiredFlag", p.getRequiredFlag());
        m.put("enumValues", p.getEnumValues()); m.put("defaultValue", p.getDefaultValue());
        return m;
    }

    private Map<String, Object> relToSnapshot(OntologyRelationship r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId()); m.put("sourceEntityId", r.getSourceEntityId());
        m.put("targetEntityId", r.getTargetEntityId()); m.put("code", r.getCode());
        m.put("relationshipType", r.getRelationshipType());
        return m;
    }

    private Map<String, Object> actionToSnapshot(OntologyAction a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId()); m.put("entityId", a.getEntityId());
        m.put("name", a.getName()); m.put("actionType", a.getActionType());
        m.put("strategy", a.getStrategy()); m.put("status", a.getStatus());
        return m;
    }

    @SuppressWarnings("unchecked")
    private Object safeParseJson(String json) {
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return json;
        }
    }

    private Map<String, Object> toMap(OntologyVersion v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("ontologyId", v.getOntologyId());
        m.put("versionNo", v.getVersionNo());
        m.put("status", v.getStatus());
        m.put("snapshot", safeParseJson(v.getSnapshot()));
        m.put("gitRef", v.getGitRef());
        m.put("contentSource", expectedSourceOf(v).name());
        m.put("changeLog", v.getChangeLog());
        m.put("publisher", v.getPublisher());
        m.put("publishedAt", v.getPublishedAt() != null ? v.getPublishedAt().toString() : null);
        m.put("createdAt", v.getCreatedAt() != null ? v.getCreatedAt().toString() : null);
        return m;
    }

    /**
     * 转换到 {@link OntologyVersionVO}（T16-2 强类型）。
     * <p>{@code snapshot} 复用 safeParseJson 行为以保持契约一致。
     */
    private OntologyVersionVO toVO(OntologyVersion v) {
        OntologyVersionVO vo = new OntologyVersionVO();
        vo.setId(v.getId());
        vo.setOntologyId(v.getOntologyId());
        vo.setVersionNo(v.getVersionNo());
        vo.setStatus(v.getStatus());
        vo.setSnapshot(safeParseJson(v.getSnapshot()));
        vo.setGitRef(v.getGitRef());
        vo.setContentSource(expectedSourceOf(v));
        vo.setChangeLog(v.getChangeLog());
        vo.setPublisher(v.getPublisher());
        vo.setPublishedAt(v.getPublishedAt() != null ? v.getPublishedAt().toString() : null);
        vo.setCreatedAt(v.getCreatedAt() != null ? v.getCreatedAt().toString() : null);
        return vo;
    }

    // ═══════════ DS-4 本体域第二层：草稿/状态事件（Git 即真源） ═══════════

    /** Git 事件化是否可用：归档通道 + 历史读面同时装配（单测直构时为空 ⇒ 走库内旧路径）。 */
    private boolean gitEventsAvailable() {
        return gitService.isPresent() && gitHistoryReader().isPresent();
    }

    private List<OntologyVersion> gitAll(String ontologyId) {
        return gitHistoryReader().map(r -> r.listAll(ontologyId)).orElse(List.of());
    }

    /** 创建 Draft = 写 {@code ontologies/<id>/drafts/<ver>.json} 并提交；失败显式抛错。 */
    private OntologyVersion createDraftInGit(String ontologyId, String changeLog, String publisher) {
        String nextVersion = computeNextVersion(ontologyId);
        String snapshot = generateSnapshot(ontologyId);
        String actor = publisher == null || publisher.isBlank() ? "system" : publisher;
        String path = "ontologies/" + ontologyId + "/drafts/" + nextVersion + ".json";
        OntologyGitArchiveResult r = gitService.get().archiveRelative(GIT_REPO_ONTOLOGY, path,
                snapshot, GIT_DRAFT_MSG + ontologyId + " " + nextVersion, actor);
        if (r == null || !OntologyGitArchiveResult.STATUS_COMMITTED.equals(r.getStatus())
                || r.getCommitId() == null || r.getCommitId().isBlank()) {
            throw new IllegalStateException("ONT-007: Git 草稿写入失败: "
                    + (r == null ? "null" : r.getStatus() + "/" + r.getReason()));
        }
        OntologyVersion ver = new OntologyVersion();
        ver.setId(OntologyGitHistoryReader.ID_PREFIX + r.getCommitId());
        ver.setOntologyId(ontologyId);
        ver.setVersionNo(nextVersion);
        ver.setStatus("Draft");
        ver.setSnapshot(snapshot);
        ver.setChangeLog(changeLog == null ? "" : changeLog);
        ver.setPublisher(actor);
        ver.setGitRef(r.getCommitId());
        ver.setCreatedAt(java.time.LocalDateTime.now());
        log.info("Version created in Git: {} v{} for ontology {}", ver.getId(), nextVersion, ontologyId);
        return ver;
    }

    /** 发布 Git 草稿：写 canonical 提交；DB 腿记 NOT_APPLICABLE（Git 事件即应用）。 */
    private GitPublishResult publishGitVersion(String ontologyId, String versionId) {
        OntologyVersion ver = gitVersionById(versionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ONT-001: Version '" + versionId + "' not found"));
        if ("Published".equals(ver.getStatus())) {
            throw new IllegalStateException("ONT-006: Version '" + versionId + "' is already Published");
        }
        fanOutOntologyPublished(ontologyId, ver.getVersionNo(), ver.getPublisher());
        GitLegRecord gitLeg = archiveToGit(ver, ver.getPublisher());
        AuditLegRecord auditLeg = auditPublish(ver, gitLeg, 1);
        PublishRecording recording = recordGitOnlyState(ver, gitLeg, auditLeg);
        OntologyVersion published = new OntologyVersion();
        published.setId(OntologyGitHistoryReader.ID_PREFIX
                + (gitLeg.gitRef() == null ? "unknown" : gitLeg.gitRef()));
        published.setOntologyId(ver.getOntologyId());
        published.setVersionNo(ver.getVersionNo());
        published.setStatus("Published");
        published.setSnapshot(ver.getSnapshot());
        published.setChangeLog(ver.getChangeLog());
        published.setPublisher(ver.getPublisher());
        published.setGitRef(gitLeg.gitRef());
        published.setPublishedAt(java.time.LocalDateTime.now());
        published.setCreatedAt(ver.getCreatedAt());
        return new GitPublishResult(published, recording);
    }

    /** 弃用 = 写状态文件提交；读面据 Deprecate 事件覆盖状态。 */
    private OntologyVersion deprecateGitVersion(String ontologyId, String versionId) {
        OntologyVersion ver = gitVersionById(versionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ONT-001: Version '" + versionId + "' not found"));
        String actor = ver.getPublisher() == null || ver.getPublisher().isBlank()
                ? "system" : ver.getPublisher();
        String path = "ontologies/" + ver.getOntologyId() + "/status/" + ver.getVersionNo() + ".json";
        OntologyGitArchiveResult r = gitService.get().archiveRelative(GIT_REPO_ONTOLOGY, path,
                GIT_STATUS_DEPRECATED,
                GIT_DEPRECATE_MSG + ver.getOntologyId() + " " + ver.getVersionNo(), actor);
        if (r == null || !OntologyGitArchiveResult.STATUS_COMMITTED.equals(r.getStatus())) {
            throw new IllegalStateException("ONT-008: Git 弃用状态写入失败: "
                    + (r == null ? "null" : r.getReason()));
        }
        ver.setStatus("Deprecated");
        return ver;
    }

    /** 回滚 = 目标状态事件 + 新 Draft 提交（内容取自该 Git 提交）。 */
    private RollbackGitResult rollbackGitVersion(String ontologyId, String versionId) {
        OntologyVersion ver = gitVersionById(versionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ONT-001: Version '" + versionId + "' not found"));
        String actor = ver.getPublisher() == null || ver.getPublisher().isBlank()
                ? "system" : ver.getPublisher();
        String statusPath = "ontologies/" + ver.getOntologyId() + "/status/"
                + ver.getVersionNo() + ".json";
        gitService.get().archiveRelative(GIT_REPO_ONTOLOGY, statusPath, GIT_STATUS_DEPRECATED,
                GIT_DEPRECATE_MSG + ver.getOntologyId() + " " + ver.getVersionNo(), actor);
        String nextVersion = computeNextVersion(ontologyId) + "-rollback";
        String draftPath = "ontologies/" + ver.getOntologyId() + "/drafts/" + nextVersion + ".json";
        OntologyGitArchiveResult r = gitService.get().archiveRelative(GIT_REPO_ONTOLOGY, draftPath,
                ver.getSnapshot(), GIT_DRAFT_MSG + ver.getOntologyId() + " " + nextVersion, actor);
        if (r == null || !OntologyGitArchiveResult.STATUS_COMMITTED.equals(r.getStatus())
                || r.getCommitId() == null) {
            throw new IllegalStateException("ONT-009: Git 回滚草稿写入失败: "
                    + (r == null ? "null" : r.getReason()));
        }
        OntologyVersion rollback = new OntologyVersion();
        rollback.setId(OntologyGitHistoryReader.ID_PREFIX + r.getCommitId());
        rollback.setOntologyId(ver.getOntologyId());
        rollback.setVersionNo(nextVersion);
        rollback.setStatus("Draft");
        rollback.setSnapshot(ver.getSnapshot());
        rollback.setChangeLog("Rollback from v" + ver.getVersionNo() + "（content from git ref）");
        rollback.setPublisher(ver.getPublisher());
        rollback.setGitRef(r.getCommitId());
        rollback.setCreatedAt(java.time.LocalDateTime.now());
        RollbackPlan plan = rollbackPlanFor(ver);
        return new RollbackGitResult(rollback,
                rollbackRecording(plan, ver,
                        new RollbackContent(ver.getSnapshot(), true,
                                "内容取自 git 提交 " + ver.getGitRef())));
    }

    /** Git-only 发布态：DB 腿显式 NOT_APPLICABLE，其余与三腿记录同口径。 */
    private PublishRecording recordGitOnlyState(OntologyVersion ver, GitLegRecord gitLeg,
                                                AuditLegRecord auditLeg) {
        DualWriteOutcome dual = DualWriteOutcome
                .builder(gitLeg.outcome(), LegOutcome.NOT_APPLICABLE, auditLeg.outcome())
                .gitRef(gitLeg.gitRef())
                .versionRef(ver.getVersionNo())
                .task(gitLeg.taskId(), DualWriteOutcome.KIND_COMPENSATION_QUEUE)
                .reason("Git 事件化：DB 腿 NOT_APPLICABLE；审计腿：" + auditLeg.reason()
                        + "；" + gitLeg.reason())
                .securityCritical(false)
                .build();
        PublishState state = PublishStateMachine.deriveState(dual);
        StringBuilder why = new StringBuilder(dual.legSummary());
        if (dual.reason() != null) {
            why.append(" | ").append(dual.reason());
        }
        if (state == PublishState.APPLIED) {
            PublishStateMachine.assertTransition(state, PublishState.VERIFYING);
            state = PublishState.VERIFYING;
            ReconciliationInput input = ReconciliationInput.builder()
                    .outcome(dual)
                    .versionRefMatched(null)
                    .dependencyMatched(null)
                    .build();
            PublishReconciler.Result verdict = PublishReconciler.reconcile(input);
            state = PublishStateMachine.stateAfterVerification(state, verdict.verdict());
            why.append(" | 对账=").append(verdict.verdict())
                    .append('(').append(verdict.reason()).append(')');
            if (verdict.verdict() != ReconciliationVerdict.CONSISTENT) {
                why.append(" | 禁报完全成功");
            }
        }
        return new PublishRecording(state.name(), why.toString());
    }

    private record GitPublishResult(OntologyVersion version, PublishRecording recording) {
    }

    private record RollbackGitResult(OntologyVersion version, PublishRecording recording) {
    }
}
