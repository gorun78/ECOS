package com.chinacreator.gzcm.ai.wagent.automation;

import com.chinacreator.gzcm.ai.wagent.orchestrator.AutomationCeilingGuard;
import com.chinacreator.gzcm.ai.wagent.orchestrator.AutomationCeilingGuard.Effective;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-21 自动化天花板离线单测：
 * 有效等级 = min(工具最低 / run 天花板 / 租户顶)；L3 无自动路径（requiresApprovalToken=true）。
 */
class AutomationLevelTest {

    /** F10-21：有效等级 = min(工具最低 L1 / run 天花板 L2 / 租户顶 L1) = L1。 */
    @Test
    void effectiveLevelIsMinOfThreeCaps() {
        // toolMin=L1(1), runCeiling=L2(2), tenantMax=L1(1) ⇒ min = 1。
        int effective = AutomationCeilingGuard.effectiveLevel(1, 2, 1);
        assertEquals(1, effective, "三重上限取 min 必为 L1");

        Effective eff = AutomationCeilingGuard.resolve(1, 2, 1);
        assertEquals(1, eff.level());
        assertFalse(eff.requiresApprovalToken(), "有效等级 L1 < L3 ⇒ 不强制审批 token");
    }

    /** F10-21：L3 永不被配置自动触发——三重上限只要有任意一项 < L3，有效等级即掉到 L3 之下。 */
    @Test
    void l3NeverAutoTriggeredByConfig() {
        // 工具最低 L3(3) 但 run 天花板 L2(2) / 租户顶 L2(2) ⇒ 有效等级被压到 L2，无自动路径。
        Effective eff = AutomationCeilingGuard.resolve(3, 2, 2);
        assertEquals(2, eff.level(), "L3 步在 L2 天花板/租户顶下必被压到 L2（无自动路径）");
        assertFalse(eff.requiresApprovalToken(),
                "有效等级 L2 < L3 ⇒ 该步不得走 L3 自动通道（L3 不可被配置触发）");

        // 三重均达 L3 ⇒ 有效 L3 ⇒ 强制审批 token（无自动路径的唯一出口是人批）。
        Effective l3 = AutomationCeilingGuard.resolve(3, 3, 3);
        assertEquals(3, l3.level());
        assertTrue(l3.requiresApprovalToken(),
                "有效等级 L3 ⇒ 强制 approval_token 非空，禁自动触发");
    }
}
