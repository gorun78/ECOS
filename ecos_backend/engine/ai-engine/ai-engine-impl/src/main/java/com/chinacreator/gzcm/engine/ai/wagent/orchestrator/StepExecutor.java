package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.AutomationLevel;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PolicyEffect;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.RunStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.StepType;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentBudgetService.Consumption;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentPolicyGuardPort.PolicyCheckContext;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentPolicyGuardPort.PolicyDecision;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Step 承流执行器（详细设计-10 F10-14 / §4.2 executing 循环体，REQ-WAG-14）。
 *
 * <p>六类分派（StepType 单源）：</p>
 * <ul>
 *   <li>{@code tool/llm/aggregate} → {@link StepInvokerPort}（#130/#131 接缝；缺席即
 *       FAILED_DEP，禁伪称成功）；执行前逐步过 {@code pre-step} 策略钩子（F10-22）；</li>
 *   <li>{@code human_gate} → AWAITING_APPROVAL（闸清由审批入口经 {@link StepWriter} 写
 *       succeeded 行完成——EXPLORED 硬约束的执行面在 {@link WAgentRunStateMachine}）；</li>
 *   <li>{@code wait_task} → FAILED_DEP（F10-15 的 ITaskManagementService 桥接属 #130 范围，
 *       本任务只留分派位并在行内留痕）；</li>
 *   <li>{@code sub_run} → 深度闸（≤2，父链计数）+ {@code on_behalf_of} 继承（initiated_by
 *       随父）+ 消耗原子回计父 Run（{@link #absorbChildConsumption}）。</li>
 * </ul>
 *
 * <p>本执行器只做"单步语义"；状态迁移、预算计数、时间线事件由
 * {@link WAgentOrchestrator} 统一承流（迁移唯一写入口纪律不因分派破坏）。</p>
 */
@Component
public class StepExecutor {

    private static final Logger log = LoggerFactory.getLogger(StepExecutor.class);

    /** F10-14 子 Run 深度上限：顶层 0，子 1，孙 2；再深拒绝。 */
    public static final int MAX_SUB_RUN_DEPTH = 2;

    /** 单步执行结局（编排器据此做状态迁移与事件登记）。 */
    public enum Disposition {
        SUCCEEDED,
        /** human_gate：等待审批入口清闸。 */
        AWAITING_APPROVAL,
        /** pre-step 裁决 DENY（F10-22）。 */
        DENIED,
        /** 依赖缺席/后端失败（E-WA-DEP 族）。 */
        FAILED_DEP
    }

    public record StepResult(Disposition disposition, Map<String, Object> output,
                             String errorCode, String errorText,
                             PolicyEffect policyEffect, String policyRuleId,
                             Consumption absorbedChildConsumption) {

        public static StepResult succeeded(Map<String, Object> output, PolicyDecision decision) {
            return new StepResult(Disposition.SUCCEEDED, output == null ? Map.of() : output,
                    null, null, decision == null ? null : decision.effect(),
                    decision == null ? null : decision.ruleId(), null);
        }

        public static StepResult awaitingApproval() {
            return new StepResult(Disposition.AWAITING_APPROVAL, Map.of(), null, null, null, null, null);
        }

        public static StepResult denied(PolicyDecision decision) {
            return new StepResult(Disposition.DENIED, Map.of(),
                    WAgentErrorCode.E_POLICY.wireCode(), decision.reason(),
                    decision.effect(), decision.ruleId(), null);
        }

        public static StepResult failedDep(String errorText) {
            return new StepResult(Disposition.FAILED_DEP, Map.of(),
                    WAgentErrorCode.E_WA_DEP.wireCode(), errorText, null, null, null);
        }
    }

    private final StepWriter stepWriter;
    private final WAgentRunDao runDao;
    private final ObjectProvider<WAgentPolicyGuardPort> policyGuardProvider;
    private final ObjectProvider<StepInvokerPort> invokerProvider;
    private final WAgentBudgetService budgetService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StepExecutor(StepWriter stepWriter, WAgentRunDao runDao,
                        ObjectProvider<WAgentPolicyGuardPort> policyGuardProvider,
                        ObjectProvider<StepInvokerPort> invokerProvider,
                        WAgentBudgetService budgetService) {
        this.stepWriter = stepWriter;
        this.runDao = runDao;
        this.policyGuardProvider = policyGuardProvider;
        this.invokerProvider = invokerProvider;
        this.budgetService = budgetService;
    }

    /**
     * 执行单步（attempt 由编排器按 {@link StepWriter#nextAttempt} 提供；成功/失败都落行留痕）。
     */
    public StepResult execute(WAgentRunDO run, WAgentPlan.Step step, int attempt,
                              Map<String, Object> resolvedInput, String operator) {
        String type = step.stepType();
        if (StepType.HUMAN_GATE.value().equals(type)) {
            return StepResult.awaitingApproval();
        }
        if (StepType.WAIT_TASK.value().equals(type)) {
            stepWriter.write(new StepWriter.StepWriteRequest(run, step, attempt,
                    WAgentStatus.StepStatus.FAILED.value(), resolvedInput, null, null, null,
                    null, null, WAgentErrorCode.E_WA_DEP.wireCode(),
                    "wait_task 桥接属 F10-15/#130（ITaskManagementService 通道未在本任务实装）",
                    null, operator));
            return StepResult.failedDep("wait_task 通道未实装（F10-15，#130）");
        }

        // pre-step 策略钩子（每步一次，§4.2；策略源缺席 = fail-closed DENY，F10-22）
        PolicyDecision decision;
        WAgentPolicyGuardPort guard = policyGuardProvider.getIfAvailable();
        if (guard == null) {
            decision = PolicyDecision.deny(WAgentPolicyGuardPlaceholder.RULE_PLACEHOLDER,
                    "策略源缺席（fail-closed）");
        } else {
            decision = guard.preStep(new PolicyCheckContext(run.getTenantId(), run.getId(),
                    step.stepKey(), type, step.toolName(), step.automationLevel(),
                    step.writeChannel() ? "production" : "none", run.getPlanSource()));
        }
        if (decision.effect() == PolicyEffect.DENY) {
            stepWriter.write(new StepWriter.StepWriteRequest(run, step, attempt,
                    WAgentStatus.StepStatus.FAILED.value(), resolvedInput, null,
                    decision.effect(), decision.ruleId(), null, null,
                    WAgentErrorCode.E_POLICY.wireCode(), decision.reason(), null, operator));
            return StepResult.denied(decision);
        }

        if (StepType.SUB_RUN.value().equals(type)) {
            return executeSubRun(run, step, attempt, resolvedInput, decision, operator);
        }

        StepInvokerPort invoker = invokerProvider.getIfAvailable();
        if (invoker == null) {
            stepWriter.write(new StepWriter.StepWriteRequest(run, step, attempt,
                    WAgentStatus.StepStatus.FAILED.value(), resolvedInput, null,
                    decision.effect(), decision.ruleId(), null, null,
                    WAgentErrorCode.E_WA_DEP.wireCode(),
                    "StepInvokerPort 未装配（工具/叙述通道属 #130/#131）", null, operator));
            return StepResult.failedDep("StepInvokerPort 未装配");
        }
        StepInvokerPort.Outcome outcome = invoker.invoke(new StepInvokerPort.Request(
                run.getTenantId(), run.getId(), step.stepKey(), type,
                step.toolName(), step.toolVersion(), resolvedInput, step.automationLevel()));
        if (!outcome.success()) {
            stepWriter.write(new StepWriter.StepWriteRequest(run, step, attempt,
                    WAgentStatus.StepStatus.FAILED.value(), resolvedInput, outcome.output(),
                    decision.effect(), decision.ruleId(), null, null,
                    outcome.errorCode(), outcome.errorText(), outcome.metrics(), operator));
            return new StepResult(Disposition.FAILED_DEP, outcome.output(),
                    outcome.errorCode(), outcome.errorText(),
                    decision.effect(), decision.ruleId(), null);
        }
        stepWriter.write(new StepWriter.StepWriteRequest(run, step, attempt,
                WAgentStatus.StepStatus.SUCCEEDED.value(), resolvedInput, outcome.output(),
                decision.effect(), decision.ruleId(), null, null, null, null,
                outcome.metrics(), operator));
        return StepResult.succeeded(outcome.output(), decision);
    }

    // ── sub_run（F10-14：depth ≤ 2、on_behalf_of 继承、消耗原子回计父 Run）──

    private StepResult executeSubRun(WAgentRunDO parent, WAgentPlan.Step step, int attempt,
                                     Map<String, Object> resolvedInput, PolicyDecision decision,
                                     String operator) {
        int parentDepth = depthOf(parent);
        if (parentDepth + 1 > MAX_SUB_RUN_DEPTH) {
            String text = "子 Run 深度超限：" + (parentDepth + 1) + " > " + MAX_SUB_RUN_DEPTH
                    + "（F10-14 depth≤2）";
            stepWriter.write(new StepWriter.StepWriteRequest(parent, step, attempt,
                    WAgentStatus.StepStatus.FAILED.value(), resolvedInput, null,
                    decision.effect(), decision.ruleId(), null, null,
                    WAgentErrorCode.E_VALIDATION.wireCode(), text, null, operator));
            return new StepResult(Disposition.FAILED_DEP, Map.of(),
                    WAgentErrorCode.E_VALIDATION.wireCode(), text,
                    decision.effect(), decision.ruleId(), null);
        }
        StepInvokerPort invoker = invokerProvider.getIfAvailable();
        if (invoker == null) {
            // 子 Run 的完整管线派生属编排面后续批次；通道缺席即如实失败，禁伪称成功
            stepWriter.write(new StepWriter.StepWriteRequest(parent, step, attempt,
                    WAgentStatus.StepStatus.FAILED.value(), resolvedInput, null,
                    decision.effect(), decision.ruleId(), null, null,
                    WAgentErrorCode.E_WA_DEP.wireCode(), "StepInvokerPort 未装配（sub_run）",
                    null, operator));
            return StepResult.failedDep("StepInvokerPort 未装配（sub_run）");
        }
        StepInvokerPort.Outcome outcome = invoker.invoke(new StepInvokerPort.Request(
                parent.getTenantId(), parent.getId(), step.stepKey(), StepType.SUB_RUN.value(),
                step.toolName(), step.toolVersion(), resolvedInput, step.automationLevel()));
        if (!outcome.success()) {
            stepWriter.write(new StepWriter.StepWriteRequest(parent, step, attempt,
                    WAgentStatus.StepStatus.FAILED.value(), resolvedInput, outcome.output(),
                    decision.effect(), decision.ruleId(), null, null,
                    outcome.errorCode(), outcome.errorText(), outcome.metrics(), operator));
            return new StepResult(Disposition.FAILED_DEP, outcome.output(),
                    outcome.errorCode(), outcome.errorText(), decision.effect(), decision.ruleId(), null);
        }
        Consumption child = consumptionOf(outcome.metrics());
        absorbChildConsumption(parent.getId(), child);
        stepWriter.write(new StepWriter.StepWriteRequest(parent, step, attempt,
                WAgentStatus.StepStatus.SUCCEEDED.value(), resolvedInput, outcome.output(),
                decision.effect(), decision.ruleId(), null, null, null, null,
                outcome.metrics(), operator));
        return new StepResult(Disposition.SUCCEEDED, outcome.output(), null, null,
                decision.effect(), decision.ruleId(), child);
    }

    /** 父链深度（顶层=0；沿 parent_run_id 计数，链深防御上限同 MAX_SUB_RUN_DEPTH+2）。 */
    public int depthOf(WAgentRunDO run) {
        int depth = 0;
        String parentId = run.getParentRunId();
        while (parentId != null && !parentId.isBlank()) {
            WAgentRunDO ancestor = runDao.findById(parentId).orElse(null);
            if (ancestor == null) {
                break;
            }
            depth++;
            parentId = ancestor.getParentRunId();
            if (depth > MAX_SUB_RUN_DEPTH + 2) {
                throw WAgentOrchestrationException.validation("parent_run_id 链超深（疑似成环）");
            }
        }
        return depth;
    }

    /**
     * 子 Run 消耗原子并入父 Run（F10-14）：重读父行 → 四计数器逐项相加 → 回写
     * （单读改写序列；并发窗口内的丢更新由对账任务补偿——本轮库未实跑，纪律登记）。
     */
    public Consumption absorbChildConsumption(String parentRunId, Consumption childConsumption) {
        WAgentRunDO parent = runDao.findById(parentRunId)
                .orElseThrow(WAgentOrchestrationException::notFound);
        Consumption merged = parseConsumption(parent.getConsumptionJson()).merge(childConsumption);
        parent.setConsumptionJson(writeJson(merged.asMap()));
        runDao.update(parent);
        log.info("[F10-14] 子 Run 消耗回计父 Run: parent={} merged={}", parentRunId, merged.asMap());
        return merged;
    }

    public Consumption parseConsumption(String json) {
        if (json == null || json.isBlank()) {
            return Consumption.zero();
        }
        try {
            Map<String, Object> m = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            return new Consumption(intOr(m, "steps"), intOr(m, "llm_calls"),
                    intOr(m, "replan_rounds"), intOr(m, "refill_rounds"));
        } catch (Exception e) {
            throw WAgentOrchestrationException.validation("consumption_json 不可解析（禁宽容当零）");
        }
    }

    private static Consumption consumptionOf(Map<String, Object> metrics) {
        // 子 Run 汇总消耗由被调面回传（metrics.child_consumption{steps,llm_calls,...}）；缺省按 1 步计
        Object raw = metrics == null ? null : metrics.get("child_consumption");
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> mm = new LinkedHashMap<>();
            m.forEach((k, v) -> mm.put(String.valueOf(k), v));
            return new Consumption(intOr(mm, "steps"), intOr(mm, "llm_calls"),
                    intOr(mm, "replan_rounds"), intOr(mm, "refill_rounds"));
        }
        return new Consumption(1, 0, 0, 0);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw WAgentOrchestrationException.validation("consumption_json 序列化失败");
        }
    }

    private static int intOr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }

    /** 顶层 Run 的自动化上限兜底（创建面使用：null ⇒ L0 最严档）。 */
    public static int ceilingOrDefault(Integer ceiling) {
        return ceiling == null ? AutomationLevel.L0.level() : ceiling;
    }

    /** 状态词表快捷（避免执行面散落字面量）。 */
    public static boolean running(RunStatus status) {
        return status == RunStatus.EXECUTING;
    }
}
