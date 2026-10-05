package com.chinacreator.gzcm.ai.wagent.orchestration;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.StepType;
import com.chinacreator.gzcm.ai.wagent.orchestrator.ExplorationGate;
import com.chinacreator.gzcm.ai.wagent.orchestrator.PlanContext;
import com.chinacreator.gzcm.ai.wagent.orchestrator.PlanContext.Step;
import com.chinacreator.gzcm.ai.wagent.orchestrator.WAgentRunStateMachine.E_STATE;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-12 探索 Plan 硬约束离线单测：source=EXPLORED 必须先过 HUMAN_GATE 才能进 EXECUTING，
 * 无 gate 或 gate 在可执行步之后 ⇒ E-WA-STATE；source=PLAYBOOK 恒放行。
 */
class ExplorationGateTest {

    private static PlanContext plan(String source, List<Step> steps) {
        return new PlanContext("q-1", "run-1",
                source == null ? null : com.chinacreator.gzcm.ai.wagent.WAgentEnums.PlanSource.valueOf(source),
                steps, 0);
    }

    /** F10-12：EXPLORED plan 无 HUMAN_GATE（或 gate 在可执行步之后）⇒ 抛 E-WA-STATE。 */
    @Test
    void planRequiresHumanGateBeforeExecution() {
        // 直接以 TOOL 开局、无 gate ⇒ 违规。
        PlanContext noGate = plan("EXPLORED", List.of(
                new Step("s1", StepType.TOOL, "query_project_profit", 1, Map.of())));
        IllegalStateException ex = assertThrows(E_STATE.class,
                () -> ExplorationGate.isSafeToEnterExecuting(noGate));
        assertTrue(ex.getMessage().contains("E-WA-STATE"), "违规必须抛 E-WA-STATE，实际：" + ex.getMessage());

        // gate 排在可执行步之后（gate 太迟）⇒ 同样违规。
        PlanContext lateGate = plan("EXPLORED", List.of(
                new Step("s1", StepType.TOOL, "query_project_profit", 1, Map.of()),
                new Step("s2", StepType.HUMAN_GATE, null, 0, Map.of())));
        assertThrows(E_STATE.class,
                () -> ExplorationGate.isSafeToEnterExecuting(lateGate),
                "HUMAN_GATE 必须位于首个可执行步之前");
    }

    /** F10-12：EXPLORED + 首步 HUMAN_GATE ⇒ 放行；PLAYBOOK ⇒ 恒放行（无 gate 约束）。 */
    @Test
    void playbookSourceAllowedDirectly() {
        // EXPLORED 但 gate 在前 ⇒ 安全。
        PlanContext gateFirst = plan("EXPLORED", List.of(
                new Step("s0", StepType.HUMAN_GATE, null, 0, Map.of()),
                new Step("s1", StepType.TOOL, "query_project_profit", 1, Map.of())));
        assertTrue(ExplorationGate.isSafeToEnterExecuting(gateFirst),
                "EXPLORED 且 HUMAN_GATE 在前必须放行");

        // PLAYBOOK 无 gate 直接执行 ⇒ 放行（模板冻结可信）。
        PlanContext playbook = plan("PLAYBOOK", List.of(
                new Step("s1", StepType.TOOL, "query_project_profit", 1, Map.of())));
        assertTrue(ExplorationGate.isSafeToEnterExecuting(playbook),
                "PLAYBOOK 无 gate 约束必须放行");
    }
}
