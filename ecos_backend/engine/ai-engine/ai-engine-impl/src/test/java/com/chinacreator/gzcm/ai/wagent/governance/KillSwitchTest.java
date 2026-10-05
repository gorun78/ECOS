package com.chinacreator.gzcm.ai.wagent.governance;

import com.chinacreator.gzcm.ai.wagent.governance.KillSwitch.Scope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-21 Kill Switch 离线单测：
 * SYSTEM-scope 置旗后对该 scope 立刻 enabled（activeRuns 立即暂停语义钩可触发）；
 * TENANT-scope 必须带 tenantId。
 */
class KillSwitchTest {

    /** F10-21：SYSTEM scope 置旗 ⇒ isEnabled 立即 true；pauseActiveRuns 语义钩可调用。 */
    @Test
    void activeRunsPausedImmediately() {
        KillSwitch kill = new KillSwitch();

        // 未置旗 ⇒ 未 enabled。
        assertFalse(kill.isEnabled("SYSTEM", null), "未置旗 SYSTEM 应未 enabled");

        // 置 SYSTEM 旗（enabled=true）⇒ 立即 enabled；pausedRunCount 递增。
        Scope stored = kill.set(new Scope("SYSTEM", null, true, "全局熔断"), "admin");
        assertEquals("SYSTEM", stored.scope());
        assertTrue(kill.isEnabled("SYSTEM", null), "SYSTEM 置旗后立刻 enabled（activeRuns 立即暂停）");
        assertEquals(1, kill.pausedRunCount(), "置一次 enabled SYSTEM 旗 ⇒ pausedRunCount=1");

        // 语义钩可被调用（生产由 runtime-task 接入真实 pause；此处只断言可触发不抛）。
        assertDoesNotThrow(kill::pauseActiveRuns, "pauseActiveRuns 语义钩可被 wiring 层触发");

        // 关闭后 ⇒ 未 enabled。
        kill.set(new Scope("SYSTEM", null, false, "恢复"), "admin");
        assertFalse(kill.isEnabled("SYSTEM", null), "关闭 SYSTEM 旗后应未 enabled");
    }

    /** F10-21：TENANT scope 必须带 tenantId（缺 ⇒ IAE）；置旗后仅对该 tenant enabled。 */
    @Test
    void tenantScopeRequiresTenantId() {
        assertThrows(IllegalArgumentException.class,
                () -> new Scope("TENANT", null, true, "r"),
                "TENANT scope 必须带 tenantId");

        KillSwitch kill = new KillSwitch();
        kill.set(new Scope("TENANT", "t-1", true, "t-1 熔断"), "tenant-admin");
        assertTrue(kill.isEnabled("TENANT", "t-1"), "t-1 应 enabled");
        assertFalse(kill.isEnabled("TENANT", "t-2"), "其它 tenant 不应被打到");

        // 非法 scope 字面字 ⇒ IAE。
        assertThrows(IllegalArgumentException.class,
                () -> new Scope("GLOBAL", null, true, "r"),
                "scope 仅允许 SYSTEM / TENANT");
    }
}
