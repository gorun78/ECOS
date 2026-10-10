package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.AutomationLevel;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.StepType;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunStepDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentToolContractDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunStepDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自动化上限落库触发点（详细设计-10 F10-21，冻结验收名）。
 *
 * <p>StepWriter 双校之一：{@code step.automation_level ≤ run.automation_ceiling}
 * 越界 ⇒ {@code E-POLICY 403}（extras 携 stepKey/stepLevel/runCeiling 诊断三元）；
 * ceiling 缺席按 L0 最严档；L3 无自动路径——approval_token 缺失同样 E-POLICY。</p>
 */
class AutomationCeilingTest {

    private static WAgentRunDO run(Integer ceiling) {
        WAgentRunDO run = new WAgentRunDO();
        run.setId("r1");
        run.setTenantId("t1");
        run.setAutomationCeiling(ceiling);
        return run;
    }

    private static WAgentPlan.Step levelStep(String key, int level) {
        return new WAgentPlan.Step(key, StepType.TOOL.value(), "create_action_draft", "1",
                level, List.of(), Map.of(), false, false, null);
    }

    private static StepWriter.StepWriteRequest req(WAgentRunDO run, WAgentPlan.Step step,
                                                   String approvalToken) {
        return new StepWriter.StepWriteRequest(run, step, 0,
                WAgentStatus.StepStatus.SUCCEEDED.value(), Map.of("k", 1), null,
                null, null, approvalToken, null, null, null, null, "u1");
    }

    @Test
    @DisplayName("F10-21：步骤档 > Run ceiling ⇒ E-POLICY 拒落；ceiling 缺席按 L0 最严档；L3 无 approval_token 同样拒")
    void stepLevelAboveRunCeilingRejected() {
        WAgentRunStepDao stepDao = mock(WAgentRunStepDao.class);
        when(stepDao.findByRunStepAttempt(any(), any(), any())).thenReturn(Optional.empty());
        StepWriter writer = new StepWriter(stepDao);

        // 越界：档 2 > ceiling 1 ⇒ E-POLICY，extras 给全诊断三元
        WAgentOrchestrationException over = assertThrows(WAgentOrchestrationException.class,
                () -> writer.write(req(run(AutomationLevel.L1.level()), levelStep("s2", 2), null)));
        assertEquals(WAgentErrorCode.E_POLICY, over.errorCode());
        assertEquals("s2", over.extras().get("stepKey"));
        assertEquals(2, over.extras().get("stepLevel"));
        assertEquals(1, over.extras().get("runCeiling"));
        verify(stepDao, never()).insert(any(WAgentRunStepDO.class));

        // 缺席即最严：ceiling null ⇒ L0 档面，步骤档 1 已越界
        WAgentOrchestrationException strict = assertThrows(WAgentOrchestrationException.class,
                () -> writer.write(req(run(null), levelStep("s1", 1), null)));
        assertEquals(WAgentErrorCode.E_POLICY, strict.errorCode());
        assertEquals(0, strict.extras().get("runCeiling"));

        // L3 无自动路径：token 缺失/空白 ⇒ E-POLICY（即便 ceiling=3 档位合规）
        assertThrows(WAgentOrchestrationException.class,
                () -> writer.write(req(run(3), levelStep("s3", 3), null)));
        WAgentOrchestrationException noToken = assertThrows(WAgentOrchestrationException.class,
                () -> writer.write(req(run(3), levelStep("s3", 3), "   ")));
        assertEquals(WAgentErrorCode.E_POLICY, noToken.errorCode());

        // 合规面放行：档 1 ≤ ceiling 1 落行成功
        writer.write(req(run(1), levelStep("s1", 1), null));
        ArgumentCaptor<WAgentRunStepDO> cap = ArgumentCaptor.forClass(WAgentRunStepDO.class);
        verify(stepDao).insert(cap.capture());
        assertEquals(1, cap.getValue().getAutomationLevel());

        // 计划面预检同口径（PlanValidator LEVEL：档 > ceiling ⇒ 独立拒绝）
        WAgentToolContractDao contractDao = mock(WAgentToolContractDao.class);
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<WAgentPolicyGuardPort> absent =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        WAgentPolicyGuardPort guard = mock(WAgentPolicyGuardPort.class);
        when(guard.prePlan(any(WAgentPolicyGuardPort.PolicyCheckContext.class)))
                .thenReturn(WAgentPolicyGuardPort.PolicyDecision.allow("r1"));
        when(absent.getIfAvailable()).thenReturn(guard);
        PlanValidator validator = new PlanValidator(contractDao, absent);
        PlanValidator.Report report = validator.validate(new WAgentPlan.Plan(
                com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PlanSource.PLAYBOOK,
                null, null, List.of(levelStep("s2", 3))), 1,
                new WAgentBudgetService.BudgetSnapshot(60, 40, 60, 30, 3, 3), "t1", "r1");
        assertTrue(report.failures().containsKey(PlanValidator.Check.LEVEL));
    }
}
