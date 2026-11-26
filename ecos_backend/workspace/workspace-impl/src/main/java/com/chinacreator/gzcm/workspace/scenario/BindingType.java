package com.chinacreator.gzcm.workspace.scenario;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 绑定类型单一权威（卷07 F07-05，与 {@code ScenarioService.BINDING_ENDPOINTS} 对齐）。
 * <p>每个类型带三层归属（子图/横切/出口）：DATASET/OBJECT_TYPE/KNOWLEDGE_BASE/RULE_SET/PIPELINE=SUBGRAPH，
 * AI_AGENT=CROSSCUT，SECURITY_POLICY/INTERFACE=EXIT。</p>
 *
 * <p><b>PIPELINE（2026-11-23 场景智能逻辑承接批）</b>：指向逻辑编排
 * （{@code ecos_ai.ecos_aip_pipeline.id}），场景运行 {@code analysisType=PIPELINE} 时按它执行；
 * 归 SUBGRAPH —— 它是场景链路里的"认知执行"节点（承接原 wagent 候选的角色），
 * 不是横切约束也不是对外出口。</p>
 */
public enum BindingType {

    DATASET(ScenarioTier.SUBGRAPH),
    OBJECT_TYPE(ScenarioTier.SUBGRAPH),
    KNOWLEDGE_BASE(ScenarioTier.SUBGRAPH),
    KNOWLEDGE_ARTICLE(ScenarioTier.SUBGRAPH),
    COGNITIVE_ASSET(ScenarioTier.SUBGRAPH),
    RULE_SET(ScenarioTier.SUBGRAPH),
    PIPELINE(ScenarioTier.SUBGRAPH),
    AI_AGENT(ScenarioTier.CROSSCUT),
    SECURITY_POLICY(ScenarioTier.EXIT),
    INTERFACE(ScenarioTier.EXIT);

    private final ScenarioTier tier;

    BindingType(ScenarioTier tier) {
        this.tier = tier;
    }

    public ScenarioTier tier() {
        return tier;
    }

    private static final Map<String, BindingType> BY_NAME;

    static {
        Map<String, BindingType> m = new LinkedHashMap<>();
        for (BindingType t : values()) {
            m.put(t.name(), t);
        }
        BY_NAME = Collections.unmodifiableMap(m);
    }

    public static Map<String, BindingType> byName() {
        return BY_NAME;
    }

    /** 宽松解析：大小写不敏感，未知返回 null（不抛，供读侧降级）。 */
    public static BindingType fromNullable(String name) {
        if (name == null) {
            return null;
        }
        return BY_NAME.get(name.trim().toUpperCase());
    }
}
