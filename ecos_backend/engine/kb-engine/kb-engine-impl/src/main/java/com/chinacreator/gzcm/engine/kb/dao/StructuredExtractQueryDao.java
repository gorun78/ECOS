package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * <p>V200/R3-B1: schema 由 `${kb.control-schema:ecos_knowledge}` 注入 (MC06 全面落地)。</p>
 */
@Repository
public class StructuredExtractQueryDao extends JdbcTemplateDaoSupport {

    private final String schema;

    public StructuredExtractQueryDao(
            @Value("${kb.control-schema:ecos_knowledge}") String schema) {
        this.schema = schema;
    }

    public List<Map<String, Object>> listWatermarkRows(int limit, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT ontology_id, entity_code, resource_id, watermark, updated_at "
                        + "FROM " + schema + ".kb_extract_watermark "
                        + "ORDER BY updated_at DESC LIMIT ? OFFSET ?",
                limit, offset);
    }

    public List<Map<String, Object>> findLatestSyncLogByJobId(String jobId) {
        return jdbcTemplate.queryForList(
                "SELECT status, op, report, error_message, created_at, finished_at "
                        + "FROM " + schema + ".kg_sync_log WHERE job_id = ? "
                        + "ORDER BY created_at DESC LIMIT 1",
                jobId);
    }

    public List<Map<String, Object>> findAuditRowsByJobId(String jobId) {
        return jdbcTemplate.queryForList(
                "SELECT id, job_id, task_id, tier, mode, status, duration_ms, "
                        + "rows_total, rows_ok, rows_failed, mismatched, error_message, created_at "
                        + "FROM " + schema + ".kb_extract_audit WHERE job_id = ? "
                        + "ORDER BY created_at DESC LIMIT 100",
                jobId);
    }
}
