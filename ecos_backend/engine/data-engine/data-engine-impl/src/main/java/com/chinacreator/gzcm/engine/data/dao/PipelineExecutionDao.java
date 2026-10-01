package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.access.document.DocumentChunk;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data-access for the pipeline execution domain:
 * DW-layer doc/doc_chunk upsert and status, TRANSFORM_SQL execution, OUTPUT_OBJECT batch insert.
 */
@Repository
public class PipelineExecutionDao extends JdbcTemplateDaoSupport {

    public static final String DW_DOC_TABLE = "ecos_dw.doc";
    public static final String DW_DOC_CHUNK_TABLE = "ecos_dw.doc_chunk";
    public static final String DW_ACTOR = "pipeline";

    /** 文档行 upsert（queued）：同 doc_id 重解析走 ON CONFLICT，不产生重复行。 */
    public void upsertDocQueued(String docId, String source, String fileName, String objectKey,
                                String contentType, long sizeBytes) {
        jdbcTemplate.update(
                "INSERT INTO " + DW_DOC_TABLE + " (id, doc_id, source, original_file_name, object_key, "
                        + "content_type, size_bytes, parse_status, chunk_count, create_time, update_time, "
                        + "create_by, update_by, is_deleted) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'queued', 0, NOW(), NOW(), ?, ?, FALSE) "
                        + "ON CONFLICT (doc_id) DO UPDATE SET source = EXCLUDED.source, "
                        + "original_file_name = EXCLUDED.original_file_name, object_key = EXCLUDED.object_key, "
                        + "content_type = EXCLUDED.content_type, size_bytes = EXCLUDED.size_bytes, "
                        + "parse_status = 'queued', error_message = NULL, chunk_count = 0, "
                        + "update_time = NOW(), update_by = EXCLUDED.update_by, is_deleted = FALSE",
                newId(), docId, source, fileName, objectKey, contentType, sizeBytes, DW_ACTOR, DW_ACTOR);
    }

    /** 文档状态流转（parsing/extracting）。 */
    public void updateDocStatus(String docId, String status) {
        jdbcTemplate.update("UPDATE " + DW_DOC_TABLE + " SET parse_status = ?, error_message = NULL, "
                + "update_time = NOW(), update_by = ? WHERE doc_id = ? AND is_deleted = FALSE",
                status, DW_ACTOR, docId);
    }

    /** 文档终态 done（记录分块数与解析完成时间）。 */
    public void markDocDone(String docId, int chunkCount) {
        jdbcTemplate.update("UPDATE " + DW_DOC_TABLE + " SET parse_status = 'done', chunk_count = ?, "
                + "parsed_at = NOW(), error_message = NULL, update_time = NOW(), update_by = ? "
                + "WHERE doc_id = ? AND is_deleted = FALSE",
                chunkCount, DW_ACTOR, docId);
    }

    /** 文档落 failed 终态；此处失败仅告警，不覆盖原始异常。 */
    public void markDocFailed(String docId, String errorMessage) {
        jdbcTemplate.update("UPDATE " + DW_DOC_TABLE + " SET parse_status = 'failed', error_message = ?, "
                + "update_time = NOW(), update_by = ? WHERE doc_id = ? AND is_deleted = FALSE",
                truncateForColumn(errorMessage, 2000), DW_ACTOR, docId);
    }

    /** 批量 upsert 分块（同 (doc_id, chunk_index) 走 ON CONFLICT，重解析不产生重复行）。 */
    public void insertDocChunks(String docId, List<DocumentChunk> chunks) {
        String sql = "INSERT INTO " + DW_DOC_CHUNK_TABLE + " (id, chunk_id, doc_id, chunk_index, content, "
                + "char_start, char_end, metadata, status, create_time, update_time, create_by, update_by, is_deleted) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, '{}'::jsonb, 'active', NOW(), NOW(), ?, ?, FALSE) "
                + "ON CONFLICT (doc_id, chunk_index) DO UPDATE SET content = EXCLUDED.content, "
                + "char_start = EXCLUDED.char_start, char_end = EXCLUDED.char_end, status = 'active', "
                + "update_time = NOW(), update_by = EXCLUDED.update_by, is_deleted = FALSE";
        List<Object[]> batchArgs = new ArrayList<>(chunks.size());
        for (DocumentChunk chunk : chunks) {
            batchArgs.add(new Object[]{
                    newId(), deterministicChunkId(docId, chunk.chunkIndex()), docId, chunk.chunkIndex(),
                    chunk.content(), chunk.charStart(), chunk.charEnd(), DW_ACTOR, DW_ACTOR});
        }
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    // ==================== TRANSFORM_SQL ====================

    /** TRANSFORM_SQL 查询类执行：返回结果行（服务层按行数回写）。 */
    public List<Map<String, Object>> transformQuery(String sql) {
        return jdbcTemplate.queryForList(sql);
    }

    /** TRANSFORM_SQL DML/DDL 类执行：返回受影响行数。 */
    public long transformUpdate(String sql) {
        return jdbcTemplate.update(sql);
    }

    // ==================== OUTPUT_OBJECT ====================

    /**
     * OUTPUT_OBJECT: 将 rows 列表逐行 INSERT 到目标表（列名取自 row 的 key）。
     */
    public long insertRows(String targetTable, List<Map<String, Object>> rows) {
        long count = 0;
        for (Map<String, Object> row : rows) {
            StringBuilder cols = new StringBuilder();
            StringBuilder vals = new StringBuilder();
            List<Object> params = new ArrayList<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (!cols.isEmpty()) {
                    cols.append(", ");
                    vals.append(", ");
                }
                cols.append(entry.getKey());
                vals.append("?");
                params.add(entry.getValue());
            }
            String sql = "INSERT INTO " + targetTable + " (" + cols + ") VALUES (" + vals + ")";
            jdbcTemplate.update(sql, params.toArray());
            count++;
        }
        return count;
    }

    // ──────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String deterministicChunkId(String docId, int chunkIndex) {
        return UUID.nameUUIDFromBytes((docId + "#" + chunkIndex).getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }

    /** 段落截断（防超列长）。 */
    private static String truncateForColumn(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
