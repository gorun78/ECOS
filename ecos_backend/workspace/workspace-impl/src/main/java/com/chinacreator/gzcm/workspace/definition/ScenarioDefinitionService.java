package com.chinacreator.gzcm.workspace.definition;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.runtime.access.git.archive.ArchiveOutcome;
import com.chinacreator.gzcm.runtime.access.git.archive.DesignAssetArchiver;
import com.chinacreator.gzcm.runtime.access.git.archive.OptionalArchiver;
import com.chinacreator.gzcm.workspace.blueprint.BlueprintSaveDTO;
import com.chinacreator.gzcm.workspace.blueprint.ScenarioBlueprintService;
import com.chinacreator.gzcm.workspace.blueprint.ScenarioBlueprintVO;
import com.chinacreator.gzcm.workspace.scenario.BindingType;
import com.chinacreator.gzcm.workspace.scenario.CompletenessDataLoader;
import com.chinacreator.gzcm.workspace.scenario.CompletenessReport;
import com.chinacreator.gzcm.workspace.scenario.ScenarioCompletenessService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioSaveDTO;
import com.chinacreator.gzcm.workspace.scenario.ScenarioService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioVO;
import com.chinacreator.gzcm.workspace.scenario.WorkspaceSecurityAudit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 场景定义聚合服务（2026-11-21 场景工作台简化 v0.3）。
 *
 * <p>职责：把「场景 + 绑定 + 蓝图版本」聚成一个业务定义读模型；保存时单事务写场景/绑定/
 * 蓝图草稿；发布时按蓝图状态机逐段推进到 RELEASED，并写场景当前定义版本/摘要。</p>
 *
 * <p>边界：不复制 {@link ScenarioService} / {@link ScenarioBlueprintService} 的业务逻辑；
 * 不新增 Maven 模块；不直连他域 DAO。运行前置只要求「已发布定义」，不要求场景 ACTIVE。</p>
 */
@Service
public class ScenarioDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioDefinitionService.class);

    /** 定义版本历史仓库（现有 DesignAssetArchiver 六仓之一）。 */
    static final String GIT_REPO_SCENARIO = "scenario-def";
    static final String GIT_ASSET_TYPE = "scenarios";

    private final ScenarioService scenarioService;
    private final ScenarioBlueprintService blueprintService;
    private final ScenarioCompletenessService completenessService;
    private final CompletenessDataLoader completenessLoader;
    private final WorkspaceSecurityAudit audit;

    /**
     * 发布归档器（{@code scenario-def} 属主通道；软依赖）。
     *
     * <p>仓库未注册/归档器缺席由 {@link OptionalArchiver} 如实转 {@code not_available}，
     * 禁返回伪成功；归档失败不回滚已提交的发布事务（Git 与 DB 非原子，§2.9.6①），
     * 但必须留 WARN 与审计证据。</p>
     */
    private final ObjectProvider<DesignAssetArchiver> archiverProvider;

    private static final ObjectMapper ARCHIVE_MAPPER = new ObjectMapper();

    public ScenarioDefinitionService(ScenarioService scenarioService,
                                     ScenarioBlueprintService blueprintService,
                                     ScenarioCompletenessService completenessService,
                                     CompletenessDataLoader completenessLoader,
                                     WorkspaceSecurityAudit audit) {
        this(scenarioService, blueprintService, completenessService, completenessLoader, audit, null);
    }

    /** 双构造器时 Spring 必须靠注解选主（否则抛 No default constructor found / 歧义）。 */
    @org.springframework.beans.factory.annotation.Autowired
    public ScenarioDefinitionService(ScenarioService scenarioService,
                                     ScenarioBlueprintService blueprintService,
                                     ScenarioCompletenessService completenessService,
                                     CompletenessDataLoader completenessLoader,
                                     WorkspaceSecurityAudit audit,
                                     ObjectProvider<DesignAssetArchiver> archiverProvider) {
        this.scenarioService = scenarioService;
        this.blueprintService = blueprintService;
        this.completenessService = completenessService;
        this.completenessLoader = completenessLoader;
        this.audit = audit;
        this.archiverProvider = archiverProvider;
    }

    /** 聚合读：场景 + 绑定 + 版本列表 + 当前版本 + 上游就绪摘要。 */
    public Map<String, Object> get(String scenarioId) {
        ScenarioVO scenario = scenarioService.get(scenarioId);
        List<ScenarioBlueprintVO> versions = blueprintService.listVersions(scenarioId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scenario", scenario);
        out.put("bindings", scenario.getBindings());
        out.put("versions", versions);
        out.put("currentVersion", currentVersion(versions));
        out.put("upstream", upstreamReadiness(scenarioId));
        out.put("editable", currentDraft(versions) != null);
        return out;
    }

    /**
     * 保存定义草稿：场景字段 + 绑定全量替换 + 蓝图草稿内容。
     * <p>不写 status；状态机走独立端点。绑定同步为蓝图 dependencies，保证发布校验与
     * 运行引用的是同一份事实。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> save(String scenarioId, ScenarioDefinitionDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, "DEF-400: 定义保存体不可为空");
        }
        ScenarioVO existing = scenarioService.get(scenarioId);

        ScenarioSaveDTO save = new ScenarioSaveDTO();
        save.setName(dto.getName() == null ? existing.getName() : dto.getName());
        save.setDescription(dto.getDescription());
        save.setBusinessGoal(dto.getBusinessGoal() == null ? existing.getBusinessGoal() : dto.getBusinessGoal());
        save.setDepartment(dto.getDepartment());
        save.setPriority(dto.getPriority());
        save.setBudget(dto.getBudget());
        if (dto.getBindings() != null) {
            save.setBindings(toScenarioBindings(dto.getBindings()));
        }
        scenarioService.update(scenarioId, save);

        List<ScenarioBlueprintVO> versions = blueprintService.listVersions(scenarioId);
        ScenarioBlueprintVO draft = currentDraft(versions);
        BlueprintSaveDTO bp = toBlueprintSave(scenarioId, dto, save);

        if (draft == null) {
            blueprintService.create(scenarioId, bp, currentOperator());
        } else {
            blueprintService.updateDraft(draft.getId(), bp, currentOperator());
        }
        // 绑定变更后完整度缓存必须失效（读面 30s TTL 不得回旧值）
        completenessService.invalidate(scenarioId);

        Map<String, Object> result = get(scenarioId);
        audit.record("scenario_definition_save", scenarioId, Map.of("version", String.valueOf(
                currentVersion(blueprintService.listVersions(scenarioId)))));
        log.info("场景定义已保存 scenario={} by={}", scenarioId, currentOperator());
        return result;
    }

    /**
     * 发布当前草稿：校验 → 状态机逐段推进 → RELEASED → 写场景当前版本/摘要 → 审计。
     * <p>低风险（approvalRequired=false）自动审批；高风险停在 READY_FOR_APPROVAL，
     * 由调用方携带 approvalRef 再次发布（不伪造审批人）。</p>
     *
     * <p>注意：蓝图状态机主链不可跳跃，本方法只做“顺序推进 + 现有门槛校验”，
     * 不绕过 {@code validateForRelease} 四道门槛。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> publish(String scenarioId, ScenarioDefinitionDTO dto) {
        scenarioService.get(scenarioId);
        List<ScenarioBlueprintVO> versions = blueprintService.listVersions(scenarioId);
        ScenarioBlueprintVO draft = currentDraft(versions);
        if (draft == null) {
            throw new BusinessException(409, "DEF-409: 没有可发布的草稿（当前版本均已发布或不存在）");
        }
        String actor = currentOperator();
        BlueprintSaveDTO save = toBlueprintSave(scenarioId, dto, null);
        if (dto != null && hasBlueprintContent(dto.getBlueprint())) {
            blueprintService.updateDraft(draft.getId(), save, actor);
        }

        // 四道发布门槛：任一失败 → 409（不推进状态）
        Map<String, Object> validation = blueprintService.validate(draft.getId());
        if (!Boolean.TRUE.equals(validation.get("passed"))) {
            throw new BusinessException(409, "DEF-409: 发布校验未通过: " + validation.get("reasons"));
        }

        String id = draft.getId();
        transitionIfPossible(id, "DISCOVERING", "definition publish", actor);
        transitionIfPossible(id, "GAP_ANALYSIS", "definition publish", actor);
        transitionIfPossible(id, "BLUEPRINT_READY", "definition publish", actor);
        transitionIfPossible(id, "VALIDATING", "definition publish", actor);
        blueprintService.enterApprovalGate(id, "definition publish", actor);

        boolean approvalRequired = dto != null && dto.isApprovalRequired();
        if (approvalRequired) {
            String ref = dto == null ? null : trim(dto.getApprovalRef());
            if (ref == null) {
                audit.record("scenario_definition_pending_approval", scenarioId, Map.of("blueprintId", id));
                Map<String, Object> pending = get(scenarioId);
                pending.put("published", false);
                pending.put("pendingApproval", true);
                return pending;
            }
            blueprintService.approve(id, ref, actor);
        } else {
            blueprintService.approve(id, "AUTO:" + actor, actor);
        }
        ScenarioBlueprintVO released = blueprintService.release(id, actor);

        String digest = sha256(String.valueOf(released.getArtifactDigest()));
        String version = String.valueOf(released.getVersion());
        // 场景当前定义版本/摘要：只存当前版本，历史走 Git（V361 列）
        updateScenarioDefinitionPointer(scenarioId, version, digest, actor);

        // 定义快照归档到 scenario-def（§3.5/§7.4：带 commit sha 与 content hash）。
        // 放 afterCommit：先落库再写 Git，避免回滚留下指向「从未发布版本」的提交；
        // 归档失败不回滚发布（Git 与 DB 非原子），只记 WARN + 审计证据。
        archiveReleasedDefinitionAfterCommit(scenarioId, version, digest, released, actor);

        Map<String, Object> result = get(scenarioId);
        result.put("published", true);
        result.put("pendingApproval", false);
        result.put("releasedVersion", version);
        result.put("artifactDigest", released.getArtifactDigest());
        audit.record("scenario_definition_publish", scenarioId,
                Map.of("version", version, "artifactDigest", String.valueOf(released.getArtifactDigest())));
        log.info("场景定义已发布 scenario={} v={} digest={} by={}",
                scenarioId, version, released.getArtifactDigest(), actor);
        return result;
    }

    /**
     * 发布后把定义快照写入 {@code scenario-def}（{@code scenarios/<id>/<ver>.json}）。
     *
     * <p>内容 = 定义聚合读模型（场景 + 绑定 + 版本列表 + 上游就绪），发布件自证所需
     * 的 content hash 用同一枚 {@code digest}（= sha256(artifactDigest)），与主表指针列一致。</p>
     */
    void archiveReleasedDefinitionAfterCommit(String scenarioId, String version, String digest,
                                             ScenarioBlueprintVO released, String actor) {
        OptionalArchiver gate = OptionalArchiver.of(archiverProvider);
        Runnable task = () -> {
            try {
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("scenarioId", scenarioId);
                snapshot.put("version", version);
                snapshot.put("contentHash", digest);
                snapshot.put("artifactDigest", released.getArtifactDigest());
                snapshot.put("contextDigest", released.getContextDigest());
                snapshot.put("goalStatement", released.getGoalStatement());
                snapshot.put("successCriteria", released.getSuccessCriteria());
                snapshot.put("scope", released.getScope());
                snapshot.put("dependencies", released.getDependencies());
                snapshot.put("capabilities", released.getCapabilities());
                snapshot.put("risks", released.getRisks());
                snapshot.put("tests", released.getTests());
                snapshot.put("bindings", scenarioService.get(scenarioId).getBindings());
                String json = ARCHIVE_MAPPER.writeValueAsString(snapshot);

                ArchiveOutcome outcome = gate.archive(GIT_REPO_SCENARIO, GIT_ASSET_TYPE, scenarioId,
                        version, json,
                        "Scenario definition release " + scenarioId + " v" + version
                                + " contentHash=" + digest, actor);
                if (outcome.status() != ArchiveOutcome.Status.ARCHIVED) {
                    log.warn("场景定义归档未完成 scenario={} v={} status={} reason={}",
                            scenarioId, version, outcome.status(), outcome.reason());
                    audit.record("scenario_definition_archive_missed", scenarioId,
                            Map.of("version", version, "status", String.valueOf(outcome.status()),
                                    "reason", String.valueOf(outcome.reason())));
                } else {
                    log.info("场景定义已归档 scenario={} v={} commit={}", scenarioId, version, outcome.commitId());
                    audit.record("scenario_definition_archive", scenarioId,
                            Map.of("version", version, "commitId", String.valueOf(outcome.commitId()),
                                    "contentHash", digest));
                }
            } catch (Exception e) {
                log.warn("场景定义归档异常 scenario={} v={}: {}", scenarioId, version, e.getMessage());
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

    // ── helpers ────────────────────────────────────────────────

    private ScenarioBlueprintVO currentDraft(List<ScenarioBlueprintVO> versions) {
        ScenarioBlueprintVO hit = null;
        for (ScenarioBlueprintVO v : versions) {
            if (v != null && "DRAFT".equalsIgnoreCase(v.getStatus())) {
                hit = v; // 取最高版本草稿
            }
        }
        return hit;
    }

    private String currentVersion(List<ScenarioBlueprintVO> versions) {
        String best = null;
        for (ScenarioBlueprintVO v : versions) {
            if (v != null && "RELEASED".equalsIgnoreCase(v.getStatus())) {
                best = String.valueOf(v.getVersion());
            }
        }
        return best;
    }

    private void transitionIfPossible(String blueprintId, String target, String reason, String actor) {
        try {
            blueprintService.transition(blueprintId, target, reason, actor);
        } catch (RuntimeException e) {
            // 幂等推进：已在该态或结构不允许时，由后续 enterApprovalGate/validate 给出真实结论
            log.debug("定义发布推进跳过 {}: {}", target, e.getMessage());
        }
    }

    private void updateScenarioDefinitionPointer(String scenarioId, String version, String digest, String actor) {
        // 复用既有动态更新入口，避免新写一条 SQL 分叉；列由 V361 增补
        scenarioService.updateDefinitionPointer(scenarioId, version, digest, actor);
    }

    private Map<String, Object> upstreamReadiness(String scenarioId) {
        List<com.chinacreator.gzcm.workspace.scenario.BindingNode> bindings = completenessLoader.bindingsOf(scenarioId);
        List<com.chinacreator.gzcm.workspace.scenario.LinkEdge> links = completenessLoader.linksOf(scenarioId);
        CompletenessReport report = completenessService.compute(scenarioId, bindings, links);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("verdict", report.getVerdict() == null ? null : report.getVerdict().name());
        out.put("coverage", report.getCoverage());
        out.put("requiredEdges", report.getRequiredEdges());
        out.put("presentEdges", report.getPresentEdges());
        out.put("missingEdges", report.getMissingEdges());
        out.put("islands", report.getIslands());
        out.put("probeErrors", report.getProbeErrors());
        return out;
    }

    private List<ScenarioSaveDTO.ScenarioBindingItem> toScenarioBindings(List<ScenarioDefinitionDTO.ScenarioBindingItem> items) {
        List<ScenarioSaveDTO.ScenarioBindingItem> out = new ArrayList<>();
        for (ScenarioDefinitionDTO.ScenarioBindingItem item : items) {
            if (item == null) continue;
            String type = trim(item.getBindingType());
            if (type == null || BindingType.fromNullable(type) == null) {
                throw new BusinessException(400, "DEF-400: 非法绑定类型: " + type);
            }
            ScenarioSaveDTO.ScenarioBindingItem target = new ScenarioSaveDTO.ScenarioBindingItem();
            target.setBindingType(type.toUpperCase());
            target.setTargetRef(item.getTargetRef());
            target.setTargetId(item.getTargetId());
            target.setTargetType(item.getTargetType());
            target.setRemark(item.getRemark());
            out.add(target);
        }
        return out;
    }

    private BlueprintSaveDTO toBlueprintSave(String scenarioId, ScenarioDefinitionDTO dto, ScenarioSaveDTO scenario) {
        BlueprintSaveDTO bp = new BlueprintSaveDTO();
        bp.setScenarioId(scenarioId);
        ScenarioDefinitionDTO.BlueprintDraft draft = dto == null ? null : dto.getBlueprint();
        String goal = draft == null ? null : trim(draft.getGoalStatement());
        if (goal == null) {
            goal = dto == null ? null : trim(dto.getBusinessGoal());
        }
        if (goal == null && scenario != null) {
            goal = trim(scenario.getBusinessGoal());
        }
        if (goal == null) {
            goal = "场景定义"; // 仅用于首次建草稿的占位；发布前校验会要求真实目标
        }
        bp.setGoalStatement(goal);
        bp.setSuccessCriteria(draft == null ? (dto == null ? null : dto.getSuccessCriteria()) : draft.getSuccessCriteria());
        bp.setScope(draft == null ? (dto == null ? null : dto.getScope()) : draft.getScope());
        bp.setDependencies(draft == null ? null : draft.getDependencies());
        bp.setCapabilities(draft == null ? null : draft.getCapabilities());
        bp.setRisks(draft == null ? null : draft.getRisks());
        bp.setTests(draft == null ? null : draft.getTests());
        bp.setApprovalRequired(dto != null && dto.isApprovalRequired());
        bp.setApprovalPolicyRef(dto == null ? null : dto.getApprovalPolicyRef());
        bp.setNote(dto == null ? null : dto.getNote());
        return bp;
    }

    private boolean hasBlueprintContent(ScenarioDefinitionDTO.BlueprintDraft draft) {
        return draft != null && (draft.getGoalStatement() != null || draft.getSuccessCriteria() != null
                || draft.getScope() != null || draft.getDependencies() != null
                || draft.getCapabilities() != null || draft.getRisks() != null || draft.getTests() != null);
    }

    private static String trim(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isEmpty() ? null : s;
    }

    private static String sha256(String v) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((v == null ? "" : v).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private String currentOperator() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String s) {
                return s;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "system";
    }
}
