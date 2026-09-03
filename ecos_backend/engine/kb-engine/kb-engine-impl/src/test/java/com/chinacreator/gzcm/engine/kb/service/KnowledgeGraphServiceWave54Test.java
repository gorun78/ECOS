package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEdgeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Wave-5.4 T-19 seed: KnowledgeGraphServiceImpl 轻边界 test. */
@ExtendWith(MockitoExtension.class)
class KnowledgeGraphServiceWave54Test {

    @Mock
    private KnowledgeNodeMapper nodeMapper;
    @Mock
    private KnowledgeEdgeMapper edgeMapper;

    private KnowledgeGraphServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeGraphServiceImpl(nodeMapper, edgeMapper);
    }

    @Test
    void createNode_generatesUuidAndMapsFields() {
        KnowledgeNode node = service.createNode("PERSON", "entity", "desc", "{\"a\":1}");
        assertNotNull(node.getId());
        assertEquals("PERSON", node.getLabel());
        assertEquals("entity", node.getNodeType());
        verify(nodeMapper).insert(node);
    }

    @Test
    void createEdge_generatesUuidAndMapsFields() {
        var edge = service.createEdge("n1", "n2", "RELATES_TO", 0.81);
        assertNotNull(edge.getId());
        assertEquals("n1", edge.getSourceNodeId());
        assertEquals("n2", edge.getTargetNodeId());
        assertEquals(0.81, edge.getWeight());
        verify(edgeMapper).insert(edge);
    }

    @Test
    void search_blank_returnsEmpty() {
        assertTrue(service.search("   ").isEmpty());
        verifyNoInteractions(nodeMapper);
    }

    @Test
    void getDataSource_returnsCount() {
        when(nodeMapper.count()).thenReturn(42L);
        assertEquals("PostgreSQL (nodes=42)", service.getDataSource());
    }
}
