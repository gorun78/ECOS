package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * 数据访问收口（R2-B2b kb 子包 / 架构铁律 §2.5 / IR06）：本体发布事件消费 JDBC 落点。
 * <p>SQL 原样搬运自原 {@code event.EcosOntologyEventConsumer}：</p>
 * <ul>
 *   <li>{@code kg_sync_log} FAILED 标记（runSync catch 兜底）</li>
 *   <li>{@code kb_ontology_snapshot} upsert（V116，ontology_id+version 幂等）</li>
 * </ul>
 * schema 前缀 {@code ecos_knowledge} 保留。
 */
@Repository
public class KbOntologySnapshotDao extends JdbcTemplateDaoSupport {

    /** 同步失败标记行（runSync catch 兜底）。object_type / job_id / errorMessage；op='KB_ONTOLOGY_SYNC'、
     *  status='FAILED' 与原实现一致内嵌 SQL 字面量。errorMessage 由调用方负责截断（保持原 truncate 语义）。 */
    public void insertSyncLogFailed(String objectType, String jobId, String errorMessage) {
        jdbcTemplate.update(
                "INSERT INTO ecos_knowledge.kg_sync_log " +
                "(object_type, op, job_id, status, error_message) VALUES (?, 'KB_ONTOLOGY_SYNC', ?, 'FAILED', ?)",
                objectType, jobId, errorMessage);
    }

    /** 旧版本软标为历史回滚基线（is_deleted=2）。 */
    public void softMarkPreviousVersion(String ontologyId, String versionId) {
        jdbcTemplate.update(
                "UPDATE ecos_knowledge.kb_ontology_snapshot " +
                "SET is_deleted = 2 WHERE ontology_id = ? AND version <> ? AND (is_deleted IS NULL OR is_deleted = 0)",
                ontologyId, versionId);
    }

    /** 幂等 upsert（UNIQUE (ontology_id, version)）。 */
    public void upsertSnapshot(String ontologyId, String versionId,
                               String entitiesJson, String relationshipsJson,
                               String schemaHash, String createdByName) {
        jdbcTemplate.update(
                "INSERT INTO ecos_knowledge.kb_ontology_snapshot " +
                "(ontology_id, version, entity_codes, relationship_codes, schema_hash, created_by, created_at) " +
                "VALUES (?, ?, ?::jsonb, ?::jsonb, ?, ?, NOW()) " +
                "ON CONFLICT (ontology_id, version) DO UPDATE SET " +
                "  entity_codes = EXCLUDED.entity_codes, " +
                "  relationship_codes = EXCLUDED.relationship_codes, " +
                "  schema_hash = EXCLUDED.schema_hash, " +
                "  created_by = EXCLUDED.created_by, " +
                "  created_at = NOW(), " +
                "  is_deleted = 0",
                ontologyId, versionId, entitiesJson, relationshipsJson, schemaHash,
                createdByName == null ? "system" : createdByName);
    }
}
