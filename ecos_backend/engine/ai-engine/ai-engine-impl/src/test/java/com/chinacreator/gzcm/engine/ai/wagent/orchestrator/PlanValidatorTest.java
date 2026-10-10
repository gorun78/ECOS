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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plan 七项校验（详细设计-10 F10-12，冻结验收名）。
 *
 * <p>七检查各自独立拒绝：每个注入缺陷 ⇒ failures 恰好命中该检查一项（互不遮蔽）；
 * 写步骤未声明 Candidate 唯一写通道 ⇒ WRITE_CHANNEL 拒绝（F10-17 咽喉）。</p>
 */
@SuppressWarnings("unchecked")
class PlanValidatorTest {

    private static final BudgetSnapshot BUDGET =
            new BudgetSnapshot(60, 40, 60, 30, 3, 3);

    private static WAgentPlan.Step toolStep(String key, String tool, int level) {
        return new WAgentPlan.Step(key, StepType.TOOL.value(), tool, "1", level,
                List.of(), Map.of(), false, false, "wagent.run");
    }

    private static WAgentPlan.Step step(String key, StepType type, int level,
                                        boolean writeChannel, boolean viaCandidate) {
        return new WAgentPlan.Step(key, type.value(), null, null, level,
                List.of(), Map.of(), writeChannel, viaCandidate, null);
    }

    private static WAgentPlan.Plan planOf(WAgentPlan.Step... steps) {
        return new WAgentPlan.Plan(PlanSource.PLAYBOOK, "git/ref/abc", "v3", List.of(steps));
    }

    private static WAgentToolContractDO activeContract() {
        WAgentToolContractDO c = new WAgentToolContractDO();
        c.setStatus(ToolContractStatus.ACTIVE.value());
        c.setInputSchemaJson("{\"type\":\"object\"}");
        c.setOutputSchemaJson("{\"type\":\"object\"}");
        return c;
    }

    private static ObjectProvider<WAgentPolicyGuardPort> guardProvider(WAgentPolicyGuardPort guard) {
        ObjectProvider<WAgentPolicyGuardPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(guard);
        return provider;
    }

    private static WAgentPolicyGuardPort allowGuard() {
        WAgentPolicyGuardPort guard = mock(WAgentPolicyGuardPort.class);
        when(guard.prePlan(any(PolicyCheckContext.class))).thenReturn(PolicyDecision.allow("r-allow"));
        return guard;
    }

    private static PlanValidator validatorWithHealthyBase(WAgentToolContractDao contractDao) {
        when(contractDao.findByNameAndVersion(anyString(), anyString()))
                .thenReturn(Optional.of(activeContract()));
        return new PlanValidator(contractDao, guardProvider(allowGuard()));
    }

    private static void assertOnlyFailure(PlanValidator.Report report, PlanValidator.Check expected) {
        assertEquals(EnumSet.of(expected), report.failures().keySet(),
                () -> "应只命中 " + expected + "，实际: " + report.failures());
        assertFalse(report.ok());
    }

    @Test
    @DisplayName("F10-12：七检查各自独立拒绝——每注入一个缺陷只命中对应一项；全过则 ok")
    void sevenChecksEachRejectIndependently() {
        WAgentToolContractDao contractDao = mock(WAgentToolContractDao.class);
        PlanValidator validator = validatorWithHealthyBase(contractDao);

        // 基线：健康计划七检全过
        PlanValidator.Report healthy = validator.validate(
                planOf(toolStep("s1", "t1", 0)), 1, BUDGET, "t1", "r1");
        assertTrue(healthy.ok(), () -> "健康计划应全过: " + healthy.failures());
        assertFalse(healthy.requiresApproval());

        // 1 STRUCTURE：空步骤集（其余六检无从犯）
        assertOnlyFailure(validator.validate(new WAgentPlan.Plan(
                PlanSource.PLAYBOOK, null, null, List.of()), 1, BUDGET, "t1", "r1"),
                PlanValidator.Check.STRUCTURE);

        // 2 TOOL：工具未注册（契约缺席 ⇒ TOOL 拒绝；CONTRACT 不记账）
        when(contractDao.findByNameAndVersion("ghost", "1")).thenReturn(Optional.empty());
        assertOnlyFailure(validator.validate(
                planOf(toolStep("s1", "ghost", 0)), 1, BUDGET, "t1", "r1"),
                PlanValidator.Check.TOOL);
        when(contractDao.findByNameAndVersion("ghost", "1"))
                .thenReturn(Optional.of(activeContract())); // 复位

        // 3 CONTRACT：active 契约缺 output schema（input/output 任一缺 = 契约不闭合）
        WAgentToolContractDO incomplete = activeContract();
        incomplete.setOutputSchemaJson(" ");
        when(contractDao.findByNameAndVersion("t1", "1")).thenReturn(Optional.of(incomplete));
        assertOnlyFailure(validator.validate(
                planOf(toolStep("s1", "t1", 0)), 1, BUDGET, "t1", "r1"),
                PlanValidator.Check.CONTRACT);
        when(contractDao.findByNameAndVersion("t1", "1")).thenReturn(Optional.of(activeContract()));

        // 4 PERMISSION：pre-plan DENY ⇒ 仅此一项失败，主导码 E-POLICY
        WAgentPolicyGuardPort denyGuard = mock(WAgentPolicyGuardPort.class);
        when(denyGuard.prePlan(any(PolicyCheckContext.class)))
                .thenReturn(PolicyDecision.deny("r-deny", "越权主体"));
        PlanValidator denyValidator = new PlanValidator(contractDao, guardProvider(denyGuard));
        PlanValidator.Report denied = denyValidator.validate(
                planOf(toolStep("s1", "t1", 0)), 1, BUDGET, "t1", "r1");
        assertOnlyFailure(denied, PlanValidator.Check.PERMISSION);
        assertEquals(WAgentErrorCode.E_POLICY, denied.dominantErrorCode());

        // 4b PERMISSION fail-closed 面：策略源缺席 ⇒ 同样 PERMISSION 拒绝（F10-22）
        ObjectProvider<WAgentPolicyGuardPort> absent = mock(ObjectProvider.class);
        when(absent.getIfAvailable()).thenReturn(null);
        PlanValidator noGuard = new PlanValidator(contractDao, absent);
        assertOnlyFailure(noGuard.validate(
                planOf(toolStep("s1", "t1", 0)), 1, BUDGET, "t1", "r1"),
                PlanValidator.Check.PERMISSION);

        // 4c REQUIRE_APPROVAL ≠ 失败：计划挂起待审（ok() 仍真，requiresApproval 置位）
        WAgentPolicyGuardPort approvalGuard = mock(WAgentPolicyGuardPort.class);
        when(approvalGuard.prePlan(any(PolicyCheckContext.class)))
                .thenReturn(PolicyDecision.requireApproval("r-approve"));
        PlanValidator approvalValidator = new PlanValidator(contractDao, guardProvider(approvalGuard));
        PlanValidator.Report pending = approvalValidator.validate(
                planOf(toolStep("s1", "t1", 0)), 1, BUDGET, "t1", "r1");
        assertTrue(pending.ok());
        assertTrue(pending.requiresApproval());

        // 5 LEVEL：步骤档 2 > Run ceiling 1（计划面预检，F10-21）
        assertOnlyFailure(validator.validate(
                planOf(step("s1", StepType.AGGREGATE, 2, false, false)), 1, BUDGET, "t1", "r1"),
                PlanValidator.Check.LEVEL);

        // 6 BUDGET：快照缺失 ⇒ BUDGET 独立失败，主导码 E-WA-BUDGET
        PlanValidator.Report noBudget = validator.validate(
                planOf(toolStep("s1", "t1", 0)), 1, null, "t1", "r1");
        assertOnlyFailure(noBudget, PlanValidator.Check.BUDGET);
        assertEquals(WAgentErrorCode.E_WA_BUDGET, noBudget.dominantErrorCode());

        // 7 WRITE_CHANNEL：写步骤未声明 Candidate 唯一写通道（tool 型须契约齐备，避免串扰 TOOL）
        assertOnlyFailure(validator.validate(
                planOf(new WAgentPlan.Step("s1", StepType.TOOL.value(), "t1", "1", 0,
                        List.of(), Map.of(), true, false, null)), 1, BUDGET, "t1", "r1"),
                PlanValidator.Check.WRITE_CHANNEL);
    }

    @Test
    @DisplayName("F10-12/F10-17：写通道步骤未声明 via_candidate_channel ⇒ WRITE_CHANNEL 拒绝（唯一写通道咽喉）")
    void writeStepWithoutCandidateChannelFails() {
        WAgentToolContractDao contractDao = mock(WAgentToolContractDao.class);
        when(contractDao.findByNameAndVersion(anyString(), anyString()))
                .thenReturn(Optional.of(activeContract()));
        PlanValidator validator = new PlanValidator(contractDao, guardProvider(allowGuard()));

        WAgentPlan.Step writeWithoutChannel = new WAgentPlan.Step("s2_commit",
                StepType.TOOL.value(), "publish_candidate", "1", 1,
                List.of(), Map.of(), true, false, "wagent.commit");
        PlanValidator.Report report = validator.validate(
                planOf(writeWithoutChannel), 1, BUDGET, "t1", "r1");

        assertOnlyFailure(report, PlanValidator.Check.WRITE_CHANNEL);
        assertEquals(WAgentErrorCode.E_VALIDATION, report.dominantErrorCode());
        assertTrue(report.failures().get(PlanValidator.Check.WRITE_CHANNEL)
                .contains("s2_commit"));

        // 同一步骤声明 Candidate 唯一写通道 ⇒ 放行（咽喉判据是声明位而非步骤名）
        WAgentPlan.Step viaCandidate = new WAgentPlan.Step("s2_commit",
                StepType.TOOL.value(), "publish_candidate", "1", 1,
                List.of(), Map.of(), true, true, "wagent.commit");
        assertTrue(validator.validate(planOf(viaCandidate), 1, BUDGET, "t1", "r1").ok());
    }
}
