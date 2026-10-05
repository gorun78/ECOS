package com.chinacreator.gzcm.ai.wagent.orchestration;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.PlanSource;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.StepType;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ToolCategory;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ToolStatus;
import com.chinacreator.gzcm.ai.wagent.orchestrator.PlanContext;
import com.chinacreator.gzcm.ai.wagent.orchestrator.PlanContext.Step;
import com.chinacreator.gzcm.ai.wagent.orchestrator.PlanValidator;
import com.chinacreator.gzcm.ai.wagent.orchestrator.PlanValidator.CheckResult;
import com.chinacreator.gzcm.ai.wagent.orchestrator.PlanValidator.OperandPolicy;
import com.chinacreator.gzcm.ai.wagent.orchestrator.RunBudget;
import com.chinacreator.gzcm.ai.wagent.tool.ToolContract;
import com.chinacreator.gzcm.ai.wagent.tool.ToolContractRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-13/F10-22 Plan 七重校验离线单测：七关独立判定（每关返回 CheckResult），
 * 结构 / 策略 / 预算 / 写通道 等独立检查各自可单独 reject（关间不牵连）。
 */
class PlanValidatorTest {

    private static PlanValidator.OperandPolicy allowPolicy() { return (hash, caller) -> "EFFECT"; }
    private static PlanValidator.OperandPolicy denyPolicy()  { return (hash, caller) -> "DENY"; }

    /** 合法骨架 plan：1 步 LLM、stepKey 唯一、wLevel 1（非空 questionId/runId）。 */
    private static PlanContext validPlan() {
        Step s = new Step("s1", StepType.LLM, null, 1, Map.of());
        return new PlanContext("q-1", "run-1", PlanSource.PLAYBOOK, List.of(s), 0);
    }

    /** 空步骤 plan（非空 questionId/runId）。 */
    private static PlanContext emptyPlan() {
        return new PlanContext("q-1", "run-1", PlanSource.PLAYBOOK, List.of(), 0);
    }

    /**
     * F10-13：七关各自独立判定。覆盖 ≥3 个独立检查（struct / policy / budget）
     * 的「各自可 reject」语义：每个违规项在其它检查全过的 plan 上被独立检出，且失败只落本关。
     */
    @Test
    void sevenChecksRejectIndependently() {
        ToolContractRegistry registry = new ToolContractRegistry();
        RunBudget.Limits limits = RunBudget.Limits.defaults();

        // #1 struct：空 steps ⇒ struct 关独立 fail。
        List<CheckResult> resEmpty = PlanValidator.validate(emptyPlan(), registry, limits, allowPolicy());
        assertEquals(7, resEmpty.size(), "七关须各产一条 CheckResult");
        CheckResult struct = resEmpty.get(0);
        assertFalse(struct.ok(), "空 plan ⇒ struct 关必须 fail");
        assertTrue(struct.code().startsWith("struct"), "struct 关须独立告警（code 前缀 struct），实际：" + struct.code());

        // #5 ceiling：wLevel 在 Step 构造器夹到 0..3，合法 plan 的 ceiling 关 = pass（通路存在）。
        List<CheckResult> resValid = PlanValidator.validate(validPlan(), registry, limits, allowPolicy());
        assertEquals(7, resValid.size());
        assertTrue(resValid.get(4).ok(), "合法 plan 的 ceiling 关应 pass");
        assertTrue(resValid.get(3).ok(), "allowPolicy 下 policy 关应 pass");
        assertTrue(resValid.get(5).ok(), "合法 plan 的 budget 关应 pass");

        // #4 policy：拒绝策略下仅 policy 关被独立拒（其余关不受牵连）。
        List<CheckResult> resDeny = PlanValidator.validate(validPlan(), registry, limits, denyPolicy());
        assertFalse(resDeny.get(3).ok(), "DENY 策略 ⇒ policy 关独立 fail");
        assertTrue(resDeny.get(0).ok(), "DENY 不应牵连 struct 关（关间独立）");
        assertTrue(resDeny.get(5).ok(), "DENY 不应牵连 budget 关（关间独立）");

        // #6 budget：steps 数 > maxStep ⇒ budget 关独立 fail。
        Step s0 = new Step("s0", StepType.LLM, null, 1, Map.of());
        Step s1 = new Step("s1", StepType.LLM, null, 1, Map.of());
        RunBudget.Limits strict = new RunBudget.Limits(1, 40, 60_000L, 1_800_000L, 3, 3);
        List<CheckResult> resOver = PlanValidator.validate(
                new PlanContext("q-1", "run-1", PlanSource.PLAYBOOK, List.of(s0, s1), 0),
                registry, strict, allowPolicy());
        assertFalse(resOver.get(5).ok(), "steps=2 > maxStep=1 ⇒ budget 关独立 fail");
        assertTrue(resOver.get(3).ok(), "budget 失败不应牵连 policy 关（关间独立）");
    }

    /**
     * F10-13：写通道关（write_channel）——COMMIT 工具必须带非空回滚通道（pre-commit 铁律）。
     * 对应「L3 写步无审批通道被拒」：COMMIT 契约缺 rollback_plan_text ⇒ 拒收（validateDefaults 抛 IAE）。
     * 带 rollback 的 L3 COMMIT 工具（INITIAL_TWELVE 自带）⇒ write_channel 关放行。
     */
    @Test
    void writeStepWithoutApprovalChannelFails() {
        ToolContractRegistry registry = new ToolContractRegistry();
        RunBudget.Limits limits = RunBudget.Limits.defaults();

        // 合法 L3 COMMIT 工具（create_action_draft，带 rollback）⇒ contract / write_channel 关放行。
        Step commitOk = new Step("s1", StepType.TOOL, "create_action_draft", 3, Map.of());
        List<CheckResult> okRes = PlanValidator.validate(
                new PlanContext("q-1", "run-1", PlanSource.PLAYBOOK, List.of(commitOk), 0),
                registry, limits, allowPolicy());
        assertEquals(7, okRes.size());
        assertTrue(okRes.get(2).ok(), "活跃契约在位 ⇒ contract 关放行，detail=" + okRes.get(2).detail());
        assertTrue(okRes.get(6).ok(), "带 rollback 的 L3 COMMIT 工具 write_channel 关应放行，detail=" + okRes.get(6).detail());

        // 收尾断言：COMMIT 契约缺 rollback_plan_text ⇒ validateDefaults 拒绝（pre-commit 回滚通道，应用+DB 双校）。
        ToolContract bad = new ToolContract(
                "noop_commit", "1", "ai-engine", ToolCategory.COMMIT, "write", 3,
                "perm:commit:noop_commit",
                "{\"t\":\"o\"}", "{\"t\":\"o\"}", 30_000, true, "cost:x", false,
                "L1_internal", null,
                "rest", "/api/v1/wagent/decisions", "POST",
                "ai-engine", "sla", ToolStatus.DRAFT, null, "business",
                "seed:noop", "disc", "dep");
        assertThrows(IllegalArgumentException.class,
                () -> ToolContract.validateDefaults(bad),
                "L3 COMMIT 写步缺回滚通道（rollback_plan_text）必须拒收");
    }
}
