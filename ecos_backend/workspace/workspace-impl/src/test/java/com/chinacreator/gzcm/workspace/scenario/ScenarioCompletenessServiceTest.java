package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.scenario.EdgeContractProbe.BindingRef;
import com.chinacreator.gzcm.workspace.scenario.EdgeContractProbe.LinkRef;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 完整度唯一算法单测（详细设计-07 F07-02 / R-27① / C148）。
 *
 * <p>纯函数用例：对 {@link ScenarioCompletenessService#compute} 直接驱动（不触 DB）——
 * 固定分母 3（主链三跳），PRD 三示例值 1.0 / 0.67 / 0 全部可复现；空场景 coverage=null 且
 * verdict=EMPTY_SCENARIO（禁 100%）；必需边为空 → null 且 NOT_APPLICABLE；孤岛按<b>边可达性</b>
 * 判定而非节点类型（破 X-27 的 resource 计数桩，F07-04 反例 islandDetectionUsesEdgeReachabilityNotNodeType）。</p>
 */
class ScenarioCompletenessServiceTest {

    private final ScenarioCompletenessService service =
            new ScenarioCompletenessService(mock(JdbcTemplate.class), new RequiredEdgePolicy(), new LinkContractProbe());

    private static BindingRef b(String id, String type) {
        return new BindingRef(id, type, "ref-" + id);
    }

    /** 主链三跳全齐：DATASET→OBJECT_TYPE→KNOWLEDGE_BASE→AI_AGENT。 */
    private static List<BindingRef> fullChainBindings() {
        return List.of(b("d1", "DATASET"), b("o1", "OBJECT_TYPE"), b("k1", "KNOWLEDGE_BASE"), b("a1", "AI_AGENT"));
    }

    private static List<LinkRef> fullChainLinks() {
        return List.of(
                new LinkRef("d1", "o1", "MAPPING", "ents_map_001"), // 非占位契约
                new LinkRef("o1", "k1", "EXTRACTION", "ent"),
                new LinkRef("k1", "a1", "COGNITION", "ent"));
    }

    @Test
    void fullChainYieldsCoverageOne() {
        CompletenessVO.Result r = service.compute(fullChainBindings(), fullChainLinks());
        assertEquals(1.0, r.coverage(), 1e-9, "主链三边全在 → coverage=1.0");
        assertEquals("OK", r.verdict());
        assertEquals(3, r.presentEdges().size());
        assertTrue(r.missingEdges().isEmpty());
    }

    @Test
    void missingMappingEdgeYieldsPointSixSeven() {
        // 仅缺 MAPPING 一条 → 2/3 = 0.667（round3）
        List<BindingRef> bids = fullChainBindings();
        List<LinkRef> links = List.of(
                new LinkRef("o1", "k1", "EXTRACTION", "ent"),
                new LinkRef("k1", "a1", "COGNITION", "ent"));
        CompletenessVO.Result r = service.compute(bids, links);
        double expected = Math.round((2.0 / 3.0) * 1000.0) / 1000.0; // 0.667
        assertEquals(expected, r.coverage(), 1e-9, "缺一条必需边 → 2/3");
        assertEquals("PARTIAL", r.verdict());
        assertEquals(1, r.missingEdges().size());
        assertEquals("MAPPING", r.missingEdges().get(0).getType());
    }

    @Test
    void placeholderMappingContractDoesNotCountAsPresent() {
        // X-48：source_contract='placeholder-…' 的映射边不算 present（契约校验通过才 present）
        List<LinkRef> links = List.of(
                new LinkRef("d1", "o1", "MAPPING", "placeholder-d1-o1"), // 占位 → 不算
                new LinkRef("o1", "k1", "EXTRACTION", "ent"),
                new LinkRef("k1", "a1", "COGNITION", "ent"));
        CompletenessVO.Result r = service.compute(fullChainBindings(), links);
        assertEquals(Math.round((2.0 / 3.0) * 1000.0) / 1000.0, r.coverage(), 1e-9,
                "占位契约的 MAPPING 边必须降为 missing");
        List<String> missingTypes = r.missingEdges().stream().map(CompletenessVO.MissingEdgeVO::getType).toList();
        assertTrue(missingTypes.contains("MAPPING"));
    }

    @Test
    void pureIslandYieldsZero() {
        // 绑定了三类主链节点但 0 连边 → 三条必需边全 missing → coverage=0，且三节点皆孤岛
        List<BindingRef> bids = List.of(b("d1", "DATASET"), b("o1", "OBJECT_TYPE"), b("k1", "KNOWLEDGE_BASE"));
        CompletenessVO.Result r = service.compute(bids, List.of());
        assertEquals(0.0, r.coverage(), 1e-9, "有绑定无边 → coverage=0（非 100%）");
        assertEquals("ISOLATED", r.verdict());
        assertEquals(3, r.islands().size(), "无任何连边的绑定全部判孤岛");
    }

    @Test
    void emptyScenarioIsZeroNotOneHundred() {
        CompletenessVO.Result r = service.compute(List.of(), List.of());
        assertNull(r.coverage(), "空场景 coverage 必须 null（前端显示 —，禁 100%）");
        assertEquals("EMPTY_SCENARIO", r.verdict());
    }

    @Test
    void noRequiredEdgeYieldsNotApplicableNotNullOneHundred() {
        // 空分母 → null + NOT_APPLICABLE（禁折算 100%）。用空 requiredEdges 策略驱动该分支。
        ScenarioCompletenessService emptyPolicyService = new ScenarioCompletenessService(
                mock(JdbcTemplate.class),
                new RequiredEdgePolicy() {
                    @Override
                    public List<RequiredEdgePolicy.RequiredEdge> requiredEdges() {
                        return List.of();
                    }
                },
                new LinkContractProbe());
        CompletenessVO.Result r = emptyPolicyService.compute(fullChainBindings(), fullChainLinks());
        assertNull(r.coverage(), "必需边为空 → coverage null");
        assertEquals("NOT_APPLICABLE", r.verdict());
    }

    @Test
    void islandDetectionUsesEdgeReachabilityNotNodeType() {
        // F07-04 反例（破 X-27）：DATASET 无连边即孤岛 —— 判定靠可达性而非"是否 resource 类型"
        List<BindingRef> bids = List.of(
                b("d1", "DATASET"), b("o1", "OBJECT_TYPE"), b("k1", "KNOWLEDGE_BASE"), b("a1", "AI_AGENT"));
        List<LinkRef> links = List.of(
                new LinkRef("o1", "k1", "EXTRACTION", "ent"),
                new LinkRef("k1", "a1", "COGNITION", "ent"));
        List<CompletenessVO.Island> islands = service.islands(bids, links);
        List<String> islandIds = islands.stream().map(CompletenessVO.Island::getBindingId).toList();
        assertTrue(islandIds.contains("d1"), "DATASET 岛（无任何入/出边）必被检出");
        assertFalse(islandIds.contains("o1"), "有连边的节点不得判孤岛");
        assertFalse(islandIds.contains("a1"), "有连边的节点不得判孤岛");
        assertNotNull(islands.get(0).getReason());
    }
}
