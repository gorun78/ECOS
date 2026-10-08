package com.chinacreator.gzcm.engine.cognitive.api;

/**
 * 趋势枚举（详细设计-05 D.2 冻结：UP/FLAT/DOWN；本期 E2 恒 FLAT，
 * 响应必须附 {@code trendSource="NOT_IMPLEMENTED"}）。
 */
public enum Trend {
    UP, FLAT, DOWN;

    public static Trend fromName(String name) {
        if (name == null) return null;
        for (Trend t : values()) {
            if (t.name().equals(name)) return t;
        }
        return null;
    }
}
