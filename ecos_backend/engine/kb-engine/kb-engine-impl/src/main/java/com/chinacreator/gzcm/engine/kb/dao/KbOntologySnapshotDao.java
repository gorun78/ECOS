package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * <p>V200/R3-B1: schema 由 `${kb.control-schema:ecos_knowledge}` 注入 (MC06 全面落地)。</p>
 *
 * <p>数据访问收口（R2-B2b kb 子包 / 架构铁律 §2.5 / IR06）：本体发布事件消费 JDBC 落点。
 * <p>SQL 原样搬运自原 {@code event.EcosOntologyEventConsumer}：</p>
 * <ul>
 *   <li>{@code kg_sync_log} FAILED 标记（runSync catch 兜底）</li>
 *   <li>{@code kb_ontology_snapshot} upsert（V116，ontology_id+version 幂等）</li>
 * </ul>
 * schema 前缀 {@code ecos_knowledge} 保留。
 *
 * <p><b>#146h D-7 收口（2026-10-27·仅 sync log 腿）</b>：本文件中 {@code kg_sync_log} 写腿
 * 切至 V179 合规新表 {@code ecos_knowledge.ecos_kg_sync_log}（{@code VARCHAR(36)} 应用侧
 * UUID PK，{@code sync_op} / {@code error_message VARCHAR(200)} / {@code create_time}）。
 * 同文件 {@code kb_ontology_snapshot} 腿不在 D-7 面（dev 库面承接在位）。</p>
 */
@Repository
public class KbOntologySnapshotDao extends JdbcTemplateDaoSupport {

    private final String schema;

    public KbOntologySnapshotDao(
            @Value("${kb.control-schema:ecos_knowledge}") String schema) {
        this.schema = schema;
    }

    /**
     * 同步失败标记行（runSync catch 兜底）。列：object_type / job_id / errorMessage；
     * op='KB_ONTOLOGY_SYNC'、status='FAILED' 与原实现一致内嵌 SQL 字面量。
     * <b>#146h D-7（2026-10-27）</b>：切 V179 合规表——补 {@code id}（应用侧 UUID，MC01）＋
     * 列名 {@code op→sync_op}，{@code error_message} 按 V179 宽度 {@code VARCHAR(200)} 截断。
     */
    public void insertSyncLogFailed(String objectType, String jobId, String errorMessage) {
        String safeErr = errorMessage == null ? null :
                (errorMessage.length() <= 200 ? errorMessage : errorMessage.substring(0, 200));
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".ecos_kg_sync_log " +
                "(id, object_type, sync_op, job_id, status, error_message, create_time) " +
                "VALUES (?, ?, 'KB_ONTOLOGY_SYNC', ?, 'FAILED', ?, NOW())",
                UUID.randomUUID().toString(), objectType, jobId, safeErr);
    }

    /** 旧版本软标为历史回滚基线（is_deleted=2）。 */
    public void softMarkPreviousVersion(String ontologyId, String versionId) {
        jdbcTemplate.update(
                "UPDATE " + schema + ".kb_ontology_snapshot " +
                "SET is_deleted = 2 WHERE ontology_id = ? AND version <> ? AND (is_deleted IS NULL OR is_deleted = 0)",
                ontologyId, versionId);
    }

    /** 幂等 upsert（UNIQUE (ontology_id, version)）。 */
    public void upsertSnapshot(String ontologyId, String versionId,
                               String entitiesJson, String relationshipsJson,
                               String schemaHash, String createdByName) {
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".kb_ontology_snapshot " +
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
