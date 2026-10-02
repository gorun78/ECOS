package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 绑定目录单源合约测（详细设计-07 F07-05 / W169 / C151）。
 *
 * <p>断言 catalog 六类类型与后端权威词表（{@code ScenarioService.BINDING_ENDPOINTS} 键）完全一致（含三层
 * 分档 3 子图 + 1 横切 + 2 出口），且 {@code linkableTo} 由 {@link RequiredEdgePolicy} 单源导出
 * （禁两处各写一份邻接表）。这是"六类命名三套收口"的服务端真源证明，前端只消费。</p>
 */
class BindingCatalogContractTest {

    private final RequiredEdgePolicy policy = new RequiredEdgePolicy();

    @Test
    void catalogTypesEqualBindingEndpointsConstant() {
        List<RequiredEdgePolicy.CatalogEntry> catalog = policy.catalog();
        assertEquals(6, catalog.size(), "六类");
        Set<String> catalogTypes = new HashSet<>();
        for (RequiredEdgePolicy.CatalogEntry e : catalog) {
            catalogTypes.add(e.type());
        }
        // 权威词表 = ScenarioService.BINDING_ENDPOINTS 键（与 catalog 同源判定，不允许分叉）
        assertEquals(ScenarioService.bindingTypes(), catalogTypes,
                "catalog 类型必须与 ScenarioService.BINDING_ENDPOINTS 键一致（单源词表）");
        // 三层分档：3 SUBGRAPH + 1 CROSS + 2 OUTPUT
        long subgraph = catalog.stream().filter(c -> "SUBGRAPH".equals(c.tier())).count();
        long cross = catalog.stream().filter(c -> "CROSS".equals(c.tier())).count();
        long output = catalog.stream().filter(c -> "OUTPUT".equals(c.tier())).count();
        assertEquals(3, subgraph);
        assertEquals(1, cross);
        assertEquals(2, output);
    }

    @Test
    void linkableToDerivedFromRequiredEdgePolicy() {
        // 主链邻接必须自洽：DATASET→OBJECT_TYPE（映射边）且反向邻居回含 DATASET
        assertTrue(policy.linkableTo("DATASET").contains("OBJECT_TYPE"), "映射边起点可连 OBJECT_TYPE");
        assertTrue(policy.linkableTo("KNOWLEDGE_BASE").contains("AI_AGENT"), "认知边终点可连 AI_AGENT");
        // 已知类型非空、未知类型空集（不抛异常）
        assertTrue(!policy.linkableTo("DATASET").isEmpty());
        assertTrue(policy.linkableTo("__NO_SUCH_TYPE__").isEmpty());
        // catalog 的 linkableTo 与 policy.linkableTo 单源一致
        for (RequiredEdgePolicy.CatalogEntry e : policy.catalog()) {
            assertEquals(policy.linkableTo(e.type()), e.linkableTo(),
                    "catalog.linkableTo 必须与 policy.linkableTo 同源自 " + e.type());
        }
    }
}
