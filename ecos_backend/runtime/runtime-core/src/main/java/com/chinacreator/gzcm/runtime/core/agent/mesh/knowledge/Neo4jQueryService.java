package com.chinacreator.gzcm.runtime.core.agent.mesh.knowledge;

import com.chinacreator.gzcm.runtime.core.agent.mesh.knowledge.entity.KnowledgeNode;
import com.chinacreator.gzcm.runtime.core.agent.mesh.knowledge.entity.KnowledgeEdge;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Neo4j Cypher 查询服务 — 知识图谱专用查询。
 *
 * <p>通过 Bolt 协议直连 Neo4j，提供 3 类查询：
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

    @org.springframework.beans.factory.annotation.Value("${neo4j.uri:bolt://localhost:7687}")
    private String neo4jUri;

    @org.springframework.beans.factory.annotation.Value("${neo4j.username:neo4j}")
    private String neo4jUsername;

    @org.springframework.beans.factory.annotation.Value("${neo4j.password:neo4j123}")
    private String neo4jPassword;

    @org.springframework.beans.factory.annotation.Value("${neo4j.database:neo4j}")
    private String neo4jDatabase;

    private Driver driver;
    private volatile boolean available = false;

    @PostConstruct
    public void init() {
        try {
            driver = GraphDatabase.driver(neo4jUri,
                    AuthTokens.basic(neo4jUsername, neo4jPassword),
                    Config.builder()
                            .withConnectionTimeout(5, TimeUnit.SECONDS)
                            .withMaxConnectionPoolSize(10)
                            .build());
            // 验证连接
            try (Session session = driver.session(SessionConfig.forDatabase(neo4jDatabase))) {
                session.run("RETURN 1").consume();
            }
            available = true;
            log.info("Neo4jQueryService 已连接: {} (database: {})", neo4jUri, neo4jDatabase);
        } catch (Exception e) {
            log.warn("Neo4j 不可用, 将回退到 PG JDBC 查询: {}", e.getMessage());
            available = false;
            if (driver != null) {
                try { driver.close(); } catch (Exception ignored) {}
                driver = null;
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        if (driver != null) {
            try { driver.close(); } catch (Exception ignored) {}
            log.info("Neo4jQueryService 已关闭");
        }
    }

    public boolean isAvailable() {
        return available && driver != null;
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

        try (Session session = driver.session(SessionConfig.forDatabase(neo4jDatabase))) {
            // 查询所有节点
            List<KnowledgeNode> nodes = session.executeRead(tx -> {
                var res = tx.run("MATCH (n:Entity) RETURN n.id AS id, n.label AS label, " +
                        "n.nodeType AS nodeType, n.description AS description, " +
                        "n.propertiesJson AS propertiesJson, n.createdAt AS createdAt " +
                        "ORDER BY n.createdAt DESC");
                List<KnowledgeNode> list = new ArrayList<>();
                while (res.hasNext()) {
                    list.add(mapToNode(res.next()));
                }
                return list;
            });

            // 查询所有关系
            List<KnowledgeEdge> edges = session.executeRead(tx -> {
                var res = tx.run("MATCH ()-[r:RELATES]->() " +
                        "RETURN r.id AS id, r.sourceNodeId AS sourceNodeId, " +
                        "r.targetNodeId AS targetNodeId, r.relationship AS relationship, " +
                        "r.weight AS weight, r.createdAt AS createdAt");
                List<KnowledgeEdge> list = new ArrayList<>();
                while (res.hasNext()) {
                    list.add(mapToEdge(res.next()));
                }
                return list;
            });

            result.put("nodes", nodes);
            result.put("edges", edges);
        }
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

        try (Session session = driver.session(SessionConfig.forDatabase(neo4jDatabase))) {
            return session.executeRead(tx -> {
                var res = tx.run(
                        "MATCH (a:Entity {id: $sourceId}), (b:Entity {id: $targetId}) " +
                        "MATCH path = shortestPath((a)-[*]-(b)) " +
                        "RETURN nodes(path) AS pathNodes, relationships(path) AS pathRels, " +
                        "length(path) AS pathLength",
                        Map.of("sourceId", sourceNodeId, "targetId", targetNodeId)
                );

                if (!res.hasNext()) return null;

                Record record = res.next();
                List<KnowledgeNode> nodes = new ArrayList<>();
                List<KnowledgeEdge> edges = new ArrayList<>();

                // 提取路径上的节点
                var pathNodes = record.get("pathNodes").asList(Value::asNode);
                for (var nodeVal : pathNodes) {
                    KnowledgeNode kn = new KnowledgeNode();
                    kn.setId(nodeVal.get("id").asString(null));
                    kn.setLabel(nodeVal.get("label").asString(""));
                    kn.setNodeType(nodeVal.get("nodeType").asString("Concept"));
                    kn.setDescription(nodeVal.get("description").asString(""));
                    kn.setPropertiesJson(nodeVal.get("propertiesJson").asString("{}"));
                    nodes.add(kn);
                }

                // 提取路径上的关系
                var pathRels = record.get("pathRels").asList(Value::asRelationship);
                for (var relVal : pathRels) {
                    KnowledgeEdge ke = new KnowledgeEdge();
                    ke.setId(relVal.get("id").asString(null));
                    // 关系在路径中连接相邻节点
                    ke.setRelationship(relVal.get("relationship").asString("RELATES"));
                    ke.setWeight(relVal.get("weight").asDouble(1.0));
                    edges.add(ke);
                }

                int pathLength = record.get("pathLength").asInt(0);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("nodes", nodes);
                result.put("edges", edges);
                result.put("pathLength", pathLength);
                return result;
            });
        }
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

        try (Session session = driver.session(SessionConfig.forDatabase(neo4jDatabase))) {
            Map<String, Object> result = new LinkedHashMap<>();
            // 邻居节点去重
            List<KnowledgeNode> neighbors = session.executeRead(tx -> {
                String query = String.format(
                        "MATCH (n:Entity {id: $nodeId})-[*1..%d]-(neighbor:Entity) " +
                        "WHERE neighbor.id <> $nodeId " +
                        "RETURN DISTINCT neighbor.id AS id, neighbor.label AS label, " +
                        "neighbor.nodeType AS nodeType, neighbor.description AS description, " +
                        "neighbor.propertiesJson AS propertiesJson, neighbor.createdAt AS createdAt",
                        d);
                var res = tx.run(query, Map.of("nodeId", nid));
                List<KnowledgeNode> list = new ArrayList<>();
                while (res.hasNext()) {
                    list.add(mapToNode(res.next()));
                }
                return list;
            });

            // 邻居关系
            List<KnowledgeEdge> edges = session.executeRead(tx -> {
                String query = String.format(
                        "MATCH (n:Entity {id: $nodeId})-[rel*1..%d]-(neighbor:Entity) " +
                        "WHERE neighbor.id <> $nodeId " +
                        "UNWIND rel AS r " +
                        "RETURN DISTINCT r.id AS id, r.sourceNodeId AS sourceNodeId, " +
                        "r.targetNodeId AS targetNodeId, r.relationship AS relationship, " +
                        "r.weight AS weight, r.createdAt AS createdAt",
                        d);
                var res = tx.run(query, Map.of("nodeId", nid));
                List<KnowledgeEdge> list = new ArrayList<>();
                while (res.hasNext()) {
                    list.add(mapToEdge(res.next()));
                }
                return list;
            });

            result.put("centerNodeId", nid);
            result.put("degree", d);
            result.put("nodes", neighbors);
            result.put("edges", edges);
            return result;
        }
    }

    /**
     * 获取单个节点详情（含关联边）。
     */
    public Map<String, Object> getNodeDetail(String id) {
        if (!isAvailable()) throw new IllegalStateException("Neo4j 不可用");

        try (Session session = driver.session(SessionConfig.forDatabase(neo4jDatabase))) {
            KnowledgeNode node = session.executeRead(tx -> {
                var res = tx.run(
                        "MATCH (n:Entity {id: $id}) " +
                        "RETURN n.id AS id, n.label AS label, n.nodeType AS nodeType, " +
                        "n.description AS description, n.propertiesJson AS propertiesJson, " +
                        "n.createdAt AS createdAt",
                        Map.of("id", id));
                if (!res.hasNext()) return null;
                return mapToNode(res.next());
            });

            if (node == null) return null;

            List<KnowledgeEdge> edges = session.executeRead(tx -> {
                var res = tx.run(
                        "MATCH (n:Entity {id: $id})-[r:RELATES]-(m:Entity) " +
                        "RETURN r.id AS id, r.sourceNodeId AS sourceNodeId, " +
                        "r.targetNodeId AS targetNodeId, r.relationship AS relationship, " +
                        "r.weight AS weight, r.createdAt AS createdAt",
                        Map.of("id", id));
                List<KnowledgeEdge> list = new ArrayList<>();
                while (res.hasNext()) {
                    list.add(mapToEdge(res.next()));
                }
                return list;
            });

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("node", node);
            result.put("edges", edges);
            return result;
        }
    }

    /**
     * 搜索节点（按 label 或 description 模糊匹配）。
     */
    public List<KnowledgeNode> search(String q) {
        if (!isAvailable()) throw new IllegalStateException("Neo4j 不可用");

        try (Session session = driver.session(SessionConfig.forDatabase(neo4jDatabase))) {
            return session.executeRead(tx -> {
                var res = tx.run(
                        "MATCH (n:Entity) " +
                        "WHERE n.label CONTAINS $q OR n.description CONTAINS $q " +
                        "RETURN n.id AS id, n.label AS label, n.nodeType AS nodeType, " +
                        "n.description AS description, n.propertiesJson AS propertiesJson, " +
                        "n.createdAt AS createdAt " +
                        "ORDER BY n.createdAt DESC",
                        Map.of("q", q));
                List<KnowledgeNode> list = new ArrayList<>();
                while (res.hasNext()) {
                    list.add(mapToNode(res.next()));
                }
                return list;
            });
        }
    }

    // ══════ 内部映射方法 ══════

    private KnowledgeNode mapToNode(Record record) {
        KnowledgeNode node = new KnowledgeNode();
        node.setId(safeGetString(record, "id"));
        node.setLabel(safeGetString(record, "label"));
        node.setNodeType(safeGetString(record, "nodeType"));
        node.setDescription(safeGetString(record, "description"));
        node.setPropertiesJson(safeGetString(record, "propertiesJson"));
        try {
            Value createdAtVal = record.get("createdAt");
            if (!createdAtVal.isNull()) {
                node.setCreatedAt(createdAtVal.asLocalDateTime());
            }
        } catch (Exception ignored) {
            // createdAt may be null or unparseable
        }
        return node;
    }

    private KnowledgeEdge mapToEdge(Record record) {
        KnowledgeEdge edge = new KnowledgeEdge();
        edge.setId(safeGetString(record, "id"));
        edge.setSourceNodeId(safeGetString(record, "sourceNodeId"));
        edge.setTargetNodeId(safeGetString(record, "targetNodeId"));
        edge.setRelationship(safeGetString(record, "relationship"));
        try {
            Value weightVal = record.get("weight");
            if (!weightVal.isNull()) {
                edge.setWeight(weightVal.asDouble());
            } else {
                edge.setWeight(1.0);
            }
        } catch (Exception ignored) {
            edge.setWeight(1.0);
        }
        try {
            Value createdAtVal = record.get("createdAt");
            if (!createdAtVal.isNull()) {
                edge.setCreatedAt(createdAtVal.asLocalDateTime());
            }
        } catch (Exception ignored) {
            // createdAt may be null
        }
        return edge;
    }

    private String safeGetString(Record record, String key) {
        try {
            Value val = record.get(key);
            return val.isNull() ? "" : val.asString();
        } catch (Exception e) {
            return "";
        }
    }
}
