package com.chinacreator.gzcm.engine.ai.agent.mesh.knowledge;

import com.chinacreator.gzcm.engine.ai.agent.mesh.knowledge.KnowledgeGraphService;
import com.chinacreator.gzcm.engine.ai.agent.mesh.knowledge.Neo4jQueryService;
import com.chinacreator.gzcm.engine.ai.agent.mesh.knowledge.entity.KnowledgeEdge;
import com.chinacreator.gzcm.engine.ai.agent.mesh.knowledge.entity.KnowledgeNode;
import com.chinacreator.gzcm.engine.ai.agent.mesh.knowledge.repository.KnowledgeEdgeRepository;
import com.chinacreator.gzcm.engine.ai.agent.mesh.knowledge.repository.KnowledgeNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Wave-5.1 T-09 — 知识 Agent 上下文 fallback 行为测试。
 *
 * <p>等同任务 "KnowledgeAgentContextTest": ai-engine 内 KnowledgeGraphService 优先走
 * Neo4j (context 加速), Neo4j 不可用时降级到 PG JDBC, 检索/聚合不阻断。
 *
 * @author ECOS AI Engine Team
 * @since 2026-09-02 (Wave-5.1)
 */
class KnowledgeAgentContextTestCase {

    private KnowledgeGraphService service;
    private KnowledgeNodeRepository nodeRepo;
    private KnowledgeEdgeRepository edgeRepo;
    private Neo4jQueryService neo4j;

    @BeforeEach
    void setUp() {
        nodeRepo = mock(KnowledgeNodeRepository.class);
        edgeRepo = mock(KnowledgeEdgeRepository.class);
        neo4j = mock(Neo4jQueryService.class);
        service = new KnowledgeGraphService();
        ReflectionTestUtils.setField(service, "nodeRepo", nodeRepo);
        ReflectionTestUtils.setField(service, "edgeRepo", edgeRepo);
        ReflectionTestUtils.setField(service, "neo4jService", neo4j);
        ReflectionTestUtils.setField(neo4j, "driver", mock(org.neo4j.driver.Driver.class));
    }

    @Test
    @DisplayName("T-09-3-1: Neo4j 不可用 + 无 domain → 走 PG findAll (tenant-agnostic 模式)")
    void graphFallsBackToPgWhenNeo4jDown() {
        when(neo4j.isAvailable()).thenReturn(false);
        KnowledgeNode n = new KnowledgeNode();
        n.setId("n-1");
        when(nodeRepo.findAll()).thenReturn(List.of(n));
        when(edgeRepo.findAll()).thenReturn(List.of());

        Map<String, Object> g = service.getGraph(null);

        assertNotNull(g);
        assertEquals(1, ((List<?>) g.get("nodes")).size());
        verify(nodeRepo).findAll();
        verify(nodeRepo, never()).findByDomain(anyString());
    }

    @Test
    @DisplayName("T-09-3-2: Neo4j 不可用 + domain 过滤 → 走 PG findByDomain (tenant-scoped 上下文)")
    void graphWithDomainUsesPgFindByDomain() {
        when(neo4j.isAvailable()).thenReturn(false);
        when(nodeRepo.findByDomain("sales")).thenReturn(List.of());
        when(edgeRepo.findAll()).thenReturn(List.of());

        Map<String, Object> g = service.getGraph("sales");

        assertNotNull(g);
        verify(nodeRepo).findByDomain("sales");
        verify(nodeRepo, never()).findAll();
    }

    @Test
    @DisplayName("T-09-3-3: Neo4j 异常 → 回退 PG (knowledge context 不阻断)")
    void graphFallsBackOnNeo4jException() {
        when(neo4j.isAvailable()).thenReturn(true);
        when(neo4j.getFullGraph()).thenThrow(new RuntimeException("Neo4j down"));
        when(nodeRepo.findAll()).thenReturn(List.of());
        when(edgeRepo.findAll()).thenReturn(List.of());

        Map<String, Object> g = service.getGraph(null);

        assertNotNull(g);
        verify(nodeRepo).findAll();
    }

    @Test
    @DisplayName("T-09-3-4: getNodeDetail — Neo4j 异常时走 PG nodeRepo + edgeRepo")
    void nodeDetailFallsBackToPgOnNeo4jException() {
        when(neo4j.isAvailable()).thenReturn(true);
        when(neo4j.getNodeDetail("n-1")).thenThrow(new RuntimeException("Neo4j timeout"));
        KnowledgeNode n = new KnowledgeNode();
        n.setId("n-1");
        when(nodeRepo.findById("n-1")).thenReturn(n);
        when(edgeRepo.findByNodeId("n-1")).thenReturn(List.of());

        Map<String, Object> d = service.getNodeDetail("n-1");

        assertNotNull(d, "PG fallback 必须返回 detail map");
        assertSame(n, d.get("node"));
    }

    @Test
    @DisplayName("T-09-3-5: search — Neo4j down → 空 query 走 findAll, 非空 q 走 search(q.trim)")
    void searchFallsBackToPg() {
        when(neo4j.isAvailable()).thenReturn(false);
        when(nodeRepo.findAll()).thenReturn(List.of());
        service.search("   ");
        service.search(" 销售 ");

        verify(nodeRepo).findAll();
        verify(nodeRepo).search("销售");
    }

    @Test
    @DisplayName("T-09-3-6: createEdge 默认 weight=1.0 (weight 为 null 时兜底)")
    void createEdgeDefaultsWeightToOne() {
        KnowledgeEdge e = service.createEdge("a", "b", "RELATES", null);
        assertNotNull(e.getId());
        assertEquals("a", e.getSourceNodeId());
        assertEquals("b", e.getTargetNodeId());
        assertEquals("RELATES", e.getRelationship());
        assertEquals(1.0, e.getWeight(), 0.001);
        verify(edgeRepo).insert(e);
    }

    @Test
    @DisplayName("T-09-3-7: recordAgentFinding — 创建 Finding 节点并在 agent 已注册时建边 (落图幂等)")
    void recordAgentFindingCreatesNodeAndEdgeWhenAgentRegistered() {
        // createNode 路径直接验证: createNode 内部直接调 nodeRepo.insert
        // 再用 nodeRepo.findById(agentId) 找到已注册 agent → 走 createEdge
        when(edgeRepo.findAll()).thenReturn(List.of());

        KnowledgeNode agentNode = new KnowledgeNode();
        agentNode.setId("agent-1");
        when(nodeRepo.findById("agent-1")).thenReturn(agentNode);

        service.recordAgentFinding("agent-1", "财务 Copilot", "毛利率下滑根因");

        // 应插入 1 个 Finding node
        var captor = org.mockito.ArgumentCaptor.forClass(KnowledgeNode.class);
        verify(nodeRepo).insert(captor.capture());
        assertEquals("Agent发现: 财务 Copilot", captor.getValue().getLabel());
        assertEquals("Finding", captor.getValue().getNodeType());

        // 应插入 1 个 edge: agent-1 → 新 finding (discovers, 0.8)
        var edgeCaptor = org.mockito.ArgumentCaptor.forClass(KnowledgeEdge.class);
        verify(edgeRepo).insert(edgeCaptor.capture());
        assertEquals("agent-1", edgeCaptor.getValue().getSourceNodeId());
        assertEquals("discovers", edgeCaptor.getValue().getRelationship());
        assertEquals(0.8, edgeCaptor.getValue().getWeight(), 0.001);
    }
}
