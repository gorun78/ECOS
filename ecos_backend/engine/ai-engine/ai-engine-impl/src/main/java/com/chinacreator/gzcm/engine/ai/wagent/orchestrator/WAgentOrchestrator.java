package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.AutomationLevel;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PlanSource;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.RunStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.StepType;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunEventDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunStepDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentQuestionDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunEventDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunStepDO;
import com.chinacreator.gzcm.engine.ai.wagent.event.WAgentEvent;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentBudgetService.BudgetSnapshot;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentBudgetService.Consumption;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * W Agent 编排器（详细设计-10 F10-11/§4.2 主干，REQ-WAG-11/12/13）。
 *
 * <p><b>无状态承流</b>：本类不持有任何内存态运行进度——恢复算法（F10-11）= 重启后读
 * {@code run_id} 下最大 {@code succeeded} Step、按其后的拓扑序继续（§4.2 明文）；
 * 崩溃恢复与正常推进共用 {@link #executeLoop} 一条代码路径。</p>
 *
 * <p><b>纪律锚点</b>：状态迁移一律经 {@link WAgentRunStateMachine#requireTransition}
 * （唯一写入口）；预算经 {@link WAgentBudgetService}（配置单源、创建时冻结快照）；
 * 幂等 = sha256(tenant|initiated_by|raw_key) 应用侧先查后插 + DB UNIQUE 双保险，
 * 命中冲突返回<b>既有 run_id</b>（§4.4）；Step 落库只经 {@link StepWriter}；
 * 事件只经 {@link WAgentEventPublisher}（runtime-event 单通道）。</p>
 */
@Component
public class WAgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(WAgentOrchestrator.class);

    /** 默认自动化上限（fail-closed 缺省 = L1：无显式声明只放行低危步）。 */
    public static final int DEFAULT_AUTOMATION_CEILING = AutomationLevel.L1.level();

    private final WAgentRunDao runDao;
    private final WAgentRunStepDao stepDao;
    private final WAgentRunEventDao runEventDao;
    private final WAgentQuestionService questionService;
    private final WAgentBudgetService budgetService;
    private final WAgentRunStateMachine stateMachine;
    private final PlanBuilder planBuilder;
    private final PlanValidator planValidator;
    private final StepExecutor stepExecutor;
    private final StepWriter stepWriter;
    private final WAgentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WAgentOrchestrator(WAgentRunDao runDao, WAgentRunStepDao stepDao,
                              WAgentRunEventDao runEventDao, WAgentQuestionService questionService,
                              WAgentBudgetService budgetService, WAgentRunStateMachine stateMachine,
                              PlanBuilder planBuilder, PlanValidator planValidator,
                              StepExecutor stepExecutor, StepWriter stepWriter,
                              WAgentEventPublisher eventPublisher) {
        this.runDao = runDao;
        this.stepDao = stepDao;
        this.runEventDao = runEventDao;
        this.questionService = questionService;
        this.budgetService = budgetService;
        this.stateMachine = stateMachine;
        this.planBuilder = planBuilder;
        this.planValidator = planValidator;
        this.stepExecutor = stepExecutor;
        this.stepWriter = stepWriter;
        this.eventPublisher = eventPublisher;
    }

    /** 建 Run 命令（idempotencyKey 为调用方原始键，本类做 sha256 散列入库）。 */
    public record RunStartCommand(String tenantId, String orgId, String operator,
                                  String questionId, String missionId, String idempotencyKey,
                                  Integer automationCeiling, String parentRunId, String previousRunId,
                                  String situationSnapshotJson) {
    }

    /** 建 Run 结果：run 行 + 是否幂等命中（命中=未新建，返回既有 run_id）。 */
    public record RunStartOutcome(WAgentRunDO run, boolean idempotentReplay) {
    }

    // ══════════════════════════ 建 Run（幂等） ══════════════════════════

    public RunStartOutcome startRun(RunStartCommand cmd) {
        if (cmd.tenantId() == null || cmd.tenantId().isBlank()) {
            throw new WAgentOrchestrationException(WAgentErrorCode.E_AUTH,
                    "tenant_id 缺失（网关未注入即拒绝）", null);
        }
        if (cmd.operator() == null || cmd.operator().isBlank()) {
            throw WAgentOrchestrationException.validation("initiated_by 不得为空（幂等键三段之一，§4.4）");
        }
        if (cmd.questionId() == null || cmd.questionId().isBlank()) {
            throw WAgentOrchestrationException.validation("question_id 不得为空（§4.2 主干：Question→Run）");
        }
        int ceiling = cmd.automationCeiling() == null
                ? DEFAULT_AUTOMATION_CEILING : cmd.automationCeiling();
        if (ceiling < AutomationLevel.L0.level() || ceiling > AutomationLevel.L3.level()) {
            throw WAgentOrchestrationException.validation("automation_ceiling 越界（仅 0~3）: " + ceiling);
        }

        WAgentQuestionDO question = questionService.get(cmd.questionId(), cmd.tenantId());
        questionService.assertRunEligible(question);

        String hashedKey = cmd.idempotencyKey() == null || cmd.idempotencyKey().isBlank()
                ? null : WAgentHashSupport.idempotencyHash(cmd.tenantId(), cmd.operator(), cmd.idempotencyKey());
        if (hashedKey != null) {
            WAgentRunDO existing = runDao.findByIdempotencyKey(cmd.tenantId(), cmd.operator(), hashedKey)
                    .orElse(null);
            if (existing != null) {
                log.info("[F10-11] 幂等命中 ⇒ 返回既有 run_id（不新建）: runId={}", existing.getId());
                return new RunStartOutcome(existing, true);
            }
        }

        BudgetSnapshot snapshot = budgetService.capture();
        WAgentRunDO run = new WAgentRunDO();
        run.setId(UUID.randomUUID().toString());
        run.setQuestionId(question.getId());
        run.setMissionId(question.getMissionId());
        run.setParentRunId(cmd.parentRunId());
        run.setPreviousRunId(cmd.previousRunId());
        run.setStatus(RunStatus.CREATED.value());
        run.setAutomationCeiling(ceiling);
        run.setInitiatedBy(cmd.operator());
        run.setIdempotencyKey(hashedKey);
        run.setBudgetJson(budgetService.toSnapshotJson(snapshot));
        run.setConsumptionJson(writeJson(Consumption.zero().asMap()));
        run.setSituationSnapshotJson(cmd.situationSnapshotJson());
        run.setTenantId(cmd.tenantId());
        run.setOrgId(cmd.orgId() != null ? cmd.orgId() : question.getOrgId());
        run.setVersionNo("1");
        run.setCreateBy(cmd.operator());
        run.setUpdateBy(cmd.operator());
        try {
            runDao.insert(run);
        } catch (DataIntegrityViolationException dup) {
            // DB UNIQUE(tenant, initiated_by, idempotency_key) 兜底：并发双插命中同键 ⇒ 读回既有行
            WAgentRunDO raced = hashedKey == null ? null
                    : runDao.findByIdempotencyKey(cmd.tenantId(), cmd.operator(), hashedKey).orElse(null);
            if (raced != null) {
                log.info("[F10-11] 幂等 DB 唯一索引兜底命中: runId={}", raced.getId());
                return new RunStartOutcome(raced, true);
            }
            throw dup;
        }
        questionService.markRunning(question.getId(), run.getId());
        appendRunEvent(run, null, EventTypes.Agent.RUN_CREATED, payload(
                "questionId", run.getQuestionId(), "status", run.getStatus()));
        publishRunEvent(EventTypes.Agent.RUN_CREATED, run, cmd.operator());
        return new RunStartOutcome(run, false);
    }

    // ══════════════════════════ 主干管线 ══════════════════════════

    /**
     * 全链推进：created→planning→(校验/闸门)→plan_validated→executing→终态/挂起态。
     * 挂起（awaiting_approval/awaiting_input）即返回当前行（HTTP 侧 200 + 状态，不伪报完成）。
     */
    public WAgentRunDO runPipeline(String runId, String operator) {
        WAgentRunDO run = requireRun(runId);
        RunStatus status = WAgentRunStateMachine.parseStatus(run.getStatus());
        if (status.isTerminal()) {
            return run;
        }
        if (status == RunStatus.AWAITING_APPROVAL || status == RunStatus.AWAITING_INPUT
                || status == RunStatus.WAITING_TASK || status == RunStatus.PAUSED) {
            return run; // 等待外部入口（approveGate/submitInput/恢复）
        }

        WAgentPlan.Plan plan;
        if (run.getPlanJson() == null) {
            if (status == RunStatus.CREATED) {
                stateMachine.requireTransition(run, RunStatus.PLANNING, true, operator);
            }
            plan = buildAndValidatePlan(run, operator);
            if (plan == null) {
                return run; // 已转 awaiting_approval（pre-plan 要求审批 / F10-13 闸未清）
            }
        } else {
            plan = WAgentPlan.PlanCodec.fromJson(run.getPlanJson());
            if (status == RunStatus.PLANNING) {
                plan = buildAndValidatePlan(run, operator);
                if (plan == null) {
                    return run;
                }
            } else if (status == RunStatus.PLAN_VALIDATED) {
                stateMachine.requireTransition(run, RunStatus.EXECUTING, humanGateCleared(run.getId()), operator);
            }
        }
        return executeLoop(run, plan, operator);
    }

    /**
     * 崩溃恢复入口（F10-11：无状态 Orchestrator 重启后读 run_id 下最大 succeeded Step 继续）。
     * 实现 = 读 succeeded 集合 → 拓扑序跳过 → 从第一个未完成步续跑（与正常推进同一路径）。
     */
    public WAgentRunDO resume(String runId, String operator) {
        WAgentRunDO run = requireRun(runId);
        RunStatus status = WAgentRunStateMachine.parseStatus(run.getStatus());
        if (status.isTerminal()) {
            return run;
        }
        if (run.getPlanJson() == null) {
            return runPipeline(runId, operator);
        }
        WAgentPlan.Plan plan = WAgentPlan.PlanCodec.fromJson(run.getPlanJson());
        switch (status) {
            case CREATED, PLANNING -> {
                return runPipeline(runId, operator);
            }
            case PLAN_VALIDATED -> stateMachine.requireTransition(run, RunStatus.EXECUTING,
                    humanGateCleared(run.getId()), operator);
            case EXECUTING -> {
                // 已在执行态：直接续跑（恢复主路径）
            }
            default -> {
                return run; // awaiting_*/waiting_task/paused 由各自唤醒入口处理
            }
        }
        return executeLoop(run, plan, operator);
    }

    // ══════════════════════════ 外部入口（§5.2 #8/#9/#10） ══════════════════════════

    /** 审批闸门（awaiting_approval + 凭证校验；EXPLORED human_gate 在此清闸并写 succeeded 闸行）。 */
    public WAgentRunDO approveGate(String runId, String approvalToken, String operator) {
        if (approvalToken == null || approvalToken.isBlank()) {
            throw WAgentOrchestrationException.validation("approval_token 不得为空（§5.2 #9 凭证校验）");
        }
        WAgentRunDO run = requireRun(runId);
        WAgentRunStateMachine.parseStatus(run.getStatus());
        if (run.getPlanJson() != null) {
            WAgentPlan.Plan plan = WAgentPlan.PlanCodec.fromJson(run.getPlanJson());
            Set<String> succeeded = succeededStepKeys(run.getId());
            for (WAgentPlan.Step step : WAgentPlan.topoOrder(plan)) {
                if (step.isHumanGate() && !succeeded.contains(step.stepKey())) {
                    stepWriter.write(new StepWriter.StepWriteRequest(run, step,
                            stepWriter.nextAttempt(run.getId(), step.stepKey()),
                            WAgentStatus.StepStatus.SUCCEEDED.value(),
                            payload("plan_source", run.getPlanSource(), "gate", step.stepKey()),
                            payload("approved_by", operator,
                                    "approval_token_ref", StepWriter.approvalTokenRef(approvalToken)),
                            null, null, approvalToken, null, null, null, null, operator));
                    break; // 只清第一个未决闸
                }
            }
        }
        stateMachine.requireTransition(run, RunStatus.EXECUTING, true, operator);
        appendRunEvent(run, null, EventTypes.Agent.DECISION_APPROVED,
                payload("gate_cleared", true, "approval_token_ref",
                        StepWriter.approvalTokenRef(approvalToken)));
        publishRunEvent(EventTypes.Agent.DECISION_APPROVED, run, operator);
        return run;
    }

    /** awaiting_input 应答（§5.2 #8；载荷只留引用快照，禁正文入事件）。 */
    public WAgentRunDO submitInput(String runId, Map<String, Object> inputPayload, String operator) {
        WAgentRunDO run = requireRun(runId);
        WAgentRunStateMachine.parseStatus(run.getStatus());
        stateMachine.requireTransition(run, RunStatus.EXECUTING, true, operator);
        appendRunEvent(run, null, EventTypes.Agent.RUN_INPUT_RECEIVED,
                inputPayload == null ? Map.of() : new LinkedHashMap<>(inputPayload));
        publishRunEvent(EventTypes.Agent.RUN_INPUT_RECEIVED, run, operator);
        return run;
    }

    /** 幂等取消（§5.2 #10：已终态直接返回既有行，不报错、不双写）。 */
    public WAgentRunDO cancel(String runId, String operator) {
        WAgentRunDO run = requireRun(runId);
        RunStatus status = WAgentRunStateMachine.parseStatus(run.getStatus());
        if (status.isTerminal()) {
            return run; // 幂等：取消终态 Run = 无操作成功
        }
        stateMachine.requireTransition(run, RunStatus.CANCELLED, true, operator);
        appendRunEvent(run, null, EventTypes.Agent.RUN_COMPLETED,
                payload("final_status", RunStatus.CANCELLED.value()));
        publishRunEvent(EventTypes.Agent.RUN_COMPLETED, run, operator);
        return run;
    }

    /**
     * 重规划（§4.4：从失败点之后重算 Plan、重跑七项校验、EVT=REPLAN、replanCount ≤ 3）。
     * 合法入口态：executing / plan_validated（迁移表已含回 planning 出边）。
     */
    public WAgentRunDO replan(String runId, String operator) {
        WAgentRunDO run = requireRun(runId);
        RunStatus status = WAgentRunStateMachine.parseStatus(run.getStatus());
        if (status != RunStatus.EXECUTING && status != RunStatus.PLAN_VALIDATED) {
            throw WAgentOrchestrationException.stateConflict(run.getStatus(), RunStatus.PLANNING.value());
        }
        BudgetSnapshot snapshot = budgetService.parseSnapshot(run.getBudgetJson());
        Consumption consumption = stepExecutor.parseConsumption(run.getConsumptionJson());
        if (snapshot.replanLimitReached(consumption)) {
            stateMachine.requireTransition(run, RunStatus.FAILED, true, operator);
            appendRunEvent(run, null, EventTypes.Agent.RUN_FAILED,
                    payload("code", WAgentErrorCode.E_WA_BUDGET.wireCode(), "reason", "replan_rounds_limit"));
            publishRunEvent(EventTypes.Agent.RUN_FAILED, run, operator);
            throw new WAgentOrchestrationException(WAgentErrorCode.E_WA_BUDGET,
                    "重规划轮数越限: " + consumption.replanRounds() + " ≥ " + snapshot.maxReplanRounds(), null);
        }
        stateMachine.requireTransition(run, RunStatus.PLANNING, true, operator);
        WAgentPlan.Plan fresh = buildAndValidatePlan(run, operator);
        if (fresh == null) {
            return run; // 重规划后转待审
        }
        Consumption updated = consumption.plusReplanRounds(1);
        run.setConsumptionJson(writeJson(updated.asMap()));
        runDao.update(run);
        appendRunEvent(run, null, EventTypes.Agent.RUN_REPLANNED,
                payload("replan_rounds", updated.replanRounds()));
        publishRunEvent(EventTypes.Agent.RUN_REPLANNED, run, operator);
        stateMachine.requireTransition(run, RunStatus.EXECUTING, humanGateCleared(run.getId()), operator);
        return executeLoop(run, fresh, operator);
    }

    // ══════════════════════════ 读面（§5.2 #6 状态 + 时间线） ══════════════════════════

    public WAgentRunDO requireRun(String runId) {
        return runDao.findById(runId).orElseThrow(WAgentOrchestrationException::notFound);
    }

    /** 租户掩蔽读取（跨租户同 E-NOT-FOUND，§5.3 防枚举）。 */
    public WAgentRunDO requireRunForTenant(String runId, String tenantId) {
        WAgentRunDO run = requireRun(runId);
        if (tenantId != null && !tenantId.equals(run.getTenantId())) {
            throw WAgentOrchestrationException.notFound();
        }
        return run;
    }

    /** 时间线（run_event 正序 + step 行摘要；GET /runs/{runId} 消费）。 */
    public Map<String, Object> timeline(String runId) {
        WAgentRunDO run = requireRun(runId);
        List<Map<String, Object>> events = new java.util.ArrayList<>();
        for (WAgentRunEventDO e : runEventDao.findByRunId(runId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("event_type", e.getEventType());
            m.put("step_id", e.getStepId());
            m.put("occur_time", e.getOccurTime() == null ? null : e.getOccurTime().toString());
            m.put("payload_ref", e.getPayloadRefText());
            events.add(m);
        }
        List<Map<String, Object>> steps = new java.util.ArrayList<>();
        for (WAgentRunStepDO s : stepDao.findByRunId(runId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("step_key", s.getStepKey());
            m.put("attempt", s.getAttempt());
            m.put("step_type", s.getStepType());
            m.put("status", s.getStatus());
            m.put("automation_level", s.getAutomationLevel());
            m.put("input_hash", s.getInputHash());
            m.put("output_hash", s.getOutputHash());
            m.put("error_code", s.getErrorCode());
            steps.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("run_id", run.getId());
        out.put("status", run.getStatus());
        out.put("events", events);
        out.put("steps", steps);
        return out;
    }

    // ══════════════════════════ 内部：计划阶段 ══════════════════════════

    /**
     * planning 相：Playbook 优先建计划 → 落 plan_json/plan_source → 七项校验。
     *
     * @return 校验通过的 Plan；返回 {@code null} = 已转 {@code awaiting_approval}
     *         （pre-plan REQUIRE_APPROVAL 或 F10-13 EXPLORED 闸未清）；失败直接抛（Run 已置 failed 终态）
     */
    private WAgentPlan.Plan buildAndValidatePlan(WAgentRunDO run, String operator) {
        WAgentQuestionDO question = questionService.get(run.getQuestionId(), run.getTenantId());
        Map<String, Object> slots = parseSlots(question.getSlotsJson());
        WAgentPlan.Plan plan = planBuilder.build(new PlanBuilder.BuildRequest(
                run.getTenantId(), run.getId(), run.getMissionId(), question.getIntent(), slots, operator));
        run.setPlanSource(plan.source() == null ? null : plan.source().value());
        run.setPlanJson(WAgentPlan.PlanCodec.toJson(plan));
        runDao.update(run);

        BudgetSnapshot snapshot = budgetService.parseSnapshot(run.getBudgetJson());
        PlanValidator.Report report = planValidator.validate(plan, run.getAutomationCeiling(), snapshot,
                run.getTenantId(), run.getId());
        if (!report.ok()) {
            failFromPlanning(run, report, operator);
        }
        if (plan.source() == PlanSource.EXPLORED && !PlanValidator.hasHumanGateBeforeTools(plan)) {
            // 探索计划结构违规（闸步不在拓扑序首）——执行面前置防线，禁入 executing
            throw new WAgentOrchestrationException(WAgentErrorCode.E_VALIDATION,
                    "EXPLORED 计划缺少前置 human_gate 步骤（F10-13 结构面）", Map.of());
        }
        if (report.requiresApproval()) {
            stateMachine.requireTransition(run, RunStatus.AWAITING_APPROVAL, true, operator);
            appendRunEvent(run, null, EventTypes.Agent.DECISION_OPTIONS_READY,
                    payload("stage", "pre_plan", "reason", "policy_require_approval"));
            publishRunEvent(EventTypes.Agent.DECISION_OPTIONS_READY, run, operator);
            return null;
        }
        if (plan.source() == PlanSource.EXPLORED && !humanGateCleared(run.getId())) {
            // F10-13：EXPLORED 必先过 human_gate 才可 executing——闸清走审批入口（#9）
            stateMachine.requireTransition(run, RunStatus.AWAITING_APPROVAL, true, operator);
            appendRunEvent(run, null, EventTypes.Agent.DECISION_OPTIONS_READY,
                    payload("stage", "human_gate", "reason", "explored_plan_requires_gate"));
            publishRunEvent(EventTypes.Agent.DECISION_OPTIONS_READY, run, operator);
            return null;
        }
        stateMachine.requireTransition(run, RunStatus.PLAN_VALIDATED, true, operator);
        appendRunEvent(run, null, EventTypes.Agent.PLAN_GENERATED,
                payload("plan_source", run.getPlanSource(), "steps", plan.steps().size()));
        publishRunEvent(EventTypes.Agent.PLAN_GENERATED, run, operator);
        return plan;
    }

    private void failFromPlanning(WAgentRunDO run, PlanValidator.Report report, String operator) {
        Map<String, Object> checks = new LinkedHashMap<>();
        report.failures().forEach((k, v) -> checks.put(k.name(), v));
        stateMachine.requireTransition(run, RunStatus.FAILED, true, operator);
        appendRunEvent(run, null, EventTypes.Agent.RUN_FAILED,
                payload("code", report.dominantErrorCode().wireCode(), "failed_checks", checks));
        publishRunEvent(EventTypes.Agent.RUN_FAILED, run, operator);
        throw new WAgentOrchestrationException(report.dominantErrorCode(),
                "计划七项校验未通过: " + report.failures().keySet(), Map.copyOf(checks));
    }

    // ══════════════════════════ 内部：执行循环 ══════════════════════════

    private WAgentRunDO executeLoop(WAgentRunDO run, WAgentPlan.Plan plan, String operator) {
        BudgetSnapshot snapshot = budgetService.parseSnapshot(run.getBudgetJson());
        Consumption consumption = stepExecutor.parseConsumption(run.getConsumptionJson());
        Set<String> succeeded = succeededStepKeys(run.getId());
        List<WAgentPlan.Step> ordered = WAgentPlan.topoOrder(plan);
        if (ordered.isEmpty()) {
            stateMachine.requireTransition(run, RunStatus.FAILED, true, operator);
            appendRunEvent(run, null, EventTypes.Agent.RUN_FAILED,
                    payload("code", WAgentErrorCode.E_VALIDATION.wireCode(), "reason", "plan DAG 结构漂移"));
            throw WAgentOrchestrationException.validation("plan_json 拓扑序不可得（结构漂移）");
        }
        Map<String, Object> lastOutput = Map.of();
        for (WAgentPlan.Step step : ordered) {
            if (succeeded.contains(step.stepKey())) {
                continue; // 恢复算法：已 succeeded 步整体跳过
            }
            if (snapshot.stepLimitReached(consumption)) {
                stateMachine.requireTransition(run, RunStatus.FAILED, true, operator);
                appendRunEvent(run, null, EventTypes.Agent.RUN_FAILED,
                        payload("code", WAgentErrorCode.E_WA_BUDGET.wireCode(),
                                "steps_consumed", consumption.steps(), "max_steps", snapshot.maxSteps()));
                publishRunEvent(EventTypes.Agent.RUN_FAILED, run, operator);
                throw WAgentBudgetService.stepBudgetExceeded(consumption.steps(), snapshot.maxSteps());
            }
            boolean llmStep = StepType.LLM.value().equals(step.stepType());
            if (llmStep && snapshot.llmLimitReached(consumption)) {
                stateMachine.requireTransition(run, RunStatus.FAILED, true, operator);
                appendRunEvent(run, null, EventTypes.Agent.RUN_FAILED,
                        payload("code", WAgentErrorCode.E_WA_BUDGET.wireCode(), "reason", "llm_calls_limit"));
                publishRunEvent(EventTypes.Agent.RUN_FAILED, run, operator);
                throw new WAgentOrchestrationException(WAgentErrorCode.E_WA_BUDGET,
                        "LLM 调用数越限: " + consumption.llmCalls() + " ≥ " + snapshot.maxLlmCalls(), null);
            }
            if (budgetService.isTimedOut(run.getStartTime() == null ? run.getCreateTime() : run.getStartTime(),
                    snapshot, true)) {
                // F10-11/§5.3：超时 → timed_out 终态后 200 返回，禁把超时包成 500
                stateMachine.requireTransition(run, RunStatus.TIMED_OUT, true, operator);
                appendRunEvent(run, null, EventTypes.Agent.RUN_COMPLETED,
                        payload("final_status", RunStatus.TIMED_OUT.value()));
                publishRunEvent(EventTypes.Agent.RUN_COMPLETED, run, operator);
                return run;
            }

            int attempt = stepWriter.nextAttempt(run.getId(), step.stepKey());
            StepExecutor.StepResult result = stepExecutor.execute(run, step, attempt,
                    step.inputSlots(), operator);
            switch (result.disposition()) {
                case AWAITING_APPROVAL -> {
                    stateMachine.requireTransition(run, RunStatus.AWAITING_APPROVAL, true, operator);
                    appendRunEvent(run, null, EventTypes.Agent.DECISION_OPTIONS_READY,
                            payload("stage", "human_gate", "step_key", step.stepKey()));
                    publishRunEvent(EventTypes.Agent.DECISION_OPTIONS_READY, run, operator);
                    return run;
                }
                case DENIED -> {
                    stateMachine.requireTransition(run, RunStatus.FAILED, true, operator);
                    appendRunEvent(run, null, EventTypes.Agent.POLICY_DENIED,
                            payload("step_key", step.stepKey(), "rule_id", result.policyRuleId()));
                    publishRunEvent(EventTypes.Agent.POLICY_DENIED, run, operator);
                    appendRunEvent(run, null, EventTypes.Agent.RUN_FAILED,
                            payload("code", WAgentErrorCode.E_POLICY.wireCode(), "step_key", step.stepKey()));
                    publishRunEvent(EventTypes.Agent.RUN_FAILED, run, operator);
                    throw new WAgentOrchestrationException(WAgentErrorCode.E_POLICY, result.errorText(),
                            Map.of("stepKey", step.stepKey()));
                }
                case FAILED_DEP -> {
                    WAgentErrorCode code = WAgentErrorCodes.fromWireCode(result.errorCode());
                    stateMachine.requireTransition(run, RunStatus.FAILED, true, operator);
                    appendRunEvent(run, null, EventTypes.Agent.STEP_FAILED,
                            payload("step_key", step.stepKey(), "code", result.errorCode()));
                    appendRunEvent(run, null, EventTypes.Agent.RUN_FAILED,
                            payload("code", result.errorCode(), "step_key", step.stepKey()));
                    publishRunEvent(EventTypes.Agent.RUN_FAILED, run, operator);
                    throw new WAgentOrchestrationException(code, result.errorText(),
                            Map.of("stepKey", step.stepKey()));
                }
                case SUCCEEDED -> {
                    consumption = consumption.plusSteps(1).plusLlmCalls(llmStep ? 1 : 0);
                    run.setConsumptionJson(writeJson(consumption.asMap()));
                    runDao.update(run);
                    succeeded.add(step.stepKey());
                    lastOutput = result.output();
                    appendRunEvent(run, null, EventTypes.Agent.STEP_COMPLETED,
                            payload("step_key", step.stepKey(), "attempt", attempt,
                                    "output_hash", lastOutput == null || lastOutput.isEmpty()
                                            ? null : WAgentHashSupport.sha256Hex(
                                            stepWriter.canonicalJson(lastOutput))));
                    publishRunEvent(EventTypes.Agent.STEP_COMPLETED, run, operator);
                }
                default -> {
                }
            }
        }

        boolean degraded = run.getDegradationsJson() != null
                && !run.getDegradationsJson().isBlank() && !"[]".equals(run.getDegradationsJson().trim());
        RunStatus terminal = degraded ? RunStatus.COMPLETED_DEGRADED : RunStatus.COMPLETED;
        if (!lastOutput.isEmpty()) {
            run.setResultRefText(stepWriter.canonicalJson(lastOutput));
        }
        stateMachine.requireTransition(run, terminal, true, operator);
        appendRunEvent(run, null, EventTypes.Agent.RUN_COMPLETED,
                payload("final_status", terminal.value()));
        publishRunEvent(EventTypes.Agent.RUN_COMPLETED, run, operator);
        questionService.markAnswered(run.getQuestionId(), run.getResultRefText());
        return run;
    }

    // ══════════════════════════ 内部小件 ══════════════════════════

    private Set<String> succeededStepKeys(String runId) {
        Set<String> keys = new LinkedHashSet<>();
        for (WAgentRunStepDO row : stepDao.findByRunIdAndStatus(runId,
                WAgentStatus.StepStatus.SUCCEEDED.value())) {
            keys.add(row.getStepKey());
        }
        return keys;
    }

    /** F10-13 执行面判据：human_gate 是否已有 succeeded 行（持久态，禁内存闸位）。 */
    private boolean humanGateCleared(String runId) {
        for (WAgentRunStepDO row : stepDao.findByRunIdAndStatus(runId,
                WAgentStatus.StepStatus.SUCCEEDED.value())) {
            if (StepType.HUMAN_GATE.value().equals(row.getStepType())) {
                return true;
            }
        }
        return false;
    }

    private void appendRunEvent(WAgentRunDO run, String stepId, String eventType, Map<String, Object> payload) {
        WAgentRunEventDO row = new WAgentRunEventDO();
        row.setId(UUID.randomUUID().toString());
        row.setRunId(run.getId());
        row.setStepId(stepId);
        row.setEventType(eventType);
        row.setPayloadRefText(writeJson(payload));
        row.setOccurTime(LocalDateTime.now());
        row.setTenantId(run.getTenantId());
        row.setOrgId(run.getOrgId());
        row.setVersionNo("1");
        row.setCreateBy(run.getInitiatedBy());
        row.setUpdateBy(run.getInitiatedBy());
        runEventDao.insert(row);
    }

    private void publishRunEvent(String eventType, WAgentRunDO run, String operator) {
        try {
            eventPublisher.publish(enrich(eventType, run, operator));
        } catch (IllegalStateException e) {
            // 信封不完整 = 发布纪律违背，fail-loud 但禁把主链炸成 500（事件面与执行面隔离）
            log.error("[§6.1] W 域事件发布被拒（信封缺项）: {}", e.getMessage());
        }
    }

    private WAgentEvent enrich(String eventType, WAgentRunDO run, String operator) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", run.getStatus());
        payload.put("plan_source", run.getPlanSource());
        WAgentEvent event = eventPublisher.envelope(eventType, WAgentEvent.AGGREGATE_RUN, run.getId(),
                run.getTenantId(), run.getId(), operator, WAgentEvent.VIA_AGENT, payload);
        // traceId 由 envelope() 从 MDC 取（AiTraceSupport 同源），此处不重复覆盖
        return event;
    }

    private Map<String, Object> parseSlots(String slotsJson) {
        if (slotsJson == null || slotsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(slotsJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw WAgentOrchestrationException.validation("slots_json 不可解析（禁宽容当空槽）");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw WAgentOrchestrationException.validation("编排面 JSON 快照序列化失败");
        }
    }

    private static Map<String, Object> payload(Object... pairs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            m.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return m;
    }
}
