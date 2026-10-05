package com.chinacreator.gzcm.ai.wagent.orchestration;

import com.chinacreator.gzcm.ai.wagent.orchestrator.AutomationCeilingGuard;
import com.chinacreator.gzcm.ai.wagent.orchestrator.SubRun;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-14/F10-21 子 Run 与步级天花板离线单测：
 * SubRun 深度 &gt;2 拒绝；步级 requiredLevel 高于 run 天花板时有效等级被压到不住 ⇒ 拒绝执行。
 */
class SubRunAndStepTest {

    /** F10-14：父深度 = 2 时子 Run 深度将达 3 &gt; MAX_DEPTH ⇒ canSpawn 拒绝。 */
    @Test
    void subRunDepthOverTwoRejected() {
        assertEquals(2, SubRun.MAX_DEPTH, "SubRun 深度硬顶 = 2（0=根 / 1=一层 / 2=二层）");
        assertTrue(SubRun.canSpawn(0), "根 run depth=0 可派子（子 depth=1）");
        assertTrue(SubRun.canSpawn(1), "一层子 depth=1 可派子（子 depth=2）");
        assertFalse(SubRun.canSpawn(2), "父 depth=2 ⇒ 子 depth=3 超顶必须拒绝");
        assertFalse(SubRun.canSpawn(3), "父 depth=3 已超顶必须拒绝");
        assertFalse(SubRun.canSpawn(-1), "负深度非法必须拒绝");

        // on_behalf_of 继承：空白拒收（审计穿透根发起人）。
        assertThrows(IllegalArgumentException.class, () -> SubRun.inheritOnBehalfOf(" "));
    }

    /**
     * F10-21：步级 requiredLevel 高于 run 天花板 ⇒ 有效等级 = min(toolMin, runCeiling, tenantMax)
     * 被压到天花板以下，达不到步所需等级 ⇒ 该步不得以所需等级自动执行（拒绝）。
     */
    @Test
    void stepLevelAboveRunCeilingRejected() {
        // run 天花板 L2(2)；步要求 L3(3)；租户顶 L3(3)。
        int stepRequired = 3;
        int runCeiling = 2;
        int tenantMax = 3;

        int effective = AutomationCeilingGuard.effectiveLevel(stepRequired, runCeiling, tenantMax);
        assertTrue(effective < stepRequired,
                "有效等级 " + effective + " 必须低于步所需 " + stepRequired + "，L3 步不可在 L2 天花板下自动执行");

        // 有效等级被压到 L2 ⇒ 不触发 L3 审批通道 ⇒ 该 L3 步在天花板之下被拒（无自动路径）。
        AutomationCeilingGuard.Effective eff = AutomationCeilingGuard.resolve(stepRequired, runCeiling, tenantMax);
        assertEquals(2, eff.level(), "有效等级应落到 run 天花板 L2");
        assertFalse(eff.requiresApprovalToken(), "有效等级 < L3 ⇒ 不走 L3 审批通道，L3 步被拒而非放行");
    }
}
