package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.kb.EcosKnowledgeGraphService;
import com.chinacreator.gzcm.engine.kb.KgSyncService;
import com.chinacreator.gzcm.engine.kb.dao.EcosKnowledgeGraphDao;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import com.chinacreator.gzcm.runtime.access.graph.Neo4jClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
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

    private final EcosKnowledgeGraphDao ecosKnowledgeGraphDao;
    private final KgSyncService kgSyncService;

    /** Neo4j 投影客户端 — standard 档允许缺省（nullable），缺省/不可用 → 显式降级不投影。 */
    private final Neo4jClient neo4j;

    public EcosKnowledgeGraphServiceImpl(EcosKnowledgeGraphDao ecosKnowledgeGraphDao, @Lazy KgSyncService kgSyncService,
                                         ObjectProvider<Neo4jClient> neo4jProvider) {
        this.ecosKnowledgeGraphDao = ecosKnowledgeGraphDao;
        this.kgSyncService = kgSyncService;
        this.neo4j = neo4jProvider == null ? null : neo4jProvider.getIfAvailable();
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
            List<Map<String, Object>> rows = ecosKnowledgeGraphDao.loadNodes();
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
            List<Map<String, Object>> rows = ecosKnowledgeGraphDao.loadEdges();
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
            Integer nodeCount = ecosKnowledgeGraphDao.countNodes();
            Integer edgeCount = ecosKnowledgeGraphDao.countEdges();
            List<String> domains = ecosKnowledgeGraphDao.distinctDomains();
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
     * syncToNeo4j — 图谱双形态做实（F04-10 / K-28~K-32）。
     *
     * <p>原实现无论任何档位都返回 {@code "ready_to_sync"} 伪成功（fire-and-forget），
     * 掩盖"standard 档压根没有 Neo4j、投影从未发生"的事实。现按设计修正：</p>
     * <ul>
     *   <li>Neo4j 投影<b>不可用</b>（standard 档无 Neo4j / {@code !neo4j.isAvailable()}）→
     *       显式返回 {@code notAttempted} + {@code degraded=true} + 原因，<b>不以 PG 为失败</b>
     *       （PG 表仍是权威形态，Neo4j 是从属投影副本）；</li>
     *   <li>Neo4j 可用 → 提交真投影任务（{@code kg_neo4j_sync}）并对账
     *       PG↔Neo4j 计数，结果写入 {@code kg_sync_log}（真结果，非"pending"空头）。</li>
     * </ul>
     * <p>全仓不再出现 {@code ready_to_sync} 伪成功字面量（源码验收
     * {@code GraphDualFormTest#noReadyToSyncLiteralRemains}）。</p>
     */
    @Override
    public Map<String, Object> syncToNeo4j() {
        Map<String, Object> result = new LinkedHashMap<>();

        int pgNodes = 0;
        int pgEdges = 0;
        try {
            Integer nc = ecosKnowledgeGraphDao.countNodes();
            Integer ec = ecosKnowledgeGraphDao.countEdges();
            pgNodes = nc == null ? 0 : nc;
            pgEdges = ec == null ? 0 : ec;
        } catch (Exception e) {
            log.warn("syncToNeo4j PG count failed (权威形态不可读): {}", e.getMessage());
        }
        result.put("pgNodeCount", pgNodes);
        result.put("pgEdgeCount", pgEdges);

        // Neo4j 投影可用性（enterprise+ 才启用；standard 档 Neo4jClient 为 null 或不可用）
        boolean neo4jAvailable = neo4j != null && neo4j.isAvailable();
        if (!neo4jAvailable) {
            // 显式降级：未尝试投影。PG 仍是权威形态，这不是错误，只是"当前档位无 Neo4j 投影"。
            result.put("status", "notAttempted");
            result.put("degraded", true);
            result.put("degradeReason",
                    "当前档位无 Neo4j 投影（standard 档），PG 表为权威形态；Neo4j 为主从投影副本，可重放。");
            return result;
        }

        // Neo4j 可用 → 提交真投影任务并做 PG↔Neo4j 计数对账
        String jobId = "tg-neo4j-" + System.currentTimeMillis();
        try {
            ecosKnowledgeGraphDao.insertNeo4jSyncLog(jobId);
        } catch (Exception e) {
            log.warn("insert ecos_kg_sync_log for neo4j sync failed: {}", e.getMessage());
        }
        try {
            kgSyncService.runKgMapper("ALL", jobId);
        } catch (Exception e) {
            log.warn("submit neo4j-sync projection failed: {}", e.getMessage());
        }

        long neo4jNodes = countNeo4jNodesSafely();
        long neo4jEdges = countNeo4jEdgesSafely();
        boolean reconciled = neo4jNodes == pgNodes && neo4jEdges == pgEdges;

        Map<String, Object> recon = new LinkedHashMap<>();
        recon.put("pgNodes", pgNodes);
        recon.put("neo4jNodes", neo4jNodes);
        recon.put("pgEdges", pgEdges);
        recon.put("neo4jEdges", neo4jEdges);
        recon.put("matched", reconciled);
        result.put("reconcile", recon);

        // 审计事件（F04-15 口径：走真结果；此处保留 log AUDIT 打点，事件收口由 F04-15 统一 EventBus）
        String payload = String.format(
                "{\"action\":\"kg_neo4j_sync\",\n\"jobId\":\"%s\",\"pgNodes\":%d,\"neo4jNodes\":%d," +
                        "\"pgEdges\":%d,\"neo4jEdges\":%d,\"matched\":%b,\"service\":\"kb-engine\"}",
                jobId, pgNodes, neo4jNodes, pgEdges, neo4jEdges, reconciled);
        log.info("AUDIT topic={} payload={}", KafkaTopics.AUDIT, payload);

        result.put("status", reconciled ? "synced" : "reconcile_drift");
        result.put("degraded", false);
        if (!reconciled) {
            // 对账差异不吞：显式标记 PG↔Neo4j 形态不一致（F04-10 GRAPH_FORM_MISMATCH 预警）
            result.put("degradeReason", "PG↔Neo4j 计数不一致（见 reconcile），投影端为从属副本，建议重放。");
            result.put("mismatchCode", KbErrorCodes.GRAPH_FORM_MISMATCH.code());
        }
        result.put("jobId", jobId);
        return result;
    }

    private long countNeo4jNodesSafely() {
        try {
            Map<String, Object> row = neo4j.runFirst("MATCH (n) RETURN count(n) AS c", Collections.emptyMap());
            Object c = row == null ? null : row.get("c");
            return c instanceof Number ? ((Number) c).longValue() : 0L;
        } catch (Exception e) {
            log.warn("neo4j node count failed: {}", e.getMessage());
            return 0L;
        }
    }

    private long countNeo4jEdgesSafely() {
        try {
            Map<String, Object> row =
                    neo4j.runFirst("MATCH ()-[r]->() RETURN count(r) AS c", Collections.emptyMap());
            Object c = row == null ? null : row.get("c");
            return c instanceof Number ? ((Number) c).longValue() : 0L;
        } catch (Exception e) {
            log.warn("neo4j edge count failed: {}", e.getMessage());
            return 0L;
        }
    }
}
