package com.chinacreator.gzcm.workspace.scenario;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 固定分母 3 的 E_req 推导（卷07 F07-03-1 本册默认，R-27）。
 * <p>必需边恒为 {映射 DATASET→OBJECT_TYPE, 抽取 OBJECT_TYPE→KNOWLEDGE_BASE, 认知 KNOWLEDGE_BASE→AI_AGENT}，
 * 三组 PRD 示例（1.0 / 0.67 / 0）全部自洽。</p>
 */
@Component
public class FixedDenominatorEdgePolicy implements RequiredEdgePolicy {

    private static final List<RequiredEdge> REQUIRED = Collections.unmodifiableList(Arrays.asList(
            new RequiredEdge(EdgeType.MAPPING, BindingType.DATASET, BindingType.OBJECT_TYPE),
            new RequiredEdge(EdgeType.EXTRACTION, BindingType.OBJECT_TYPE, BindingType.KNOWLEDGE_BASE),
            new RequiredEdge(EdgeType.COGNITION, BindingType.KNOWLEDGE_BASE, BindingType.AI_AGENT)));

    @Override
    public List<RequiredEdge> derive(Set<BindingType> boundTypes) {
        return REQUIRED;
    }

    @Override
    public Map<BindingType, Set<BindingType>> adjacency() {
        Map<BindingType, Set<BindingType>> adj = new EnumMap<>(BindingType.class);
        for (BindingType t : BindingType.values()) {
            adj.put(t, EnumSet.noneOf(BindingType.class));
        }
        for (RequiredEdge e : REQUIRED) {
            adj.get(e.getSourceType()).add(e.getTargetType());
        }
        // 横切/出口为可选边：AI_AGENT 可被治理引用、SECURITY_POLICY/INTERFACE 为链尾出口，不参与必需分母。
        Map<BindingType, Set<BindingType>> immutable = new LinkedHashMap<>();
        for (Map.Entry<BindingType, Set<BindingType>> en : adj.entrySet()) {
            immutable.put(en.getKey(), Collections.unmodifiableSet(en.getValue()));
        }
        return Collections.unmodifiableMap(immutable);
    }

    /** 供测试构造空必需边之外的动态口径（R-27 备选），保留同声明单点。 */
    static List<RequiredEdge> requiredEdgesSnapshot() {
        return new ArrayList<>(REQUIRED);
    }
}
