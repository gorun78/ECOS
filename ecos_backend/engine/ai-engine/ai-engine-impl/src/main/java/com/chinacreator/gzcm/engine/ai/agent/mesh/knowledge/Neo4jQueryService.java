package com.chinacreator.gzcm.engine.ai.agent.mesh.knowledge;

import com.chinacreator.gzcm.engine.ai.agent.mesh.knowledge.entity.KnowledgeNode;
import com.chinacreator.gzcm.engine.ai.agent.mesh.knowledge.entity.KnowledgeEdge;
import com.chinacreator.gzcm.runtime.access.graph.Neo4jClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Neo4j Cypher 查询服务 — 知识图谱专用查询。
 *
 * <p>Cypher 编排统一走 runtime-access Neo4jClient (铁律 §3.2)，提供 3 类查询：
 * <ul>
 *   <li><b>全图查询</b>：返回所有节点 + 关系</li>
 *   <li><b>路径查询</b>：两个节点间的最短路径</li>
 *   <li><b>邻居查询</b>：某节点的 N 度邻居</li>
 * </ul>
 *
 * <p>配置属性 (application.yml):
 * <pre>
 * neo4j:
 *   uri: bolt://localhost:7687
 *   username: neo4j
 *   password: neo4j123
 *   database: neo4j
 * </pre>
 */
@Service
public class Neo4jQueryService {

    private static final Logger log = LoggerFactory.getLogger(Neo4jQueryService.class);

    // M0 改造 (2026-09): Neo4j Driver 由 runtime-access/Neo4jConfig 统一管理 (收敛铁律 2.5)。
    // standard 档 / neo4j.uri 未配置时客户端不可用, 调用方 isAvailable() 判空。
    @Autowired(required = false)
    private Neo4jClient neo4jClient;

    private volatile boolean available = false;

    @PostConstruct
    public void init() {
        try {
            if (neo4jClient == null || !neo4jClient.isAvailable()) {
                log.warn("Neo4j 不可用 (standard 档 或 neo4j.uri 未配置), 将回退到 PG JDBC 查询");
                available = false;
                return;
            }
            // 验证连接 (使用 runtime-access 统一客户端)
            if (neo4jClient.testConnection()) {
                available = true;
                log.info("Neo4jQueryService 已连接 (database: {}), 使用 runtime-access 统一客户端", neo4jClient.getDatabase());
            } else {
                available = false;
            }
        } catch (Exception e) {
            log.warn("Neo4j 连接验证失败, 将回退到 PG JDBC 查询: {}", e.getMessage());
            available = false;
        }
    }

    @PreDestroy
    public void shutdown() {
        // Driver 是 runtime-access 管理的 Bean, 不在此 close
        log.info("Neo4jQueryService shutdown: Neo4j 客户端由 runtime-access 管理, 不在此处 close");
    }

    public boolean isAvailable() {
        return available && neo4jClient != null && neo4jClient.isAvailable();
    }

    // ═══════════════════════════════════════════════
    // 1. 全图查询 — 所有节点 + 所有关系
    // ═══════════════════════════════════════════════

    /**
     * 返回知识图谱中所有节点和边。
     */
    public Map<String, Object> getFullGraph() {
        if (!isAvailable()) throw new IllegalStateException("Neo4j 不可用");
        Map<String, Object> result = new LinkedHashMap<>();

        // 查询所有节点
        List<KnowledgeNode> nodes = new ArrayList<>();
        for (Map<String, Object> row : neo4jClient.run("MATCH (n:Entity) RETURN n.id AS id, n.label AS label, " +
                "n.nodeType AS nodeType, n.description AS description, " +
                "n.propertiesJson AS propertiesJson, n.createdAt AS createdAt " +
                "ORDER BY n.createdAt DESC", Map.of())) {
            nodes.add(mapToNode(row));
        }

        // 查询所有关系
        List<KnowledgeEdge> edges = new ArrayList<>();
        for (Map<String, Object> row : neo4jClient.run("MATCH ()-[r:RELATES]->() " +
                "RETURN r.id AS id, r.sourceNodeId AS sourceNodeId, " +
                "r.targetNodeId AS targetNodeId, r.relationship AS relationship, " +
                "r.weight AS weight, r.createdAt AS createdAt", Map.of())) {
            edges.add(mapToEdge(row));
        }

        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    // ═══════════════════════════════════════════════
    // 2. 路径查询 — 两个节点间的最短路径
    // ═══════════════════════════════════════════════

    /**
     * 查询两个节点之间的最短路径。
     *
     * @param sourceNodeId 起始节点 ID
     * @param targetNodeId 目标节点 ID
     * @return 包含路径节点和关系的 Map，若不存在路径则返回 null
     */
    public Map<String, Object> getShortestPath(String sourceNodeId, String targetNodeId) {
        if (!isAvailable()) throw new IllegalStateException("Neo4j 不可用");

        List<Map<String, Object>> rows = neo4jClient.run(
                "MATCH (a:Entity {id: $sourceId}), (b:Entity {id: $targetId}) " +
                "MATCH path = shortestPath((a)-[*]-(b)) " +
                "RETURN nodes(path) AS pathNodes, relationships(path) AS pathRels, " +
                "length(path) AS pathLength",
                Map.of("sourceId", sourceNodeId, "targetId", targetNodeId)
        );

        if (rows.isEmpty()) return null;

        Map<String, Object> record = rows.get(0);
        List<KnowledgeNode> nodes = new ArrayList<>();
        List<KnowledgeEdge> edges = new ArrayList<>();

        // 提取路径上的节点 (扁平化结构: {elementId, labels, properties})
        for (Object nodeObj : listOf(record.get("pathNodes"))) {
            Map<String, Object> props = propertiesOf(nodeObj);
            KnowledgeNode kn = new KnowledgeNode();
            kn.setId(strOrNull(props.get("id")));
            kn.setLabel(strOrDefault(props.get("label"), ""));
            kn.setNodeType(strOrDefault(props.get("nodeType"), "Concept"));
            kn.setDescription(strOrDefault(props.get("description"), ""));
            kn.setPropertiesJson(strOrDefault(props.get("propertiesJson"), "{}"));
            nodes.add(kn);
        }

        // 提取路径上的关系
        for (Object relObj : listOf(record.get("pathRels"))) {
            Map<String, Object> props = propertiesOf(relObj);
            KnowledgeEdge ke = new KnowledgeEdge();
            ke.setId(strOrNull(props.get("id")));
            // 关系在路径中连接相邻节点
            ke.setRelationship(strOrDefault(props.get("relationship"), "RELATES"));
            ke.setWeight(props.get("weight") instanceof Number w ? w.doubleValue() : 1.0);
            edges.add(ke);
        }

        int pathLength = record.get("pathLength") instanceof Number n ? n.intValue() : 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("pathLength", pathLength);
        return result;
    }

    // ═══════════════════════════════════════════════
    // 3. 邻居查询 — 某节点的 N 度邻居
    // ═══════════════════════════════════════════════

    /**
     * 查询某节点的 N 度邻居（去重，排除自身）。
     *
     * @param nodeId 中心节点 ID
     * @param degree 邻居度数 (1-3)
     * @return 包含邻居节点和关系的 Map
     */
    public Map<String, Object> getNeighbors(String nodeId, int degree) {
        if (!isAvailable()) throw new IllegalStateException("Neo4j 不可用");
        final int d = Math.max(1, Math.min(degree, 5));
        final String nid = nodeId;

        Map<String, Object> result = new LinkedHashMap<>();
        // 邻居节点去重
        List<KnowledgeNode> neighbors = new ArrayList<>();
        String neighborQuery = String.format(
                "MATCH (n:Entity {id: $nodeId})-[*1..%d]-(neighbor:Entity) " +
                "WHERE neighbor.id <> $nodeId " +
                "RETURN DISTINCT neighbor.id AS id, neighbor.label AS label, " +
                "neighbor.nodeType AS nodeType, neighbor.description AS description, " +
                "neighbor.propertiesJson AS propertiesJson, neighbor.createdAt AS createdAt",
                d);
        for (Map<String, Object> row : neo4jClient.run(neighborQuery, Map.of("nodeId", nid))) {
            neighbors.add(mapToNode(row));
        }

        // 邻居关系
        List<KnowledgeEdge> edges = new ArrayList<>();
        String relQuery = String.format(
                "MATCH (n:Entity {id: $nodeId})-[rel*1..%d]-(neighbor:Entity) " +
                "WHERE neighbor.id <> $nodeId " +
                "UNWIND rel AS r " +
                "RETURN DISTINCT r.id AS id, r.sourceNodeId AS sourceNodeId, " +
                "r.targetNodeId AS targetNodeId, r.relationship AS relationship, " +
                "r.weight AS weight, r.createdAt AS createdAt",
                d);
        for (Map<String, Object> row : neo4jClient.run(relQuery, Map.of("nodeId", nid))) {
            edges.add(mapToEdge(row));
        }

        result.put("centerNodeId", nid);
        result.put("degree", d);
        result.put("nodes", neighbors);
        result.put("edges", edges);
        return result;
    }

    /**
     * 获取单个节点详情（含关联边）。
     */
    public Map<String, Object> getNodeDetail(String id) {
        if (!isAvailable()) throw new IllegalStateException("Neo4j 不可用");

        Map<String, Object> nodeRow = neo4jClient.runFirst(
                "MATCH (n:Entity {id: $id}) " +
                "RETURN n.id AS id, n.label AS label, n.nodeType AS nodeType, " +
                "n.description AS description, n.propertiesJson AS propertiesJson, " +
                "n.createdAt AS createdAt",
                Map.of("id", id));
        if (nodeRow == null) return null;
        KnowledgeNode node = mapToNode(nodeRow);

        List<KnowledgeEdge> edges = new ArrayList<>();
        for (Map<String, Object> row : neo4jClient.run(
                "MATCH (n:Entity {id: $id})-[r:RELATES]-(m:Entity) " +
                "RETURN r.id AS id, r.sourceNodeId AS sourceNodeId, " +
                "r.targetNodeId AS targetNodeId, r.relationship AS relationship, " +
                "r.weight AS weight, r.createdAt AS createdAt",
                Map.of("id", id))) {
            edges.add(mapToEdge(row));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("node", node);
        result.put("edges", edges);
        return result;
    }

    /**
     * 搜索节点（按 label 或 description 模糊匹配）。
     */
    public List<KnowledgeNode> search(String q) {
        if (!isAvailable()) throw new IllegalStateException("Neo4j 不可用");

        List<KnowledgeNode> list = new ArrayList<>();
        for (Map<String, Object> row : neo4jClient.run(
                "MATCH (n:Entity) " +
                "WHERE n.label CONTAINS $q OR n.description CONTAINS $q " +
                "RETURN n.id AS id, n.label AS label, n.nodeType AS nodeType, " +
                "n.description AS description, n.propertiesJson AS propertiesJson, " +
                "n.createdAt AS createdAt " +
                "ORDER BY n.createdAt DESC",
                Map.of("q", q))) {
            list.add(mapToNode(row));
        }
        return list;
    }

    // ══════ 内部映射方法 ══════

    private KnowledgeNode mapToNode(Map<String, Object> row) {
        KnowledgeNode node = new KnowledgeNode();
        node.setId(str(row.get("id")));
        node.setLabel(str(row.get("label")));
        node.setNodeType(str(row.get("nodeType")));
        node.setDescription(str(row.get("description")));
        node.setPropertiesJson(str(row.get("propertiesJson")));
        node.setCreatedAt(toLocalDateTime(row.get("createdAt")));
        return node;
    }

    private KnowledgeEdge mapToEdge(Map<String, Object> row) {
        KnowledgeEdge edge = new KnowledgeEdge();
        edge.setId(str(row.get("id")));
        edge.setSourceNodeId(str(row.get("sourceNodeId")));
        edge.setTargetNodeId(str(row.get("targetNodeId")));
        edge.setRelationship(str(row.get("relationship")));
        edge.setWeight(row.get("weight") instanceof Number w ? w.doubleValue() : 1.0);
        edge.setCreatedAt(toLocalDateTime(row.get("createdAt")));
        return edge;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String strOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String strOrDefault(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listOf(Object value) {
        return value instanceof List<?> list ? (List<Object>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> propertiesOf(Object graphEntity) {
        if (graphEntity instanceof Map<?, ?> map) {
            Object props = map.get("properties");
            if (props instanceof Map) {
                return (Map<String, Object>) props;
            }
        }
        return Map.of();
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof ZonedDateTime zdt) return zdt.toLocalDateTime();
        if (value instanceof Number n) return java.time.Instant.ofEpochMilli(n.longValue())
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        return null;
    }
}
