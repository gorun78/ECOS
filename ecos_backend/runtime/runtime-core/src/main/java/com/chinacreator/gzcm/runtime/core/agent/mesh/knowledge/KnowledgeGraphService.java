package com.chinacreator.gzcm.runtime.core.agent.mesh.knowledge;

import com.chinacreator.gzcm.runtime.core.agent.mesh.knowledge.entity.KnowledgeNode;
import com.chinacreator.gzcm.runtime.core.agent.mesh.knowledge.entity.KnowledgeEdge;
import com.chinacreator.gzcm.runtime.core.agent.mesh.knowledge.repository.KnowledgeNodeRepository;
import com.chinacreator.gzcm.runtime.core.agent.mesh.knowledge.repository.KnowledgeEdgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 知识图谱服务 — 节点/边 CRUD + Agent落图。
 *
 * <p><b>查询策略</b>: 优先使用 Neo4j Cypher 查询，Neo4j 不可用时回退到 PG JDBC。
 * <p><b>写操作</b>: 同时写入 PG (主) 和 Neo4j (副，用于查询加速)。
 */
@Service
public class KnowledgeGraphService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphService.class);

    @Autowired(required = false)
    private KnowledgeNodeRepository nodeRepo;

    @Autowired(required = false)
    private KnowledgeEdgeRepository edgeRepo;

    @Autowired(required = false)
    private Neo4jQueryService neo4jService;

    // ══════ 全图 ══════

    public Map<String, Object> getGraph(String domain) {
        // 优先 Neo4j
        if (neo4jService != null && neo4jService.isAvailable()) {
            try {
                Map<String, Object> graph = neo4jService.getFullGraph();
                if (domain != null && !domain.isEmpty()) {
                    // Neo4j 侧对 domain 做内存过滤
                    @SuppressWarnings("unchecked")
                    List<KnowledgeNode> nodes = (List<KnowledgeNode>) graph.get("nodes");
                    if (nodes != null) {
                        List<KnowledgeNode> filtered = nodes.stream()
                                .filter(n -> {
                                    String props = n.getPropertiesJson();
                                    return props != null && props.contains("\"domain\":\"" + domain + "\"");
                                })
                                .toList();
                        graph.put("nodes", filtered);
                    }
                }
                log.debug("getGraph from Neo4j: {} nodes, {} edges",
                        ((List<?>) graph.get("nodes")).size(),
                        ((List<?>) graph.get("edges")).size());
                return graph;
            } catch (Exception e) {
                log.warn("Neo4j 全图查询失败, 回退 PG: {}", e.getMessage());
            }
        }

        // 回退 PG JDBC
        if (nodeRepo == null || edgeRepo == null) return Map.of("nodes", List.of(), "edges", List.of());
        Map<String, Object> result = new LinkedHashMap<>();
        List<KnowledgeNode> nodes;
        if (domain != null && !domain.isEmpty()) {
            nodes = nodeRepo.findByDomain(domain);
        } else {
            nodes = nodeRepo.findAll();
        }
        List<KnowledgeEdge> edges = edgeRepo.findAll();
        result.put("nodes", nodes);
        result.put("edges", edges);
        log.debug("getGraph from PG: {} nodes, {} edges", nodes.size(), edges.size());
        return result;
    }

    // ══════ 节点详情 ══════

    public Map<String, Object> getNodeDetail(String id) {
        // 优先 Neo4j
        if (neo4jService != null && neo4jService.isAvailable()) {
            try {
                Map<String, Object> detail = neo4jService.getNodeDetail(id);
                if (detail != null) return detail;
            } catch (Exception e) {
                log.warn("Neo4j 节点详情查询失败, 回退 PG: {}", e.getMessage());
            }
        }

        // 回退 PG JDBC
        if (nodeRepo == null) return null;
        KnowledgeNode node = nodeRepo.findById(id);
        if (node == null) return null;
        List<KnowledgeEdge> edges = edgeRepo.findByNodeId(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("node", node);
        result.put("edges", edges);
        return result;
    }

    // ══════ 搜索 ══════

    public List<KnowledgeNode> search(String q) {
        // 优先 Neo4j
        if (neo4jService != null && neo4jService.isAvailable()) {
            try {
                return neo4jService.search(q);
            } catch (Exception e) {
                log.warn("Neo4j 搜索失败, 回退 PG: {}", e.getMessage());
            }
        }

        // 回退 PG JDBC
        if (nodeRepo == null) return List.of();
        if (q == null || q.trim().isEmpty()) return nodeRepo.findAll();
        return nodeRepo.search(q.trim());
    }

    // ══════ 最短路径 ══════

    public Map<String, Object> getShortestPath(String sourceNodeId, String targetNodeId) {
        if (neo4jService != null && neo4jService.isAvailable()) {
            try {
                Map<String, Object> path = neo4jService.getShortestPath(sourceNodeId, targetNodeId);
                if (path != null) return path;
                return Map.of("message", "未找到路径", "sourceNodeId", sourceNodeId,
                        "targetNodeId", targetNodeId, "nodes", List.of(), "edges", List.of());
            } catch (Exception e) {
                log.warn("Neo4j 路径查询失败: {}", e.getMessage());
                return Map.of("error", "路径查询失败: " + e.getMessage());
            }
        }
        return Map.of("error", "Neo4j 不可用, 路径查询仅支持 Neo4j");
    }

    // ══════ N 度邻居 ══════

    public Map<String, Object> getNeighbors(String nodeId, int degree) {
        if (neo4jService != null && neo4jService.isAvailable()) {
            try {
                return neo4jService.getNeighbors(nodeId, degree);
            } catch (Exception e) {
                log.warn("Neo4j 邻居查询失败: {}", e.getMessage());
                return Map.of("error", "邻居查询失败: " + e.getMessage());
            }
        }
        return Map.of("error", "Neo4j 不可用, 邻居查询仅支持 Neo4j");
    }

    // ══════ 创建节点 ══════

    public KnowledgeNode createNode(String label, String nodeType, String description,
                                     String propertiesJson) {
        KnowledgeNode node = new KnowledgeNode();
        node.setId("kgn" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        node.setLabel(label);
        node.setNodeType(nodeType);
        node.setDescription(description);
        node.setPropertiesJson(propertiesJson != null ? propertiesJson : "{}");
        nodeRepo.insert(node);
        return node;
    }

    // ══════ 创建边 ══════

    public KnowledgeEdge createEdge(String sourceNodeId, String targetNodeId,
                                     String relationship, Double weight) {
        KnowledgeEdge edge = new KnowledgeEdge();
        edge.setId("kge" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        edge.setSourceNodeId(sourceNodeId);
        edge.setTargetNodeId(targetNodeId);
        edge.setRelationship(relationship);
        edge.setWeight(weight != null ? weight : 1.0);
        edgeRepo.insert(edge);
        return edge;
    }

    // ══════ Agent 结果落图 ══════

    public void recordAgentFinding(String agentId, String agentName, String finding) {
        KnowledgeNode node = createNode(
                "Agent发现: " + agentName,
                "Finding",
                finding,
                "{\"source\":\"agent\",\"agentId\":\"" + agentId + "\"}"
        );
        // 链接到 Agent 自身（如果注册了）
        KnowledgeNode agentNode = nodeRepo.findById(agentId);
        if (agentNode != null) {
            createEdge(agentNode.getId(), node.getId(), "discovers", 0.8);
        }
    }
}
