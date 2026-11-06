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
 *
 * <p><b>B28（2026-11-04）快照写腿切 V179</b>：原写 {@code ecos_knowledge.kb_ontology_snapshot}
 * 在 live 库不存在（实表只有 {@code ecos_ontology.kb_ontology_snapshot}），本体发布事件恒失败；
 * 今切 V179 权威表 {@code ecos_knowledge.ecos_kb_ontology_snapshot}
 * （{@code id} UUID PK / {@code snap_version} / {@code entity_codes_json,relationship_codes_json} TEXT /
 * {@code create_by} / {@code is_deleted}），唯一键 {@code (ontology_id, snap_version, is_deleted)}。</p>
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
                "UPDATE " + schema + ".ecos_kb_ontology_snapshot " +
                "SET is_deleted = 2, update_time = NOW() " +
                "WHERE ontology_id = ? AND snap_version <> ? AND is_deleted = 0",
                ontologyId, versionId);
    }

    /** 幂等 upsert（UNIQUE (ontology_id, snap_version, is_deleted)，V179）。 */
    public void upsertSnapshot(String ontologyId, String versionId,
                               String entitiesJson, String relationshipsJson,
                               String schemaHash, String createdByName) {
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".ecos_kb_ontology_snapshot " +
                "(id, ontology_id, snap_version, entity_codes_json, relationship_codes_json, " +
                " schema_hash, create_by, is_deleted) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 0) " +
                "ON CONFLICT (ontology_id, snap_version, is_deleted) DO UPDATE SET " +
                "  entity_codes_json = EXCLUDED.entity_codes_json, " +
                "  relationship_codes_json = EXCLUDED.relationship_codes_json, " +
                "  schema_hash = EXCLUDED.schema_hash, " +
                "  create_by = EXCLUDED.create_by, " +
                "  update_time = NOW()",
                UUID.randomUUID().toString(), ontologyId, versionId, entitiesJson, relationshipsJson,
                schemaHash, createdByName == null ? "system" : createdByName);
    }
}
