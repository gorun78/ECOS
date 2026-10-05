package com.chinacreator.gzcm.ai.wagent.runstate;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.RunStatus;
import com.chinacreator.gzcm.ai.wagent.orchestrator.WAgentRunStateMachine;
import com.chinacreator.gzcm.ai.wagent.orchestrator.WAgentRunStateMachine.E_STATE;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-11 Run 状态机离线单测：非法迁移（PLANNING→COMPLETED）恒被拒（E-WA-STATE），
 * 合法链 PLANNING→PLAN_VALIDATED→EXECUTING→COMPLETED 全程放行。
 */
class RunStateMachineTest {

    /** F10-11：非法迁移动作必须被拒（canTransition=false 且 ensureTransition 抛 E_STATE）。 */
    @Test
    void illegalTransitionRejected() {
        // PLANNING 出边：AWAITING_INPUT / AWAITING_APPROVAL / PLAN_VALIDATED / FAILED / CANCELLED
        // —— COMPLETED 不在出边内，非法。
        assertFalse(WAgentRunStateMachine.canTransition(RunStatus.PLANNING, RunStatus.COMPLETED),
                "PLANNING -> COMPLETED 是非法迁移，canTransition 必须 false");
        IllegalStateException ex = assertThrows(E_STATE.class,
                () -> WAgentRunStateMachine.ensureTransition(RunStatus.PLANNING, RunStatus.COMPLETED));
        assertTrue(ex.getMessage().contains("E-WA-STATE"), "异常消息须带 E-WA-STATE 短码，实际：" + ex.getMessage());

        // 终态无出边：COMPLETED → 任何态均非法（补一条独立证伪）。
        assertFalse(WAgentRunStateMachine.canTransition(RunStatus.COMPLETED, RunStatus.EXECUTING));
        assertThrows(E_STATE.class,
                () -> WAgentRunStateMachine.ensureTransition(RunStatus.COMPLETED, RunStatus.EXECUTING));
    }

    /** F10-11：合法链全程放行，ensureTransition 回传目标态。 */
    @Test
    void legalChainCompletes() {
        assertTrue(WAgentRunStateMachine.canTransition(RunStatus.PLANNING, RunStatus.PLAN_VALIDATED));
        assertTrue(WAgentRunStateMachine.canTransition(RunStatus.PLAN_VALIDATED, RunStatus.EXECUTING));
        assertTrue(WAgentRunStateMachine.canTransition(RunStatus.EXECUTING, RunStatus.COMPLETED));

        assertEquals(RunStatus.PLAN_VALIDATED,
                WAgentRunStateMachine.ensureTransition(RunStatus.PLANNING, RunStatus.PLAN_VALIDATED));
        assertEquals(RunStatus.EXECUTING,
                WAgentRunStateMachine.ensureTransition(RunStatus.PLAN_VALIDATED, RunStatus.EXECUTING));
        assertEquals(RunStatus.COMPLETED,
                WAgentRunStateMachine.ensureTransition(RunStatus.EXECUTING, RunStatus.COMPLETED));
    }
}
