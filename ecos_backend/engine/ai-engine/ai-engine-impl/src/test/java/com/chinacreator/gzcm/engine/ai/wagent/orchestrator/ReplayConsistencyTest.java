package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.StepType;
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

import java.util.LinkedHashMap;
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
 * 回放一致性（详细设计-10 F10-14，冻结验收名）。
 *
 * <p>同输入必得同哈希：{@link StepWriter#canonicalJson} 递归字典序排键（Map 迭代序
 * 不参与产物）；确定性步骤（同 invoker、同输入）⇒ 同 {@code output_hash}。plan_json
 * 编解码往返自洽（恢复重放读同一序列——数字不来自 LLM 的确定性形态）。</p>
 */
@SuppressWarnings("unchecked")
class ReplayConsistencyTest {

    private static WAgentRunDO run(String id) {
        WAgentRunDO run = new WAgentRunDO();
        run.setId(id);
        run.setTenantId("t1");
        run.setAutomationCeiling(1);
        return run;
    }

    private static WAgentPlan.Step toolStep(String key) {
        return new WAgentPlan.Step(key, StepType.TOOL.value(), "query_project_profit", "1",
                0, List.of(), Map.of(), false, false, null);
    }

    /** 两个同内容不同插入序的 Map（含嵌套层），回放语义下的"同一输入"。 */
    private static Map<String, Object> input(boolean reversedOrder) {
        Map<String, Object> inner = new LinkedHashMap<>();
        if (reversedOrder) {
            inner.put("zeta", 1);
            inner.put("alpha", "A");
        } else {
            inner.put("alpha", "A");
            inner.put("zeta", 1);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        if (reversedOrder) {
            root.put("project", inner);
            root.put("asOf", "2026-09-30");
        } else {
            root.put("asOf", "2026-09-30");
            root.put("project", inner);
        }
        return root;
    }

    @Test
    @DisplayName("F10-14：同输入（任意键序）⇒ 同 input_hash；确定性 invoker 同输入 ⇒ 同 output_hash；plan_json 往返自洽")
    void sameInputHashYieldsSameOutputHash() {
        StepWriter writer = new StepWriter(mock(WAgentRunStepDao.class));

        // 规范形层：插入序不参与产物（逐字符相等）
        assertEquals(writer.canonicalJson(input(false)), writer.canonicalJson(input(true)));

        // 落库面：经 StepExecutor 用确定性 invoker 走两次（不同 step_key，同输入异序）
        WAgentRunStepDao stepDao = mock(WAgentRunStepDao.class);
        when(stepDao.findByRunStepAttempt(any(), any(), any())).thenReturn(Optional.empty());
        StepWriter stored = new StepWriter(stepDao);

        WAgentPolicyGuardPort guard = mock(WAgentPolicyGuardPort.class);
        when(guard.preStep(any(PolicyCheckContext.class))).thenReturn(PolicyDecision.allow("r-step"));
        ObjectProvider<WAgentPolicyGuardPort> guardProvider = mock(ObjectProvider.class);
        when(guardProvider.getIfAvailable()).thenReturn(guard);

        // 确定性假 invoker：输出=输入的透传（无时钟/随机/LLM 参与）
        StepInvokerPort invoker = req -> StepInvokerPort.Outcome.ok(req.input());
        ObjectProvider<StepInvokerPort> invokerProvider = mock(ObjectProvider.class);
        when(invokerProvider.getIfAvailable()).thenReturn(invoker);

        StepExecutor executor = new StepExecutor(stored, mock(WAgentRunDao.class),
                guardProvider, invokerProvider, mock(WAgentBudgetService.class));
        assertEquals(StepExecutor.Disposition.SUCCEEDED,
                executor.execute(run("r1"), toolStep("s1"), 1, input(false), "u1").disposition());
        assertEquals(StepExecutor.Disposition.SUCCEEDED,
                executor.execute(run("r1"), toolStep("s2"), 1, input(true), "u1").disposition());

        ArgumentCaptor<WAgentRunStepDO> cap = ArgumentCaptor.forClass(WAgentRunStepDO.class);
        verify(stepDao, org.mockito.Mockito.times(2)).insert(cap.capture());
        List<WAgentRunStepDO> rows = cap.getAllValues();
        assertEquals(rows.get(0).getInputHash(), rows.get(1).getInputHash(),
                "同输入异序必须同 input_hash");
        assertEquals(rows.get(0).getOutputHash(), rows.get(1).getOutputHash(),
                "确定性步骤同输入 ⇒ 同 output_hash");
        assertEquals(64, rows.get(0).getOutputHash().length());
        assertEquals(rows.get(0).getInputRefText(), rows.get(1).getInputRefText());

        // 计划面自洽：plan_json → Plan → plan_json 字符串等值，拓扑序复现
        WAgentPlan.Plan plan = new WAgentPlan.Plan(
                com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PlanSource.EXPLORED,
                null, null, List.of(
                        new WAgentPlan.Step("s1_human_gate", StepType.HUMAN_GATE.value(), null, null,
                                0, List.of(), Map.of(), false, false, null),
                        toolStep("s2")));
        String json = WAgentPlan.PlanCodec.toJson(plan);
        WAgentPlan.Plan replayed = WAgentPlan.PlanCodec.fromJson(json);
        assertEquals(json, WAgentPlan.PlanCodec.toJson(replayed), "plan_json 往返必自洽（重放同串）");
        List<String> first = WAgentPlan.topoOrder(plan).stream().map(WAgentPlan.Step::stepKey).toList();
        List<String> second = WAgentPlan.topoOrder(replayed).stream().map(WAgentPlan.Step::stepKey).toList();
        assertEquals(first, second);
        assertTrue(first.get(0).startsWith("s1_human_gate"));
    }
}
