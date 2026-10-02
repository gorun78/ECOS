package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F07-03-1（必需边策略单点）门禁。
 *
 * <p>{@link RequiredEdgePolicy} 是场景组成模型的唯一词汇表与必需边来源（R-27 固定分母 3），
 * 完整度与绑定目录都必须从这里导出。本测试锁四条不变量：三条主链必需边、固定分母口径、
 * 六类绑定目录的稳定顺序与三层归属、邻接可达的起/终点一致性。任何人想"在别处再写一份
 * 邻接表/必需边清单"（F07-03 / F07-05 单源纪律）都会被这里的枚举断言拦下。</p>
 */
class RequiredEdgeDerivationTest {

    private final RequiredEdgePolicy policy = new RequiredEdgePolicy();

    @Test
    @DisplayName("必需边恒为主链三跳：MAPPING / EXTRACTION / COGNITION，顺序与起终点固定")
    void requiredEdgesAreTheThreeMainChainHops() {
        List<RequiredEdgePolicy.RequiredEdge> edges = policy.requiredEdges();

        assertEquals(3, edges.size());
        assertEquals("MAPPING", edges.get(0).edgeType());
        assertEquals("EXTRACTION", edges.get(1).edgeType());
        assertEquals("COGNITION", edges.get(2).edgeType());

        assertEquals("DATASET", edges.get(0).sourceType());
        assertEquals("OBJECT_TYPE", edges.get(0).targetType());
        assertEquals("OBJECT_TYPE", edges.get(1).sourceType());
        assertEquals("KNOWLEDGE_BASE", edges.get(1).targetType());
        assertEquals("KNOWLEDGE_BASE", edges.get(2).sourceType());
        assertEquals("AI_AGENT", edges.get(2).targetType());
    }

    @Test
    @DisplayName("F07-03-1 / R-27: 固定分母口径下 requiredEdgeTypes 恒返三边，dynamic 开关当前不改变集合")
    void fixedDenominatorIsAlwaysThreeWhetherDynamicFlagSetOrNot() {
        // 两口径只改这一个 bean；当前 dynamic 口径预留、尚未启用，故两入参产出等价三边。
        assertEquals(List.of("MAPPING", "EXTRACTION", "COGNITION"), policy.requiredEdgeTypes(false));
        assertEquals(List.of("MAPPING", "EXTRACTION", "COGNITION"), policy.requiredEdgeTypes(true));
    }

    @Test
    @DisplayName("绑定目录六类稳定有序：子图 3 → 横切 1 → 出口 2，三层归属与单源一致")
    void catalogIsSixEntriesInStableTierOrder() {
        List<RequiredEdgePolicy.CatalogEntry> catalog = policy.catalog();

        assertEquals(6, catalog.size());
        for (int i = 0; i < 6; i++) {
            assertEquals(List.of("DATASET", "OBJECT_TYPE", "KNOWLEDGE_BASE",
                            "SECURITY_POLICY", "AI_AGENT", "INTERFACE").get(i),
                    catalog.get(i).type());
            assertEquals(List.of("SUBGRAPH", "SUBGRAPH", "SUBGRAPH",
                            "CROSS", "OUTPUT", "OUTPUT").get(i),
                    catalog.get(i).tier());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"DATASET", "OBJECT_TYPE", "KNOWLEDGE_BASE",
            "SECURITY_POLICY", "AI_AGENT", "INTERFACE"})
    @DisplayName("每个目录项的 linkableTo 与单一邻接源一致，且 iconKey/labelKey 非空")
    void catalogLinkableToMatchesSingleSourceLinkable(String type) {
        RequiredEdgePolicy.CatalogEntry entry = policy.catalog().stream()
                .filter(c -> c.type().equals(type))
                .findFirst()
                .orElseThrow();

        assertEquals(policy.linkableTo(type), entry.linkableTo());
        assertFalse(entry.linkableTo().isEmpty());
        assertFalse(entry.iconKey().isBlank());
        assertTrue(entry.labelKey().startsWith("workspace.binding."));
    }

    @Test
    @DisplayName("edge(edgeType) 按类型反查必需边，未知/ null 类型返回 null")
    void edgeLookupResolvesByTypeAndNullsForUnknown() {
        assertEquals("OBJECT_TYPE", policy.edge("EXTRACTION").sourceType());
        assertEquals("KNOWLEDGE_BASE", policy.edge("EXTRACTION").targetType());
        assertNotNull(policy.edge("MAPPING"));
        assertNotNull(policy.edge("COGNITION"));
        assertNull(policy.edge("NOT_AN_EDGE_TYPE"));
        assertNull(policy.edge(null));
    }

    @Test
    @DisplayName("C-1 步骤 3 / 相邻闭环: 三条主链必需边的起终点必须都在六类绑定类型内（避免孤儿类型）")
    void everyRequiredEdgeEndpointIsAknownType() {
        for (RequiredEdgePolicy.RequiredEdge e : policy.requiredEdges()) {
            assertTrue(policy.isKnownBindingType(e.sourceType()), e.edgeType() + ".source");
            assertTrue(policy.isKnownBindingType(e.targetType()), e.edgeType() + ".target");
        }
    }

    @Test
    @DisplayName("未知 bindingType 判定为非法（大小写不敏感接受合法值）")
    void isKnownBindingTypeRejectsUnknownAndIsCaseInsensitive() {
        assertTrue(policy.isKnownBindingType("DATASET"));
        assertTrue(policy.isKnownBindingType("dataset"));
        assertFalse(policy.isKnownBindingType("WIDGET"));
        assertFalse(policy.isKnownBindingType(null));
    }
}
