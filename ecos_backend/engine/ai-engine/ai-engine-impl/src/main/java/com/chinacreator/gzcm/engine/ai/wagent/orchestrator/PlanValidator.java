package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PlanSource;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.StepType;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ToolContractStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentToolContractDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentToolContractDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentBudgetService.BudgetSnapshot;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentPolicyGuardPort.PolicyCheckContext;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentPolicyGuardPort.PolicyDecision;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan 七项校验（详细设计-10 F10-12，REQ-WAG-12；§4.2 planning → plan_validated 唯一闸）。
 *
 * <p>七检查各自独立拒绝（PlanValidatorTest#sevenChecksEachRejectIndependently 的口径）：</p>
 * <ol>
 *   <li>STRUCTURE——step_key 非空唯一、依赖存在、DAG 无环、步骤集非空；</li>
 *   <li>TOOL——tool 类步骤的 name+version 必须在契约目录存在且 status=active（只认在用版，F10-17）；</li>
 *   <li>CONTRACT——命中契约必须 input/output schema 齐备（缺任一 = 契约不闭合，禁执行）；</li>
 *   <li>PERMISSION——经 {@link WAgentPolicyGuardPort#prePlan}（权限检查即消费 pre-plan 钩子，
 *       #131 接真实策略源）；DENY ⇒ 拒绝，REQUIRE_APPROVAL ⇒ 计划挂起待审（非拒绝）；
 *       策略源缺席 = DENY（fail-closed，F10-22）；</li>
 *   <li>LEVEL——逐步骤 {@code automation_level ≤ run.automation_ceiling}（计划面预检；
 *       落库触发点在 StepWriter 双校，F10-21）；</li>
 *   <li>BUDGET——静态预算：{@code steps ≤ budget.maxSteps}（运行期越限终止在 Orchestrator，F10-11）；</li>
 *   <li>WRITE_CHANNEL——写通道步骤必须声明 {@code via_candidate_channel=true}
 *       （F10-17 唯一写通道咽喉；PlanValidatorTest#writeStepWithoutCandidateChannelFails）。</li>
 * </ol>
 *
 * <p>重规划（§4.4）由调用方（Orchestrator）重跑本方法——校验器自身无状态，天然可重入。</p>
 */
@Component
public class PlanValidator {

    /** 七检查名单源（失败报告键；禁散落字面量）。 */
    public enum Check {
        STRUCTURE, TOOL, CONTRACT, PERMISSION, LEVEL, BUDGET, WRITE_CHANNEL
    }

    /** 校验报告：逐项失败原因（空 = 全过）+ pre-plan 是否要求审批。 */
    public record Report(Map<Check, String> failures, boolean requiresApproval) {
        public Report {
            failures = failures == null ? Map.of() : new LinkedHashMap<>(failures);
        }

        public boolean ok() {
            return failures.isEmpty();
        }

        /** 越权失败映射错误码（PERMISSION→E-POLICY；BUDGET→E-WA-BUDGET；其余 E-VALIDATION）。 */
        public WAgentErrorCode dominantErrorCode() {
            if (failures.containsKey(Check.PERMISSION)) {
                return WAgentErrorCode.E_POLICY;
            }
            if (failures.containsKey(Check.BUDGET)) {
                return WAgentErrorCode.E_WA_BUDGET;
            }
            return WAgentErrorCode.E_VALIDATION;
        }
    }

    private final WAgentToolContractDao toolContractDao;
    private final ObjectProvider<WAgentPolicyGuardPort> policyGuardProvider;

    public PlanValidator(WAgentToolContractDao toolContractDao,
                         ObjectProvider<WAgentPolicyGuardPort> policyGuardProvider) {
        this.toolContractDao = toolContractDao;
        this.policyGuardProvider = policyGuardProvider;
    }

    /**
     * 全量七检（不改库、不发事件——纯判定；失败落 Run 终态与事件由 Orchestrator 承担）。
     *
     * @param plan             待验计划
     * @param automationCeiling run.automation_ceiling（null 视为 0，最严档）
     * @param budget           创建时冻结的预算快照
     */
    public Report validate(WAgentPlan.Plan plan, Integer automationCeiling, BudgetSnapshot budget,
                           String tenantId, String runId) {
        Map<Check, String> failures = new EnumMap<>(Check.class);
        int ceiling = automationCeiling == null ? 0 : automationCeiling;

        checkStructure(plan, failures);
        checkToolAndContract(plan, failures);
        checkLevel(plan, ceiling, failures);
        checkBudget(plan, budget, failures);
        checkWriteChannel(plan, failures);
        boolean requiresApproval = checkPermission(plan, tenantId, runId, failures);
        return new Report(failures, requiresApproval);
    }

    // ── 1 STRUCTURE ────────────────────────────────────────────────
    private void checkStructure(WAgentPlan.Plan plan, Map<Check, String> failures) {
        if (plan == null || plan.steps().isEmpty()) {
            failures.put(Check.STRUCTURE, "计划步骤集为空");
            return;
        }
        List<String> keys = new ArrayList<>();
        for (WAgentPlan.Step s : plan.steps()) {
            if (s.stepKey() == null || s.stepKey().isBlank()) {
                failures.put(Check.STRUCTURE, "存在空 step_key");
                return;
            }
            if (keys.contains(s.stepKey())) {
                failures.put(Check.STRUCTURE, "step_key 重复: " + s.stepKey());
                return;
            }
            keys.add(s.stepKey());
            if (!StepType.isValid(s.stepType())) {
                failures.put(Check.STRUCTURE, "step_type 越界（StepType 六类单源）: " + s.stepType());
                return;
            }
        }
        if (WAgentPlan.topoOrder(plan).isEmpty()) {
            failures.put(Check.STRUCTURE, "DAG 结构非法（依赖悬空或成环）");
        }
    }

    // ── 2 TOOL + 3 CONTRACT（同一遍目录命中，失败分别记账互不遮蔽）──
    private void checkToolAndContract(WAgentPlan.Plan plan, Map<Check, String> failures) {
        for (WAgentPlan.Step s : plan.steps()) {
            if (!s.isToolStep()) {
                continue;
            }
            if (s.toolName() == null || s.toolName().isBlank()
                    || s.toolVersion() == null || s.toolVersion().isBlank()) {
                failures.putIfAbsent(Check.TOOL, "tool 步骤缺 name/version: " + s.stepKey());
                continue;
            }
            WAgentToolContractDO contract =
                    toolContractDao.findByNameAndVersion(s.toolName(), s.toolVersion()).orElse(null);
            if (contract == null || !ToolContractStatus.ACTIVE.value().equals(contract.getStatus())) {
                failures.putIfAbsent(Check.TOOL, "工具未注册或非 active 版: "
                        + s.toolName() + "@" + s.toolVersion());
                continue;
            }
            if (isBlank(contract.getInputSchemaJson()) || isBlank(contract.getOutputSchemaJson())) {
                failures.putIfAbsent(Check.CONTRACT, "契约 input/output schema 缺失: "
                        + s.toolName() + "@" + s.toolVersion());
            }
        }
    }

    // ── 4 PERMISSION（pre-plan 钩子即权限检查）────────────────────
    private boolean checkPermission(WAgentPlan.Plan plan, String tenantId, String runId,
                                    Map<Check, String> failures) {
        WAgentPolicyGuardPort guard = policyGuardProvider.getIfAvailable();
        if (guard == null) {
            failures.put(Check.PERMISSION, "策略源缺席（fail-closed，F10-22）");
            return false;
        }
        String source = plan == null || plan.source() == null ? null : plan.source().value();
        PolicyDecision decision = guard.prePlan(PolicyCheckContext.ofPlan(tenantId, runId, source));
        if (decision.effect() == com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PolicyEffect.DENY) {
            failures.put(Check.PERMISSION, "pre-plan 裁决 DENY: " + nullSafe(decision.reason()));
            return false;
        }
        return decision.effect() == com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PolicyEffect.REQUIRE_APPROVAL;
    }

    // ── 5 LEVEL ────────────────────────────────────────────────────
    private void checkLevel(WAgentPlan.Plan plan, int ceiling, Map<Check, String> failures) {
        for (WAgentPlan.Step s : plan.steps()) {
            if (s.automationLevel() > ceiling) {
                failures.putIfAbsent(Check.LEVEL, "步骤等级 " + s.automationLevel()
                        + " 超 Run automation_ceiling " + ceiling + ": " + s.stepKey());
            }
        }
    }

    // ── 6 BUDGET（静态面：计划规模 vs 快照）───────────────────────
    private void checkBudget(WAgentPlan.Plan plan, BudgetSnapshot budget, Map<Check, String> failures) {
        if (budget == null) {
            failures.put(Check.BUDGET, "预算快照缺失（创建时必须冻结 budget_json）");
            return;
        }
        if (plan.steps().size() > budget.maxSteps()) {
            failures.put(Check.BUDGET, "计划步骤数 " + plan.steps().size() + " > maxSteps "
                    + budget.maxSteps() + "（提示拆分问题）");
        }
    }

    // ── 7 WRITE_CHANNEL ────────────────────────────────────────────
    private void checkWriteChannel(WAgentPlan.Plan plan, Map<Check, String> failures) {
        for (WAgentPlan.Step s : plan.steps()) {
            if (s.writeChannel() && !s.viaCandidateChannel()) {
                failures.putIfAbsent(Check.WRITE_CHANNEL,
                        "写通道步骤未声明 Candidate 唯一写通道: " + s.stepKey() + "（F10-17 咽喉）");
            }
        }
    }

    /** EXPLORED 计划的 human_gate 前置结构断言（F10-13 结构面：闸步骤必须排在拓扑序首位；
     *  执行面硬约束写在 {@link WAgentRunStateMachine#canTransition}，双闸不依赖提示词）。 */
    public static boolean hasHumanGateBeforeTools(WAgentPlan.Plan plan) {
        if (plan == null || plan.source() != PlanSource.EXPLORED) {
            return true; // PLAYBOOK 不受本断言约束
        }
        List<WAgentPlan.Step> ordered = WAgentPlan.topoOrder(plan);
        return !ordered.isEmpty() && ordered.get(0).isHumanGate();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullSafe(String s) {
        return s == null ? "-" : s;
    }
}
