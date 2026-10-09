package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-07 F07-02 完整度计算单点验收（R-27 固定分母 3）。
 *
 * <p>覆盖 PRD-01 §2.5 三夹具（完整链 1.0 / 缺 MAPPING 0.667 / 纯孤岛 0）+ 空场景/无必需边二元 null 语义，
 * 并断言覆盖率分母为“边”而非“节点”（破 X-23/X-25 节点占比）。纯内存计算，不依赖 DB/Spring 上下文。</p>
 */
class ScenarioCompletenessServiceTest {

    private static final Instant VERIFIED_AT = Instant.parse("2026-09-29T00:00:00Z");

    private ScenarioCompletenessService serviceVerifiedProbe() {
        return new ScenarioCompletenessService(
                new FixedDenominatorEdgePolicy(),
                probeWith(edge -> new ContractSnapshot(7L, VERIFIED_AT)),
                new IslandBindingGuard());
    }

    /** 用 Mockito 桩装配 EdgeContractProbe 的 ObjectProvider（facade 由 lambda 决定）。 */
    @SuppressWarnings("unchecked")
    private EdgeContractProbe probeWith(ContractProbeFacade facade) {
        ObjectProvider<ContractProbeFacade> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(facade);
        return new EdgeContractProbe(provider);
    }

    private BindingNode node(String id, BindingType type) {
        return new BindingNode(id, id, type);
    }

    @Test
    @DisplayName("PRD 夹具 1：三必需边全具备 → coverage=1.0/verdict=OK")
    void fullChainYieldsCoverageOne() {
        List<BindingNode> bindings = Arrays.asList(
                node("ds-1", BindingType.DATASET),
                node("ot-1", BindingType.OBJECT_TYPE),
                node("kb-1", BindingType.KNOWLEDGE_BASE),
                node("ai-1", BindingType.AI_AGENT));
        List<LinkEdge> links = Arrays.asList(
                new LinkEdge("ds-1", "ot-1", EdgeType.MAPPING),
                new LinkEdge("ot-1", "kb-1", EdgeType.EXTRACTION),
                new LinkEdge("kb-1", "ai-1", EdgeType.COGNITION));

        CompletenessReport r = serviceVerifiedProbe().compute("s-1", bindings, links);

        assertNotNull(r.getCoverage());
        assertEquals(1.0d, r.getCoverage(), 1e-9);
        assertSame(CompletenessReport.Verdict.OK, r.getVerdict());
        assertEquals(3, r.getPresentEdges().size());
    }

    @Test
    @DisplayName("PRD 夹具 2：缺 MAPPING 连边 → coverage≈0.667/PARTIAL")
    void missingMappingEdgeYieldsPointSixSeven() {
        List<BindingNode> bindings = Arrays.asList(
                node("ds-1", BindingType.DATASET),
                node("ot-1", BindingType.OBJECT_TYPE),
                node("kb-1", BindingType.KNOWLEDGE_BASE),
                node("ai-1", BindingType.AI_AGENT));
        List<LinkEdge> links = Arrays.asList(
                new LinkEdge("ot-1", "kb-1", EdgeType.EXTRACTION),
                new LinkEdge("kb-1", "ai-1", EdgeType.COGNITION));

        CompletenessReport r = serviceVerifiedProbe().compute("s-2", bindings, links);

        assertEquals(2.0d / 3.0d, r.getCoverage(), 1e-9);
        assertSame(CompletenessReport.Verdict.PARTIAL, r.getVerdict());
        assertEquals(1, r.getMissingEdges().size());
        assertEquals("MAPPING", r.getMissingEdges().get(0).getType());
    }

    @Test
    @DisplayName("PRD 夹具 3：绑定齐但无任何连边（纯孤岛）→ coverage=0，破空场景 100%")
    void pureIslandYieldsZero() {
        List<BindingNode> bindings = Arrays.asList(
                node("ds-1", BindingType.DATASET),
                node("ot-1", BindingType.OBJECT_TYPE),
                node("kb-1", BindingType.KNOWLEDGE_BASE),
                node("ai-1", BindingType.AI_AGENT));

        CompletenessReport r = serviceVerifiedProbe().compute("s-3", bindings, Collections.emptyList());

        assertNotNull(r.getCoverage());
        assertEquals(0.0d, r.getCoverage(), 1e-9);
        assertSame(CompletenessReport.Verdict.PARTIAL, r.getVerdict());
        assertEquals(4, r.getIslands().size(), "无任何连边 → 四绑定全孤岛");
    }

    @Test
    @DisplayName("空场景（无任何绑定）→ coverage=null/EMPTY_SCENARIO，禁折算 100%")
    void emptyScenarioIsZeroNotOneHundred() {
        CompletenessReport r = serviceVerifiedProbe().compute("s-4", Collections.emptyList(), Collections.emptyList());

        assertNull(r.getCoverage(), "空场景 coverage 必 null（破 X-25）");
        assertSame(CompletenessReport.Verdict.EMPTY_SCENARIO, r.getVerdict());
    }

    @Test
    @DisplayName("无必需边（动态分母口径）→ coverage=null/NOT_APPLICABLE，非 100%")
    void noRequiredEdgeYieldsNotApplicableNotNullOneHundred() {
        RequiredEdgePolicy emptyPolicy = new RequiredEdgePolicy() {
            @Override
            public List<RequiredEdge> derive(Set<BindingType> boundTypes) {
                return new ArrayList<>();
            }

            @Override
            public java.util.Map<BindingType, Set<BindingType>> adjacency() {
                return Collections.emptyMap();
            }
        };
        ScenarioCompletenessService svc = new ScenarioCompletenessService(
                emptyPolicy,
                probeWith(edge -> new ContractSnapshot(7L, VERIFIED_AT)),
                new IslandBindingGuard());

        List<BindingNode> bindings = Arrays.asList(node("ds-1", BindingType.DATASET), node("ot-1", BindingType.OBJECT_TYPE));
        CompletenessReport r = svc.compute("s-5", bindings, Collections.emptyList());

        assertNull(r.getCoverage(), "无必需边 coverage 必 null（禁折算 100%）");
        assertSame(CompletenessReport.Verdict.NOT_APPLICABLE, r.getVerdict());
    }

    @Test
    @DisplayName("覆盖率分母=必需边数(3)，与节点数无关：6 节点仅 1 条 present 边 → 1/3，破 X-23 节点占比")
    void nodeRatioFormulaIsGone() {
        List<BindingNode> bindings = Arrays.asList(
                node("ds-1", BindingType.DATASET),
                node("ot-1", BindingType.OBJECT_TYPE),
                node("kb-1", BindingType.KNOWLEDGE_BASE),
                node("ai-1", BindingType.AI_AGENT),
                node("sec-1", BindingType.SECURITY_POLICY),
                node("if-1", BindingType.INTERFACE));
        List<LinkEdge> links = Collections.singletonList(new LinkEdge("ds-1", "ot-1", EdgeType.MAPPING));

        CompletenessReport r = serviceVerifiedProbe().compute("s-6", bindings, links);

        assertEquals(1.0d / 3.0d, r.getCoverage(), 1e-9, "分母恒为 E_req=3，非节点数 6");
        assertTrue(Math.abs(r.getCoverage() - (1.0d / 6.0d)) > 1e-6, "绝不得等于节点占比 1/6");
        assertEquals(1, r.getPresentEdges().size());
    }
}
