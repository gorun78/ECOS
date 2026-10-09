package com.chinacreator.gzcm.workspace.scenario;

/**
 * 运行模式正交列语义（卷07 F07-08 / R-25：与 run_type 正交，禁挤同一列）。
 */
public enum RunMode {
    FORMAL,
    SANDBOX;

    public static RunMode fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (RunMode m : values()) {
            if (m.name().equalsIgnoreCase(value.trim())) {
                return m;
            }
        }
        return null;
    }

    /** 解析入参：null/空白 → 默认 FORMAL（A31 向后兼容）；未知值 → 抛非法（禁静默）。 */
    public static RunMode parseOrDefault(String value) {
        if (value == null || value.isBlank()) {
            return FORMAL;
        }
        RunMode m = fromNullable(value);
        if (m == null) {
            throw new InvalidRunModeException(value);
        }
        return m;
    }
}
