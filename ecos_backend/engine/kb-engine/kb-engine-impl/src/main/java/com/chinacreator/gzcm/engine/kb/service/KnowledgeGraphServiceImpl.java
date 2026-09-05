package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.KnowledgeGraphService;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeNodeMapper;
import com.chinacreator.gzcm.engine.kb.repository.KnowledgeEdgeMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphServiceImpl.class);

    private static final int SEARCH_CACHE_TTL_SECONDS = 30;

    private final KnowledgeNodeMapper nodeMapper;
    private final KnowledgeEdgeMapper edgeMapper;

    /**
     * P99 优化: search 结果 30s 缓存。
     * <p>{@code ILIKE '%..%'} 无法命中 B-tree 索引 (需 pg_trgm GIN 才加速),
     * 同源 query 在 UI 弹跳 / 前端防抖 / 多次重绘等高频场景内命中率很高,
     * 缓存至列表显示稳定后 (30s) 到期重建, 兼顾一致性与开销。</p>
     */
    private final Cache<String, List<KnowledgeNode>> searchCache = Caffeine.newBuilder()
            .maximumSize(512)
            .expireAfterWrite(SEARCH_CACHE_TTL_SECONDS, TimeUnit.SECONDS)
            .build();

    public KnowledgeGraphServiceImpl(KnowledgeNodeMapper nodeMapper, KnowledgeEdgeMapper edgeMapper) {
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
    }

    @Override
    public Map<String, Object> getGraph(String domain) {
        try {
            List<KnowledgeNode> nodes = domain != null ? nodeMapper.findByDomain(domain) : nodeMapper.findAll();
            List<KnowledgeEdge> edges = edgeMapper.findAll();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nodes", nodes);
            result.put("edges", edges);
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch graph from PG: {}", e.getMessage(), e);
            throw new RuntimeException("图谱数据获取失败", e);
        }
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
        // P0-3 修: 拼 ILIKE 通配符, 避免 PG 扩展协议对 CONCAT('%',?,'%') 推不出参数类型
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        // P99 优化: 30s 缓存, 前后端重复 query / 防抖 / 前端重绘等同一 query 复用结果
        String cacheKey = "search:" + query;
        return searchCache.get(cacheKey, k -> nodeMapper.searchByLabelPattern("%" + query + "%"));
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
        LocalDateTime now = LocalDateTime.now();
        KnowledgeNode node = new KnowledgeNode();
        node.setId(UUID.randomUUID().toString());
        node.setLabel(label);
        node.setNodeType(nodeType);
        node.setDescription(description);
        node.setPropertiesJson(propertiesJson);
        node.setCreatedAt(now);
        node.setUpdatedAt(now);
        try {
            nodeMapper.insert(node);
        } catch (Exception e) {
            log.error("Failed to insert knowledge node: label={}, cause={}", label, e.getMessage(), e);
            throw new RuntimeException("节点创建失败", e);
        }
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
        edge.setCreatedAt(LocalDateTime.now());
        try {
            edgeMapper.insert(edge);
        } catch (Exception e) {
            log.error("Failed to insert knowledge edge: {}->[{}]->{} cause={}",
                    sourceNodeId, relationship, targetNodeId, e.getMessage(), e);
            throw new RuntimeException("关系创建失败", e);
        }
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