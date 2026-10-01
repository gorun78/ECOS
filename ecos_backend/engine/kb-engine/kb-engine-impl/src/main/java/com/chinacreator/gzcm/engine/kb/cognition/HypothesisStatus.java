package com.chinacreator.gzcm.engine.kb.cognition;

/**
 * 认知三态（hypothesis/belief/mind）状态枚举 — 详细设计-04 F04-08 / REQ-KB-04「status 枚举冻结」。
 *
 * <p>冻结于 2026-10-01：kb 只读开放三态时，枚举以「OpenAPI enum + Java 枚举」双锁；
 * 追加任何新值都必须走 ADR（arch guard {@code CognitionContractGuardTest#hypothesisStatusEnumIsFrozen}
 * 断言枚举恰为下列 4 值且类型 final，防止被扩值/enumeration 篡改）。
 */
public enum HypothesisStatus {
    PROPOSED,
    EVIDENCED,
    BELIEVED,
    REFUTED;

    /** 大小写不敏感解析；未知/空 → {@code null}（不抛，供白名单判定）。 */
    public static HypothesisStatus fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return null;
        }
        for (HypothesisStatus s : values()) {
            if (s.name().equalsIgnoreCase(v)) {
                return s;
            }
        }
        return null;
    }

    /** 白名单判定：仅冻结的 4 值合法。 */
    public static boolean isValidStatus(String raw) {
        return fromValue(raw) != null;
    }

    /** 全部合法值（供 SQL {@code IN (...)} 白名单拼装，防注入 — 值均为本枚举 name，不可外部注入）。 */
    public static String[] validStatusCodes() {
        HypothesisStatus[] all = values();
        String[] out = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            out[i] = all[i].name();
        }
        return out;
    }
}
