package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PlanSource;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.RunStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.StepType;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentPlan.Plan;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentPlan.Step;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 探索计划器（详细设计-10 F10-13，冻结验收名）。
 *
 * <p>EXPLORED Plan 未过 human_gate 不得进 executing——硬约束双闸独立成立：
 * 结构面（生成的计划拓扑首步必为 human_gate，重排后即被 hasHumanGateBeforeTools 识破）、
 * 执行面（RunStateMachine.canTransition 对 EXPLORED 首入且未清闸直接判非法）。</p>
 */
class ExplorationPlannerTest {

    @Test
    @DisplayName("F10-13：EXPLORED 计划拓扑首步为 human_gate 且记缺口；状态机对未清闸的 EXPLORED 首入 executing 硬拒")
    void planRequiresHumanGateBeforeExecution() {
        WAgentPlaybookGapEventService gapEventService = mock(WAgentPlaybookGapEventService.class);
        ExplorationPlanner planner = new ExplorationPlanner(gapEventService);

        Plan plan = planner.plan("t1", "r1", "m1", "FORECAST",
                Map.of("metric", "project_profit"), "u1");

        // 结构面：EXPLORED 来源、拓扑首步 human_gate、尾步 aggregate、确定性工具序列
        assertEquals(PlanSource.EXPLORED, plan.source());
        List<Step> ordered = WAgentPlan.topoOrder(plan);
        assertFalse(ordered.isEmpty(), "生成的计划拓扑必须可序化");
        assertTrue(ordered.get(0).isHumanGate(), "F10-13：EXPLORED 计划首步必须是 human_gate");
        assertEquals(ExplorationPlanner.HUMAN_GATE_STEP_KEY, ordered.get(0).stepKey());
        assertEquals(ExplorationPlanner.AGGREGATE_STEP_KEY, ordered.get(ordered.size() - 1).stepKey());
        assertTrue(PlanValidator.hasHumanGateBeforeTools(plan));
        assertEquals(List.of("freeze_data_snapshot", "run_profit_forecast"),
                ExplorationPlanner.toolSequenceFor("FORECAST"));

        // 缺口登记随计划生成同步发生（missionId 作聚合定位）
        verify(gapEventService).recordGap("t1", "r1", "m1", "FORECAST",
                plan.steps().get(1).inputSlots().keySet(),
                ExplorationPlanner.toolSequenceFor("FORECAST"), "u1");

        // 重排防御：同样步骤把闸挪到尾部 ⇒ 结构断言立刻识破（非提示词约束）
        Plan gateAtTail = new Plan(PlanSource.EXPLORED, null, null, List.of(
                new Step("s2_tool", StepType.TOOL.value(), "freeze_data_snapshot", "1",
                        0, List.of(), Map.of(), false, false, null),
                new Step(ExplorationPlanner.HUMAN_GATE_STEP_KEY, StepType.HUMAN_GATE.value(),
                        null, null, 0, List.of("s2_tool"), Map.of(), false, false, null)));
        assertFalse(PlanValidator.hasHumanGateBeforeTools(gateAtTail));
        assertFalse(gateAtTail.steps().get(0).isHumanGate());
        // PLAYBOOK 计划不受该结构断言约束（F10-13 只管 EXPLORED 面）
        assertTrue(PlanValidator.hasHumanGateBeforeTools(
                new Plan(PlanSource.PLAYBOOK, "git/ref", "v1", gateAtTail.steps())));

        // 执行面硬约束（写在状态机，独立于结构面）：未清闸 ⇒ PLAN_VALIDATED→EXECUTING 非法
        WAgentRunStateMachine sm = new WAgentRunStateMachine(mock(WAgentRunDao.class));
        assertFalse(sm.canTransition(RunStatus.PLAN_VALIDATED, RunStatus.EXECUTING,
                PlanSource.EXPLORED, false));
        assertTrue(sm.canTransition(RunStatus.PLAN_VALIDATED, RunStatus.EXECUTING,
                PlanSource.EXPLORED, true));
        assertTrue(sm.canTransition(RunStatus.PLAN_VALIDATED, RunStatus.EXECUTING,
                PlanSource.PLAYBOOK, false));
    }
}
