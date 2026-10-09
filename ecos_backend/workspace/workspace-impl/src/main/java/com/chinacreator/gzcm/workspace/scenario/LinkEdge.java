package com.chinacreator.gzcm.workspace.scenario;

/** 连边行视图（来自 ecos_scenario_binding_link，承载 present 判定与孤岛可达性）。 */
public final class LinkEdge {

    private final String sourceBindingId;
    private final String targetBindingId;
    private final EdgeType type;

    public LinkEdge(String sourceBindingId, String targetBindingId, EdgeType type) {
        this.sourceBindingId = sourceBindingId;
        this.targetBindingId = targetBindingId;
        this.type = type;
    }

    public String getSourceBindingId() {
        return sourceBindingId;
    }

    public String getTargetBindingId() {
        return targetBindingId;
    }

    public EdgeType getType() {
        return type;
    }
}
