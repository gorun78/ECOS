package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * <p>V200/R3-B1: schema 由 `${kb.control-schema:ecos_knowledge}` 注入 (MC06 全面落地)。</p>
 *
 * <p><b>#146h D-7 收口（2026-10-27）</b>：三枚读腿由裸表（V115 kg_sync_log / V136 水位 /
 * V142 审计，dev PG 库面均不存在）切至 V179 合规新表 {@code ecos_kg_sync_log} /
 * {@code ecos_kb_extract_watermark} / {@code ecos_kb_extract_audit}。
 * 新表列名已语义化（{@code op→sync_op}、{@code report→report_json}(TEXT)、
 * {@code nodes→node_count}、{@code tier→extract_tier}…），本类以 SQL 别名
 * （AS op / report / created_at / updated_at / finished_at / tier / mode）
 * 保持既有 map 键面契约不变，消费方（StructuredExtractController 等）零改动。</p>
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
                "SELECT ontology_id, entity_code, resource_id, watermark, "
                        + "update_time AS updated_at "
                        + "FROM " + schema + ".ecos_kb_extract_watermark "
                        + "ORDER BY update_time DESC LIMIT ? OFFSET ?",
                limit, offset);
    }

    public List<Map<String, Object>> findLatestSyncLogByJobId(String jobId) {
        return jdbcTemplate.queryForList(
                "SELECT status, sync_op AS op, report_json AS report, error_message, "
                        + "create_time AS created_at, finished_time AS finished_at "
                        + "FROM " + schema + ".ecos_kg_sync_log WHERE job_id = ? "
                        + "ORDER BY create_time DESC LIMIT 1",
                jobId);
    }

    public List<Map<String, Object>> findAuditRowsByJobId(String jobId) {
        return jdbcTemplate.queryForList(
                "SELECT id, job_id, task_id, extract_tier AS tier, extract_mode AS mode, status, duration_ms, "
                        + "rows_total, rows_ok, rows_failed, mismatched, error_message, create_time AS created_at "
                        + "FROM " + schema + ".ecos_kb_extract_audit WHERE job_id = ? "
                        + "ORDER BY create_time DESC LIMIT 100",
                jobId);
    }

    /**
     * 水位线活行分页（{@code is_deleted=0}，按 {@code update_time} 倒序），供
     * {@code GET /api/v1/knowledge/extract/watermarks} 使用。
     *
     * <p>与 {@link #listWatermarkRows} 的区别是显式剔除软删行并补齐全列投影：
     * E4（作业列表）沿用旧投影与旧谓词，本批不改其语义（API 只增不改）。</p>
     */
    public List<Map<String, Object>> listWatermarkItems(int limit, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT ontology_id, entity_code, resource_id, watermark, trace_id, domain, version_no, "
                        + "update_time AS updated_at "
                        + "FROM " + schema + ".ecos_kb_extract_watermark WHERE is_deleted = 0 "
                        + "ORDER BY update_time DESC LIMIT ? OFFSET ?",
                limit, offset);
    }

    /** 水位线活行总数（与 {@link #listWatermarkItems} 同谓词）。 */
    public long countWatermarkItems() {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM " + schema + ".ecos_kb_extract_watermark WHERE is_deleted = 0",
                Long.class);
        return n == null ? 0L : n;
    }

    /**
     * 按 runtime-task {@code task_id} 反查最近一条抽取审计行，供
     * {@code GET /api/v1/knowledge/extract/runs/{taskId}} 在任务状态存储缺失时兜底。
     *
     * <p>runs 族的异步提交里 {@code jobId}（{@code KBK1S-…}）与 {@code taskId}（runtime-task 生成）
     * 是两个不同标识，审计行的 {@code job_id} 列不能用于反查任务 → 必须按 {@code task_id} 查。</p>
     */
    public List<Map<String, Object>> findLatestAuditByTaskId(String taskId) {
        return jdbcTemplate.queryForList(
                "SELECT job_id, task_id, extract_mode AS mode, status, duration_ms, "
                        + "rows_total, rows_ok, rows_failed, error_message, create_time AS created_at "
                        + "FROM " + schema + ".ecos_kb_extract_audit WHERE task_id = ? "
                        + "ORDER BY create_time DESC LIMIT 1",
                taskId);
    }
}
