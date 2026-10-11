package com.chinacreator.gzcm.common.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Object Runtime 数据访问收口（R2 B4c-runtime · 架构铁律 §2.5#1 / IR06）。
 *
 * <p>{@code ecos_object_timeline} / {@code ecos_object_version} / {@code ecos_object_relationship} /
 * {@code ecos_object_attachment} 全部 raw-SQL 自 {@code com.chinacreator.gzcm.common.service
 * .ObjectRuntimeService} 逐字节下沉，SQL 文本与参数顺序不变；{@code ObjectRuntimeService}
 * 退化为编排/事件门面，SQL 只经本 DAO 执行。</p>
 *
 * <p>注：本 DAO 与 {@code ObjectRuntimeService} 同处 {@code common-api} 底座，而
 * {@code JdbcTemplateDaoSupport} 居 {@code runtime-core}（runtime-core 依赖 common-api），
 * 反向继承会形成模块环（阻断构建），故本 DAO 直接自持 {@link JdbcTemplate}
 * （{@code *Dao} 为收口点，架构门禁允许）。</p>
 *
 * <p>红线 EN01/IR01：全部 slot 参数化。</p>
 */
@Repository
public class ObjectRuntimeDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ============================= Timeline =============================

    public void insertTimeline(String id, String objectId, String entityCode, String eventType,
                               String summary, String actor, String detailsJson) {
        jdbcTemplate.update("""
                INSERT INTO ecos_object_timeline (id, object_id, entity_code, event_type, event_summary, actor, details, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, NOW())
                """, id, objectId, entityCode, eventType, summary, actor, detailsJson);
    }

    public Long countTimeline(String objectId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_object_timeline WHERE object_id = ?", Long.class, objectId);
    }

    public List<Map<String, Object>> queryTimeline(String objectId, int size, int offset) {
        String dataSql = """
            SELECT id, object_id, entity_code, event_type, event_summary, actor, details, created_at
            FROM ecos_object_timeline
            WHERE object_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;
        return jdbcTemplate.queryForList(dataSql, objectId, size, offset);
    }

    // ============================= Version =============================

    public Long queryMaxVersion(String objectId) {
        return jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(version_no), 0) FROM ecos_object_version WHERE object_id = ?",
            Long.class, objectId);
    }

    public void insertVersion(String id, String objectId, String entityCode, int newVer,
                              String snapshotJson, String changeSummary, String createdBy) {
        jdbcTemplate.update("""
                INSERT INTO ecos_object_version (id, object_id, entity_code, version_no, snapshot, change_summary, created_by, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, NOW())
                """, id, objectId, entityCode, newVer, snapshotJson, changeSummary, createdBy);
    }

    public List<Map<String, Object>> queryVersions(String objectId) {
        String sql = """
            SELECT id, object_id, entity_code, version_no, change_summary, created_by, created_at
            FROM ecos_object_version
            WHERE object_id = ?
            ORDER BY version_no DESC
            """;
        return jdbcTemplate.queryForList(sql, objectId);
    }

    public List<Map<String, Object>> queryVersion(String objectId, int versionNo) {
        String sql = """
            SELECT id, object_id, entity_code, version_no, snapshot, change_summary, created_by, created_at
            FROM ecos_object_version
            WHERE object_id = ? AND version_no = ?
            """;
        return jdbcTemplate.queryForList(sql, objectId, versionNo);
    }

    // ============================= Relationship =============================

    public void insertRelationship(String id, String sourceObjectId, String targetObjectId,
                                   String sourceEntityCode, String targetEntityCode,
                                   String relationshipCode, String relationshipType, String propertiesJson) {
        jdbcTemplate.update("""
                INSERT INTO ecos_object_relationship (id, source_object_id, target_object_id, source_entity_code, target_entity_code, relationship_code, relationship_type, properties, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW())
                """, id, sourceObjectId, targetObjectId, sourceEntityCode, targetEntityCode,
                relationshipCode, relationshipType, propertiesJson);
    }

    public List<Map<String, Object>> queryRelationships(String objectId) {
        String sql = """
            SELECT r.id, r.source_object_id, r.target_object_id, r.source_entity_code, r.target_entity_code,
                   r.relationship_code, r.relationship_type, r.properties, r.created_at
            FROM ecos_object_relationship r
            WHERE r.source_object_id = ? OR r.target_object_id = ?
            ORDER BY r.created_at DESC
            """;
        return jdbcTemplate.queryForList(sql, objectId, objectId);
    }

    /** 中心/邻接节点 label 解析：按 demo 实体表查 name（表名由 ENTITY_TABLE 白名单给出）。 */
    public List<Map<String, Object>> queryEntityName(String table, String objectId) {
        return jdbcTemplate.queryForList("SELECT name FROM " + table + " WHERE id = ?", objectId);
    }

    /** PostgreSQL 递归 CTE 多层关系展开；level 上限由 depth 控制。 */
    public List<Map<String, Object>> queryGraphEdges(String objectId, int depth) {
        String cteSql = """
            WITH RECURSIVE graph_traverse AS (
                -- Anchor: edges directly connected to the center node
                SELECT
                    r.id,
                    r.source_object_id,
                    r.target_object_id,
                    r.source_entity_code,
                    r.target_entity_code,
                    r.relationship_code,
                    r.relationship_type,
                    1 AS level
                FROM ecos_object_relationship r
                WHERE r.source_object_id = ? OR r.target_object_id = ?
                UNION
                -- Recursive: edges connected to previously discovered nodes
                SELECT
                    r.id,
                    r.source_object_id,
                    r.target_object_id,
                    r.source_entity_code,
                    r.target_entity_code,
                    r.relationship_code,
                    r.relationship_type,
                    g.level + 1
                FROM ecos_object_relationship r
                INNER JOIN graph_traverse g ON (
                    r.source_object_id = g.source_object_id
                    OR r.source_object_id = g.target_object_id
                    OR r.target_object_id = g.source_object_id
                    OR r.target_object_id = g.target_object_id
                )
                AND r.id <> g.id
                WHERE g.level < ?
            )
            SELECT DISTINCT ON (id) id, source_object_id, target_object_id,
                   source_entity_code, target_entity_code,
                   relationship_code, relationship_type, level
            FROM graph_traverse
            ORDER BY id, level
            """;
        return jdbcTemplate.queryForList(cteSql, objectId, objectId, depth);
    }

    public int deleteRelationship(String relId) {
        return jdbcTemplate.update("DELETE FROM ecos_object_relationship WHERE id = ?", relId);
    }

    // ============================= Attachment =============================

    public List<Map<String, Object>> queryAttachments(String objectId) {
        String sql = """
            SELECT id, object_id, entity_code, file_name, file_path, file_size, mime_type, version_no, uploaded_by, created_at
            FROM ecos_object_attachment
            WHERE object_id = ?
            ORDER BY created_at DESC
            """;
        return jdbcTemplate.queryForList(sql, objectId);
    }
}
