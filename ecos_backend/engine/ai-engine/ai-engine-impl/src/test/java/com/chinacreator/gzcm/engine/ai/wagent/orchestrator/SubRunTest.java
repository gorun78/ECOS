package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PolicyEffect;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.StepType;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunStepDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunStepDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentPolicyGuardPort.PolicyCheckContext;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentPolicyGuardPort.PolicyDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 子 Run 深度闸（详细设计-10 F10-14，冻结验收名）。
 *
 * <p>depth≤2：顶层 0、子 1、孙 2；孙 Run（深度 2 的父）再派 sub_run ⇒ 拒绝并以
 * failed 行留痕（E-VALIDATION 线码），禁静默截断、禁伪称成功；深度达标面 ⇒ 正常派生
 * 并把子消耗原子回计父 Run。</p>
 */
@SuppressWarnings("unchecked")
class SubRunTest {

    private static WAgentRunDO run(String id, String parentId) {
        WAgentRunDO run = new WAgentRunDO();
        run.setId(id);
        run.setTenantId("t1");
        run.setParentRunId(parentId);
        run.setPlanSource("EXPLORED");
        run.setAutomationCeiling(1);
        return run;
    }

    private static WAgentPlan.Step subRunStep() {
        return new WAgentPlan.Step("s3_sub", StepType.SUB_RUN.value(), "spawn_child_run", "1",
                0, List.of(), Map.of(), false, false, null);
    }

    @Test
    @DisplayName("F10-14：父链深度 2 再派 sub_run ⇒ 拒绝（failed 行留痕 E-VALIDATION）；深度合规 ⇒ 派生且消耗回计父 Run")
    void depthOverTwoRejected() {
        // 三层链：grand(parent=child)、child(parent=top)、top(无父)
        WAgentRunDO top = run("top", null);
        WAgentRunDO child = run("child", "top");
        WAgentRunDO grand = run("grand", "child");
        WAgentRunDao runDao = mock(WAgentRunDao.class);
        when(runDao.findById("top")).thenReturn(Optional.of(top));
        when(runDao.findById("child")).thenReturn(Optional.of(child));
        when(runDao.findById("grand")).thenReturn(Optional.of(grand));

        WAgentRunStepDao stepDao = mock(WAgentRunStepDao.class);
        when(stepDao.findByRunStepAttempt(any(), any(), any())).thenReturn(Optional.empty());
        StepWriter stepWriter = new StepWriter(stepDao);

        ObjectProvider<WAgentPolicyGuardPort> guardProvider = mock(ObjectProvider.class);
        WAgentPolicyGuardPort guard = mock(WAgentPolicyGuardPort.class);
        when(guard.preStep(any(PolicyCheckContext.class))).thenReturn(PolicyDecision.allow("r-step"));
        when(guardProvider.getIfAvailable()).thenReturn(guard);

        ObjectProvider<StepInvokerPort> invokerProvider = mock(ObjectProvider.class);
        StepExecutor executor = new StepExecutor(stepWriter, runDao, guardProvider,
                invokerProvider, mock(WAgentBudgetService.class));

        // 父链计数（顶层 0 / 子 1 / 孙 2）
        assertEquals(0, executor.depthOf(top));
        assertEquals(1, executor.depthOf(child));
        assertEquals(2, executor.depthOf(grand));
        assertEquals(2, StepExecutor.MAX_SUB_RUN_DEPTH);

        // 孙层再派 ⇒ 3 > 2 拒绝：Disposition=FAILED_DEP、E-VALIDATION 线码、failed 行留痕
        StepExecutor.StepResult rejected = executor.execute(
                grand, subRunStep(), 1, Map.of("mission", "m1"), "u1");
        assertEquals(StepExecutor.Disposition.FAILED_DEP, rejected.disposition());
        assertEquals(WAgentErrorCode.E_VALIDATION.wireCode(), rejected.errorCode());

        ArgumentCaptor<WAgentRunStepDO> failedCap = ArgumentCaptor.forClass(WAgentRunStepDO.class);
        verify(stepDao).insert(failedCap.capture());
        WAgentRunStepDO failedRow = failedCap.getValue();
        assertEquals(WAgentStatus.StepStatus.FAILED.value(), failedRow.getStatus());
        assertEquals(WAgentErrorCode.E_VALIDATION.wireCode(), failedRow.getErrorCode());
        assertEquals("grand", failedRow.getRunId());
        assertTrue(failedRow.getErrorText().contains("2"));

        // 合规深度（顶层派子，1 ≤ 2）：invoker ok ⇒ SUCCEEDED，子消耗原子回计父行
        StepInvokerPort invoker = mock(StepInvokerPort.class);
        when(invoker.invoke(any(StepInvokerPort.Request.class)))
                .thenReturn(StepInvokerPort.Outcome.ok(Map.of("child_run_id", "c-new")));
        when(invokerProvider.getIfAvailable()).thenReturn(invoker);

        StepExecutor.StepResult derived = executor.execute(
                top, subRunStep(), 1, Map.of("mission", "m1"), "u1");
        assertEquals(StepExecutor.Disposition.SUCCEEDED, derived.disposition());
        assertEquals(PolicyEffect.ALLOW, derived.policyEffect());
        assertEquals(1, derived.absorbedChildConsumption().steps()); // metrics 无 child_consumption ⇒ 按 1 步计

        ArgumentCaptor<WAgentRunDO> updateCap = ArgumentCaptor.forClass(WAgentRunDO.class);
        verify(runDao).update(updateCap.capture());
        assertTrue(updateCap.getValue().getConsumptionJson().contains("\"steps\":1"),
                "子消耗必须并入父 consumption_json: " + updateCap.getValue().getConsumptionJson());
    }
}
