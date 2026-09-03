package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEdgeMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Wave-5.1 T-07 — KnowledgeGraphServiceImpl 行为测试 (P0-3 反向摄验)。
 *
 * <p>覆盖:
 * <ul>
 *   <li>search(null) / search("") → emptyList, 不查库 (mock mapper 行为摄验)</li>
 *   <li>search("Sales") → 拼 %Sales% 调 mapper.searchByLabelPattern</li>
 *   <li>getShortestPath → Neo4j 缺席时 PG fallback 返回空路径 (P0-3 同根因: 业务侧不阻断)</li>
 *   <li>getGraph(domain=null) → findAll; 非 null → findByDomain</li>
 *   <li>createNode / createEdge 生成 UUID + 写库 (通过 mock mapper)</li>
 *   <li>getDataSource → PG 字样 + 节点计数</li>
 * </ul>
 *
 * @author ECOS KB Engine Team
 * @since 2026-09-02 (Wave-5.1)
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeGraphServiceImplTest {

    @Mock private KnowledgeNodeMapper nodeMapper;
    @Mock private KnowledgeEdgeMapper edgeMapper;
    private KnowledgeGraphServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeGraphServiceImpl(nodeMapper, edgeMapper);
    }

    // ── T-07-3-1: search() 空 query 返回 emptyList ──

    @Test
    @DisplayName("T-07-3-1: search(null) → emptyList (P0-3: 空 query 不查库)")
    void searchNullReturnsEmptyList() {
        assertTrue(service.search(null).isEmpty());
        verify(nodeMapper, never()).searchByLabelPattern(anyString());
    }

    @Test
    @DisplayName("T-07-3-2: search(blank) → emptyList")
    void searchBlankReturnsEmptyList() {
        assertTrue(service.search("   ").isEmpty());
        verify(nodeMapper, never()).searchByLabelPattern(anyString());
    }

    // ── T-07-3-3: search() 非空 query 调 mapper, 收到 %query% ──

    @Test
    @DisplayName("T-07-3-3: search('Sales') 把通配符拼在 Java 端再调 mapper (P0-3 反向)")
    void searchWithNonBlankInvokesMapperWithWildcards() {
        KnowledgeNode n = new KnowledgeNode("n-1", "SalesTarget", "METRIC", "desc", null);
        when(nodeMapper.searchByLabelPattern("%Sales%")).thenReturn(List.of(n));

        List<KnowledgeNode> res = service.search("Sales");

        assertEquals(1, res.size());
        assertEquals("SalesTarget", res.get(0).getLabel());
        verify(nodeMapper).searchByLabelPattern("%Sales%");
    }

    // ── getGraph ──

    @Test
    @DisplayName("getGraph(null domain) 走 findAll (全图)")
    void getGraphNoDomainUsesFindAll() {
        when(nodeMapper.findAll()).thenReturn(List.of(new KnowledgeNode("n-1", "A", "ORG", null, null)));
        when(edgeMapper.findAll()).thenReturn(List.of());
        Map<String, Object> g = service.getGraph(null);
        assertEquals(1, ((List<?>) g.get("nodes")).size());
        verify(nodeMapper).findAll();
        verify(nodeMapper, never()).findByDomain(anyString());
    }

    @Test
    @DisplayName("getGraph(domain) 走 findByDomain (按域过滤)")
    void getGraphWithDomainUsesFindByDomain() {
        when(nodeMapper.findByDomain("finance")).thenReturn(List.of(new KnowledgeNode("n-1", "A", "ORG", null, null)));
        when(edgeMapper.findAll()).thenReturn(List.of(new KnowledgeEdge("e-1", "n-1", "n-2", "CAUSES", 0.8)));
        Map<String, Object> g = service.getGraph("finance");
        assertEquals(1, ((List<?>) g.get("nodes")).size());
        verify(nodeMapper).findByDomain("finance");
    }

    // ── getNodeDetail ──

    @Test
    @DisplayName("getNodeDetail misses 返回 null")
    void getNodeDetailMissingReturnsNull() {
        when(nodeMapper.findById("missing")).thenReturn(null);
        assertNull(service.getNodeDetail("missing"));
    }

    @Test
    @DisplayName("getNodeDetail hit 返回 node + outgoing/incoming edges")
    void getNodeDetailHitReturnsNodeAndEdges() {
        KnowledgeNode n = new KnowledgeNode("n-1", "A", "ORG", null, null);
        when(nodeMapper.findById("n-1")).thenReturn(n);
        when(edgeMapper.findBySourceNodeId("n-1")).thenReturn(List.of(new KnowledgeEdge("e-1", "n-1", "n-2", "CAUSES", 0.8)));
        when(edgeMapper.findByTargetNodeId("n-1")).thenReturn(List.of(new KnowledgeEdge("e-2", "n-0", "n-1", "AFFECTS", 0.6)));
        Map<String, Object> d = service.getNodeDetail("n-1");
        assertSame(n, d.get("node"));
        assertEquals(1, ((List<?>) d.get("outgoingEdges")).size());
        assertEquals(1, ((List<?>) d.get("incomingEdges")).size());
    }

    // ── getShortestPath (PG fallback) ──

    @Test
    @DisplayName("getShortestPath PG fallback: length=-1 + note 提示需要 Neo4j")
    void getShortestPathPgFallbackReturnsEmpty() {
        Map<String, Object> r = service.getShortestPath("a", "b");
        assertEquals(-1, r.get("length"));
        assertEquals("a", r.get("source"));
        assertEquals("b", r.get("target"));
        assertTrue(((List<?>) r.get("path")).isEmpty());
        assertNotNull(r.get("note"));
    }

    // ── getNeighbors ──

    @Test
    @DisplayName("getNeighbors 返回 center + edges")
    void getNeighborsReturnsCenterAndEdges() {
        KnowledgeNode c = new KnowledgeNode("c", "C", "ORG", null, null);
        when(nodeMapper.findById("c")).thenReturn(c);
        when(edgeMapper.findBySourceNodeId("c")).thenReturn(List.of(new KnowledgeEdge("e-1", "c", "d", "PART_OF", 0.5)));
        Map<String, Object> r = service.getNeighbors("c", 1);
        assertSame(c, r.get("center"));
        assertEquals(1, r.get("degree"));
        assertEquals(1, ((List<?>) r.get("neighbors")).size());
    }

    // ── createNode / createEdge ──

    @Test
    @DisplayName("createNode 生成 UUID 并写库 (P0-3 不阻断)")
    void createNodeGeneratesUuidAndInserts() {
        when(nodeMapper.insert(any(KnowledgeNode.class))).thenReturn(1);
        KnowledgeNode n = service.createNode("SalesDept", "ORG", "销售部门", "{}");
        assertNotNull(n.getId());
        assertEquals("SalesDept", n.getLabel());
        assertTrue(n.getCreatedAt() > 0);
        verify(nodeMapper).insert(n);
    }

    @Test
    @DisplayName("createEdge 生成 UUID 并写库")
    void createEdgeGeneratesUuidAndInserts() {
        when(edgeMapper.insert(any(KnowledgeEdge.class))).thenReturn(1);
        KnowledgeEdge e = service.createEdge("n-1", "n-2", "CAUSES", 0.95);
        assertNotNull(e.getId());
        assertEquals("n-1", e.getSourceNodeId());
        assertEquals("n-2", e.getTargetNodeId());
        assertEquals(0.95, e.getWeight(), 0.001);
        verify(edgeMapper).insert(e);
    }

    // ── getDataSource ──

    @Test
    @DisplayName("getDataSource 计数成功时返回 PostgreSQL (nodes=N)")
    void getDataSourceReturnsPgWithCount() {
        when(nodeMapper.count()).thenReturn(42L);
        assertEquals("PostgreSQL (nodes=42)", service.getDataSource());
    }

    @Test
    @DisplayName("getDataSource count 失败时返回 unavailable (PG 不阻断 P0-3)")
    void getDataSourceReturnsUnavailableOnError() {
        when(nodeMapper.count()).thenThrow(new RuntimeException("连接失败"));
        assertTrue(service.getDataSource().startsWith("unavailable"));
    }
}
