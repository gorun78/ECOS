package com.chinacreator.gzcm.workspace.scenario;

/**
 * 场景状态四态显式枚举（卷07 F07-07，C-2 迁移表）。
 * <p>读侧大小写不敏感；未知值降级为 null（不抛，交由上层校验）。</p>
 */
public enum ScenarioStatus {
    DRAFT,
    ACTIVE,
    SUSPENDED,
    COMPLETED;

    public static ScenarioStatus fromNullable(String value) {
        if (value == null) {
            return null;
        }
        for (ScenarioStatus s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        return null;
    }
}
