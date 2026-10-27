package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * <p>V200/R3-B1: schema 由 `${kb.control-schema:ecos_knowledge}` 注入 (MC06 全面落地)。</p>
 *
 * <p>本体快照 / 图谱对齐 / 回滚 DAO。
 *
 * <p>SQL 原样搬运自原 {@code service.KgMapperService}（只搬不改语义；
 * snapshot 表名 / 列名、align 目标、回滚 LIKE 前缀逐字保留）。</p>
 */
@Repository
public class KgMapperDao extends JdbcTemplateDaoSupport {

    private final String schema;

    public KgMapperDao(
            @Value("${kb.control-schema:ecos_knowledge}") String schema) {
        this.schema = schema;
    }

    /** 全量范围：每个本体取最新一条生效快照。 */
    public List<Map<String, Object>> readSnapshotsAll() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT ON (ontology_id) ontology_id, version, " +
                "       jsonb_array_length(entity_codes) AS entity_count, " +
                "       jsonb_array_length(relationship_codes) AS relationship_count " +
                "FROM " + schema + ".kb_ontology_snapshot " +
                "WHERE is_deleted = 0 " +
                "ORDER BY ontology_id, created_at DESC");
    }

    /** 单本体：按 ontology_id 取最新一条生效快照。 */
    public List<Map<String, Object>> readSnapshotsSingle(String scope) {
        return jdbcTemplate.queryForList(
                "SELECT ontology_id, version, " +
                "       jsonb_array_length(entity_codes) AS entity_count, " +
                "       jsonb_array_length(relationship_codes) AS relationship_count " +
                "FROM " + schema + ".kb_ontology_snapshot " +
                "WHERE is_deleted = 0 AND ontology_id = ? " +
                "ORDER BY created_at DESC LIMIT 1",
                scope);
    }

    /** 版本对齐（graph_node）：把快照版本打到该本体已落库的节点上（幂等，已对齐行不重复写）。 */
    public int alignNodeVersion(String version, String ontologyId) {
        return jdbcTemplate.update(
                "UPDATE " + schema + ".graph_node SET ontology_version = ?, updated_at = NOW() " +
                "WHERE ontology_id = ? AND (ontology_version IS NULL OR ontology_version <> ?)",
                version, ontologyId, version);
    }

    /** 版本对齐（graph_edge）：语义同 {@link #alignNodeVersion}。 */
    public int alignEdgeVersion(String version, String ontologyId) {
        return jdbcTemplate.update(
                "UPDATE " + schema + ".graph_edge SET ontology_version = ?, updated_at = NOW() " +
                "WHERE ontology_id = ? AND (ontology_version IS NULL OR ontology_version <> ?)",
                version, ontologyId, version);
    }

    /**
     * rollback 前置：读该 job 最新一行的 nodes/edges 计数。
     * <b>#146h D-7（2026-10-27）</b>：kg_sync_log（V115，dev 库面不存在）→ V179 合规新表
     * {@code ecos_kg_sync_log}；节点/边计数字段名 {@code nodes/edges → node_count/edge_count}，
     * 以别名 {@code AS nNodes / AS nEdges} 保持既有 map 键 "nNodes"/"nEdges" 契约不变。
     */
    public List<Map<String, Object>> rollbackCounts(String jobId) {
        return jdbcTemplate.queryForList(
                "SELECT node_count AS nNodes, edge_count AS nEdges FROM " + schema + ".ecos_kg_sync_log " +
                "WHERE job_id = ? ORDER BY create_time DESC LIMIT 1",
                jobId);
    }

    /** rollback（graph_node）：按 job 前缀 LIKE 匹配删除最新 nNodes 行。 */
    public int deleteNodesByJob(String jobId, int nNodes) {
        return jdbcTemplate.update(
                "DELETE FROM " + schema + ".graph_node WHERE id IN (" +
                "SELECT id FROM " + schema + ".graph_node WHERE id LIKE ? ORDER BY created_at DESC LIMIT ?)",
                "kg-" + jobId + "%", nNodes);
    }

    /** rollback（graph_edge）：按 job 前缀 LIKE 匹配删除最新 nEdges 行。 */
    public int deleteEdgesByJob(String jobId, int nEdges) {
        return jdbcTemplate.update(
                "DELETE FROM " + schema + ".graph_edge WHERE id IN (" +
                "SELECT id FROM " + schema + ".graph_edge WHERE id LIKE ? ORDER BY created_at DESC LIMIT ?)",
                "kgrel-" + jobId + "%", nEdges);
    }
}
