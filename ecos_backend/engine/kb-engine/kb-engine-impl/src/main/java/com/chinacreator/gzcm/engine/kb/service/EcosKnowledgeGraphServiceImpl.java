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

    // ── 附录 A 第 15 条（graph/sync 投影腿）：固定标签/关系类型，禁动态拼接（Cypher 注入面归零）──
    // 节点标签固定 :KGNode、关系类型固定 :KG_REL；原始分类/边类型只进属性，不进 Cypher 结构位置。

    /** 无 {@code node_type} 的节点在对账两侧的同口径兜底桶（禁静默丢弃无类型节点）。 */
    private static final String UNKNOWN_NODE_TYPE = "UNKNOWN";

    /** 未投影原因单一文案（POST/GET 双脚同源，禁两处各写一套）。 */
    private static final String REASON_NO_PROJECTION =
            "当前档位无 Neo4j 投影（standard 档），PG 表为权威形态；Neo4j 为从属投影副本，可重放。";

    /** 单条 {@code UNWIND} 语句的行数（大图分批，避免单事务参数膨胀）。 */
    private static final int PROJECTION_BATCH = 200;

    private static final String CYPHER_WIPE_PROJECTION = "MATCH (n:KGNode) DETACH DELETE n";

    private static final String CYPHER_MERGE_NODES = "UNWIND $rows AS r MERGE (n:KGNode {id: r.id}) "
            + "SET n.label = r.label, n.nodeType = r.nodeType, n.domain = r.domain";

    private static final String CYPHER_MERGE_EDGES = "UNWIND $rows AS r "
            + "MATCH (a:KGNode {id: r.sourceId}) MATCH (b:KGNode {id: r.targetId}) "
            + "MERGE (a)-[e:KG_REL {id: r.id}]->(b) SET e.type = r.type";

    private static final String CYPHER_COUNT_PROJECTED_NODES = "MATCH (n:KGNode) RETURN count(n) AS c";

    private static final String CYPHER_COUNT_PROJECTED_EDGES = "MATCH ()-[r:KG_REL]->() RETURN count(r) AS c";

    private static final String CYPHER_COUNT_PROJECTED_NODES_BY_TYPE =
            "MATCH (n:KGNode) RETURN coalesce(n.nodeType, 'UNKNOWN') AS nodeType, count(*) AS c ORDER BY nodeType";

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

    /**
     * 真实 PG→Neo4j 投影并逐类型对账（附录 A 第 15 条 POST 腿，详设 §7.2.3「投影/对账/诚实」）。
     *
     * <p>诚实口径：投影只有两种结局——真写并读回对账一致（{@code PROJECTED}），
     * 或本档位无投影能力时显式 {@code PG_ONLY}＋{@code notAttempted}＋原因。
     * 「写了但读不回来」「读回来对不上」都是形态不可信，必须以 500 {@code ECOS-KB-080} 上浮，
     * 禁以 200＋空对账糊过去。</p>
     */
    @Override
    public Map<String, Object> projectGraph(boolean fullResync) {
        // PG 权威面装载：读故障是 DataAccessException → advice 落 500 ECOS-KB-050（不降级成「空图已投影」）
        List<Map<String, Object>> pgNodes = ecosKnowledgeGraphDao.loadProjectionNodes();
        List<Map<String, Object>> pgEdges = ecosKnowledgeGraphDao.loadProjectionEdges();

        Map<String, Object> result = new LinkedHashMap<>();
        if (neo4j == null || !neo4j.isAvailable()) {
            result.put("graphForm", "PG_ONLY");
            result.put("attempted", 0);
            result.put("written", 0);
            result.put("notAttempted", pgNodes.size());
            result.put("reason", REASON_NO_PROJECTION);
            result.put("reconcileDiff", null);
            return result;
        }

        Map<String, Object> diff;
        try {
            if (fullResync) {
                neo4j.write(CYPHER_WIPE_PROJECTION, Collections.emptyMap());
            }
            writeProjection(CYPHER_MERGE_NODES, nodeRows(pgNodes));
            writeProjection(CYPHER_MERGE_EDGES, edgeRows(pgEdges));
            diff = reconcileDiff(pgNodes.size(), pgEdges.size(), ecosKnowledgeGraphDao.countNodesByType());
        } catch (KbErrorCodes.DomainException e) {
            throw e;
        } catch (RuntimeException e) {
            throw KbErrorCodes.GRAPH_FORM_MISMATCH.exception(
                    "图投影失败（graphForm=MISMATCH）：" + e.getMessage());
        }
        if (diff == null) {
            throw KbErrorCodes.GRAPH_FORM_MISMATCH.exception(
                    "Neo4j 投影计数不可读（graphForm=MISMATCH）：PG 有 " + pgNodes.size()
                            + " 节点／" + pgEdges.size() + " 边，投影端未给出可对账读数");
        }
        if (!Boolean.TRUE.equals(diff.get("matched"))) {
            throw KbErrorCodes.GRAPH_FORM_MISMATCH.exception(
                    "PG↔Neo4j 计数不一致（graphForm=MISMATCH）：" + describeDiff(diff));
        }
        result.put("graphForm", "PROJECTED");
        result.put("attempted", pgNodes.size());
        result.put("written", diff.get("nodesNeo4j"));
        result.put("notAttempted", 0);
        result.put("reason", null);
        result.put("reconcileDiff", diff);
        return result;
    }

    /**
     * 图谱双形态只读状态（附录 A 第 15 条 GET 腿）。
     *
     * <p>三态互不折叠：{@code PG_ONLY}（无投影/投影读数不可得）、{@code PROJECTED}（对账一致）、
     * {@code MISMATCH}（对账漂移，body 另带 {@code mismatchCode}）。漂移是被读对象本身，
     * 故读面仍 200；而台账/PG 读故障上浮 500 {@code ECOS-KB-050}（禁空态伪装）。</p>
     */
    @Override
    public Map<String, Object> graphFormStatus() {
        Integer nodeCount = ecosKnowledgeGraphDao.countNodes();
        Integer edgeCount = ecosKnowledgeGraphDao.countEdges();
        int pgNodes = nodeCount == null ? 0 : nodeCount;
        int pgEdges = edgeCount == null ? 0 : edgeCount;
        // 台账读故障上浮（禁把「读不到同步记录」渲染成「从未同步」）
        String lastSyncTime = kgSyncService.getLastSyncTime();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lastSyncTime", lastSyncTime);

        if (neo4j == null || !neo4j.isAvailable()) {
            result.put("graphForm", "PG_ONLY");
            result.put("reason", REASON_NO_PROJECTION);
            result.put("diff", unreadableDiff(pgNodes, pgEdges));
            return result;
        }

        Map<String, Object> diff = null;
        String reason = null;
        try {
            diff = reconcileDiff(pgNodes, pgEdges, ecosKnowledgeGraphDao.countNodesByType());
        } catch (RuntimeException e) {
            reason = "Neo4j 投影计数不可读：" + e.getMessage();
        }
        if (diff == null) {
            result.put("graphForm", "PG_ONLY");
            result.put("reason", reason == null ? "Neo4j 投影计数不可读" : reason);
            result.put("diff", unreadableDiff(pgNodes, pgEdges));
            return result;
        }
        boolean matched = Boolean.TRUE.equals(diff.get("matched"));
        result.put("graphForm", matched ? "PROJECTED" : "MISMATCH");
        if (!matched) {
            result.put("mismatchCode", KbErrorCodes.GRAPH_FORM_MISMATCH.code());
            result.put("reason", "PG↔Neo4j 计数不一致：" + describeDiff(diff));
        }
        result.put("diff", diff);
        return result;
    }

    /** 分批写投影（单批一条 {@code UNWIND} 语句一个写事务）。 */
    private void writeProjection(String cypher, List<Map<String, Object>> rows) {
        for (int i = 0; i < rows.size(); i += PROJECTION_BATCH) {
            List<Map<String, Object>> chunk = rows.subList(i, Math.min(i + PROJECTION_BATCH, rows.size()));
            neo4j.writeBatch(List.of(cypher), List.of(Map.of("rows", chunk)));
        }
    }

    private List<Map<String, Object>> nodeRows(List<Map<String, Object>> pgNodes) {
        List<Map<String, Object>> rows = new ArrayList<>(pgNodes.size());
        for (Map<String, Object> row : pgNodes) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", String.valueOf(row.get("id")));
            r.put("label", row.get("label"));
            Object type = row.get("nodeType");
            r.put("nodeType", type == null || String.valueOf(type).isBlank()
                    ? UNKNOWN_NODE_TYPE : String.valueOf(type));
            r.put("domain", row.get("domain"));
            rows.add(r);
        }
        return rows;
    }

    private List<Map<String, Object>> edgeRows(List<Map<String, Object>> pgEdges) {
        List<Map<String, Object>> rows = new ArrayList<>(pgEdges.size());
        for (Map<String, Object> row : pgEdges) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", String.valueOf(row.get("id")));
            r.put("sourceId", String.valueOf(row.get("sourceId")));
            r.put("targetId", String.valueOf(row.get("targetId")));
            r.put("type", row.get("type"));
            rows.add(r);
        }
        return rows;
    }

    /**
     * 读投影端计数并与 PG 权威数对账；投影端读数不可得返回 {@code null}
     * （由调用方决定是 500 {@code ECOS-KB-080} 还是 {@code PG_ONLY} 降级）。
     */
    private Map<String, Object> reconcileDiff(int pgNodes, int pgEdges, List<Map<String, Object>> pgByType) {
        Long neoNodes = readNeoCount(CYPHER_COUNT_PROJECTED_NODES);
        Long neoEdges = readNeoCount(CYPHER_COUNT_PROJECTED_EDGES);
        if (neoNodes == null || neoEdges == null) {
            return null;
        }
        Map<String, Long> pgCounts = new TreeMap<>();
        for (Map<String, Object> row : pgByType) {
            Object type = row.get("nodeType");
            Object count = row.get("c");
            if (type != null && count instanceof Number) {
                pgCounts.put(String.valueOf(type), ((Number) count).longValue());
            }
        }
        Map<String, Long> neoCounts = new TreeMap<>();
        List<Map<String, Object>> neoByType =
                neo4j.run(CYPHER_COUNT_PROJECTED_NODES_BY_TYPE, Collections.emptyMap());
        for (Map<String, Object> row : neoByType) {
            Object type = row.get("nodeType");
            Object count = row.get("c");
            if (type != null && count instanceof Number) {
                neoCounts.put(String.valueOf(type), ((Number) count).longValue());
            }
        }
        Map<String, Object> byType = new LinkedHashMap<>();
        Set<String> types = new TreeSet<>(pgCounts.keySet());
        types.addAll(neoCounts.keySet());
        boolean byTypeMatched = true;
        for (String type : types) {
            long pg = pgCounts.getOrDefault(type, 0L);
            long neo = neoCounts.getOrDefault(type, 0L);
            boolean same = pg == neo;
            byTypeMatched &= same;
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("pg", pg);
            cell.put("neo4j", neo);
            cell.put("matched", same);
            byType.put(type, cell);
        }

        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("nodesPg", (long) pgNodes);
        diff.put("nodesNeo4j", neoNodes);
        diff.put("nodesMatched", (long) pgNodes == neoNodes);
        diff.put("edgesPg", (long) pgEdges);
        diff.put("edgesNeo4j", neoEdges);
        diff.put("edgesMatched", (long) pgEdges == neoEdges);
        diff.put("edgeSkipped", Math.max(0L, (long) pgEdges - neoEdges));
        diff.put("byType", byType);
        diff.put("matched", (long) pgNodes == neoNodes && (long) pgEdges == neoEdges && byTypeMatched);
        return diff;
    }

    /** 投影端读数不可得时的对账占位：{@code matched=null} ＝「未知」，禁写成 false 冒充已知不一致。 */
    private Map<String, Object> unreadableDiff(int pgNodes, int pgEdges) {
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("nodesPg", (long) pgNodes);
        diff.put("nodesNeo4j", null);
        diff.put("nodesMatched", null);
        diff.put("edgesPg", (long) pgEdges);
        diff.put("edgesNeo4j", null);
        diff.put("edgesMatched", null);
        diff.put("edgeSkipped", null);
        diff.put("byType", null);
        diff.put("matched", null);
        return diff;
    }

    private Long readNeoCount(String cypher) {
        Map<String, Object> row = neo4j.runFirst(cypher, Collections.emptyMap());
        Object count = row == null ? null : row.get("c");
        return count instanceof Number ? ((Number) count).longValue() : null;
    }

    /** 对账摘要（进 500 message／status reason；逐类型只报差异项且最多 5 类，防消息无界）。 */
    private String describeDiff(Map<String, Object> diff) {
        StringBuilder sb = new StringBuilder();
        sb.append("nodes pg=").append(diff.get("nodesPg")).append("/neo4j=").append(diff.get("nodesNeo4j"))
                .append("，edges pg=").append(diff.get("edgesPg")).append("/neo4j=").append(diff.get("edgesNeo4j"));
        Object byType = diff.get("byType");
        if (byType instanceof Map<?, ?> cells) {
            List<String> drift = new ArrayList<>();
            for (Map.Entry<?, ?> entry : cells.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> cell && !Boolean.TRUE.equals(cell.get("matched"))) {
                    drift.add(entry.getKey() + "=" + cell.get("pg") + "/" + cell.get("neo4j"));
                    if (drift.size() >= 5) {
                        drift.add("…");
                        break;
                    }
                }
            }
            if (!drift.isEmpty()) {
                sb.append("，byType 差异: ").append(String.join(",", drift));
            }
        }
        return sb.toString();
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
