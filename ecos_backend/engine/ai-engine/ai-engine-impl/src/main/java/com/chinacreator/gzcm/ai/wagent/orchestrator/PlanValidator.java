package com.chinacreator.gzcm.ai.wagent.orchestrator;

import com.chinacreator.gzcm.ai.wagent.orchestrator.PlanContext.Step;
import com.chinacreator.gzcm.ai.wagent.tool.ToolContract;
import com.chinacreator.gzcm.ai.wagent.tool.ToolContractRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * 分册10 F10-13/F10-22 · Plan 七重校验（pre-plan 前跑，全过才进 PLAN_VALIDATED）。
 * 七关依次：struct / tools / contract / policy / ceiling / budget / write-channel。
 * 每关返回一个 {@link CheckResult}（ok + code + detail），禁合并成一个布尔——
 * 审计与 controller 层需逐关回显 E-* code（E_VALIDATION / E_WA-BUDGET / E_WA-NUM 等）。
 */
public final class PlanValidator {

    private PlanValidator() {}

    /** 单关结论：ok=true 通过；code = E-* 短码（失败时非空）；detail = 人读定位。 */
    public record CheckResult(boolean ok, String code, String detail) {
        public static CheckResult ok(String code) { return new CheckResult(true, code, "pass"); }
        public static CheckResult fail(String code, String detail) { return new CheckResult(false, code, detail); }
    }

    /**
     * Policy Guard 端口（F10-22 pre-plan 裁决，经 security-engine REST；不可用/异常/超时 ⇒ DENY）。
     * 实现方在 W Agent 侧薄壳封装 security-engine，本类只调接口，不重实现策略。
     */
    public interface OperandPolicy {
        /** @return "EFFECT"（放行）或 "DENY"（拒绝）；实现方对异常/超时一律兜底 DENY。 */
        String evaluatePrePlan(String planHash, String caller);
    }

    public static final List<String> CODES =
            List.of("struct", "tools", "contract", "policy", "ceiling", "budget", "write_channel");

    /** 七关校验入口：顺序固定（CODES 顺序），失败不短路——返回全 7 条结论。 */
    public static List<CheckResult> validate(PlanContext plan, ToolContractRegistry registry,
                                             RunBudget.Limits limits, OperandPolicy policy) {
        List<CheckResult> r = new ArrayList<>(7);
        r.add(checkStruct(plan));
        r.add(checkTools(plan, registry));
        r.add(checkContract(plan, registry));
        r.add(checkPolicy(plan, policy));
        r.add(checkCeiling(plan));
        r.add(checkBudget(plan, limits));
        r.add(checkWriteChannel(plan, registry));
        return List.copyOf(r);
    }

    /** #1 结构：非空、stepKey 唯一、type 合法、至少一步。 */
    private static CheckResult checkStruct(PlanContext plan) {
        if (plan == null || plan.steps() == null || plan.steps().isEmpty()) {
            return CheckResult.fail("struct", "E_VALIDATION: plan or steps empty");
        }
        int n = plan.steps().size();
        java.util.HashSet<String> keys = new java.util.HashSet<>();
        for (Step s : plan.steps()) {
            if (s.stepKey() == null || s.stepKey().isEmpty()) return CheckResult.fail("struct", "E_VALIDATION: blank stepKey");
            if (n > RunBudget.Limits.defaults().maxStep()) {
                return CheckResult.fail("struct", "E_WA-BUDGET: step count " + n + " hard-over");
            }
            if (!keys.add(s.stepKey())) return CheckResult.fail("struct", "E_VALIDATION: duplicate stepKey " + s.stepKey());
        }
        return CheckResult.ok("struct");
    }

    /** #2 工具：TOOL 型步骤必须给 toolName。 */
    private static CheckResult checkTools(PlanContext plan, ToolContractRegistry registry) {
        for (Step s : plan.steps()) {
            if (s.type() == com.chinacreator.gzcm.ai.wagent.WAgentEnums.StepType.TOOL
                    && (s.toolName() == null || s.toolName().isEmpty())) {
                return CheckResult.fail("tools", "E_VALIDATION: TOOL step " + s.stepKey() + " lacks toolName");
            }
        }
        return CheckResult.ok("tools");
    }

    /** #3 契约：TOOL 型步骤必须能在 registry 选到 ACTIVE 契约，否则契约缺失。 */
    private static CheckResult checkContract(PlanContext plan, ToolContractRegistry registry) {
        for (Step s : plan.steps()) {
            if (s.type() != com.chinacreator.gzcm.ai.wagent.WAgentEnums.StepType.TOOL) continue;
            ToolContract tc = registry.isActiveTool(s.toolName());
            if (tc == null) {
                return CheckResult.fail("contract", "E_VALIDATION: no ACTIVE contract for tool " + s.toolName());
            }
        }
        return CheckResult.ok("contract");
    }

    /** #4 策略：pre-plan 裁决，DENY 或非 EFFECT 一律 fail（security 侧已兜底，这里二次断言）。 */
    private static CheckResult checkPolicy(PlanContext plan, OperandPolicy policy) {
        if (policy == null) return CheckResult.fail("policy", "E_POLICY: policy port absent (fail-closed)");
        String verdict;
        try {
            verdict = policy.evaluatePrePlan(planHash(plan), plan.questionId());
        } catch (RuntimeException ex) {
            return CheckResult.fail("policy", "E_POLICY: " + ex.getMessage() + " (fail-closed DENY)");
        }
        if (!"EFFECT".equals(verdict)) {
            return CheckResult.fail("policy", "E_POLICY: pre-plan verdict not EFFECT (" + verdict + ")");
        }
        return CheckResult.ok("policy");
    }

    /** #5 天花板：wLevel 越 3 即 fail（AutomationLevel L0..L3 上限）。 */
    private static CheckResult checkCeiling(PlanContext plan) {
        for (Step s : plan.steps()) {
            if (s.wLevel() > 3) return CheckResult.fail("ceiling", "E_VALIDATION: wLevel > L3 at " + s.stepKey());
        }
        return CheckResult.ok("ceiling");
    }

    /** #6 预算：步数不超过租户 maxStep（budget 硬顶）。 */
    private static CheckResult checkBudget(PlanContext plan, RunBudget.Limits limits) {
        RunBudget.Limits eff = (limits == null) ? RunBudget.Limits.defaults() : RunBudget.enforceTenantDowngrade(limits);
        if (plan.steps().size() > eff.maxStep()) {
            return CheckResult.fail("budget", "E_WA-BUDGET: steps " + plan.steps().size() + " > maxStep " + eff.maxStep());
        }
        return CheckResult.ok("budget");
    }

    /** #7 写通道：COMMIT 类工具必须带非空 rollbackPlanText（回滚通道，铁律 pre-commit）。 */
    private static CheckResult checkWriteChannel(PlanContext plan, ToolContractRegistry registry) {
        for (Step s : plan.steps()) {
            if (s.type() != com.chinacreator.gzcm.ai.wagent.WAgentEnums.StepType.TOOL) continue;
            ToolContract tc = registry.isActiveTool(s.toolName());
            if (tc == null) continue;                    // 契约缺失已在 #3 记录
            if (tc.category() == com.chinacreator.gzcm.ai.wagent.WAgentEnums.ToolCategory.COMMIT
                    && (tc.rollbackPlanText() == null || tc.rollbackPlanText().isBlank())) {
                return CheckResult.fail("write_channel", "E_VALIDATION: COMMIT tool " + s.toolName() + " lacks rollback_plan_text");
            }
        }
        return CheckResult.ok("write_channel");
    }

    private static String planHash(PlanContext plan) {
        StringBuilder sb = new StringBuilder();
        for (Step s : plan.steps()) sb.append(s.stepKey()).append('|').append(s.type()).append('|').append(s.toolName());
        return String.valueOf(sb.toString().hashCode()) + ":" + plan.source();
    }
}
