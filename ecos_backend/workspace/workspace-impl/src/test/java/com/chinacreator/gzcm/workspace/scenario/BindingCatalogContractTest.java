package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-07 F07-05/W169/C150 词表单源验收：
 * <ol>
 *   <li>{@code BindingType} 六值与 {@code ScenarioService.BINDING_ENDPOINTS} 常量**反射比对**（禁两处各写）；</li>
 *   <li>三层归属 3 子图 + 1 横切 + 2 出口（铁律 §0.6.1）；</li>
 *   <li>{@code linkableTo} 由 {@link RequiredEdgePolicy#adjacency()} 导出（禁前端另写邻接表）。</li>
 * </ol>
 */
class BindingCatalogContractTest {

    @Test
    @DisplayName("BindingType 六值 ≡ ScenarioService.BINDING_ENDPOINTS 键集（反射比对）")
    void catalogTypesEqualBindingEndpointsConstant() throws Exception {
        Field f = ScenarioService.class.getDeclaredField("BINDING_ENDPOINTS");
        f.setAccessible(true);
        Map<?, ?> endpoints = (Map<?, ?>) f.get(null);
        assertNotNull(endpoints);
        Set<String> endpointKeys = new HashSet<>();
        for (Object k : endpoints.keySet()) {
            endpointKeys.add(String.valueOf(k));
        }
        Set<String> enumNames = new HashSet<>();
        for (BindingType t : BindingType.values()) {
            enumNames.add(t.name());
        }
        assertEquals(endpointKeys, enumNames,
                "BindingType 枚举与 BINDING_ENDPOINTS 键集必须等价（单源词表）");
    }

    @Test
    @DisplayName("三层归属：SUBGRAPH×3 / CROSSCUT×1 / EXIT×2（铁律 §0.6.1）")
    void tierSplitMatchesIronLaw() {
        Map<ScenarioTier, Integer> count = new LinkedHashMap<>();
        for (BindingType t : BindingType.values()) {
            count.merge(t.tier(), 1, Integer::sum);
        }
        assertEquals(Integer.valueOf(3), count.get(ScenarioTier.SUBGRAPH),
                "子图层 = DATASET/OBJECT_TYPE/KNOWLEDGE_BASE");
        assertEquals(Integer.valueOf(1), count.get(ScenarioTier.CROSSCUT),
                "横切层 = AI_AGENT");
        assertEquals(Integer.valueOf(2), count.get(ScenarioTier.EXIT),
                "出口层 = SECURITY_POLICY/INTERFACE");
    }

    @Test
    @DisplayName("linkableTo 由 RequiredEdgePolicy 导出：三对源→目标齐全，与前端目录字段等价")
    void linkableToDerivedFromRequiredEdgePolicy() {
        RequiredEdgePolicy policy = new FixedDenominatorEdgePolicy();
        Map<BindingType, Set<BindingType>> adj = policy.adjacency();
        assertTrue(adj.get(BindingType.DATASET).contains(BindingType.OBJECT_TYPE));
        assertTrue(adj.get(BindingType.OBJECT_TYPE).contains(BindingType.KNOWLEDGE_BASE));
        assertTrue(adj.get(BindingType.KNOWLEDGE_BASE).contains(BindingType.AI_AGENT));
        // 反向不可达（保证邻接是有向，破前端"完全图"式误实现）
        assertTrue(!adj.get(BindingType.OBJECT_TYPE).contains(BindingType.DATASET));
        assertTrue(!adj.get(BindingType.KNOWLEDGE_BASE).contains(BindingType.OBJECT_TYPE));
    }

    @Test
    @DisplayName("宽松解析：大小写不敏感、null 与未知返回 null 而非抛异常（读侧降级）")
    void fromNullableIsLenient() {
        assertEquals(BindingType.DATASET, BindingType.fromNullable("dataset"));
        assertEquals(BindingType.AI_AGENT, BindingType.fromNullable("  AI_Agent  "));
        assertEquals(null, BindingType.fromNullable(null));
        assertEquals(null, BindingType.fromNullable("UNKNOWN_TYPE"));
    }
}
