package com.chinacreator.gzcm.workspace.scenario;

import java.util.Objects;

/** 参与完整度/孤岛计算的最小绑定视图（来自 ecos_scenario_binding 行）。 */
public final class BindingNode {

    private final String bindingId;
    private final String name;
    private final BindingType type;

    public BindingNode(String bindingId, String name, BindingType type) {
        this.bindingId = bindingId;
        this.name = name;
        this.type = type;
    }

    public String getBindingId() {
        return bindingId;
    }

    public String getName() {
        return name;
    }

    public BindingType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BindingNode)) {
            return false;
        }
        return Objects.equals(bindingId, ((BindingNode) o).bindingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bindingId);
    }
}
