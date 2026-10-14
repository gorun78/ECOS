package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessCheckStatus;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentReadinessGapDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentQuestionDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessGapDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.StepExecutor;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentBudgetService;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentBudgetService.BudgetSnapshot;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentBudgetService.Consumption;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentStatus;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 缺口面服务（详细设计-10 F10-09「{@code WAgentGapService} 产出 {@code suggested_action} +
 * {@code unlock_value}；{@code type=task} 经 {@link ITaskManagementService} 建任务，不新建任务表；
 * 补齐复评循环 {@code refillRound ≤ 3}，超限输出缺口并转 {@code awaiting_input}」。
 * §4.1 组件落位 {@code readiness/GapService} 的实位；V232 写通道）。
 *
 * <p><b>三件事都在这一个类里，因为它们共用同一份台账口径</b>：
 * ①缺口落库（只追加 + 反事实解锁档）；②补齐轮次闸（≤3，读 Run 的 budget/consumption 两列）；
 * ③任务派发（只经 runtime-task，禁自建调度表——铁律 v2.0 §2.5 八条 / F10-09 验收项
 * {@code GapTaskTest#taskCreatedViaRuntimeTaskOnly}）。</p>
 *
 * <p><b>V232 没有 {@code check_code} 列</b>（DDL 实测：id/assessment_id/layer/severity/
 * description_text/impact_text/suggested_action_text/suggested_owner/effort_estimate/
 * unlock_from/unlock_to/status/resolved_time）。补齐派发必须知道"这条缺口源自哪个检查项、
 * 建议动作是哪类"，而这两个信息只能落在既有列里 ⇒
 * {@code suggested_action_text} 取受控形态 {@code type=<动作>;check_code=<码>}，
 * 写侧 {@link #encodeSuggestedAction} 与读侧 {@link #parseSuggestedAction} 成对单源
 * （禁调用点自拼/自解析字符串）。这是"不改 DDL、不加列"前提下的唯一可追溯落法——
 * 加列属 DDL 变更，须另立迁移脚本与批准，不在本批授权内。</p>
 *
 * <p><b>{@code effort_estimate} 恒为 null</b>（裁决 D-B2④-11）：工时是运营承诺，代码无从算出，
 * 写个默认"3d"就是编造。留空由 owner 在缺口看板上填。</p>
 *
 * <p><b>{@code description_text} / {@code impact_text} 由服务端确定性生成</b>（F10-08 硬规则①
 * 「LLM 仅可填 description/impact」的另一个方向：本批不调 LLM，所以这两列内容是
 * check_code + 判档原因 + 档位迁移的拼装，禁出现任何生成式文案，也禁出现金额/比率
 * ——硬规则③「LLM 文本出现金额或比率即剔除」对本类不适用，因为压根不产 LLM 文本）。</p>
 */
@Service
public class ReadinessGapService {

    private static final Logger log = LoggerFactory.getLogger(ReadinessGapService.class);

    /** 补齐动作四类（F10-09 设计行原文 {@code type=fetch|task|confirm|approve}，词表单源在此）。 */
    public static final String ACTION_FETCH = "fetch";
    public static final String ACTION_TASK = "task";
    public static final String ACTION_CONFIRM = "confirm";
    public static final String ACTION_APPROVE = "approve";

    /** 动作词表（请求体 {@code actionType} 的合法域；越界 ⇒ E-VALIDATION，禁宽容成 task）。 */
    public static final Set<String> ACTION_TYPES = unmodifiableSetOf(ACTION_FETCH, ACTION_TASK,
            ACTION_CONFIRM, ACTION_APPROVE);

    /** runtime-task 任务类型字面（与 {@code ModelCapabilityExecutorImpl#ASYNC_TASK_TYPE} 同族形态）。 */
    public static final String GAP_TASK_TYPE = "WAGENT_READINESS_GAP";

    /** {@code suggested_action_text} 受控形态的前缀键（写读同源，禁散落）。 */
    private static final String ACTION_PREFIX = "type=";
    private static final String CHECK_PREFIX = ";check_code=";

    /** 引擎侧补数端口未接线时给调用方的机器可读原因（缺口不谎称已派发）。 */
    public static final String DEFERRED_ENGINE_PORT = "engine-metric-writeback-port-not-wired";
    public static final String DEFERRED_TASK_HUB_ABSENT = "runtime-task-unavailable";

    private final WAgentReadinessGapDao gapDao;
    private final WAgentRunDao runDao;
    private final WAgentBudgetService budgetService;
    private final StepExecutor stepExecutor;
    private final ObjectProvider<ITaskManagementService> taskManagementProvider;

    public ReadinessGapService(WAgentReadinessGapDao gapDao,
                               WAgentRunDao runDao,
                               WAgentBudgetService budgetService,
                               StepExecutor stepExecutor,
                               ObjectProvider<ITaskManagementService> taskManagementProvider) {
        this.gapDao = gapDao;
        this.runDao = runDao;
        this.budgetService = budgetService;
        this.stepExecutor = stepExecutor;
        this.taskManagementProvider = taskManagementProvider;
    }

    // ── ① 缺口落库 ────────────────────────────────────────────

    /**
     * 本次评估的缺项 ⇒ V232 缺口行（只追加；非缺项不生成行）。
     *
     * @param assessmentId         新评估头 ID（由 {@code ReadinessService} 先生成，明细先于头表落库）
     * @param ctx                  评估上下文（租户/组织/问题锚点）
     * @param outcomes             逐项结论（内部按 {@link CheckOutcome#missing()} 过滤）
     * @param gradeFrom            本次总档（缺口 unlock_from）
     * @param unlockToByCheckCode  反事实重算结果：该检查项置 pass 后能到的档位
     * @param operator             操作者（落 create_by/update_by）
     */
    public List<WAgentReadinessGapDO> openGaps(String assessmentId, ReadinessProbeContext ctx,
                                               List<CheckOutcome> outcomes, String gradeFrom,
                                               Map<String, String> unlockToByCheckCode, String operator) {
        if (assessmentId == null || assessmentId.isBlank()) {
            throw WAgentOrchestrationException.validation("缺口落库缺 assessment_id（无主明细禁写）");
        }
        List<WAgentReadinessGapDO> written = new ArrayList<>();
        for (CheckOutcome outcome : outcomes) {
            if (!outcome.missing()) {
                continue;
            }
            WAgentReadinessGapDO gap = newGap(assessmentId, ctx, outcome, gradeFrom,
                    unlockToByCheckCode.get(outcome.code().checkCode()), operator);
            gapDao.insert(gap);
            written.add(gap);
        }
        log.info("[F10-09] 缺口落库: assessmentId={} 行数={} gradeFrom={}", assessmentId, written.size(), gradeFrom);
        return List.copyOf(written);
    }

    /** 旧评估的 {@code open} 缺口置 {@code superseded}（裁决 D-B2④-10；禁写成 resolved）。 */
    public int supersedeOpenGaps(String assessmentId, String operator) {
        if (assessmentId == null || assessmentId.isBlank()) {
            return 0;
        }
        int moved = gapDao.supersedeOpen(assessmentId, WAgentStatus.GapStatus.SUPERSEDED.value(),
                WAgentStatus.GapStatus.OPEN.value(), operator);
        if (moved > 0) {
            log.info("[F10-09] 旧缺口置 superseded: assessmentId={} 行数={}", assessmentId, moved);
        }
        return moved;
    }

    /** 本次评估在位的 open 缺口（响应面/派发面共用）。 */
    public List<WAgentReadinessGapDO> findOpenGaps(String assessmentId) {
        return gapDao.findByAssessmentIdAndStatus(assessmentId, WAgentStatus.GapStatus.OPEN.value());
    }

    /**
     * 建议动作映射（裁决 D-B2④-11 定版；{@code UNKNOWN} 单列出——"探测未回"的补齐动作是
     * 让人去看端点/接线，不是去补数据）。
     */
    public static String suggestedActionOf(ReadinessCheckCode code, ReadinessCheckStatus status) {
        if (status == ReadinessCheckStatus.UNKNOWN) {
            return ACTION_TASK;
        }
        return switch (code) {
            case DATA_COVERAGE, DATA_STALENESS, KNOW_SAMPLE_SIZE -> ACTION_FETCH;
            case ONTO_CALIBER_DEFINED, ONTO_CONFLICT_COUNT -> ACTION_APPROVE;
            // DG-K1 requires_ack=true ⇒ 语义是"请用户确认"而非阻断 ⇒ confirm（裁决 D-B2④-7 同族）
            case KNOW_APPLICABILITY -> ACTION_CONFIRM;
            case COG_CM_READY, COG_BACKTEST_READY -> ACTION_TASK;
        };
    }

    /** 写侧：受控形态 {@code type=<动作>;check_code=<码>}。 */
    static String encodeSuggestedAction(String actionType, String checkCode) {
        return ACTION_PREFIX + actionType + CHECK_PREFIX + checkCode;
    }

    /** 读侧：解析 {@code suggested_action_text}；形态不符 ⇒ empty（调用方按"不可派发"处理，禁猜）。 */
    static Optional<String[]> parseSuggestedAction(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String trimmed = text.trim();
        int typeAt = trimmed.indexOf(ACTION_PREFIX);
        int codeAt = trimmed.indexOf(CHECK_PREFIX);
        if (typeAt != 0 || codeAt < 0) {
            return Optional.empty();
        }
        String type = trimmed.substring(ACTION_PREFIX.length(), codeAt).trim();
        String checkCode = trimmed.substring(codeAt + CHECK_PREFIX.length()).trim();
        if (!ACTION_TYPES.contains(type) || checkCode.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new String[]{type, checkCode});
    }

    /** 缺口行上的检查码（读不到 ⇒ null；只用于展示与派发前置校验，禁据 null 判"没有缺口"）。 */
    public static String checkCodeOf(WAgentReadinessGapDO gap) {
        return parseSuggestedAction(gap.getSuggestedActionText()).map(pair -> pair[1]).orElse(null);
    }

    private WAgentReadinessGapDO newGap(String assessmentId, ReadinessProbeContext ctx,
                                        CheckOutcome outcome, String gradeFrom, String unlockTo,
                                        String operator) {
        ReadinessCheckCode code = outcome.code();
        String actionType = suggestedActionOf(code, outcome.status());
        WAgentReadinessGapDO gap = new WAgentReadinessGapDO();
        gap.setId(UUID.randomUUID().toString());
        gap.setAssessmentId(assessmentId);
        gap.setLayer(code.layer().value());
        // severity 复用 ReadinessCheckStatus 词面（裁决 D-B2④-4；V232 severity VARCHAR(10) 容 blocker=8）
        gap.setSeverity(outcome.status().value());
        gap.setDescriptionText(code.checkCode() + ": " + outcome.judgement().reason());
        gap.setImpactText("grade " + gradeFrom + (unlockTo == null ? "" : " -> " + unlockTo)
                + (outcome.probeReason() == null ? "" : "; probe=" + outcome.probeReason()));
        gap.setSuggestedActionText(encodeSuggestedAction(actionType, code.checkCode()));
        gap.setSuggestedOwner(suggestedOwnerOf(code));
        gap.setEffortEstimate(null);   // 禁编造工时（D-B2④-11）
        gap.setUnlockFrom(gradeFrom);
        gap.setUnlockTo(unlockTo);
        gap.setStatus(WAgentStatus.GapStatus.OPEN.value());
        gap.setResolvedTime(null);     // 只有 RESOLVED 才写，本批无该态写入方
        gap.setTenantId(ctx == null ? null : ctx.tenantId());
        gap.setOrgId(ctx == null ? null : ctx.orgId());
        gap.setVersionNo("1");
        gap.setCreateBy(operator);
        gap.setUpdateBy(operator);
        return gap;
    }

    /** 建议 owner = 该检查项所属 Engine 的 ECOS 服务名（R-43 目录单源，禁散落服务名字面量）。 */
    private static String suggestedOwnerOf(ReadinessCheckCode code) {
        return WAgentEngineCatalog.entry(code.engineKey())
                .map(WAgentEngineCatalog.EngineEntry::ecosService)
                .orElse(null);
    }

    // ── ② 补齐轮次闸（≤3） ────────────────────────────────────

    /**
     * Run 台账三元组（budget 快照 + 已消耗 + 目标行）。
     *
     * @param refillRound 当前已消耗的补评轮次（= 本轮之前的轮次数）
     */
    public record RefillLedger(WAgentRunDO run, BudgetSnapshot budget, Consumption consumption) {

        public boolean limitReached() {
            return budget.refillLimitReached(consumption);
        }

        public int refillRound() {
            return consumption.refillRounds();
        }

        public int maxRefillRounds() {
            return budget.maxRefillRounds();
        }
    }

    /**
     * 取补齐台账（裁决 D-B2④-9：<b>无可解析的 Run ⇒ fail-closed 拒绝</b>）。
     *
     * <p>轮次计数只有 Run 的 {@code consumption_json} 一处落点（F10-11 单源）。
     * 若允许"没有 Run 也能 refill"，计数器无处可写 ⇒ 补槽循环变成无限循环，正是 §5.2
     * "循环 ≤3" 要防的形态。定位链：{@code assessment.run_id → question.latest_run_id}，
     * 两者皆空即拒（E-VALIDATION，禁静默新建 Run——Run 是编排的产物，不是缺口服务的产物）。</p>
     */
    public RefillLedger loadRefillLedger(WAgentReadinessDO assessment, WAgentQuestionDO question) {
        String runId = firstNonBlank(assessment.getRunId(), question == null ? null : question.getLatestRunId());
        if (runId == null) {
            throw WAgentOrchestrationException.validation(
                    "补齐循环无可解析的 Run（assessment.run_id 与 question.latest_run_id 皆空）"
                            + " ⇒ 拒绝 refill（禁无台账补槽，§5.2 循环 ≤3 依赖 consumption_json 单源）");
        }
        WAgentRunDO run = runDao.findById(runId).orElseThrow(WAgentOrchestrationException::notFound);
        BudgetSnapshot budget = budgetService.parseSnapshot(run.getBudgetJson());
        Consumption consumption = stepExecutor.parseConsumption(run.getConsumptionJson());
        return new RefillLedger(run, budget, consumption);
    }

    /**
     * 轮次 +1 并把新评估挂到 Run 的 {@code readiness_id}（F10-09「复评一轮」的落账动作）。
     *
     * <p>一次 refill = 一次复评 = 消耗一个轮次（<b>裁决 D-B2④-13 定版</b>：轮次每次调用即计，
     * 禁以"这轮没进展"为由免计——否则 ≤3 上限可被无限次空转绕过，比直接放开循环更糟）。
     * 重读父行→改→整行回写，沿用 {@code StepExecutor#absorbChildConsumption} 的既有范式。</p>
     */
    public Consumption recordRefillRound(RefillLedger ledger, String newAssessmentId, String operator) {
        Consumption updated = ledger.consumption().plusRefillRounds(1);
        WAgentRunDO run = ledger.run();
        run.setConsumptionJson(budgetService.toConsumptionJson(updated));
        if (newAssessmentId != null && !newAssessmentId.isBlank()) {
            run.setReadinessId(newAssessmentId);
        }
        run.setUpdateBy(operator);
        runDao.update(run);
        log.info("[F10-09] 补评轮次落账: runId={} refill_rounds={}/{} readinessId={}",
                run.getId(), updated.refillRounds(), ledger.maxRefillRounds(), run.getReadinessId());
        return updated;
    }

    /**
     * 复评产物回挂到 Run 的 {@code readiness_id}（{@link #recordRefillRound} 的第二段写）。
     *
     * <p>拆成两段的原因（裁决 D-B2④-13 的落地形态）：轮次必须<b>在复评之前</b>落账
     * （否则"复评失败 ⇒ 不计轮"可被无限重试绕过 ≤3），而新评估 ID 只有复评之后才存在。
     * 于是 {@code consumption_json} 先 +1，本方法只补 {@code readiness_id} 指针。
     * 中途崩溃的后果是"轮次已计但 Run 未指向新评估"——读面按问题取当前评估，结论仍保守，
     * 不会伪造进展。</p>
     */
    public void attachAssessmentToRun(WAgentRunDO run, String newAssessmentId, String operator) {
        if (run == null || newAssessmentId == null || newAssessmentId.isBlank()) {
            return;
        }
        run.setReadinessId(newAssessmentId);
        run.setUpdateBy(operator);
        runDao.update(run);
        log.info("[F10-09] 复评产物回挂 Run: runId={} readinessId={}", run.getId(), newAssessmentId);
    }

    // ── ③ 任务派发（只经 runtime-task） ───────────────────────

    /**
     * 派发结果：{@code taskIds} = runtime-task 回显的任务 ID；
     * {@code deferred} = 未落地的动作（每条带 gap_id/action_type/reason，禁静默丢弃）。
     */
    public record TaskDispatch(List<String> taskIds, List<Map<String, Object>> deferred) {

        public boolean dispatchedAnything() {
            return !taskIds.isEmpty();
        }
    }

    /**
     * 按动作类型派发缺口（F10-09 原文只给 {@code type=task} 建任务；
     * {@code confirm} 的"待办卡"与 {@code fetch/approve} 的引擎侧补数端口均未接线 ⇒
     * <b>如实进 deferred 清单并点名原因，禁把 confirm 冒充成 task 交出去</b>）。
     *
     * @param requestedActionType 调用方显式指定的动作（null/空 ⇒ 用缺口行自己的建议动作）
     */
    public TaskDispatch dispatchResolutionTasks(List<WAgentReadinessGapDO> gaps, String requestedActionType,
                                                ReadinessProbeContext ctx, String operator) {
        if (gaps == null || gaps.isEmpty()) {
            return new TaskDispatch(List.of(), List.of());
        }
        List<String> taskIds = new ArrayList<>();
        List<Map<String, Object>> deferred = new ArrayList<>();
        ITaskManagementService task = taskManagementProvider.getIfAvailable();
        for (WAgentReadinessGapDO gap : gaps) {
            Optional<String[]> parsed = parseSuggestedAction(gap.getSuggestedActionText());
            String actionType = firstNonBlank(requestedActionType, parsed.map(pair -> pair[0]).orElse(null));
            if (actionType == null) {
                deferred.add(deferredRow(gap, null, "suggested-action-unparsable"));
                continue;
            }
            if (!ACTION_TASK.equals(actionType)) {
                deferred.add(deferredRow(gap, actionType, DEFERRED_ENGINE_PORT));
                continue;
            }
            if (task == null) {
                deferred.add(deferredRow(gap, actionType, DEFERRED_TASK_HUB_ABSENT));
                continue;
            }
            String taskId = submitGapTask(task, gap, parsed.map(pair -> pair[1]).orElse(null), ctx, operator);
            if (taskId == null) {
                deferred.add(deferredRow(gap, actionType, "runtime-task-rejected"));
            } else {
                taskIds.add(taskId);
            }
        }
        log.info("[F10-09] 缺口派发: taskIds={} deferred={} requestedActionType={}",
                taskIds.size(), deferred.size(), requestedActionType);
        return new TaskDispatch(List.copyOf(taskIds), List.copyOf(deferred));
    }

    /** 提交单条缺口任务；失败 ⇒ null（调用方落 deferred，禁让任务面异常打断复评主链）。 */
    private String submitGapTask(ITaskManagementService task, WAgentReadinessGapDO gap, String checkCode,
                                 ReadinessProbeContext ctx, String operator) {
        TaskDescription td = new TaskDescription();
        td.setTaskName("wagent-readiness-gap-" + gap.getLayer());
        td.setTaskType(GAP_TASK_TYPE);
        td.setAsync(Boolean.TRUE);
        td.setTenantId(gap.getTenantId());
        td.setCreatedBy(operator);
        td.setDescription(gap.getDescriptionText());
        td.setParameters(taskParameters(gap, checkCode, ctx));
        try {
            String taskId = task.submitTask(td);   // 任务 ID 由 runtime-task 生成/回显（引擎不自建调度）
            log.info("[F10-09] 缺口任务已建: gapId={} taskId={} checkCode={}", gap.getId(), taskId, checkCode);
            return taskId;
        } catch (ITaskManagementService.TaskManagementException e) {
            log.warn("[F10-09] 缺口任务提交失败: gapId={} err={}", gap.getId(), e.getMessage());
            return null;
        }
    }

    private static Map<String, Object> taskParameters(WAgentReadinessGapDO gap, String checkCode,
                                                      ReadinessProbeContext ctx) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("gap_id", gap.getId());
        params.put("assessment_id", gap.getAssessmentId());
        params.put("question_id", ctx == null ? null : ctx.questionId());
        params.put("run_id", ctx == null ? null : ctx.runId());
        params.put("check_code", checkCode);
        params.put("layer", gap.getLayer());
        params.put("severity", gap.getSeverity());
        params.put("unlock_from", gap.getUnlockFrom());
        params.put("unlock_to", gap.getUnlockTo());
        params.put("suggested_owner", gap.getSuggestedOwner());
        return params;
    }

    private static Map<String, Object> deferredRow(WAgentReadinessGapDO gap, String actionType, String reason) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("gap_id", gap.getId());
        row.put("check_code", checkCodeOf(gap));
        row.put("action_type", actionType);
        row.put("reason", reason);
        return row;
    }

    /** 词表合法性前置（请求体校验用；越界 ⇒ E-VALIDATION，禁宽容成默认动作）。 */
    public static void assertActionType(String actionType) {
        if (actionType != null && !actionType.isBlank() && !ACTION_TYPES.contains(actionType.trim())) {
            throw WAgentOrchestrationException.validation(
                    "action_type 越界: " + actionType + "（允许 fetch|task|confirm|approve）");
        }
    }

    private static Set<String> unmodifiableSetOf(String... values) {
        Set<String> set = new LinkedHashSet<>();
        for (String value : values) {
            set.add(value);
        }
        return java.util.Collections.unmodifiableSet(set);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null || second.isBlank() ? null : second.trim();
    }
}
