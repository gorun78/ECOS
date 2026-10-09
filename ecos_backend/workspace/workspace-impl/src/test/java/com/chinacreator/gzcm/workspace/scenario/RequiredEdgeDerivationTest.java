package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-07 F07-03/W167/C149 验收：固定分母 3 的 E_req 推导可复现 PRD-01 §2.5 三夹具
 * （完整链 1.0 / 缺 MAPPING 0.67 / 纯孤岛 0）。R-27 已批准采用本册默认「固定分母」。
 *
 * <p>本测试类只锁 policy 层的推导语义（不依赖 DB/HTTP 装配），完整链集成用例见
 * {@code ScenarioCompletenessServiceTest}（依赖 F07-02 服务实现，§8.6 记未执行）。</p>
 */
class RequiredEdgeDerivationTest {

    private final FixedDenominatorEdgePolicy policy = new FixedDenominatorEdgePolicy();

    @Test
    @DisplayName("E_req 恒为三条必需边 (MAPPING/EXTRACTION/COGNITION)，与已绑定集合无关")
    void fixedDenominatorDerivesThreeRequiredEdges() {
        Set<BindingType> bound = EnumSet.of(BindingType.DATASET, BindingType.OBJECT_TYPE,
                BindingType.KNOWLEDGE_BASE, BindingType.AI_AGENT);
        List<RequiredEdge> req = policy.derive(bound);
        assertEquals(3, req.size(), "E_req 恒为 3（R-27 固定分母）");
        assertTrue(req.contains(new RequiredEdge(EdgeType.MAPPING, BindingType.DATASET, BindingType.OBJECT_TYPE)));
        assertTrue(req.contains(new RequiredEdge(EdgeType.EXTRACTION, BindingType.OBJECT_TYPE, BindingType.KNOWLEDGE_BASE)));
        assertTrue(req.contains(new RequiredEdge(EdgeType.COGNITION, BindingType.KNOWLEDGE_BASE, BindingType.AI_AGENT)));
    }

    @Test
    @DisplayName("PRD 夹具 1：三必需边均具备 → coverage=1.0")
    void fixedDenominatorReproducesPrdExamples_fullChainYieldsOne() {
        Set<RequiredEdge> present = new HashSet<>();
        present.add(new RequiredEdge(EdgeType.MAPPING, BindingType.DATASET, BindingType.OBJECT_TYPE));
        present.add(new RequiredEdge(EdgeType.EXTRACTION, BindingType.OBJECT_TYPE, BindingType.KNOWLEDGE_BASE));
        present.add(new RequiredEdge(EdgeType.COGNITION, BindingType.KNOWLEDGE_BASE, BindingType.AI_AGENT));
        assertEquals(1.0d, coverage(present), 1e-9);
    }

    @Test
    @DisplayName("PRD 夹具 2：缺 MAPPING → coverage≈0.667")
    void fixedDenominatorReproducesPrdExamples_missingMappingYieldsPointSixSeven() {
        Set<RequiredEdge> present = new HashSet<>();
        present.add(new RequiredEdge(EdgeType.EXTRACTION, BindingType.OBJECT_TYPE, BindingType.KNOWLEDGE_BASE));
        present.add(new RequiredEdge(EdgeType.COGNITION, BindingType.KNOWLEDGE_BASE, BindingType.AI_AGENT));
        assertEquals(2.0d / 3.0d, coverage(present), 1e-9);
    }

    @Test
    @DisplayName("PRD 夹具 3：无任何连边 → coverage=0（破 X-25 空场景 100%）")
    void fixedDenominatorReproducesPrdExamples_pureIslandYieldsZero() {
        assertEquals(0.0d, coverage(new HashSet<>()), 1e-9);
    }

    @Test
    @DisplayName("邻接表由同一份 REQUIRED 导出（F07-05 linkableTo 单源，禁两处各写）")
    void adjacencyDerivesFromSameDeclaration() {
        java.util.Map<BindingType, Set<BindingType>> adj = policy.adjacency();
        assertNotNull(adj);
        assertTrue(adj.get(BindingType.DATASET).contains(BindingType.OBJECT_TYPE),
                "DATASET 邻接需含 OBJECT_TYPE（MAPPING 边）");
        assertTrue(adj.get(BindingType.OBJECT_TYPE).contains(BindingType.KNOWLEDGE_BASE));
        assertTrue(adj.get(BindingType.KNOWLEDGE_BASE).contains(BindingType.AI_AGENT));
    }

    /** coverage = |E_present ∩ E_req| / |E_req|；E_req 恒 3。 */
    private double coverage(Set<RequiredEdge> present) {
        List<RequiredEdge> req = policy.derive(EnumSet.allOf(BindingType.class));
        long hit = 0L;
        for (RequiredEdge e : req) {
            if (present.contains(e)) {
                hit++;
            }
        }
        return (double) hit / (double) req.size();
    }
}
