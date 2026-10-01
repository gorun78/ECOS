package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 数据访问收口（R2 / B2-kb / 架构铁律 §2.5 / IR06）：
 * {@code service.KbEntityInstanceExtractionService} 的图谱实例写入 + 本体快照 + 抽取水位 JDBC 落点。
 *
 * <p>SQL 原样搬运自原 service（表 {@code ecos_knowledge.graph_node / graph_edge /
 * kb_ontology_snapshot / kb_extract_watermark}，<b>带 schema 前缀</b>，verbatim 保留；
 * 幂等 upsert / 分片存在性查询 / 水位 upsert 语义不变）。</p>
 */
@Repository
public class KbEntityInstanceExtractionServiceDao extends JdbcTemplateDaoSupport {

    /** 分片批量查询已存在的节点 id（IN 列表）。 */
    public List<String> queryExistingNodeIds(List<String> idChunk) {
        String placeholders = String.join(",", java.util.Collections.nCopies(idChunk.size(), "?"));
        return jdbcTemplate.queryForList(
                "SELECT id FROM ecos_knowledge.graph_node WHERE id IN (" + placeholders + ")",
                String.class, idChunk.toArray());
    }

    /**
     * 幂等 upsert 实例节点。
     *
     * @return {@code true} = 新建（INSERT），{@code false} = 更新既有（ON CONFLICT DO UPDATE）
     */
    public boolean upsertNode(String nodeId, String pkValue64, String nodeType, String description,
                              String propertiesJson, String domain, String ontologyId, String ontologyVersion,
                              String resourceId, String pkValue255) {
        // RETURNING (xmax = 0)：PG 惯用法，区分 INSERT（true）与 ON CONFLICT UPDATE（false）
        List<Boolean> inserted = jdbcTemplate.queryForList(
                "INSERT INTO ecos_knowledge.graph_node "
                        + "(id, label, node_type, description, properties, domain, created_at, updated_at, "
                        + " ontology_id, ontology_version, source_resource_id, source_pk) "
                        + "VALUES (?, ?, ?, ?, ?::jsonb, ?, NOW(), NOW(), ?, ?, ?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET label = EXCLUDED.label, node_type = EXCLUDED.node_type, "
                        + " description = EXCLUDED.description, properties = EXCLUDED.properties, "
                        + " domain = EXCLUDED.domain, updated_at = NOW(), ontology_id = EXCLUDED.ontology_id, "
                        + " ontology_version = EXCLUDED.ontology_version, "
                        + " source_resource_id = EXCLUDED.source_resource_id, source_pk = EXCLUDED.source_pk "
                        + "RETURNING (xmax = 0) AS inserted",
                Boolean.class,
                nodeId, pkValue64, nodeType, description, propertiesJson,
                domain, ontologyId, ontologyVersion, resourceId, pkValue255);
        return !inserted.isEmpty() && Boolean.TRUE.equals(inserted.get(0));
    }

    /** 幂等 upsert 实例边（同 id 覆盖，支持全量重跑）。 */
    public void upsertEdge(String edgeId, String sourceNodeId, String targetNodeId, String relationType,
                           String ontologyId, String ontologyVersion, String resourceId, String sourcePk255) {
        jdbcTemplate.update(
                "INSERT INTO ecos_knowledge.graph_edge "
                        + "(id, source_id, target_id, type, weight, properties, created_at, "
                        + " ontology_id, ontology_version, source_resource_id, source_pk) "
                        + "VALUES (?, ?, ?, ?, 1.0, '{}'::jsonb, NOW(), ?, ?, ?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET source_id = EXCLUDED.source_id, "
                        + " target_id = EXCLUDED.target_id, type = EXCLUDED.type, properties = EXCLUDED.properties, "
                        + " ontology_id = EXCLUDED.ontology_id, ontology_version = EXCLUDED.ontology_version, "
                        + " source_resource_id = EXCLUDED.source_resource_id, source_pk = EXCLUDED.source_pk",
                edgeId, sourceNodeId, targetNodeId, relationType,
                ontologyId, ontologyVersion, resourceId, sourcePk255);
    }

    /** 本体快照 — 全量（每个本体最新一条生效快照）。 */
    public List<Map<String, Object>> selectAllSnapshots() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT ON (ontology_id) ontology_id, version, entity_codes "
                        + "FROM ecos_knowledge.kb_ontology_snapshot WHERE is_deleted = 0 "
                        + "ORDER BY ontology_id, created_at DESC");
    }

    /** 本体快照 — 单本体最新一条生效快照。 */
    public List<Map<String, Object>> selectLatestSnapshot(String ontologyId) {
        return jdbcTemplate.queryForList(
                "SELECT ontology_id, version, entity_codes FROM ecos_knowledge.kb_ontology_snapshot "
                        + "WHERE is_deleted = 0 AND ontology_id = ? ORDER BY created_at DESC LIMIT 1",
                ontologyId);
    }

    /** 读抽取水位（ontologyId + entityCode + resourceId）；无行返回 null。 */
    public String readWatermark(String ontologyId, String entityCode, String resourceId) {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT watermark FROM ecos_knowledge.kb_extract_watermark "
                        + "WHERE ontology_id = ? AND entity_code = ? AND resource_id = ?",
                String.class, ontologyId, entityCode, resourceId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 写本次抽取水位（幂等 upsert）。 */
    public void writeWatermark(String ontologyId, String entityCode, String resourceId, String watermark255) {
        jdbcTemplate.update(
                "INSERT INTO ecos_knowledge.kb_extract_watermark "
                        + "(ontology_id, entity_code, resource_id, watermark, updated_at) "
                        + "VALUES (?, ?, ?, ?, NOW()) "
                        + "ON CONFLICT (ontology_id, entity_code, resource_id) "
                        + "DO UPDATE SET watermark = EXCLUDED.watermark, updated_at = NOW()",
                ontologyId, entityCode, resourceId, watermark255);
    }
}
