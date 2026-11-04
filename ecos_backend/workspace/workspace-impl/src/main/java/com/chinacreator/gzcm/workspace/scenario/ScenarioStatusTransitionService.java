package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import com.chinacreator.gzcm.runtime.access.git.archive.ArchiveOutcome;
import com.chinacreator.gzcm.runtime.access.git.archive.DesignAssetArchiver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

/**
 * 场景状态迁移编排（卷07 F07-07 N12，PATCH /{id}/status 唯一入口）。
 * <p>权限（evaluate，默认 DENY）+ 状态机（非法 409）+ 激活前置（完整度/孤岛/认知可达）+ 落库 + 审计。
 * 主体取自 JWT，绝不读请求体 userId。</p>
 */
@Service
public class ScenarioStatusTransitionService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioStatusTransitionService.class);

    private final ScenarioService scenarioService;
    private final ScenarioStatusMachine statusMachine;
    private final CompletenessDataLoader loader;
    private final ScenarioCompletenessService completenessService;
    private final WorkspaceSecurityAudit audit;
    private final WorkspaceDataDao dao;

    /** 设计资产归档器（#147E；{@code scenario-def} 属主通道）。未装配时归档腿显式跳过。 */
    private final ObjectProvider<DesignAssetArchiver> archiverProvider;

    /** 注册表 repositoryId（V300 种子）。 */
    static final String GIT_REPO_SCENARIO = "scenario-def";

    private static final ObjectMapper ARCHIVE_MAPPER = new ObjectMapper();

    public ScenarioStatusTransitionService(ScenarioService scenarioService,
                                           ScenarioStatusMachine statusMachine,
                                           CompletenessDataLoader loader,
                                           ScenarioCompletenessService completenessService,
                                           WorkspaceSecurityAudit audit,
                                           WorkspaceDataDao dao) {
        this(scenarioService, statusMachine, loader, completenessService, audit, dao, null);
    }

    @Autowired
    public ScenarioStatusTransitionService(ScenarioService scenarioService,
                                           ScenarioStatusMachine statusMachine,
                                           CompletenessDataLoader loader,
                                           ScenarioCompletenessService completenessService,
                                           WorkspaceSecurityAudit audit,
                                           WorkspaceDataDao dao,
                                           ObjectProvider<DesignAssetArchiver> archiverProvider) {
        this.scenarioService = scenarioService;
        this.statusMachine = statusMachine;
        this.loader = loader;
        this.completenessService = completenessService;
        this.audit = audit;
        this.dao = dao;
        this.archiverProvider = archiverProvider;
    }

    /** 执行迁移；返回迁移后状态 + allowedTransitions（前端按钮态）。 */
    @Transactional(rollbackFor = Exception.class)
    public TransitionResult transition(String scenarioId, String targetStatus) {
        ScenarioStatus from = ScenarioStatus.fromNullable(currentStatus(scenarioId));
        ScenarioStatus to = ScenarioStatus.fromNullable(targetStatus);
        if (to == null) {
            throw IllegalScenarioTransitionException.illegal(str(from), targetStatus);
        }
        TransitionContext ctx = buildContext(scenarioId, to);
        statusMachine.assertTransition(from, to, ctx);

        ScenarioSaveDTO dto = new ScenarioSaveDTO();
        dto.setStatus(to.name());
        scenarioService.update(scenarioId, dto);

        List<ScenarioStatus> allowed = statusMachine.allowedTransitions(to, buildContext(scenarioId, to));
        audit.record("scenario:transition:" + to.name(), scenarioId, "OK");
        archiveScenarioAfterCommit(scenarioId, to);
        log.info("场景状态迁移 scenario={} {}->{} by={}", scenarioId, str(from), to, ctx.getSubject());
        return new TransitionResult(to.name(), statusNames(allowed));
    }

    /**
     * 状态迁移后归档场景定义到 {@code scenario-def}（#147E；§2.9.7 全域接入）。
     *
     * <p><b>为什么放在 {@code afterCommit}</b>：本方法 {@code @Transactional}，若在提交前归档，
     * 事务回滚会留下一个指向「从未落库的状态」的 Git 提交。Git 与 DB 非原子（§2.9.6①），
     * 但我们至少让顺序正确：先落库提交，再归档；归档失败只记分态，不回滚业务事务。</p>
     */
    void archiveScenarioAfterCommit(String scenarioId, ScenarioStatus to) {
        DesignAssetArchiver archiver = archiverProvider != null ? archiverProvider.getIfAvailable() : null;
        if (archiver == null) {
            log.debug("DesignAssetArchiver 未装配，跳过 scenario 归档 id={}", scenarioId);
            return;
        }
        Runnable task = () -> {
            try {
                ScenarioVO vo = scenarioService.get(scenarioId);
                String json = ARCHIVE_MAPPER.writeValueAsString(vo);
                ArchiveOutcome outcome = archiver.archive(GIT_REPO_SCENARIO, "scenarios", scenarioId,
                        null, json,
                        "Scenario transition to " + to.name() + " (" + scenarioId + ")", null);
                if (outcome.status() != ArchiveOutcome.Status.ARCHIVED) {
                    log.info("scenario 归档未完成 id={} status={} reason={}",
                            scenarioId, outcome.status(), outcome.reason());
                } else {
                    auditScenarioArchive(scenarioId, to, json, outcome.commitId());
                }
            } catch (Exception e) {
                log.warn("scenario 归档异常 id={}: {}", scenarioId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /**
     * scenario 归档成功后的治理审计（§2.9.9 G5「其余发布域的 `git_commit_sha`」的 scenario 腿；#147BE）。
     *
     * <p>只在归档成功时调用：事件携 {@code git_repository=scenario-def} /
     * {@code git_commit_sha=<真实提交号>} / {@code content_hash}，与 ontology、kb-rule 同口径；
     * 归档未成功不发，免得「没归档」被读成「已发布」。投递失败只记日志，不影响业务事务
     * （它跑在 afterCommit 里，业务已提交）。</p>
     */
    private void auditScenarioArchive(String scenarioId, ScenarioStatus to, String json, String commitId) {
        try {
            com.chinacreator.gzcm.common.audit.AuditEventContract contract =
                    new com.chinacreator.gzcm.common.audit.AuditEventContract(
                            "scenario-arc-" + scenarioId + "-" + to.name(),
                            null,
                            null,
                            java.util.List.of(),
                            "scenario",
                            "scenario",
                            scenarioId,
                            com.chinacreator.gzcm.common.audit.AuditLevel.L3_HIGH_RISK.name(),
                            GIT_REPO_SCENARIO,
                            commitId,
                            null,
                            sha256Hex(json),
                            null,
                            null,
                            null,
                            null,
                            null,
                            "APPLIED",
                            "PUBLISH_ARCHIVED",
                            java.time.Instant.now(),
                            java.util.UUID.randomUUID().toString());
            audit.recordGovernance("scenario.status.archive", scenarioId, "SUCCESS", contract.toColumns());
        } catch (Exception e) {
            log.warn("scenario 归档审计投递失败 id={} reason={}", scenarioId, e.getMessage());
        }
    }

    /** 内容摘要（审计 content_hash）；无内容返回 {@code null}（不伪造空串）。 */
    private static String sha256Hex(String content) {
        if (content == null || content.isEmpty()) {
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

    /** 详情响应的 allowedTransitions（结构可达态，服务端计算，前端按钮态唯一来源）。 */
    public List<String> allowedTransitionsFor(String scenarioId) {
        ScenarioStatus from = ScenarioStatus.fromNullable(currentStatus(scenarioId));
        TransitionContext ctx = buildContext(scenarioId, null);
        return statusNames(statusMachine.allowedTransitions(from, ctx));
    }

    private TransitionContext buildContext(String scenarioId, ScenarioStatus target) {
        List<BindingNode> bindings = loader.bindingsOf(scenarioId);
        List<LinkEdge> links = loader.linksOf(scenarioId);
        CompletenessReport report = completenessService.compute(scenarioId, bindings, links);
        boolean cognitiveReachable = report.getPresentEdges().stream()
                .anyMatch(p -> EdgeType.COGNITION.name().equals(p.getType()));
        boolean formalRun = hasFormalRun(scenarioId);
        return TransitionContext.builder()
                .islands(report.getIslands())
                .verdict(report.getVerdict())
                .cognitiveReachable(cognitiveReachable || target != ScenarioStatus.ACTIVE)
                .formalRunExists(formalRun)
                .subject(currentSubject())
                .build();
    }

    private String currentStatus(String scenarioId) {
        return scenarioService.get(scenarioId).getStatus();
    }

    private boolean hasFormalRun(String scenarioId) {
        List<Map<String, Object>> runs = dao.listRunHistory(scenarioId, 100);
        // 过渡期：旧 run 表无 run_mode 列，任何成功运行按 FORMAL 处理（V209 新表落地后改读 run_mode='FORMAL'，见 Mapper 收口接缝）。
        return runs != null && !runs.isEmpty();
    }

    private List<String> statusNames(List<ScenarioStatus> statuses) {
        List<String> names = new java.util.ArrayList<>(statuses.size());
        for (ScenarioStatus s : statuses) {
            names.add(s.name());
        }
        return names;
    }

    private String currentSubject() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception e) {
            log.debug("读取 SecurityContext 主体失败: {}", e.getMessage());
        }
        return "anonymous";
    }

    private String str(ScenarioStatus s) {
        return s == null ? "null" : s.name();
    }

    /** 迁移结果（{status, allowedTransitions[]}）。 */
    public static final class TransitionResult {
        private final String status;
        private final List<String> allowedTransitions;

        public TransitionResult(String status, List<String> allowedTransitions) {
            this.status = status;
            this.allowedTransitions = allowedTransitions;
        }

        public String getStatus() {
            return status;
        }

        public List<String> getAllowedTransitions() {
            return allowedTransitions;
        }
    }
}
