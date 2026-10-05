package com.chinacreator.gzcm.ai.wagent.degrade;

import com.chinacreator.gzcm.ai.wagent.degrade.DegradePolicyValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-10 降级白名单离线单测：
 * 未登记降级码拒收；配置只能关已登记值、不可引入新码（assertConfigCanNotHideNew 强校边界）。
 */
class DegradePolicyTest {

    /** F10-10：未登记降级码（DG_X9）⇒ assertRegistered 抛 IAE（仅 8 值 DG-*）。 */
    @Test
    void unregisteredDegradeIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DegradePolicyValidator.assertRegistered("DG_X9"));
        assertTrue(ex.getMessage().contains("E-WA-DEP"), "未登记码须带 E-WA-DEP，实际：" + ex.getMessage());

        // 对照：8 个预登记 DG-* 全部通过。
        for (String code : DegradePolicyValidator.registered.keySet()) {
            assertDoesNotThrow(() -> DegradePolicyValidator.assertRegistered(code), code + " 应已登记");
        }
        assertEquals(8, DegradePolicyValidator.registered.size(), "降级白名单必为 8 值");
    }

    /** F10-10：配置引用未登记码 ⇒ 视为「开新」拒收（配置只可关不可开新）。 */
    @Test
    void configCanOnlyDisableNeverInvent() {
        // 配置侧引用一个未登记码 DG_X9 ⇒ 抛 IAE（不可开新）。
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DegradePolicyValidator.assertConfigCanNotHideNew("DG_X9", "DG_K1"));
        assertTrue(ex.getMessage().contains("E-WA-DEP"), "配置开新必须带 E-WA-DEP，实际：" + ex.getMessage());

        // 对照：配置侧引用已登记码 DG_K1 ⇒ 通过。
        assertDoesNotThrow(
                () -> DegradePolicyValidator.assertConfigCanNotHideNew("DG_K1", "DG_K1"),
                "配置引用已登记码应放行");

        // 对照：配置侧无声明（null/空）⇒ 通过。
        assertDoesNotThrow(
                () -> DegradePolicyValidator.assertConfigCanNotHideNew(null, "DG_K1"),
                "配置无声明应放行");
        assertDoesNotThrow(
                () -> DegradePolicyValidator.assertConfigCanNotHideNew("  ", "DG_D1"),
                "配置空串应放行");
    }
}
