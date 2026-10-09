package com.chinacreator.gzcm.workspace.scenario;

import java.util.Objects;

/** 必需边声明：一种连边类型 + 其两端绑定类型 + i18n key。 */
public final class RequiredEdge {

    private final EdgeType type;
    private final BindingType sourceType;
    private final BindingType targetType;

    public RequiredEdge(EdgeType type, BindingType sourceType, BindingType targetType) {
        this.type = type;
        this.sourceType = sourceType;
        this.targetType = targetType;
    }

    public EdgeType getType() {
        return type;
    }

    public BindingType getSourceType() {
        return sourceType;
    }

    public BindingType getTargetType() {
        return targetType;
    }

    /** 缺失边 i18n key（前端消费，禁前端自造）。 */
    public String labelKey() {
        return "workspace.completeness.missing." + type.name().toLowerCase();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RequiredEdge)) {
            return false;
        }
        RequiredEdge e = (RequiredEdge) o;
        return type == e.type && sourceType == e.sourceType && targetType == e.targetType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, sourceType, targetType);
    }

    @Override
    public String toString() {
        return type + "(" + sourceType + "->" + targetType + ")";
    }
}
