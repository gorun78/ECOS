package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.exception.IllegalScenarioTransitionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 状态机迁移守卫单测（详细设计-07 F07-07 / C-2 / C153）。
 *
 * <p>逐条覆盖 C-2 迁移表：DRAFT→ACTIVE 合法；DRAFT→SUSPENDED/COMPLETED 非法（409 ILLEGAL_TRANSITION）；
 * SUSPENDED→ACTIVE 合法；ACTIVE→SUSPENDED/COMPLETED 合法（→COMPLETED 需正式运行，否则 NO_FORMAL_RUN）；
 * COMPLETED 终态不可回退（409 TERMINAL_STATE）。服务端 {@code allowedTransitions} 为按钮态唯一来源。</p>
 */
class ScenarioStatusMachineTest {

    private final ScenarioStatusMachine machine = new ScenarioStatusMachine();

    @Test
    void draftToActiveIsLegal() {
        assertDoesNotThrow(() -> machine.assertTransition("DRAFT", "ACTIVE", false));
    }

    @Test
    void draftToSuspendedIsIllegal() {
        IllegalScenarioTransitionException ex = assertThrows(IllegalScenarioTransitionException.class,
                () -> machine.assertTransition("DRAFT", "SUSPENDED", false));
        assertEquals(IllegalScenarioTransitionException.CODE_ILLEGAL, ex.getErrorCodeString());
    }

    @Test
    void draftToCompletedIsIllegal() {
        assertThrows(IllegalScenarioTransitionException.class,
                () -> machine.assertTransition("DRAFT", "COMPLETED", true));
    }

    @Test
    void suspendedToActiveIsLegal() {
        assertDoesNotThrow(() -> machine.assertTransition("SUSPENDED", "ACTIVE", false));
    }

    @Test
    void activeToSuspendedIsLegal() {
        assertDoesNotThrow(() -> machine.assertTransition("ACTIVE", "SUSPENDED", false));
    }

    @Test
    void activeToCompletedRequiresFormalRun() {
        IllegalScenarioTransitionException ex = assertThrows(IllegalScenarioTransitionException.class,
                () -> machine.assertTransition("ACTIVE", "COMPLETED", false));
        assertEquals(IllegalScenarioTransitionException.CODE_NO_FORMAL_RUN, ex.getErrorCodeString());
        // 有正式运行则放行
        assertDoesNotThrow(() -> machine.assertTransition("ACTIVE", "COMPLETED", true));
    }

    @Test
    void completedIsTerminalState() {
        IllegalScenarioTransitionException ex = assertThrows(IllegalScenarioTransitionException.class,
                () -> machine.assertTransition("COMPLETED", "ACTIVE", true));
        assertEquals(IllegalScenarioTransitionException.CODE_TERMINAL, ex.getErrorCodeString());
    }

    @Test
    void allowedTransitionsIsServerAuthoritative() {
        assertEquals(java.util.List.of("ACTIVE"), machine.allowedTransitions("DRAFT"));
        assertTrue(machine.allowedTransitions("COMPLETED").isEmpty(), "终态无出边");
        assertEquals(2, machine.allowedTransitions("ACTIVE").size());
    }
}
