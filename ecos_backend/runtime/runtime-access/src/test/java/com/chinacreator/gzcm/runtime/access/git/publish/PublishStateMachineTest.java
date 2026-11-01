package com.chinacreator.gzcm.runtime.access.git.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * §2.9.6① 发布 / 双写状态机的可执行判据（验收 §3.1.4 DF-1 / DF-2 / DF-3；#147D）。
 *
 * <p>本类不验证「代码看起来像状态机」，只验证四件事能被实测：</p>
 * <ol>
 *   <li><b>16 态 = 主链 9 + 旁支 7</b>，且主链单向不跳跃；</li>
 *   <li><b>失败产生可识别状态</b>：三腿任意组合都落得到枚举止（禁「停在原态 + warn」）；</li>
 *   <li><b>禁伪成功</b>：{@code SUCCEEDED} 只有对账一致可达，腿成功但缺证据指针必须
 *       {@code PARTIAL_FAILURE}；</li>
 *   <li><b>回滚只走「已知历史版本」</b>：无 ref 即不可执行，禁反向 SQL 形态。</li>
 * </ol>
 */
class PublishStateMachineTest {

    private static DualWriteOutcome legs(LegOutcome git, LegOutcome db, LegOutcome audit) {
        return DualWriteOutcome.builder(git, db, audit)
                .gitRef(git.isSuccess() ? "abc123" : null)
                .versionRef(db.isSuccess() ? "v1" : null)
                .build();
    }

    @Test
    @DisplayName("DF-1：16 态齐全，主链 9 态顺序与 §2.9.6① 逐字同构")
    void stateVocabularyIsExactly16() {
        assertEquals(16, PublishState.values().length);
        List<PublishState> mainChain = Arrays.stream(PublishState.values())
                .filter(PublishState::isMainChain)
                .toList();
        assertEquals(List.of(
                PublishState.DRAFT, PublishState.VALIDATING, PublishState.PENDING_APPROVAL,
                PublishState.APPROVED, PublishState.READY_TO_APPLY, PublishState.APPLYING,
                PublishState.APPLIED, PublishState.VERIFYING, PublishState.SUCCEEDED),
                mainChain);
        assertEquals(7, PublishState.values().length - mainChain.size());
    }

    @Test
    @DisplayName("DF-1：转换表 16 枚起点全覆盖，主链前进边恰一枚且不跳跃")
    void transitionTableIsCompleteAndForwardOnly() {
        assertEquals(16, PublishStateMachine.transitionTable().size());
        for (PublishState s : PublishState.values()) {
            assertNotNull(PublishStateMachine.allowedTransitions(s), "缺出边集：" + s);
        }
        for (PublishState s : PublishState.values()) {
            if (!s.isMainChain() || s == PublishState.SUCCEEDED) {
                continue;
            }
            PublishState next = PublishStateMachine.mainChainNext(s);
            assertNotNull(next, "主链断裂：" + s);
            assertEquals(s.mainChainOrder() + 1, next.mainChainOrder());
            assertTrue(PublishStateMachine.canTransition(s, next));
        }
        assertNull(PublishStateMachine.mainChainNext(PublishState.SUCCEEDED));
        assertFalse(PublishStateMachine.canTransition(PublishState.DRAFT, PublishState.APPROVED));
        assertFalse(PublishStateMachine.canTransition(PublishState.APPLYING, PublishState.SUCCEEDED));
    }

    @Test
    @DisplayName("DF-1：唯一终态为 ROLLED_BACK；非法跳步与终态推进分别出码")
    void terminalAndIllegalTransitionsAreDistinguishable() {
        assertEquals(Set.of(PublishState.ROLLED_BACK), PublishStateMachine.terminalStates());
        assertTrue(PublishStateMachine.isTerminal(PublishState.ROLLED_BACK));

        IllegalPublishTransitionException illegal = assertThrows(
                IllegalPublishTransitionException.class,
                () -> PublishStateMachine.assertTransition(PublishState.DRAFT, PublishState.APPLIED));
        assertEquals(IllegalPublishTransitionException.CODE_ILLEGAL_TRANSITION, illegal.errorCode());

        IllegalPublishTransitionException terminal = assertThrows(
                IllegalPublishTransitionException.class,
                () -> PublishStateMachine.assertTransition(PublishState.ROLLED_BACK, PublishState.DRAFT));
        assertEquals(IllegalPublishTransitionException.CODE_TERMINAL_STATE, terminal.errorCode());
    }

    @Test
    @DisplayName("DF-2：Git 成功但 DB 失败 / 任一腿未知 → PARTIAL_FAILURE（禁报完全成功）")
    void partialFailureIsReachableFromEveryBrokenCombination() {
        assertEquals(PublishState.PARTIAL_FAILURE,
                PublishStateMachine.deriveState(legs(LegOutcome.SUCCEEDED, LegOutcome.FAILED,
                        LegOutcome.NOT_APPLICABLE)));
        assertEquals(PublishState.PARTIAL_FAILURE,
                PublishStateMachine.deriveState(legs(LegOutcome.SUCCEEDED, LegOutcome.UNKNOWN,
                        LegOutcome.NOT_APPLICABLE)));
        assertEquals(PublishState.PARTIAL_FAILURE,
                PublishStateMachine.deriveState(legs(LegOutcome.FAILED, LegOutcome.SUCCEEDED,
                        LegOutcome.NOT_APPLICABLE)));
        // 腿自称成功但没有正向证据指针 ＝ 伪成功
        DualWriteOutcome noGitRef = DualWriteOutcome.builder(
                        LegOutcome.SUCCEEDED, LegOutcome.SUCCEEDED, LegOutcome.NOT_APPLICABLE)
                .versionRef("v1").build();
        assertEquals(PublishState.PARTIAL_FAILURE, PublishStateMachine.deriveState(noGitRef));
        DualWriteOutcome noVersionRef = DualWriteOutcome.builder(
                        LegOutcome.SUCCEEDED, LegOutcome.SUCCEEDED, LegOutcome.NOT_APPLICABLE)
                .gitRef("abc123").build();
        assertEquals(PublishState.PARTIAL_FAILURE, PublishStateMachine.deriveState(noVersionRef));
    }

    @Test
    @DisplayName("DF-1/DF-2：全腿失败 → FAILED；三腿成套 → APPLIED（成功仍须对账）")
    void failureAndAppliedBoundaries() {
        assertEquals(PublishState.FAILED,
                PublishStateMachine.deriveState(legs(LegOutcome.FAILED, LegOutcome.FAILED,
                        LegOutcome.NOT_APPLICABLE)));
        assertEquals(PublishState.FAILED,
                PublishStateMachine.deriveState(legs(LegOutcome.UNKNOWN, LegOutcome.FAILED,
                        LegOutcome.NOT_APPLICABLE)));
        PublishState applied = PublishStateMachine.deriveState(
                legs(LegOutcome.SUCCEEDED, LegOutcome.SUCCEEDED, LegOutcome.NOT_APPLICABLE));
        assertEquals(PublishState.APPLIED, applied);
        assertFalse(applied.mayReportFullSuccess());
    }

    @Test
    @DisplayName("§2.9.6②：关键安全变更 + 审计腿未确证 → fail-closed FAILED")
    void securityCriticalWithoutAuditFailsClosed() {
        DualWriteOutcome outcome = DualWriteOutcome.builder(
                        LegOutcome.SUCCEEDED, LegOutcome.SUCCEEDED, LegOutcome.NOT_APPLICABLE)
                .gitRef("abc123").versionRef("v1").securityCritical(true).build();
        assertEquals(PublishState.FAILED, PublishStateMachine.deriveState(outcome));
        DualWriteOutcome audited = DualWriteOutcome.builder(
                        LegOutcome.SUCCEEDED, LegOutcome.SUCCEEDED, LegOutcome.SUCCEEDED)
                .gitRef("abc123").versionRef("v1").securityCritical(true).build();
        assertEquals(PublishState.APPLIED, PublishStateMachine.deriveState(audited));
    }

    @Test
    @DisplayName("§2.9.6③：八类对账判定映射到状态；UNVERIFIABLE 停在 VERIFYING 不落成功")
    void verificationMappingCoversAllEightVerdicts() {
        assertEquals(PublishState.SUCCEEDED, PublishStateMachine.stateAfterVerification(
                PublishState.VERIFYING, ReconciliationVerdict.CONSISTENT));
        assertEquals(PublishState.DRIFTED, PublishStateMachine.stateAfterVerification(
                PublishState.VERIFYING, ReconciliationVerdict.CONTENT_DRIFT));
        assertEquals(PublishState.DRIFTED, PublishStateMachine.stateAfterVerification(
                PublishState.VERIFYING, ReconciliationVerdict.VERSION_REF_MISMATCH));
        assertEquals(PublishState.DRIFTED, PublishStateMachine.stateAfterVerification(
                PublishState.VERIFYING, ReconciliationVerdict.DEPENDENCY_MISMATCH));
        assertEquals(PublishState.PARTIAL_FAILURE, PublishStateMachine.stateAfterVerification(
                PublishState.VERIFYING, ReconciliationVerdict.MISSING_OBJECT));
        assertEquals(PublishState.PARTIAL_FAILURE, PublishStateMachine.stateAfterVerification(
                PublishState.VERIFYING, ReconciliationVerdict.EXTRA_OBJECT));
        assertEquals(PublishState.PARTIAL_FAILURE, PublishStateMachine.stateAfterVerification(
                PublishState.VERIFYING, ReconciliationVerdict.PARTIAL_PUBLISH));
        assertEquals(PublishState.VERIFYING, PublishStateMachine.stateAfterVerification(
                PublishState.VERIFYING, ReconciliationVerdict.UNVERIFIABLE));
        assertEquals(8, ReconciliationVerdict.values().length);
    }

    @Test
    @DisplayName("DF-3：回滚计划只认「已知历史版本」；缺 ref/version_no 即不可执行")
    void rollbackPlanIsTypedAndRefRequired() {
        RollbackPlan plan = PublishStateMachine.planRollback(
                PublishState.APPLIED, "ontology-model", "abc123", "v3");
        assertTrue(plan.isExecutable());
        assertEquals(RollbackPlan.MECHANISM_REPUBLISH_KNOWN_VERSION, plan.mechanism());
        assertEquals("abc123", plan.gitRef());
        assertEquals("v3", plan.versionNo());
        PublishStateMachine.assertRollbackExecutable(plan);

        RollbackPlan unavailable = PublishStateMachine.planRollback(
                PublishState.APPLIED, "ontology-model", null, "v3");
        assertFalse(unavailable.isExecutable());
        assertNull(unavailable.mechanism());
        IllegalPublishTransitionException ex = assertThrows(IllegalPublishTransitionException.class,
                () -> PublishStateMachine.assertRollbackExecutable(unavailable));
        assertEquals(IllegalPublishTransitionException.CODE_ROLLBACK_REF_REQUIRED, ex.errorCode());

        // DRAFT 不在可回滚集合内：状态面直接拒绝
        IllegalPublishTransitionException notEligible = assertThrows(
                IllegalPublishTransitionException.class,
                () -> PublishStateMachine.planRollback(PublishState.DRAFT, "r", "abc", "v1"));
        assertEquals(IllegalPublishTransitionException.CODE_ILLEGAL_TRANSITION, notEligible.errorCode());

        assertEquals(Set.of(
                        PublishState.APPLYING, PublishState.APPLIED, PublishState.VERIFYING,
                        PublishState.SUCCEEDED, PublishState.PARTIAL_FAILURE, PublishState.FAILED,
                        PublishState.DRIFTED),
                PublishStateMachine.rollbackEligibleStates());
    }

    @Test
    @DisplayName("三腿记录不可缺省：null / 半空对象都拒绝构造")
    void outcomeRequiresAllThreeLegs() {
        assertThrows(IllegalArgumentException.class,
                () -> PublishStateMachine.deriveState(null));
        assertThrows(IllegalArgumentException.class,
                () -> DualWriteOutcome.builder(LegOutcome.SUCCEEDED, null, LegOutcome.NOT_APPLICABLE));
        assertThrows(IllegalArgumentException.class,
                () -> DualWriteOutcome.builder(null, LegOutcome.SUCCEEDED, LegOutcome.NOT_APPLICABLE));
    }
}
