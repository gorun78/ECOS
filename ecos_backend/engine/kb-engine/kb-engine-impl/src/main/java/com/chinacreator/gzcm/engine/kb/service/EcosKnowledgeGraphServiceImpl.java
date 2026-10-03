package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.EcosKnowledgeGraphService;
import com.chinacreator.gzcm.runtime.access.graph.Neo4jClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ECOS 通用知识图谱服务 — PMO-50 T3 去硬编码版。
 *
 * <p>原 11 节点 11 边假图谱替换为读取真实 PG：
 * <ul>
 *   <li>{@link #getGraphSnapshot()} 读 {@code ecos_knowledge.graph_node / graph_edge}；</li>
 *   <li>{@link #syncToNeo4j()}（F04-10 图谱双形态做实）：要么真写 Neo4j 投影并做 PG↔Neo4j
 *       计数对账落 {@code kg_sync_log}，要么在 Neo4j 不可用 / 开关关闭时明确返回
 *       {@code notAttempted}/{@code PG_ONLY}（F04-10 删除旧"伪准备态"，K-29）。
 *       standard 档以 PG 表为权威形态，Neo4j 只是从属投影副本。</li>
 * </ul>
 * 重写表 schema 适配 V115 建表（node/edge 表已在 V51 建库，server:: 不动）。
 */
@Service
public class EcosKnowledgeGraphServiceImpl implements EcosKnowledgeGraphService {

    private static final Logger log = LoggerFactory.getLogger(EcosKnowledgeGraphServiceImpl.class);

    /** F04-10：本域键（纠正 K-32 借键 cognitive.neo4j.switch-on-write），默认关（standard 档 PG 唯一形态）。 */
    private static final String NEO4J_SWITCH_ON_WRITE = "ecos.kb.graph.neo4j.switch-on-write";

    private final JdbcTemplate jdbc;
    private final boolean neo4jSwitchOnWrite;

    /** Neo4j 投影客户端（enterprise/ultimate 档才有；standard 档不构造 → null，走 PG_ONLY）。 */
    @Autowired(required = false)
    private Neo4jClient neo4jClient;

    public EcosKnowledgeGraphServiceImpl(JdbcTemplate jdbc,
                                         @Value("${" + NEO4J_SWITCH_ON_WRITE + ":false}")
                                         boolean neo4jSwitchOnWrite) {
        this.jdbc = jdbc;
        this.neo4jSwitchOnWrite = neo4jSwitchOnWrite;
    }

    @Override
    public Map<String, Object> getGraphSnapshot() {
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("nodes", loadNodes());
        graph.put("edges", loadEdges());
        graph.put("stats", loadStats());
        return graph;
    }

    /**
     * 读 graph_node 全量，映射成响应字段：code/name/domainId/domainName/type/properties。
     */
    private List<Map<String, Object>> loadNodes() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, label, node_type, domain, properties FROM ecos_knowledge.graph_node ORDER BY label");
            for (Map<String, Object> row : rows) {
                Map<String, Object> n = new LinkedHashMap<>();
                n.put("code", row.getOrDefault("label", ""));
                n.put("name", row.getOrDefault("label", ""));
                n.put("domainId", row.getOrDefault("domain", ""));
                n.put("domainName", row.getOrDefault("domain", ""));
                n.put("type", row.getOrDefault("node_type", "OntologyEntity"));
                n.put("id", row.getOrDefault("id", ""));
                Object props = row.get("properties");
                n.put("properties", props == null ? "" : props.toString());
                nodes.add(n);
            }
        } catch (Exception e) {
            log.warn("loadNodes failed (table missing?): {}", e.getMessage());
        }
        return nodes;
    }

    /**
     * 读 graph_edge 全量并 join 两次 graph_node 取出 source/target 名称。
     * 字段：source/target（label）+ relationshipType/label（type）。
     */
    private List<Map<String, Object>> loadEdges() {
        List<Map<String, Object>> edges = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT e.id, e.source_id, e.target_id, e.type AS \"relationship\", " +
                    "       n1.label AS \"sourceName\", n2.label AS \"targetName\" " +
                    "FROM ecos_knowledge.graph_edge e " +
                    "LEFT JOIN ecos_knowledge.graph_node n1 ON n1.id = e.source_id " +
                    "LEFT JOIN ecos_knowledge.graph_node n2 ON n2.id = e.target_id " +
                    "ORDER BY n1.label, n2.label");
            for (Map<String, Object> row : rows) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("id", row.getOrDefault("id", ""));
                e.put("source", row.getOrDefault("sourceName", row.getOrDefault("source_id", "")));
                e.put("target", row.getOrDefault("targetName", row.getOrDefault("target_id", "")));
                e.put("relationshipType", row.getOrDefault("relationship", ""));
                e.put("label", row.getOrDefault("relationship", ""));
                edges.add(e);
            }
        } catch (Exception e) {
            log.warn("loadEdges failed (table missing?): {}", e.getMessage());
        }
        return edges;
    }

    /**
     * stats：node/edge 真实计数 + domains 去重清单。
     */
    private Map<String, Object> loadStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            Integer nodeCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_knowledge.graph_node", Integer.class);
            Integer edgeCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_knowledge.graph_edge", Integer.class);
            List<String> domains = jdbc.queryForList(
                    "SELECT DISTINCT domain FROM ecos_knowledge.graph_node " +
                    "WHERE domain IS NOT NULL ORDER BY domain",
                    String.class);
            stats.put("nodeCount", nodeCount == null ? 0 : nodeCount);
            stats.put("edgeCount", edgeCount == null ? 0 : edgeCount);
            stats.put("domains", domains);
        } catch (Exception e) {
            stats.put("nodeCount", 0);
            stats.put("edgeCount", 0);
            stats.put("domains", new ArrayList<String>());
            log.warn("loadStats failed: {}", e.getMessage());
        }
        return stats;
    }

    /**
     * F04-10 图谱双形态做实：要么真写 Neo4j 投影并做 PG↔Neo4j 计数对账（真写 / 真失败），
     * 要么 Neo4j 不可用 / 开关关闭 → 明确返回 {@code notAttempted}（PG 为唯一权威形态）。
     *
     * <p>F04-10 删除旧实现的"伪准备态"（K-29：PG 218 节点 vs Neo4j 44 却谎报准备同步）。
     * 权威形态恒为 PG；Neo4j 仅作 enterprise/ultimate 档的从属投影副本。</p>
     */
    @Override
    public Map<String, Object> syncToNeo4j() {
        String jobId = "tg-neo4j-" + System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", jobId);
        result.put("authoritativeForm", "PG");

        int pgNodes = 0;
        int pgEdges = 0;
        try {
            Integer nc = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_knowledge.graph_node", Integer.class);
            Integer ec = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_knowledge.graph_edge", Integer.class);
            pgNodes = nc == null ? 0 : nc;
            pgEdges = ec == null ? 0 : ec;
        } catch (Exception e) {
            log.warn("syncToNeo4j PG count failed: {}", e.getMessage());
        }
        result.put("pgNodes", pgNodes);
        result.put("pgEdges", pgEdges);

        boolean attempted =
                neo4jSwitchOnWrite
                        && neo4jClient != null
                        && neo4jClient.isAvailable()
                        && neo4jClient.verifyConnectivity();

        if (!attempted) {
            // Neo4j 不可用 / standard 档 / 开关关闭 → PG 唯一权威形态，明确不尝试投影
            String reason = !neo4jSwitchOnWrite
                    ? " ecos.kb.graph.neo4j.switch-on-write=false（standard 档 PG 为唯一权威形态）"
                    : " Neo4j 客户端不可用或连接验证失败（enterprise/ultimate 档需 neo4j.uri 生效）";
            markSyncLog(jobId, "PG_ONLY", 0, 0, null);
            result.put("status", "notAttempted");
            result.put("graphForm", "PG_ONLY");
            result.put("reason", reason.trim());
            log.warn("syncToNeo4j：未尝试 Neo4j 投影（notAttempted）— {}；PG 权威 {}节点/{}边",
                    reason.trim(), pgNodes, pgEdges);
            return result;
        }

        // Neo4j 可用且开关开：真写投影（节点/边从属 PG），失败为真 FAILED（不伪装成功）
        long neo4jNodes;
        long neo4jEdges;
        try {
            writeNeo4jProjection(pgNodes, pgEdges);
            neo4jNodes = countNeo4jNodes();
            neo4jEdges = countNeo4jEdges();
            markSyncLog(jobId, "SUCCESS", pgNodes, pgEdges, null);
            result.put("status", "synced");
            result.put("graphForm", "PG_NEO4J");
            result.put("neo4jNodes", neo4jNodes);
            result.put("neo4jEdges", neo4jEdges);
            result.put("reconciled", (neo4jNodes == pgNodes));
            log.info("syncToNeo4j：真写完成 — PG({},{}) Neo4j({},{}) reconciled={}",
                    pgNodes, pgEdges, neo4jNodes, neo4jEdges, neo4jNodes == pgNodes);
        } catch (Exception e) {
            markSyncLog(jobId, "FAILED", 0, 0, e.getMessage());
            result.put("status", "failed");
            result.put("graphForm", "PG_ONLY");
            result.put("reason", e.getMessage());
            log.error("syncToNeo4j：Neo4j 真写失败（真失败，非伪成功）: {}", e.getMessage(), e);
        }
        return result;
    }

    /**
     * 真写 Neo4j 投影：从 PG 读 graph_node/graph_edge 全量用 Cypher 写为从属副本。
     * 任一 Cypher 抛异常向上抛出 → 调用方落 FAILED（真失败，不静默）。
     */
    private void writeNeo4jProjection(int pgNodes, int pgEdges) {
        // MERGE 幂等写节点（按 id 去重）
        List<Map<String, Object>> nodes = jdbc.queryForList(
                "SELECT id, label, node_type AS \"nodeType\", domain " +
                        "FROM ecos_knowledge.graph_node");
        for (Map<String, Object> n : nodes) {
            neo4jClient.run(
                    "MERGE (kg:EcosKgNode {id: $id}) " +
                            "SET kg.name = $name, kg.nodeType = $nodeType, kg.domain = $domain",
                    Map.of(
                            "id", String.valueOf(n.get("id")),
                            "name", String.valueOf(n.getOrDefault("label", "")),
                            "nodeType", String.valueOf(n.getOrDefault("nodeType", "")),
                            "domain", n.get("domain") == null ? "" : String.valueOf(n.get("domain"))));
        }
        // 写边（MERGE 两端节点，按 label relationshipType）
        List<Map<String, Object>> edges = jdbc.queryForList(
                "SELECT e.source_id AS \"sourceId\", e.target_id AS \"targetId\", " +
                        "       e.type AS \"relType\" " +
                        "FROM ecos_knowledge.graph_edge e");
        for (Map<String, Object> e : edges) {
            neo4jClient.run(
                    "MATCH (s:EcosKgNode {id: $s}), (t:EcosKgNode {id: $t}) " +
                            "CREATE (s)-[:EcosKgRel {type: $r}]->(t)",
                    Map.of(
                            "s", String.valueOf(e.get("sourceId")),
                            "t", String.valueOf(e.get("targetId")),
                            "r", String.valueOf(e.getOrDefault("relType", "RELATED"))));
        }
    }

    /** Neo4j 节点计数（对账口径：投影副本节点数）。 */
    private long countNeo4jNodes() {
        List<Map<String, Object>> rows =
                neo4jClient.run("MATCH (kg:EcosKgNode) RETURN count(kg) AS cnt", Map.of());
        return rows.isEmpty() || !(rows.get(0).get("cnt") instanceof Number cnt) ? 0L : cnt.longValue();
    }

    /** Neo4j 边计数（对账口径：投影副本边数）。 */
    private long countNeo4jEdges() {
        List<Map<String, Object>> rows =
                neo4jClient.run(
                        "MATCH (:EcosKgNode)-[r:EcosKgRel]->(:EcosKgNode) RETURN count(r) AS cnt", Map.of());
        return rows.isEmpty() || !(rows.get(0).get("cnt") instanceof Number cnt) ? 0L : cnt.longValue();
    }

    /** 落 kg_sync_log 对账台账（status 携带真结果 PG_ONLY/SUCCESS/FAILED）。 */
    private void markSyncLog(String jobId, String status, int nodes, int edges, String error) {
        try {
            jdbc.update(
                    "INSERT INTO ecos_knowledge.kg_sync_log " +
                            "(object_type, op, job_id, status, progress, nodes, edges, error_message) " +
                            "VALUES (?, 'NEO4J_SYNC', ?, ?, ?, ?, ?, ?)",
                    "ALL", jobId, status,
                    "FAILED".equals(status) ? 0 : 100, nodes, edges,
                    error == null ? null : error.substring(0, Math.min(error.length(), 1024)));
        } catch (Exception e) {
            log.warn("落 kg_sync_log 对账台账失败（不阻断真结果返回）: {}", e.getMessage());
        }
    }
}
