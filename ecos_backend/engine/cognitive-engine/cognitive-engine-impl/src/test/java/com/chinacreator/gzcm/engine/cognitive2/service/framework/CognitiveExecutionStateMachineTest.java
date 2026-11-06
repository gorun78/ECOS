package com.chinacreator.gzcm.engine.cognitive2.service.framework;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CognitiveExecutionStateMachineTest {

    @Test
    void happyPathIsLegal() {
        assertTrue(CognitiveExecutionStateMachine.isLegal(
                CognitiveExecutionStateMachine.Status.CREATED, CognitiveExecutionStateMachine.Status.PLANNED));
        assertTrue(CognitiveExecutionStateMachine.isLegal(
                CognitiveExecutionStateMachine.Status.PLANNED, CognitiveExecutionStateMachine.Status.VALIDATING));
        assertTrue(CognitiveExecutionStateMachine.isLegal(
                CognitiveExecutionStateMachine.Status.VALIDATING, CognitiveExecutionStateMachine.Status.RUNNING));
        assertTrue(CognitiveExecutionStateMachine.isLegal(
                CognitiveExecutionStateMachine.Status.RUNNING, CognitiveExecutionStateMachine.Status.VERIFYING));
        assertTrue(CognitiveExecutionStateMachine.isLegal(
                CognitiveExecutionStateMachine.Status.VERIFYING, CognitiveExecutionStateMachine.Status.SUCCEEDED));
    }

    @Test
    void illegalJumpAndTerminalTransitionsAreRejected() {
        assertFalse(CognitiveExecutionStateMachine.isLegal(
                CognitiveExecutionStateMachine.Status.CREATED, CognitiveExecutionStateMachine.Status.RUNNING));
        assertFalse(CognitiveExecutionStateMachine.isLegal(
                CognitiveExecutionStateMachine.Status.CREATED, CognitiveExecutionStateMachine.Status.SUCCEEDED));
        assertFalse(CognitiveExecutionStateMachine.isLegal(
                CognitiveExecutionStateMachine.Status.SUCCEEDED, CognitiveExecutionStateMachine.Status.RUNNING));
        assertFalse(CognitiveExecutionStateMachine.isLegal(
                CognitiveExecutionStateMachine.Status.CANCELLED, CognitiveExecutionStateMachine.Status.PLANNED));
    }

    @Test
    void successRequiresVerifyingAndBothValidations() {
        assertFalse(CognitiveExecutionStateMachine.maySucceed(
                CognitiveExecutionStateMachine.Status.RUNNING, true, true));
        assertFalse(CognitiveExecutionStateMachine.maySucceed(
                CognitiveExecutionStateMachine.Status.VERIFYING, true, false));
        assertFalse(CognitiveExecutionStateMachine.maySucceed(
                CognitiveExecutionStateMachine.Status.VERIFYING, false, true));
        assertTrue(CognitiveExecutionStateMachine.maySucceed(
                CognitiveExecutionStateMachine.Status.VERIFYING, true, true));
    }

    @Test
    void terminalStatesAreMarked() {
        for (CognitiveExecutionStateMachine.Status s : CognitiveExecutionStateMachine.Status.values()) {
            if (s == CognitiveExecutionStateMachine.Status.SUCCEEDED
                    || s == CognitiveExecutionStateMachine.Status.FAILED
                    || s == CognitiveExecutionStateMachine.Status.CANCELLED
                    || s == CognitiveExecutionStateMachine.Status.BUDGET_EXCEEDED
                    || s == CognitiveExecutionStateMachine.Status.NEEDS_INPUT
                    || s == CognitiveExecutionStateMachine.Status.NEEDS_HUMAN_REVIEW
                    || s == CognitiveExecutionStateMachine.Status.REJECTED
                    || s == CognitiveExecutionStateMachine.Status.INVALID_OUTPUT) {
                assertTrue(s.terminal(), s.name());
            } else {
                assertFalse(s.terminal(), s.name());
            }
        }
    }
}
