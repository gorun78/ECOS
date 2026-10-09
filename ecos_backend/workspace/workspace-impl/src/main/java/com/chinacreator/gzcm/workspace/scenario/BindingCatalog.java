package com.chinacreator.gzcm.workspace.scenario;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 六类三层单一词表运行时下发（卷07 F07-05 N2）。
 * <p>类型集与后端 {@code ScenarioService.BINDING_ENDPOINTS}（= {@link BindingType}）严格一致；
 * {@code linkableTo} 由 {@link RequiredEdgePolicy#adjacency()} 同一份声明导出，禁两处各写邻接表。</p>
 */
@Component
public class BindingCatalog {

    private final RequiredEdgePolicy edgePolicy;

    public BindingCatalog(RequiredEdgePolicy edgePolicy) {
        this.edgePolicy = edgePolicy;
    }

    public List<BindingCatalogItem> list() {
        Map<BindingType, Set<BindingType>> adj = edgePolicy.adjacency();
        List<BindingCatalogItem> items = new ArrayList<>();
        for (BindingType t : BindingType.values()) {
            List<String> linkable = new ArrayList<>();
            for (BindingType to : adj.getOrDefault(t, Set.of())) {
                linkable.add(to.name());
            }
            items.add(new BindingCatalogItem(
                    t.name(),
                    t.tier().name(),
                    "workspace.binding." + t.name().toLowerCase(),
                    iconKey(t),
                    linkable));
        }
        return items;
    }

    /** 类型集合（与 BINDING_ENDPOINTS 比对用，单点）。 */
    public Set<String> types() {
        Set<String> s = new LinkedHashSet<>();
        for (BindingType t : BindingType.values()) {
            s.add(t.name());
        }
        return s;
    }

    private String iconKey(BindingType t) {
        switch (t) {
            case DATASET:
                return "Database";
            case OBJECT_TYPE:
                return "Boxes";
            case KNOWLEDGE_BASE:
                return "BookOpen";
            case AI_AGENT:
                return "Bot";
            case SECURITY_POLICY:
                return "ShieldCheck";
            case INTERFACE:
                return "Plug";
            default:
                return "Circle";
        }
    }
}
