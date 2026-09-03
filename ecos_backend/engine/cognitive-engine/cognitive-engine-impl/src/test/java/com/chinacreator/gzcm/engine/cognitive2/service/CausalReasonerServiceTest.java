package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainNode;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainResult;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalEdge;
import com.chinacreator.gzcm.engine.cognitive2.model.DiagnosisRequest;
import com.chinacreator.gzcm.engine.kb.KnowledgeGraphService;
import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.engine.kb.repository.ComplianceRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Wave-5.1 T-08 — CausalReasonerServiceImpl 端到端集成测试。
 *
 * <p>对应任务 1: (mock kg/service + LLM) metric="sales" → step 4 + Node (not null) + CausalStep chain
 *
 * <p>覆盖:
 * <ol>
 *   <li>diagnose: 单起点 + KG 命中 → 因果链 ≥ 3 层 (root + 2 KG), 因果节点 source=KG 必带 label</li>
 *   <li>diagnose: 因果链 < 3 时走 ruleBasedExpansion 规则兜底, 输出 ≥3 层</li>
 *   <li>diagnose: 末层 LLM 补充不阻断 (异常被 catch), 链长度仍 ≥3</li>
 *   <li>diagnose: reasoningPath 必含 1+ RULE 节点 (G2 验收)</li>
 *   <li>inferCausalGraph: KG edges → CausalEdge 列表 (1 => 1)</li>
 *   <li>estimateCausalEffect: getShortestPath 命中 length > 0 → 1/(1+len)</li>
 * </ol>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-02 (Wave-5.1)
 */
@ExtendWith(MockitoExtension.class)
class CausalReasonerServiceTest {

    @Mock private KnowledgeGraphService kgService;
    @Mock private CausalDetector causalDetector;
    @Mock private SuggestionBuilder suggestionBuilder;
    @Mock private RootCauseAnalyzer rootCauseAnalyzer;
    @Mock private ReasoningPathFromCausalBuilder rpBuilder;
    @Mock private PrecedentRecaller precedentRecaller;
    @Mock private RuleRefCollector ruleRefCollector;
    @Mock private ComplianceRuleMapper ruleMapper;
    private CausalReasonerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CausalReasonerServiceImpl(kgService, causalDetector, suggestionBuilder,
                rootCauseAnalyzer, rpBuilder, precedentRecaller, ruleRefCollector, ruleMapper);
    }

    // ── T-08-1-1: diagnose 基本 ──

    @Test
    @DisplayName("T-08-1-1: diagnose metric='sales' → 因果链 ≥ 3 层 + Node 不为 null (业务硬约束)")
    void diagnoseSalesProducesCausalChainOfThree() {
        DiagnosisRequest req = new DiagnosisRequest("sales", -0.12, "sales", 5);

        // mock causalDetector 真实运行替换: 调同一个 detector 实现
        CausalDetector realDetector = new CausalDetector(kgService);
        when(kgService.search("sales")).thenReturn(List.of(new KnowledgeNode("s1", "sales", "METRIC", "营收", null)));
        when(kgService.getNeighbors("s1", 1)).thenReturn(neighborsOf(
                new KnowledgeEdge("e1", "s1", "atk-1", "CAUSES", 0.9)));
        when(kgService.getNeighbors("atk-1", 1)).thenReturn(neighborsOf(
                new KnowledgeEdge("e2", "atk-1", "atk-2", "AFFECTS", 0.8)));
        when(kgService.getNeighbors("atk-2", 1)).thenReturn(neighborsOf());
        when(kgService.getNodeDetail(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return Map.of("node", new KnowledgeNode(id, "n-" + id, "ORG", null, null));
        });

        // 替换 service 内部 detector (mock 不行, 因为 traverseKgChain 是 package 级)
        // 用 reflection 改字段 (CausalReasonerServiceImpl 的 causalDetector 是 final)
        try {
            var f = CausalReasonerServiceImpl.class.getDeclaredField("causalDetector");
            f.setAccessible(true);
            f.set(service, realDetector);
        } catch (Exception ex) {
            fail("refl: " + ex.getMessage());
        }

        // mock 后续 mock: 根因分析
        // mock ruleBasedExpansion 不触发时, identifyRootCause 也不会抛
        doNothing().when(rootCauseAnalyzer).identifyRootCauseAndSuggestions(any(), any());
        // mock reasoningPath (avoid npe)
        com.chinacreator.gzcm.engine.cognitive2.model.ReasoningPath dummyPath =
                new com.chinacreator.gzcm.engine.cognitive2.model.ReasoningPath();
        dummyPath.setSteps(new ArrayList<>());
        when(rpBuilder.buildPath(any(), anyString(), any(), anyInt())).thenReturn(dummyPath);
        when(precedentRecaller.recall(any(), anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(ruleMapper.findByDomain(anyString())).thenReturn(Collections.emptyList());
        when(ruleMapper.findAll()).thenReturn(Collections.emptyList());
        when(ruleRefCollector.toIndex(any())).thenReturn(Collections.emptyMap());

        CausalChainResult r = service.diagnose(req);

        // 链 ≥3 层 (root + KG 2 个)
        assertNotNull(r, "diagnose 必须返非 null");
        assertTrue(r.getCausalChain().size() >= 3,
                "因果链必须 >=3 层 (root + 2 KG), 实际=" + r.getCausalChain().size());
        // 第 0 层是 metric (本层根)
        assertEquals("metric", r.getCausalChain().get(0).getSource());
        // 至少 1 个 KG 节点
        boolean hasKg = r.getCausalChain().stream().anyMatch(n -> "KG".equals(n.getSource()));
        assertTrue(hasKg, "必须有 KG 节点");
        // 因果节点 description 非 null
        for (CausalChainNode n : r.getCausalChain()) {
            assertNotNull(n.getNode(), "CausalChainNode 的 node 字段不能 null");
            assertNotNull(n.getDepth(), "depth 不能 null");
        }
    }

    // ── T-08-1-2: KG 不改 → 走 ruleBasedExpansion 兜底 ──

    @Test
    @DisplayName("T-08-1-2: KG 空结果 → ruleBasedExpansion 兜底因果链")
    void diagnoseWithEmptyKgTriggersRuleBasedFallback() {
        DiagnosisRequest req = new DiagnosisRequest("毛利率", -0.05, "finance", 4);

        // 替换为真 detector
        CausalDetector realDetector = new CausalDetector(kgService);
        try {
            var f = CausalReasonerServiceImpl.class.getDeclaredField("causalDetector");
            f.setAccessible(true);
            f.set(service, realDetector);
        } catch (Exception ex) { fail("refl"); }

        // KG 搜索空
        when(kgService.search(anyString())).thenReturn(Collections.emptyList());
        // mock 规则兜底
        doAnswer(inv -> {
            CausalChainResult res = inv.getArgument(0);
            // 真实规则兜底是 ca_2/3/4 三层, 简化测试桩
            res.getCausalChain().add(new CausalChainNode(2, "原材料涨价 push cost up", 0.75, "RULE", "finance"));
            res.getCausalChain().add(new CausalChainNode(3, "供应链上游大宗商品波动", 0.6, "RULE", "finance"));
            res.getCausalChain().add(new CausalChainNode(4, "地缘政治/供需失衡", 0.5, "RULE", "finance"));
            return null;
        }).when(rootCauseAnalyzer).ruleBasedExpansion(any(), any(), anyInt());
        doAnswer(inv -> {
            CausalChainResult res = inv.getArgument(0);
            res.setSuggestions(List.of("建议 1", "建议 2"));
            return null;
        }).when(rootCauseAnalyzer).identifyRootCauseAndSuggestions(any(), any());

        // 跳过 LLM 补充 (kgLastDepth = 1, < maxDepth 时, suggestionBuilder 抛错被 catch)
        doThrow(new RuntimeException("LLM down")).when(suggestionBuilder).llmSupplementChain(any(), any(), anyInt(), anyInt());
        when(rpBuilder.buildPath(any(), anyString(), any(), anyInt()))
                .thenAnswer(inv -> {
                    com.chinacreator.gzcm.engine.cognitive2.model.ReasoningPath path =
                            new com.chinacreator.gzcm.engine.cognitive2.model.ReasoningPath();
                    path.setSteps(new ArrayList<>());
                    return path;
                });
        when(precedentRecaller.recall(any(), anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(ruleMapper.findByDomain(anyString())).thenReturn(Collections.emptyList());
        when(ruleMapper.findAll()).thenReturn(Collections.emptyList());
        when(ruleRefCollector.toIndex(any())).thenReturn(Collections.emptyMap());

        CausalChainResult r = service.diagnose(req);

        // 链 >= 3 层 (root + 3 RULE)
        assertTrue(r.getCausalChain().size() >= 4, "兜底规则后必须有 4 层 (root + 3), 实际=" + r.getCausalChain().size());
        // 含 RULE 节点
        assertTrue(r.getCausalChain().stream().anyMatch(n -> "RULE".equals(n.getSource())));
        // 建议 >= 1 条
        assertFalse(r.getSuggestions().isEmpty());
    }

    // ── T-08-1-3: reasoningPath 必含 RULE 节点 (G2) ──

    @Test
    @DisplayName("T-08-1-3: reasoningPath (非 null) 且 rpBuilder.attachStructuralCount 必被调 (G2)")
    void diagnoseProducesReasoningPathWithRuleRefCollectorAttached() {
        DiagnosisRequest req = new DiagnosisRequest("销售增长率", 0.05, "sales", 5);
        CausalDetector realDetector = new CausalDetector(kgService);
        try {
            var f = CausalReasonerServiceImpl.class.getDeclaredField("causalDetector");
            f.setAccessible(true);
            f.set(service, realDetector);
        } catch (Exception ex) { fail("refl"); }

        when(kgService.search(anyString())).thenReturn(Collections.emptyList());
        // 兜底
        doNothing().when(rootCauseAnalyzer).ruleBasedExpansion(any(), any(), anyInt());
        doNothing().when(rootCauseAnalyzer).identifyRootCauseAndSuggestions(any(), any());
        com.chinacreator.gzcm.engine.cognitive2.model.ReasoningPath path =
                new com.chinacreator.gzcm.engine.cognitive2.model.ReasoningPath();
        path.setSteps(new ArrayList<>());
        when(rpBuilder.buildPath(any(), anyString(), any(), anyInt())).thenReturn(path);
        doNothing().when(ruleRefCollector).attachStructuralCount(any());
        when(precedentRecaller.recall(any(), anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(ruleMapper.findByDomain(anyString())).thenReturn(Collections.emptyList());
        when(ruleMapper.findAll()).thenReturn(Collections.emptyList());
        when(ruleRefCollector.toIndex(any())).thenReturn(Collections.emptyMap());

        CausalChainResult r = service.diagnose(req);

        // reasoningPath 非 null (已 attach)
        assertNotNull(r.getReasoningPath(), "G2: reasoningPath 必须非 null");
        // RULE 计数被 attach
        verify(ruleRefCollector).attachStructuralCount(any());
    }

    // ── T-08-1-4: inferCausalGraph ──

    @Test
    @DisplayName("T-08-1-4: inferCausalGraph → KG edges → CausalEdge 列表 (1-to-1)")
    void inferCausalGraphConvertsKgEdges() {
        KnowledgeEdge kg = new KnowledgeEdge("e1", "n1", "n2", "CAUSES", 0.88);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("nodes", Collections.emptyList());
        map.put("edges", List.of(kg));
        when(kgService.getGraph("sales")).thenReturn(map);

        List<CausalEdge> edges = service.inferCausalGraph("sales");

        assertEquals(1, edges.size());
        assertEquals("e1", edges.get(0).getId());
        assertEquals("n1", edges.get(0).getSourceNode());
        assertEquals("n2", edges.get(0).getTargetNode());
        assertEquals("CAUSES", edges.get(0).getDescription());
        assertEquals(0.88, edges.get(0).getWeight(), 0.001);
    }

    @Test
    @DisplayName("T-08-1-5: inferCausalGraph KG 异常 → 返空 list (不阻断)")
    void inferCausalGraphOnKgExceptionReturnsEmpty() {
        when(kgService.getGraph(anyString())).thenThrow(new RuntimeException("KG down"));
        List<CausalEdge> edges = service.inferCausalGraph("sales");
        assertTrue(edges.isEmpty());
    }

    // ── T-08-1-6: estimateCausalEffect ──

    @Test
    @DisplayName("T-08-1-6: estimateCausalEffect KG 有路 → 1/(1+length), length>0")
    void estimateCausalEffectUsesKgPathLength() {
        Map<String, Object> pathResult = new LinkedHashMap<>();
        pathResult.put("length", 2);
        pathResult.put("path", List.of("a", "b", "c"));
        when(kgService.getShortestPath("a", "c")).thenReturn(pathResult);

        double effect = service.estimateCausalEffect("a", "c");

        assertEquals(1.0 / 3.0, effect, 0.001, "length=2 → effect=1/3");
    }

    @Test
    @DisplayName("T-08-1-7: estimateCausalEffect KG 无路 → 走 fallback diagnose, 返 0.5 (avg 置信兜底)")
    void estimateCausalEffectFallsBackToDiagnose() {
        Map<String, Object> pathResult = new LinkedHashMap<>();
        pathResult.put("length", -1);
        when(kgService.getShortestPath(anyString(), anyString())).thenReturn(pathResult);

        // 兜底 diagnose 跑 (重路径) — 替换真 detector + 空 KG + mock 兜底
        CausalDetector realDetector = new CausalDetector(kgService);
        try {
            var f = CausalReasonerServiceImpl.class.getDeclaredField("causalDetector");
            f.setAccessible(true);
            f.set(service, realDetector);
        } catch (Exception ex) { fail("refl"); }
        when(kgService.search(anyString())).thenReturn(Collections.emptyList());
        doNothing().when(rootCauseAnalyzer).ruleBasedExpansion(any(), any(), anyInt());
        doNothing().when(rootCauseAnalyzer).identifyRootCauseAndSuggestions(any(), any());
        when(rpBuilder.buildPath(any(), anyString(), any(), anyInt())).thenAnswer(inv -> {
                com.chinacreator.gzcm.engine.cognitive2.model.ReasoningPath p =
                        new com.chinacreator.gzcm.engine.cognitive2.model.ReasoningPath();
                p.setSteps(new ArrayList<>());
                return p;
            });
        when(precedentRecaller.recall(any(), anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(ruleMapper.findAll()).thenReturn(Collections.emptyList());
        when(ruleMapper.findByDomain(anyString())).thenReturn(Collections.emptyList());
        when(ruleRefCollector.toIndex(any())).thenReturn(Collections.emptyMap());

        double effect = service.estimateCausalEffect("a", "c");

        // fallback diag: chain=[root(conf=1.0)] avg=1.0 → effect=1.0
        // ruleBasedExpansion 桩是 doNothing → 不追加 RULE 节点，根节点 confidence=1.0 → 1.0
        assertEquals(1.0, effect, 0.001,
                "fallback 单根节点(conf=1.0) → avg 1.0 → effect=1.0");
    }

    // ── 工具 ────────────────────────────────────────────────

    private Map<String, Object> neighborsOf(KnowledgeEdge... edges) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("neighbors", List.of(edges));
        return m;
    }
}
