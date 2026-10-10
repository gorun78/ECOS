package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PlanSource;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.RunStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Run 状态机（详细设计-10 F10-11 §4.2 迁移表 + F10-13 硬闸，冻结验收名）。
 *
 * <p>非法迁移一律 {@code E-WA-STATE 409}；终态出边恒空；
 * EXPLORED 计划首入 executing 未清闸同样判非法（写进状态机、不写在提示词里）。</p>
 */
class RunStateMachineTest {

    private static WAgentRunDO runIn(RunStatus status, PlanSource planSource) {
        WAgentRunDO run = new WAgentRunDO();
        run.setId("r1");
        run.setStatus(status.value());
        run.setPlanSource(planSource == null ? null : planSource.value());
        return run;
    }

    @Test
    @DisplayName("F10-11/F10-13：表外迁移与 EXPLORED 未清闸迁移均拒绝（E-WA-STATE）；合法迁移只经 requireTransition 落库")
    void illegalTransitionRejected() {
        WAgentRunDao runDao = mock(WAgentRunDao.class);
        WAgentRunStateMachine sm = new WAgentRunStateMachine(runDao);

        // 迁移表面：三类表外迁移
        assertFalse(sm.isLegalTransition(RunStatus.CREATED, RunStatus.EXECUTING));
        assertFalse(sm.isLegalTransition(RunStatus.COMPLETED, RunStatus.EXECUTING));
        assertFalse(sm.isLegalTransition(RunStatus.AWAITING_INPUT, RunStatus.PLANNING));
        assertTrue(sm.isLegalTransition(RunStatus.CREATED, RunStatus.PLANNING));

        // 终态出边恒空（五终态全体积）
        for (RunStatus terminal : RunStatus.values()) {
            if (terminal.isTerminal()) {
                assertEquals(0, sm.allowedOps(terminal).size(), "终态禁再迁移: " + terminal);
            }
        }

        // requireTransition 唯一写入口：completed → executing ⇒ E-WA-STATE
        WAgentOrchestrationException e1 = assertThrows(WAgentOrchestrationException.class,
                () -> sm.requireTransition(runIn(RunStatus.COMPLETED, null), RunStatus.EXECUTING, true, "u1"));
        assertEquals(WAgentErrorCode.E_WA_STATE, e1.errorCode());

        // F10-13 硬闸：EXPLORED 首入 executing 未清闸 ⇒ 非法；清闸后放行；再入不受闸
        assertFalse(sm.canTransition(RunStatus.PLAN_VALIDATED, RunStatus.EXECUTING, PlanSource.EXPLORED, false));
        assertTrue(sm.canTransition(RunStatus.PLAN_VALIDATED, RunStatus.EXECUTING, PlanSource.EXPLORED, true));
        assertTrue(sm.canTransition(RunStatus.PLAN_VALIDATED, RunStatus.EXECUTING, PlanSource.PLAYBOOK, false));
        assertTrue(sm.canTransition(RunStatus.AWAITING_APPROVAL, RunStatus.EXECUTING, PlanSource.EXPLORED, false));
        WAgentOrchestrationException e2 = assertThrows(WAgentOrchestrationException.class,
                () -> sm.requireTransition(runIn(RunStatus.PLAN_VALIDATED, PlanSource.EXPLORED),
                        RunStatus.EXECUTING, false, "u1"));
        assertEquals(WAgentErrorCode.E_WA_STATE, e2.errorCode());

        // 合法迁移：created → planning 经 DAO 唯一写面落库
        WAgentRunDO run = runIn(RunStatus.CREATED, null);
        sm.requireTransition(run, RunStatus.PLANNING, true, "u1");
        assertEquals(RunStatus.PLANNING.value(), run.getStatus());
        ArgumentCaptor<String> statusCap = ArgumentCaptor.forClass(String.class);
        verify(runDao).updateStatus(org.mockito.ArgumentMatchers.eq("r1"), statusCap.capture(),
                org.mockito.ArgumentMatchers.eq("u1"));
        assertEquals(RunStatus.PLANNING.value(), statusCap.getValue());
    }
}
