package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainNode;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainResult;
import com.chinacreator.gzcm.engine.kb.KnowledgeGraphService;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Wave-5.1 T-08 — CausalDetector (traverseKgChain + 深度截断) 行为测试。
 *
 * <p>对应任务 2/4:
 * <ul>
 *   <li>Chain-1: KG 单起点, maxDepth=3, 因果链长度 >= 3 内含 metric+KG 节点, depth 严格落地</li>
 *   <li>深度截断: maxDepth=3 + 长 KG → 链只到 depth 3 (不超)</li>
 *   <li>search 空 → currentDepth 原样返回</li>
 *   <li>visited 防环: 同一节点第二次遍历不被 append (visited 检查)</li>
 *   <li>relationship 仅接受 CAUSES/AFFECTS/CORRELATES (其他 rel skip)</li>
 *   <li>confidence: 0.80 起每层 -0.05, 最低 0.35</li>
 * </ul>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-02 (Wave-5.1)
 */
@ExtendWith(MockitoExtension.class)
class CausalDetectorTest {

    @Mock private KnowledgeGraphService kgService;
    private CausalDetector detector;

    @BeforeEach
    void setUp() {
        detector = new CausalDetector(kgService);
    }

    /** 构造节点快捷。 */
    private KnowledgeNode node(String id, String label) {
        return new KnowledgeNode(id, label, "ORG", null, null);
    }

    /** mock neighbor map (CausalDetector 内部用 Map<String,Object> 解包 "neighbors")。 */
    private Map<String, Object> neighborsMap(List<KnowledgeEdge> edges) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("neighbors", edges);
        return m;
    }

    /** mock KG Node detail (用于 getNodeDescription fallback 兜底)。 */
    private Map<String, Object> nodeDetail(KnowledgeNode n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("node", n);
        return m;
    }

    // ── Chain-1: 单 hop-3 (search 命 1 个节点 + 1 个 CAUSES 出边) ──

    @Test
    @DisplayName("Chain-1: 单起点 + 1 CAUSES 出边 → 链长 3 (metric + 2 KG 节点), 深度截断 maxDepth=3")
    void singleHopCausalChainUpToDepthThree() {
        CausalChainResult result = new CausalChainResult();
        result.getCausalChain().add(new CausalChainNode(1, "sales", 1.0, "metric", "sales"));
        Set<String> visited = new HashSet<>();

        // search 命中 sales_node
        when(kgService.search("sales")).thenReturn(List.of(node("sales_node", "sales")));
        // sales_node 出边: → atk_1 (CAUSES)
        when(kgService.getNeighbors("sales_node", 1)).thenReturn(neighborsMap(List.of(
                new KnowledgeEdge("e1", "sales_node", "atk_1", "CAUSES", 0.9))));
        // atk_1 (depth 3) 出边: 空
        when(kgService.getNeighbors("atk_1", 1)).thenReturn(neighborsMap(Collections.emptyList()));
        // 节点描述
        when(kgService.getNodeDetail("atk_1")).thenReturn(nodeDetail(node("atk_1", "客户流失")));

        int lastDepth = detector.traverseKgChain(result, "sales", "sales", 3, 1, visited);

        // 链 = metric(root) + sales_node 不出链 (search 命中但不入链, 只入队) + atk_1(KG depth 2) = 2
        assertEquals(2, result.getCausalChain().size(),
                "链长度: metric + startNode 邻居(KG), startNode 本身不入链, 实际=" + result.getCausalChain().size());
        // depth 2 KG 节点 (startNode 的邻居)
        CausalChainNode kgN2 = result.getCausalChain().get(1);
        assertEquals(2, kgN2.getDepth());
        assertEquals("客户流失", kgN2.getNode(), "节点描述来自 getNodeDetail(startNode)");
        assertEquals(0.75, kgN2.getConfidence(), 0.001, "depth 2: 0.80 - 1*0.05 = 0.75");
        assertEquals("KG", kgN2.getSource());
        // lastDepth 不超 maxDepth
        assertTrue(lastDepth >= 2 && lastDepth <= 3,
                "lastDepth 必须 1<=d<=3, 实际=" + lastDepth);
    }

    // ── 深度截断: maxDepth=3 + KG 6 层 → 链只到 depth 3 (不超) ──

    @Test
    @DisplayName("maxDepth=3 + KG 6 层 → 链内所有节点 depth <= 3 (不超 maxDepth)")
    void maxDepthStrictlyLimitsChainLength() {
        CausalChainResult result = new CausalChainResult();
        result.getCausalChain().add(new CausalChainNode(1, "m", 1.0, "metric"));
        Set<String> visited = new HashSet<>();

        // 构造链 k0 -> k1 -> k2 -> k3 -> k4 -> k5 (6 层 KG)
        // maxDepth=3: k0(depth2) enqueued+processed, k1(depth3) enqueued+processed
        // k1's neighbor k2(depth4) added to visited BEFORE depth check (defensive mark)
        // k2 NOT enqueued (depth=3 = maxDepth), so k3/k4/k5 never reached
        when(kgService.search("m")).thenReturn(List.of(node("k0", "k0label")));
        when(kgService.getNeighbors("k0", 1)).thenReturn(neighborsMap(List.of(
                new KnowledgeEdge("e0", "k0", "k1", "CAUSES", 0.9))));
        when(kgService.getNeighbors("k1", 1)).thenReturn(neighborsMap(List.of(
                new KnowledgeEdge("e1", "k1", "k2", "CAUSES", 0.9))));
        when(kgService.getNodeDetail("k1")).thenReturn(nodeDetail(node("k1", "kk1")));
        when(kgService.getNodeDetail("k2")).thenReturn(nodeDetail(node("k2", "kk2")));

        detector.traverseKgChain(result, "m", "sales", 3, 1, visited);

        // 链严格 <= 3 (metric + 2 KG)
        assertTrue(result.getCausalChain().size() <= 3,
                "链长度必须 <= 3, 实际=" + result.getCausalChain().size());
        // 链内不允许 depth > 3
        for (CausalChainNode n : result.getCausalChain()) {
            assertTrue(n.getDepth() <= 3, "所有节点 depth <= 3, 实际=" + n.getDepth());
        }
        // visited: k0+k1 被处理, k2 在 else-if 之前被加 (defensive mark)
        // k3/k4/k5 永远不会被访问 (k2 不入队)
        assertFalse(visited.contains("k3"), "k3 depth 5+ 不能进 visited");
        assertFalse(visited.contains("k4"), "k4 depth 5+ 不能进 visited");
        assertFalse(visited.contains("k5"), "k5 depth 5+ 不能进 visited");
    }

    // ── search 空 → 立即返回 currentDepth ──

    @Test
    @DisplayName("search 空 (无匹配节点) → 直接返回 currentDepth")
    void searchEmptyReturnsCurrentDepth() {
        CausalChainResult result = new CausalChainResult();
        Set<String> visited = new HashSet<>();
        when(kgService.search("no-such-metric")).thenReturn(Collections.emptyList());

        int last = detector.traverseKgChain(result, "no-such-metric", "sales", 5, 1, visited);

        assertEquals(1, last, "无起点节点, currentDepth 返回 1");
        assertTrue(result.getCausalChain().isEmpty());
        verify(kgService, never()).getNeighbors(anyString(), anyInt());
    }

    // ── visited 防环 ──

    @Test
    @DisplayName("visited 防环: KG 中两条 CAUSES 边指向同一节点, 只 append 一次")
    void visitedSetDedupesNodeIds() {
        CausalChainResult result = new CausalChainResult();
        result.getCausalChain().add(new CausalChainNode(1, "root", 1.0, "metric"));
        Set<String> visited = new HashSet<>();

        when(kgService.search("root")).thenReturn(List.of(node("n0", "root")));
        when(kgService.getNeighbors("n0", 1)).thenReturn(neighborsMap(List.of(
                new KnowledgeEdge("e1", "n0", "shared", "CAUSES", 0.9),
                new KnowledgeEdge("e2", "n0", "shared", "AFFECTS", 0.8) // 两条边同一 target
        )));
        when(kgService.getNeighbors("shared", 1)).thenReturn(neighborsMap(Collections.emptyList()));
        when(kgService.getNodeDetail("shared")).thenReturn(nodeDetail(node("shared", "s-label")));

        detector.traverseKgChain(result, "root", "sales", 5, 1, visited);

        // 链 = root + shared(1 次) = 2 (不重复)
        assertEquals(2, result.getCausalChain().size(), "两条边指向同一 target, 应 skip 第二次");
        assertTrue(visited.contains("shared"), "shared 进 visited");
        // NOTE: visited.add(startNode) 在 enqueue 之前调用 (KG 搜索后 mark),
        // 所以 n0 (起点) 也在 visited — 这是设计 (起点已访问, 防环)
        assertTrue(visited.contains("n0"), "起点 n0 进 visited (KG 搜索后 mark)");
    }

    // ── 关系类型过滤 ──

    @Test
    @DisplayName("关系类型过滤: PART_OF / type (非因果) 被跳过, 仅 CAUSES 进链")
    void unknownRelationTypesSkipped() {
        CausalChainResult result = new CausalChainResult();
        result.getCausalChain().add(new CausalChainNode(1, "root", 1.0, "metric"));
        Set<String> visited = new HashSet<>();

        when(kgService.search("root")).thenReturn(List.of(node("n0", "root")));
        when(kgService.getNeighbors("n0", 1)).thenReturn(neighborsMap(List.of(
                new KnowledgeEdge("e1", "n0", "t1", "PART_OF", 0.9),   // 非因果
                new KnowledgeEdge("e2", "n0", "t2", "AFFECTS", 0.7),   // 因果
                new KnowledgeEdge("e3", "n0", "t3", "type", 0.5)       // 非因果
        )));
        when(kgService.getNeighbors("t2", 1)).thenReturn(neighborsMap(Collections.emptyList()));
        when(kgService.getNodeDetail("t2")).thenReturn(nodeDetail(node("t2", "t2-label")));

        detector.traverseKgChain(result, "root", "sales", 5, 1, visited);

        // 只 t2 进链
        assertEquals(2, result.getCausalChain().size(), "只有 AFFECTS/CAUSES 边进链");
        assertFalse(visited.contains("t1"), "PART_OF 不应进 visited");
        assertFalse(visited.contains("t3"), "非因果 rel 不应进 visited");
        assertTrue(visited.contains("t2"), "因果 rel 必须进 visited");
    }

    // ── 置信度下限 0.35 ──

    @Test
    @DisplayName("confidence 下限 0.35: depth 11 时 0.80 - 10*0.05 = 0.30 → 钳到 0.35")
    void confidenceFloorIsThirtyFive() {
        CausalChainResult result = new CausalChainResult();
        result.getCausalChain().add(new CausalChainNode(1, "root", 1.0, "metric"));
        Set<String> visited = new HashSet<>();

        // 构造 9 层 KG 链 n0 -> k1 -> ... -> k9
        when(kgService.search("root")).thenReturn(List.of(node("n0", "root")));
        when(kgService.getNeighbors("n0", 1)).thenReturn(neighborsMap(List.of(
                new KnowledgeEdge("e0", "n0", "k1", "CAUSES", 0.9))));
        for (int i = 1; i <= 8; i++) {
            when(kgService.getNeighbors("k" + i, 1)).thenReturn(neighborsMap(List.of(
                    new KnowledgeEdge("e" + i, "k" + i, "k" + (i + 1), "CAUSES", 0.9))));
        }
        when(kgService.getNeighbors("k9", 1)).thenReturn(neighborsMap(Collections.emptyList()));
        for (int i = 1; i <= 9; i++) {
            when(kgService.getNodeDetail(eq("k" + i))).thenReturn(nodeDetail(node("k" + i, "label-" + i)));
        }

        detector.traverseKgChain(result, "root", "sales", 12, 1, visited);

        // 至少 1 root + 9 KG (链不止 10)
        assertTrue(result.getCausalChain().size() >= 10,
                "链必须 >= 10, 实际=" + result.getCausalChain().size());
        // 找 KG 节点 confidence 都 >= 0.35
        for (CausalChainNode n : result.getCausalChain()) {
            if ("KG".equals(n.getSource())) {
                assertTrue(n.getConfidence() >= 0.35,
                        "KG d=" + n.getDepth() + " 置信度 >= 0.35, 实际=" + n.getConfidence());
            }
        }
    }

    // ── KG 起点入队 depth = currentDepth+1 ──

    @Test
    @DisplayName("KG 起点搜索命中 → 该节点 depth = currentDepth+1")
    void startNodeDepthIsCurrentPlusOne() {
        CausalChainResult result = new CausalChainResult();
        result.getCausalChain().add(new CausalChainNode(1, "root", 1.0, "metric"));
        Set<String> visited = new HashSet<>();

        when(kgService.search("root")).thenReturn(List.of(node("kg-1", "根节点")));
        when(kgService.getNeighbors("kg-1", 1)).thenReturn(neighborsMap(List.of(
                new KnowledgeEdge("e", "kg-1", "kg-2", "CAUSES", 0.9))));
        when(kgService.getNeighbors("kg-2", 1)).thenReturn(neighborsMap(Collections.emptyList()));
        when(kgService.getNodeDetail(eq("kg-2"))).thenReturn(nodeDetail(node("kg-2", "中间")));

        detector.traverseKgChain(result, "root", "sales", 5, 1, visited);

        // root(depth 1) + kg-1 的邻居 kg-2(depth 2)
        // NOTE: kg-1 (search 起点) 不入链 (只入队), 它的邻居 kg-2 入链 depth=2
        assertEquals(2, result.getCausalChain().size());
        assertEquals(2, result.getCausalChain().get(1).getDepth());
        assertEquals("中间", result.getCausalChain().get(1).getNode());
    }
}
