package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.KnowledgeGraphService;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEdgeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphServiceImpl.class);

    private final KnowledgeNodeMapper nodeMapper;
    private final KnowledgeEdgeMapper edgeMapper;

    public KnowledgeGraphServiceImpl(KnowledgeNodeMapper nodeMapper, KnowledgeEdgeMapper edgeMapper) {
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
    }

    @Override
    public Map<String, Object> getGraph(String domain) {
        List<KnowledgeNode> nodes = domain != null ? nodeMapper.findByDomain(domain) : nodeMapper.findAll();
        List<KnowledgeEdge> edges = edgeMapper.findAll();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    @Override
    public Map<String, Object> getNodeDetail(String nodeId) {
        KnowledgeNode node = nodeMapper.findById(nodeId);
        if (node == null) return null;
        List<KnowledgeEdge> outgoing = edgeMapper.findBySourceNodeId(nodeId);
        List<KnowledgeEdge> incoming = edgeMapper.findByTargetNodeId(nodeId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("node", node);
        detail.put("outgoingEdges", outgoing);
        detail.put("incomingEdges", incoming);
        return detail;
    }

    @Override
    public List<KnowledgeNode> search(String query) {
        return nodeMapper.searchByLabel(query);
    }

    @Override
    public Map<String, Object> getShortestPath(String sourceNodeId, String targetNodeId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", sourceNodeId);
        result.put("target", targetNodeId);
        result.put("path", Collections.emptyList());
        result.put("length", -1);
        result.put("note", "Shortest path requires Neo4j — PG fallback returns empty");
        return result;
    }

    @Override
    public Map<String, Object> getNeighbors(String nodeId, int degree) {
        KnowledgeNode node = nodeMapper.findById(nodeId);
        List<KnowledgeEdge> edges = edgeMapper.findBySourceNodeId(nodeId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("center", node);
        result.put("degree", degree);
        result.put("neighbors", edges);
        return result;
    }

    @Override
    public KnowledgeNode createNode(String label, String nodeType, String description, String propertiesJson) {
        KnowledgeNode node = new KnowledgeNode();
        node.setId(UUID.randomUUID().toString());
        node.setLabel(label);
        node.setNodeType(nodeType);
        node.setDescription(description);
        node.setPropertiesJson(propertiesJson);
        node.setCreatedAt(System.currentTimeMillis());
        node.setUpdatedAt(System.currentTimeMillis());
        nodeMapper.insert(node);
        log.info("Created knowledge node: {} [{}]", node.getId(), label);
        return node;
    }

    @Override
    public KnowledgeEdge createEdge(String sourceNodeId, String targetNodeId, String relationship, double weight) {
        KnowledgeEdge edge = new KnowledgeEdge();
        edge.setId(UUID.randomUUID().toString());
        edge.setSourceNodeId(sourceNodeId);
        edge.setTargetNodeId(targetNodeId);
        edge.setRelationship(relationship);
        edge.setWeight(weight);
        edge.setCreatedAt(System.currentTimeMillis());
        edgeMapper.insert(edge);
        log.info("Created knowledge edge: {} [{}]-[{}]->[{}]", edge.getId(), sourceNodeId, relationship, targetNodeId);
        return edge;
    }

    @Override
    public String getDataSource() {
        try {
            long count = nodeMapper.count();
            return "PostgreSQL (nodes=" + count + ")";
        } catch (Exception e) {
            return "unavailable: " + e.getMessage();
        }
    }
}