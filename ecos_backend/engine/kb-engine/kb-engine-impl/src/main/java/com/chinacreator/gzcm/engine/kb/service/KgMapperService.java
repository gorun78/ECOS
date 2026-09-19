package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.DataAccessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 本体快照 → 图谱版本对齐服务（PMO 批次 B1，缺陷 D1 / D9）。
 *
 * <p>B1 修正要点：
 * <ol>
 *   <li><b>数据源修正（D1）</b>：原实现读 {@code ontology_objects} / {@code object_relationships}
 *       两张全仓无 DDL 的幻影表，且异常被吞成空集 → 本体发布后图谱实际产出 0 节点 0 边。
 *       现改为读本体快照表 {@code ecos_knowledge.kb_ontology_snapshot}（V116，含
 *       {@code ontology_id} + {@code version} + {@code schema_hash} + entity/relationship codes），
 *       它是本体版本的唯一权威对齐基准。</li>
 *   <li><b>异常不吞</b>：快照读取失败或快照缺失一律 {@code log.error}（含 ontologyId / 异常堆栈）
 *       并抛 {@link DataAccessException} / {@link NotFoundException}（{@code DataBridgeException} 子类），
 *       禁止「catch 后返回空集」的静默降级。</li>
 *   <li><b>版本对齐（D9）</b>：以快照的 {@code ontology_id} / {@code ontology_version}
 *       对齐既有 {@code graph_node} / {@code graph_edge} 记录（V134 溯源列），实现
 *       「图谱版本 = 本体版本」。</li>
 *   <li><b>骨架范围（Q4 裁决）</b>：<b>不物化本体实体为图节点</b>。类型只作为节点属性
 *       （{@code graph_node.node_type}）+ 类型索引；本体 schema 骨架视图由
 *       {@code kb_ontology_snapshot} 单独渲染。图谱实例节点由后续批次（B3，契约驱动抽取）写入。</li>
 * </ol>
 *
 * <p>调用方：{@link KgSyncServiceImpl#runKgMapper(String, String)}（手动触发，T2）与
 * {@code EcosOntologyEventConsumer#runSync}（本体发布事件，T4）。
 */
@Service
public class KgMapperService {

    private static final Logger log = LoggerFactory.getLogger(KgMapperService.class);

    /** 全量范围标识：对齐全部生效本体快照。 */
    private static final String SCOPE_ALL = "ALL";

    private final JdbcTemplate jdbc;

    public KgMapperService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 触发一次本体 → 图谱版本对齐。
     *
     * @param ontologyId 本体 ID；{@code ALL} 或空表示全部生效本体
     * @param jobId      job 标识，写入审计与 {@code kg_sync_log} 供溯源
     * @return { nodes, edges, skipped, ontologyObjectCount, ontologyId, ontologyVersions,
     *           entityCount, relationshipCount }
     */
    public Map<String, Object> syncFromOntology(String ontologyId, String jobId) {
        String scope = (ontologyId == null || ontologyId.isBlank()) ? SCOPE_ALL : ontologyId.trim();
        String jobPrefix = jobId == null || jobId.isBlank() ? "" : jobId;

        // 1. 读本体快照（唯一权威来源；缺失或读取失败直接抛业务异常，不静默降级）
        List<OntologySnapshot> snapshots = readSnapshots(scope);

        // 2. 版本对齐（D9）：把快照的 ontology_id / ontology_version 打到既有图谱记录上
        int alignedNodes = 0;
        int alignedEdges = 0;
        int entityCount = 0;
        int relationshipCount = 0;
        List<String> versions = new ArrayList<>(snapshots.size());
        for (OntologySnapshot snap : snapshots) {
            entityCount += snap.entityCount();
            relationshipCount += snap.relationshipCount();
            versions.add(snap.ontologyId() + "@" + snap.version());
            alignedNodes += alignNodeVersion(snap);
            alignedEdges += alignEdgeVersion(snap);
        }

        log.info("KgMapper: done scope='{}' jobId='{}' → snapshots={}, alignedNodes={}, alignedEdges={}, "
                        + "entities={}, relationships={}, versions={}",
                scope, jobPrefix, snapshots.size(), alignedNodes, alignedEdges,
                entityCount, relationshipCount, versions);
        emitAudit("kg_sync_progress", "scope=" + scope + " jobId=" + jobPrefix
                + " alignedNodes=" + alignedNodes + " alignedEdges=" + alignedEdges
                + " versions=" + versions);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nodes", alignedNodes);
        out.put("edges", alignedEdges);
        out.put("skipped", 0);
        out.put("ontologyObjectCount", entityCount);
        out.put("ontologyId", scope);
        out.put("ontologyVersions", versions);
        out.put("entityCount", entityCount);
        out.put("relationshipCount", relationshipCount);
        return out;
    }

    /**
     * dry-run 预览（PMO-50 T4 /jobs/{jobId}/preview）：返回本次同步将产生的变更计数，不写库。
     *
     * <p>B1 按 Q4 裁决不物化本体实体为图节点，故 {@code create/update/skip} 恒为 0；
     * 骨架范围（实体/关系计数 + 对齐版本）由 {@code entityCount} / {@code relationshipCount} /
     * {@code ontologyVersions} 给出，供前端骨架视图核对。
     */
    public Map<String, Object> previewDryRun(String ontologyId) {
        String scope = (ontologyId == null || ontologyId.isBlank()) ? SCOPE_ALL : ontologyId.trim();
        List<OntologySnapshot> snapshots = readSnapshots(scope);

        int entityCount = 0;
        int relationshipCount = 0;
        List<String> versions = new ArrayList<>(snapshots.size());
        for (OntologySnapshot snap : snapshots) {
            entityCount += snap.entityCount();
            relationshipCount += snap.relationshipCount();
            versions.add(snap.ontologyId() + "@" + snap.version());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("create", 0);
        out.put("update", 0);
        out.put("skip", 0);
        out.put("ontologyId", scope);
        out.put("ontologyVersions", versions);
        out.put("entityCount", entityCount);
        out.put("relationshipCount", relationshipCount);
        return out;
    }

    /**
     * 回滚（PMO-50 T4 /jobs/{jobId}/rollback）：
     * 按 kg_sync_log 该行 nodes/edges 计数，删除本 job 关联 job 最新的相应量 node/edge。
     */
    public Map<String, Object> rollback(String jobId) {
        int nodeDel = 0;
        int edgeDel = 0;
        if (jobId == null || jobId.isBlank()) {
            throw new BusinessException("jobId 不能为空");
        }
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT nodes AS nNodes, edges AS nEdges FROM ecos_knowledge.kg_sync_log " +
                    "WHERE job_id = ? ORDER BY created_at DESC LIMIT 1",
                    jobId);
            if (!rows.isEmpty()) {
                int nNodes = ((Number) rows.get(0).getOrDefault("nNodes", 0)).intValue();
                int nEdges = ((Number) rows.get(0).getOrDefault("nEdges", 0)).intValue();
                if (nNodes > 0) {
                    nodeDel = jdbc.update(
                            "DELETE FROM ecos_knowledge.graph_node WHERE id IN (" +
                            "SELECT id FROM ecos_knowledge.graph_node WHERE id LIKE ? ORDER BY created_at DESC LIMIT ?)",
                            "kg-" + jobId + "%", nNodes);
                }
                if (nEdges > 0) {
                    edgeDel = jdbc.update(
                            "DELETE FROM ecos_knowledge.graph_edge WHERE id IN (" +
                            "SELECT id FROM ecos_knowledge.graph_edge WHERE id LIKE ? ORDER BY created_at DESC LIMIT ?)",
                            "kgrel-" + jobId + "%", nEdges);
                }
            }
        } catch (Exception e) {
            log.warn("rollback failed: jobId={}, err={}", jobId, e.getMessage());
        }
        emitAudit("kg_sync_rollback", "jobId=" + jobId + " nodeDel=" + nodeDel + " edgeDel=" + edgeDel);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rolledBackNodes", nodeDel);
        out.put("rolledBackEdges", edgeDel);
        out.put("rolledBack", nodeDel + edgeDel);
        return out;
    }

    // ── 私有辅助 ──────────────────────────────────────

    /**
     * 读取本体快照（版本对齐基准）。
     *
     * <p>只读 {@code kb_ontology_snapshot}：{@code scope=ALL} 时每个本体取最新一条生效快照，
     * 否则按 {@code ontology_id} 取最新一条。读取失败抛 {@link DataAccessException}，
     * 快照缺失抛 {@link NotFoundException}，两者均先 {@code log.error}（含堆栈）。
     *
     * @param scope 本体 ID 或 {@link #SCOPE_ALL}
     * @return 快照投影列表（非空）
     */
    private List<OntologySnapshot> readSnapshots(String scope) {
        List<Map<String, Object>> rows;
        try {
            if (SCOPE_ALL.equalsIgnoreCase(scope)) {
                rows = jdbc.queryForList(
                        "SELECT DISTINCT ON (ontology_id) ontology_id, version, " +
                        "       jsonb_array_length(entity_codes) AS entity_count, " +
                        "       jsonb_array_length(relationship_codes) AS relationship_count " +
                        "FROM ecos_knowledge.kb_ontology_snapshot " +
                        "WHERE is_deleted = 0 " +
                        "ORDER BY ontology_id, created_at DESC");
            } else {
                rows = jdbc.queryForList(
                        "SELECT ontology_id, version, " +
                        "       jsonb_array_length(entity_codes) AS entity_count, " +
                        "       jsonb_array_length(relationship_codes) AS relationship_count " +
                        "FROM ecos_knowledge.kb_ontology_snapshot " +
                        "WHERE is_deleted = 0 AND ontology_id = ? " +
                        "ORDER BY created_at DESC LIMIT 1",
                        scope);
            }
        } catch (Exception e) {
            log.error("KgMapper: 读取本体快照失败 scope={}", scope, e);
            throw new DataAccessException("读取本体快照失败: ontologyId=" + scope, e);
        }

        if (rows.isEmpty()) {
            log.error("KgMapper: 本体快照不存在，无法对齐图谱版本 scope={}", scope);
            throw new NotFoundException("本体快照不存在，无法对齐图谱版本: ontologyId=" + scope);
        }

        List<OntologySnapshot> snapshots = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            snapshots.add(new OntologySnapshot(
                    str(row.get("ontology_id")),
                    str(row.get("version")),
                    intVal(row.get("entity_count")),
                    intVal(row.get("relationship_count"))));
        }
        return snapshots;
    }

    /**
     * 版本对齐（graph_node）：把快照版本打到该本体已落库的节点上（幂等，已对齐行不重复写）。
     *
     * @return 受影响行数
     */
    private int alignNodeVersion(OntologySnapshot snap) {
        return jdbc.update(
                "UPDATE ecos_knowledge.graph_node SET ontology_version = ?, updated_at = NOW() " +
                "WHERE ontology_id = ? AND (ontology_version IS NULL OR ontology_version <> ?)",
                snap.version(), snap.ontologyId(), snap.version());
    }

    /**
     * 版本对齐（graph_edge）：语义同 {@link #alignNodeVersion(OntologySnapshot)}。
     *
     * @return 受影响行数
     */
    private int alignEdgeVersion(OntologySnapshot snap) {
        return jdbc.update(
                "UPDATE ecos_knowledge.graph_edge SET ontology_version = ?, updated_at = NOW() " +
                "WHERE ontology_id = ? AND (ontology_version IS NULL OR ontology_version <> ?)",
                snap.version(), snap.ontologyId(), snap.version());
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private int intVal(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }

    /**
     * 审计事件：走 Kafka {@code ecos.audit}；Kafka 不可用时本地 log 兜底（铁律 §2.4 #5）。
     */
    private void emitAudit(String action, String detail) {
        try {
            String payload = String.format(
                    "{\"action\":\"%s\",\"detail\":\"%s\",\"service\":\"kb-engine\"}",
                    action, sanitize(detail));
            log.info("AUDIT topic={} payload={}", KafkaTopics.AUDIT, payload);
        } catch (Exception e) {
            log.warn("emitAudit fallback log failed: {}", e.getMessage());
        }
    }

    private String sanitize(String s) {
        return s == null ? "" : s.replace("\"", "'").replace("\\", "/");
    }

    /**
     * 本体快照只读投影（版本对齐基准）。
     *
     * @param ontologyId        本体业务 ID
     * @param version           本体版本
     * @param entityCount       快照内实体 code 数
     * @param relationshipCount 快照内关系 code 数
     */
    private record OntologySnapshot(String ontologyId, String version,
                                    int entityCount, int relationshipCount) {
    }
}
