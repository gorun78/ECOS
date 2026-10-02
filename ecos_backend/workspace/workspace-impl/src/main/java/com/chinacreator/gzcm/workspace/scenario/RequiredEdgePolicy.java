package com.chinacreator.gzcm.workspace.scenario;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 必需边策略单点（详细设计-07 F07-03-1 / F07-05 / R-27 / §0.6.1）。
 *
 * <p>本类是场景组成模型的<b>唯一</b>词汇表与必需边来源：六类三层归属、六类邻接可达、
 * 三条主链必需边（MAPPING/EXTRACTION/COGNITION）与固定分母口径全部收敛于此，
 * 完整度（{@link ScenarioCompletenessService}）与绑定目录（binding-catalog）都从这里导出，
 * 禁止在别处各写一份邻接表或必需边清单（F07-03 / F07-05 单源纪律）。</p>
 *
 * <p>分母口径（R-27 已批 ①）：必需边恒为主链三跳（固定分母 3），动态推导口径仅在此处
 * {@link #requiredEdgeTypes(boolean)} 内切换，两口径只改一个 bean。</p>
 */
@Component
public class RequiredEdgePolicy {

    /** 六类绑定类型的三层归属（§0.6.1）。 */
    public enum Tier { SUBGRAPH, CROSS, OUTPUT }

    /** 主链必需边（固定分母 3，R-27）。起点类型 → 终点类型 → 边类型。 */
    public record RequiredEdge(String edgeType, String sourceType, String targetType, String labelKey) {
    }

    /** 绑定目录条目（binding-catalog N2 出参）。 */
    public record CatalogEntry(String type, String tier, String labelKey, String iconKey, List<String> linkableTo) {
    }

    /** bindingType 六值（后端词表为权威，X-73）。 */
    public static final Set<String> BINDING_TYPES = Set.of(
            "DATASET", "OBJECT_TYPE", "KNOWLEDGE_BASE", "AI_AGENT", "SECURITY_POLICY", "INTERFACE");

    /** 三层归属：3 子图 + 1 横切 + 2 出口（§0.6.1，F07-05 验收分档）。 */
    private static final Map<String, Tier> TIERS = new LinkedHashMap<>();
    /** lucide-react 图标名（前端铁律：图标仅 lucide-react，iconKey 为组件名字符串）。 */
    private static final Map<String, String> ICONS = new LinkedHashMap<>();
    /** i18n labelKey（workspace.binding.*）。 */
    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        TIERS.put("DATASET", Tier.SUBGRAPH);
        TIERS.put("OBJECT_TYPE", Tier.SUBGRAPH);
        TIERS.put("KNOWLEDGE_BASE", Tier.SUBGRAPH);
        TIERS.put("SECURITY_POLICY", Tier.CROSS);
        TIERS.put("AI_AGENT", Tier.OUTPUT);
        TIERS.put("INTERFACE", Tier.OUTPUT);

        ICONS.put("DATASET", "Database");
        ICONS.put("OBJECT_TYPE", "Box");
        ICONS.put("KNOWLEDGE_BASE", "BookOpen");
        ICONS.put("SECURITY_POLICY", "Shield");
        ICONS.put("AI_AGENT", "Bot");
        ICONS.put("INTERFACE", "Plug");

        LABELS.put("DATASET", "workspace.binding.dataset");
        LABELS.put("OBJECT_TYPE", "workspace.binding.objectType");
        LABELS.put("KNOWLEDGE_BASE", "workspace.binding.knowledgeBase");
        LABELS.put("SECURITY_POLICY", "workspace.binding.securityPolicy");
        LABELS.put("AI_AGENT", "workspace.binding.aiAgent");
        LABELS.put("INTERFACE", "workspace.binding.interface");
    }

    /** 三条主链必需边（固定分母 3）。 */
    public static final List<RequiredEdge> REQUIRED_EDGES = List.of(
            new RequiredEdge("MAPPING", "DATASET", "OBJECT_TYPE", "workspace.edge.mapping"),
            new RequiredEdge("EXTRACTION", "OBJECT_TYPE", "KNOWLEDGE_BASE", "workspace.edge.extraction"),
            new RequiredEdge("COGNITION", "KNOWLEDGE_BASE", "AI_AGENT", "workspace.edge.cognition"));

    /** 邻接可达（linkableTo 由必需边同源推导 + 横切/出口挂载），单源（F07-05-2）。 */
    private static final Map<String, List<String>> LINKABLE = new LinkedHashMap<>();
    static {
        LINKABLE.put("DATASET", List.of("OBJECT_TYPE"));
        LINKABLE.put("OBJECT_TYPE", List.of("DATASET", "KNOWLEDGE_BASE"));
        LINKABLE.put("KNOWLEDGE_BASE", List.of("OBJECT_TYPE", "AI_AGENT"));
        LINKABLE.put("AI_AGENT", List.of("KNOWLEDGE_BASE", "INTERFACE"));
        LINKABLE.put("SECURITY_POLICY", List.of("DATASET", "OBJECT_TYPE", "KNOWLEDGE_BASE"));
        LINKABLE.put("INTERFACE", List.of("AI_AGENT"));
    }

    /**
     * 必需边类型集合。R-27 默认固定分母（恒为主链三边）；dynamic 口径当前预留，
     * 若启用则为"只对已绑定节点对产生"，仅在此切换。
     */
    public List<String> requiredEdgeTypes(boolean dynamic) {
        return REQUIRED_EDGES.stream().map(RequiredEdge::edgeType).toList();
    }

    /** 必需边全量（含起终点类型与 labelKey）。 */
    public List<RequiredEdge> requiredEdges() {
        return REQUIRED_EDGES;
    }

    /**
     * 绑定目录（binding-catalog N2 单源）。返回顺序稳定（子图 → 横切 → 出口）。
     */
    public List<CatalogEntry> catalog() {
        List<CatalogEntry> out = new ArrayList<>();
        for (String type : List.of("DATASET", "OBJECT_TYPE", "KNOWLEDGE_BASE",
                "SECURITY_POLICY", "AI_AGENT", "INTERFACE")) {
            out.add(new CatalogEntry(
                    type, TIERS.get(type).name(), LABELS.get(type), ICONS.get(type),
                    LINKABLE.get(type)));
        }
        return out;
    }

    /** 邻接可达类型（唯一来源，供 catalog 与画布共同消费）。 */
    public List<String> linkableTo(String type) {
        List<String> v = LINKABLE.get(type == null ? "" : type.toUpperCase());
        return v == null ? List.of() : v;
    }

    /** 某必需边类型的规范起/终点类型（不存在返回 null）。 */
    public RequiredEdge edge(String edgeType) {
        for (RequiredEdge e : REQUIRED_EDGES) {
            if (e.edgeType().equals(edgeType)) {
                return e;
            }
        }
        return null;
    }

    /** 绑定类型是否合法（六值）。 */
    public boolean isKnownBindingType(String t) {
        return t != null && BINDING_TYPES.contains(t.toUpperCase());
    }
}
