package com.chinacreator.gzcm.ai.wagent.degrade;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.DegradeCode;

import java.util.Map;

/**
 * 分册10 F10-10 · 降级白名单集中登记（预登记 8 值 DG-*，与 {@link DegradeCode} 同源）。
 *
 * <p><b>配置可关不可开新</b>：运行态配置只能把已登记值关掉（{@link DegradeRule#requiresAck()}
 * 语义），<b>不允许</b>引入未登记的新降级码——否则期间用户看到的降级面会“无据可依”。
 * {@link #assertConfigCanNotHideNew(String, String)} 强校此边界。</p>
 */
public final class DegradePolicyValidator {

    /** 降级规则项（8 值各自登记）。 */
    public record DegradeRule(String code, String appliesTo, boolean requiresAck) {}

    /** 8 值集中登记（唯一权威表），static 块填充（实例型初始化块对 static 字段无效）。 */
    public static final Map<String, DegradeRule> registered = new java.util.LinkedHashMap<>();
    static {
        // 8 值各自登记（阶段/态 + 是否需要人工确认）
        registered.put(DegradeCode.DG_K1.name(), new DegradeRule(DegradeCode.DG_K1.name(), "阶段1-planning-profile", false));
        registered.put(DegradeCode.DG_D1.name(), new DegradeRule(DegradeCode.DG_D1.name(), "阶段2-sampling", true));
        registered.put(DegradeCode.DG_C1.name(), new DegradeRule(DegradeCode.DG_C1.name(), "阶段3-graph", false));
        registered.put(DegradeCode.DG_C2.name(), new DegradeRule(DegradeCode.DG_C2.name(), "阶段3-column-perm", true));
        registered.put(DegradeCode.DG_L1.name(), new DegradeRule(DegradeCode.DG_L1.name(), "阶段4-llm", true));
        registered.put(DegradeCode.DG_T1.name(), new DegradeRule(DegradeCode.DG_T1.name(), "阶段5-timeseries", false));
        registered.put(DegradeCode.DG_M1.name(), new DegradeRule(DegradeCode.DG_M1.name(), "阶段5-model-route", true));
        registered.put(DegradeCode.DG_M2.name(), new DegradeRule(DegradeCode.DG_M2.name(), "阶段5-model-cap", true));
    }

    private DegradePolicyValidator() {}

    /** 强校：code 必须在 8 值内，否则抛 {@link IllegalArgumentException}。 */
    public static void assertRegistered(String code) {
        if (!registered.containsKey(code)) {
            throw new IllegalArgumentException(
                    "E-WA-DEP: 未登记降级码 " + code + "（F10-10 仅 8 值 DG-*）");
        }
    }

    /**
     * 配置与登记一致性：配置（fromConfig）如果引用了未登记（registered 表中没有）的值
     * ⇒ 抛IllegalArgumentException（配置不可开新，只可关引用已登记值）。
     *
     * @param fromConfig 配置中声明的降级码（可 null/空 = 无）
     * @param registeredCode 待校验的登记侧 code
     */
    public static void assertConfigCanNotHideNew(String fromConfig, String registeredCode) {
        // 配置侧引用的 code 若不在登记表 ⇒ 试图“开新”，拒绝。
        if (fromConfig != null && !fromConfig.isBlank()) {
            assertRegistered(fromConfig);
        }
        // 登记侧 code 必须已在表内（防御）。
        assertRegistered(registeredCode);
    }
}
