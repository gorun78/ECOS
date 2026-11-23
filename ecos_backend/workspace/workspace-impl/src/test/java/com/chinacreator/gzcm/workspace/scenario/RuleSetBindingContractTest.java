package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则库绑定类型契约测试（2026-11-21 场景工作台简化 v0.3）。
 * <p>RULE_SET 已接入单一词表/分组/邻接，但不进固定 3 条必需边；完整度口径不变。</p>
 */
class RuleSetBindingContractTest {

    @Test
    @DisplayName("RULE_SET 入目录且不进必需边")
    void ruleSetIsInCatalogAndNotRequired() {
        var catalog = new BindingCatalog(new FixedDenominatorEdgePolicy());
        var item = catalog.list().stream()
                .filter(i -> "RULE_SET".equals(i.getType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("RULE_SET 未入目录"));
        assertEquals("SUBGRAPH", item.getTier());
        assertTrue(item.getLinkableTo().isEmpty(), "RULE_SET 不参与必需边邻接");
    }

    @Test
    @DisplayName("必需边仍是固定 3 条")
    void requiredEdgesRemainFixedThree() {
        assertEquals(3, new FixedDenominatorEdgePolicy().derive(Set.of(
                BindingType.DATASET, BindingType.OBJECT_TYPE, BindingType.KNOWLEDGE_BASE,
                BindingType.RULE_SET, BindingType.AI_AGENT)).size());
    }
}
