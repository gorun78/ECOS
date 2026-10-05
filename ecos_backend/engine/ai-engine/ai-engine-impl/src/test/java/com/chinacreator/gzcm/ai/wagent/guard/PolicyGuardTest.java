package com.chinacreator.gzcm.ai.wagent.guard;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.PolicyCheckpoint;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.PolicyEffect;
import com.chinacreator.gzcm.ai.wagent.guard.PolicyGuard.EvaluationContext;
import com.chinacreator.gzcm.ai.wagent.guard.PolicyGuard.EvaluateResponse;
import com.chinacreator.gzcm.ai.wagent.guard.PolicyGuard.PolicyTarget;
import com.chinacreator.gzcm.ai.wagent.guard.PolicyGuard.SecurityClient;
import com.chinacreator.gzcm.ai.wagent.guard.PolicyGuard.Verdict;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-22 Policy Guard 离线单测：五检查点（pre-plan / pre-step / post-step /
 * pre-commit / pre-output）逐一过闸；security 不可用时 level≥1 一律 fail-closed DENY（L0 只读透传）。
 */
class PolicyGuardTest {

    /** F10-22：五检查点顺序全被评估（安全客户端各被调用 1 次，共 5 次）。 */
    @Test
    void fiveCheckpointsAllInvoked() {
        PolicyGuard guard = new PolicyGuard();
        java.util.concurrent.atomic.AtomicInteger visits = new java.util.concurrent.atomic.AtomicInteger();
        SecurityClient security = recorder(visits);
        PolicyTarget target = new PolicyTarget("run-1", "q-1", "tool-1", 1, "scope-hash");

        Set<PolicyCheckpoint> seen = EnumSet.noneOf(PolicyCheckpoint.class);
        for (PolicyCheckpoint cp : PolicyCheckpoint.values()) {
            EvaluateResponse r = guard.evaluate(cp, target, 1, security, true);
            assertNotNull(r, cp + " 必须返回评估结果");
            seen.add(cp);
        }
        assertEquals(5, PolicyCheckpoint.values().length, "PolicyCheckpoint 必为 5 值");
        assertEquals(EnumSet.allOf(PolicyCheckpoint.class), seen, "五检查点必须逐一过闸");
        assertEquals(5, visits.get(), "安全客户端对五检查点各调用一次，共 5 次");
    }

    /** F10-22：security 不可用（available=false）且 level≥1 ⇒ fail-closed DENY；L0 只读可透传 ALLOW。 */
    @Test
    void securityUnavailableDeniesForLevelOneAndAbove() {
        PolicyGuard guard = new PolicyGuard();
        SecurityClient security = (ruleSet, ctx) -> Verdict.ALLOW; // available=false 时不应被调用

        assertEquals(PolicyEffect.DENY, guard.evaluate(
                PolicyCheckpoint.PRE_STEP, targetAt(1), 1, security, false).effect(),
                "L1 + security 不可用 ⇒ DENY（fail-closed）");
        assertEquals(PolicyEffect.DENY, guard.evaluate(
                PolicyCheckpoint.PRE_COMMIT, targetAt(2), 2, security, false).effect(),
                "L2 + security 不可用 ⇒ DENY");
        assertEquals(PolicyEffect.DENY, guard.evaluate(
                PolicyCheckpoint.PRE_STEP, targetAt(3), 3, security, false).effect(),
                "L3 + security 不可用 ⇒ DENY");

        // L0 只读：security 不可用可透传（文档：L0 只读可按白名单继续）。
        assertEquals(PolicyEffect.ALLOW, guard.evaluate(
                PolicyCheckpoint.PRE_STEP, targetAt(0), 0, security, false).effect(),
                "L0 只读 + security 不可用可透传 ALLOW");
    }

    /** F10-22：security 可用时映射裁决（ALLOW / REQUIRE_APPROVAL / DENY 各自回传对应 effect）。 */
    @Test
    void availableSecurityMapsVerdicts() {
        PolicyGuard guard = new PolicyGuard();
        assertEquals(PolicyEffect.ALLOW, guard.evaluate(
                PolicyCheckpoint.PRE_STEP, targetAt(1), 1, (r, c) -> Verdict.ALLOW, true).effect());
        assertEquals(PolicyEffect.REQUIRE_APPROVAL, guard.evaluate(
                PolicyCheckpoint.PRE_STEP, targetAt(1), 1, (r, c) -> Verdict.REQUIRE_APPROVAL, true).effect());
        EvaluateResponse deny = guard.evaluate(
                PolicyCheckpoint.PRE_STEP, targetAt(1), 1, (r, c) -> Verdict.DENY, true);
        assertEquals(PolicyEffect.DENY, deny.effect());
        assertEquals(PolicyGuard.DENY_REASON, deny.reason(), "DENY 消息固定不泄漏资源存在性");
    }

    private static PolicyTarget targetAt(int level) {
        return new PolicyTarget("run-1", "q-1", "tool-1", level, "scope-hash");
    }

    private static SecurityClient recorder(java.util.concurrent.atomic.AtomicInteger visits) {
        return (ruleSet, ctx) -> { visits.incrementAndGet(); return Verdict.ALLOW; };
    }
}
