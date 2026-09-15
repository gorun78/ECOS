package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.kb.EcosKnowledgeGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ECOS 通用知识图谱服务 — PMO-50 T3 去硬编码版。
 *
 * <p>原 11 节点 11 边假图谱替换为读取真实 PG：
 * <ul>
 *   <li>{@link #getGraphSnapshot()} 读 {@code ecos_knowledge.graph_node / graph_edge}；</li>
 *   <li>{@link #syncToNeo4j()} 提交"异步任务"语义（{@code kg_neo4j_sync}），不再纯日志空转；
 *       通过 {@link KgSyncServiceImpl#runKgMapper(String, String)}（同 Bean {@code @Async} 调度）执行
 *       （standard 档 RAG._Neo4j 不可用时由 target 端 no-op 处理，V115 DDL 兜底状态行）。
 * </ul>
 * 重写表 schema 适配 V115 建表（node/edge 表已在 V51 建库，server:: 不动）。
 */
@Service
public class EcosKnowledgeGraphServiceImpl implements EcosKnowledgeGraphService {

    private static final Logger log = LoggerFactory.getLogger(EcosKnowledgeGraphServiceImpl.class);

    private final JdbcTemplate jdbc;
    private final KgSyncServiceImpl kgSyncService;

    public EcosKnowledgeGraphServiceImpl(JdbcTemplate jdbc, @Lazy KgSyncServiceImpl kgSyncService) {
        this.jdbc = jdbc;
        this.kgSyncService = kgSyncService;
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
     * syncToNeo4j — 提交异步任务（kg_neo4j_sync）。
     * 异步任务实际为"重跑一次 kgMapper 同步"以便 PG 与 Neo4j 对齐；
     * 执行通过 {@link KgSyncServiceImpl#runKgMapper(String, String)}（@Async 走 taskExecutor）。
     */
    @Override
    public Map<String, Object> syncToNeo4j() {
        Map<String, Object> result = new LinkedHashMap<>();
        int nodes = 0;
        int edges = 0;
        try {
            Integer nc = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_knowledge.graph_node", Integer.class);
            Integer ec = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_knowledge.graph_edge", Integer.class);
            nodes = nc == null ? 0 : nc;
            edges = ec == null ? 0 : ec;
        } catch (Exception e) {
            log.warn("syncToNeo4j count failed: {}", e.getMessage());
        }
        // 提交异步任务计数行（status=RUNNING），由 runKgMapper 异步执行 + 回填
        String jobId = "tg-neo4j-" + System.currentTimeMillis();
        try {
            jdbc.update(
                    "INSERT INTO ecos_knowledge.kg_sync_log " +
                    "(object_type, op, job_id, status, progress) VALUES (?, ?, ?, 'RUNNING', 0)",
                    "ALL", "NEO4J_SYNC", jobId);
        } catch (Exception e) {
            log.warn("insert kg_sync_log for neo4j sync failed: {}", e.getMessage());
        }
        // 异步执行（@Async 在 KgSyncServiceImpl 内被 Spring 代理调度）。
        // 不抛到 IO 路径：fire-and-forget，结果写入 kg_sync_log。
        try {
            kgSyncService.runKgMapper("ALL", jobId);
        } catch (Exception e) {
            log.warn("submit async neo4j-sync failed (fire-and-forget): {}", e.getMessage());
        }
        // 审计事件
        String payload = String.format(
                "{\"action\":\"kg_neo4j_sync_submit\",\"jobId\":\"%s\",\"nodes\":%d,\"edges\":%d,\"service\":\"kb-engine\"}",
                jobId, nodes, edges);
        log.info("AUDIT topic={} payload={}", KafkaTopics.AUDIT, payload);

        result.put("status", "ready_to_sync");
        result.put("message", "pending async task kg_neo4j_sync jobId=" + jobId);
        result.put("jobId", jobId);
        result.put("nodesAvailable", nodes);
        result.put("edgesAvailable", edges);
        return result;
    }
}
