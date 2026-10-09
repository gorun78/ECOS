package com.chinacreator.gzcm.workspace.scenario;

/**
 * 场景连边类型。
 * <p>前三者为必需边（E_req），后两者为可选边（横切治理 / 出口暴露），参与孤岛可达性但不进覆盖率分子。</p>
 */
public enum EdgeType {
    MAPPING(true),
    EXTRACTION(true),
    COGNITION(true),
    GOVERN(false),
    EXPOSE(false);

    private final boolean required;

    EdgeType(boolean required) {
        this.required = required;
    }

    public boolean isRequired() {
        return required;
    }

    public static EdgeType fromNullable(String name) {
        if (name == null) {
            return null;
        }
        for (EdgeType t : values()) {
            if (t.name().equalsIgnoreCase(name.trim())) {
                return t;
            }
        }
        return null;
    }
}
