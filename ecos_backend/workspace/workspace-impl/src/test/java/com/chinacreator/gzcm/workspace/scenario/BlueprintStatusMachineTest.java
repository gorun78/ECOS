package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.blueprint.BlueprintStatus;
import com.chinacreator.gzcm.workspace.blueprint.IllegalBlueprintTransitionException;
import com.chinacreator.gzcm.workspace.blueprint.ScenarioBlueprintStateMachine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 补充设计 v1.0 §16 <b>SUP-BP-01</b> 冻结测试：场景蓝图状态机（§6.3 唯一规则持有者
 * {@link ScenarioBlueprintStateMachine}）非法迁移拒绝验收。
 *
 * <p>主链 11 态单向（DRAFT → … → RELEASED → ACTIVE 起始冻结）+ 异常/回退态；
 * 非法迁移一律 {@link IllegalBlueprintTransitionException}（HTTP 409，状态不变）。</p>
 */
class BlueprintStatusMachineTest {

    private final ScenarioBlueprintStateMachine machine = new ScenarioBlueprintStateMachine();

    @Test
    @DisplayName("SUP-BP-01: DRAFT 直进 ACTIVE 必拒 (DRAFT_DIRECT_ACTIVE_FORBIDDEN, 409)")
    void draftDirectToActiveIsForbidden() {
        IllegalBlueprintTransitionException ex = assertThrows(IllegalBlueprintTransitionException.class,
                () -> machine.assertTransition(BlueprintStatus.DRAFT, BlueprintStatus.ACTIVE));
        assertEquals(409, ex.getHttpStatus());
        assertEquals("DRAFT_DIRECT_ACTIVE_FORBIDDEN", ex.getCode());
        assertTrue(ex.getMessage().contains("DRAFT"), "异常文案须指明 DRAFT 直进 ACTIVE 违例");
    }

    @Test
    @DisplayName("SUP-BP-01: 主链全链路 DRAFT -> COMPLETED 逐步推进全部放行")
    void fullLegalLifecycleChainSucceeds() {
        // §6.3 主链单向 11 态链（含经 APPROVED 到 RELEASED 的合法形态，§6.3 出边矩阵 APPROVED→RELEASED）
        machine.assertTransition(BlueprintStatus.DRAFT, BlueprintStatus.DISCOVERING);
        machine.assertTransition(BlueprintStatus.DISCOVERING, BlueprintStatus.GAP_ANALYSIS);
        machine.assertTransition(BlueprintStatus.GAP_ANALYSIS, BlueprintStatus.BLUEPRINT_READY);
        machine.assertTransition(BlueprintStatus.BLUEPRINT_READY, BlueprintStatus.VALIDATING);
        machine.assertTransition(BlueprintStatus.VALIDATING, BlueprintStatus.READY_FOR_APPROVAL);
        machine.assertTransition(BlueprintStatus.READY_FOR_APPROVAL, BlueprintStatus.APPROVED);
        machine.assertTransition(BlueprintStatus.APPROVED, BlueprintStatus.RELEASED);
        machine.assertTransition(BlueprintStatus.RELEASED, BlueprintStatus.ACTIVE);
        machine.assertTransition(BlueprintStatus.ACTIVE, BlueprintStatus.RUNNING);
        machine.assertTransition(BlueprintStatus.RUNNING, BlueprintStatus.COMPLETED);
    }

    @Test
    @DisplayName("SUP-BP-01: 主链跳跃推进(GAP_ANALYSIS 直跳 VALIDATING)拒且码为 ILLEGAL_TRANSITION")
    void skippingMainChainStepsIsIllegal() {
        IllegalBlueprintTransitionException ex = assertThrows(IllegalBlueprintTransitionException.class,
                () -> machine.assertTransition(BlueprintStatus.GAP_ANALYSIS, BlueprintStatus.VALIDATING));
        assertEquals(409, ex.getHttpStatus());
        assertEquals("ILLEGAL_TRANSITION", ex.getCode());
    }

    @Test
    @DisplayName("SUP-BP-01: 终态 COMPLETED / DEPRECATED 出边全无,任何迁移均拒 (TERMINAL_STATE)")
    void terminalStatesRefuseAllMoves() {
        IllegalBlueprintTransitionException completed = assertThrows(
                IllegalBlueprintTransitionException.class,
                () -> machine.assertTransition(BlueprintStatus.COMPLETED, BlueprintStatus.ACTIVE));
        assertEquals("TERMINAL_STATE", completed.getCode());

        IllegalBlueprintTransitionException deprecated = assertThrows(
                IllegalBlueprintTransitionException.class,
                () -> machine.assertTransition(BlueprintStatus.DEPRECATED, BlueprintStatus.DRAFT));
        assertEquals("TERMINAL_STATE", deprecated.getCode());

        assertTrue(ScenarioBlueprintStateMachine.isTerminal(BlueprintStatus.COMPLETED));
        assertTrue(ScenarioBlueprintStateMachine.isTerminal(BlueprintStatus.DEPRECATED));
        assertTrue(machine.allowedTransitions(BlueprintStatus.DEPRECATED).isEmpty());
    }

    @Test
    @DisplayName("SUP-BP-01: 幂等同态迁移为 no-op 放行(合法且不变态)")
    void sameStateTransitionIsIdempotentNoOp() {
        machine.assertTransition(BlueprintStatus.DRAFT, BlueprintStatus.DRAFT);
        machine.assertTransition(BlueprintStatus.RELEASED, BlueprintStatus.RELEASED);
    }

    @Test
    @DisplayName("SUP-BP-01: 发布不可变区 - RELEASED 仅 ACTIVE 或版本替代 DEPRECATED 出边; 非主链态非法出边拒")
    void releasedIsFrozenWithOnlyActiveOrDeprecationEdges() {
        // 发布区不可回退编辑
        assertThrows(IllegalBlueprintTransitionException.class,
                () -> machine.assertTransition(BlueprintStatus.RELEASED, BlueprintStatus.DRAFT));
        // 非主链源态的非法出边（RUNNING 无 VALIDATION_FAILED 出边）
        assertThrows(IllegalBlueprintTransitionException.class,
                () -> machine.assertTransition(BlueprintStatus.RUNNING, BlueprintStatus.VALIDATION_FAILED));
        // 合法出边：版本替代
        machine.assertTransition(BlueprintStatus.RELEASED, BlueprintStatus.DEPRECATED);
        // 运行谱线冻结：RELEASED 合法出边仅 ACTIVE / DEPRECATED
        assertEquals(2, machine.allowedTransitions(BlueprintStatus.RELEASED).size(),
                "RELEASED 合法出边仅 ACTIVE / DEPRECATED");
    }

    @Test
    @DisplayName("SUP-BP-01: 异常态回退边按矩阵放行(VALIDATION_FAILED -> VALIDATING 重跑;PAUSED -> RUNNING 恢复)")
    void exceptionStateReturnEdgesFollowMatrix() {
        machine.assertTransition(BlueprintStatus.VALIDATION_FAILED, BlueprintStatus.VALIDATING);
        machine.assertTransition(BlueprintStatus.PAUSED, BlueprintStatus.RUNNING);
        machine.assertTransition(BlueprintStatus.BLOCKED, BlueprintStatus.BLUEPRINT_READY);
        machine.assertTransition(BlueprintStatus.NEEDS_CLARIFICATION, BlueprintStatus.GAP_ANALYSIS);
        // PAUSED 仅可从 ACTIVE/RUNNING 进入：DRAFT 直入 PAUSED 非法
        assertThrows(IllegalBlueprintTransitionException.class,
                () -> machine.assertTransition(BlueprintStatus.DRAFT, BlueprintStatus.PAUSED));
    }
}
