package com.chinacreator.gzcm.engine.kb.service;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 结构化抽取数据访问服务 — {@code kb_extract_watermark} / {@code kg_sync_log} /
 * {@code kb_extract_audit}（均在 ecos_knowledge schema）的只读查询。
 *
 * <p>供 {@code StructuredExtractController} 调用：三处 SELECT 全部收敛在 Service 层
 * （架构铁律 §3.6：Controller 不得直接访问数据库）。</p>
 *
 * <p>异常语义：查询抛出的 {@code DataAccessException} 原样上浮，由调用方 Controller
 * 捕获并按既有 HTTP 行为降级返回。</p>
 */
@Service
public class StructuredExtractQueryService {

    private final JdbcTemplate jdbcTemplate;

    public StructuredExtractQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 水位表分页行（按 updated_at 倒序），供 E4 作业列表使用。
     *
     * @param limit  分页大小（LIMIT）
     * @param offset 偏移（OFFSET）
     * @return 原始行列表（ontology_id / entity_code / resource_id / watermark / updated_at）
     */
    public List<Map<String, Object>> listWatermarkRows(int limit, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT ontology_id, entity_code, resource_id, watermark, updated_at "
                        + "FROM ecos_knowledge.kb_extract_watermark "
                        + "ORDER BY updated_at DESC LIMIT ? OFFSET ?",
                limit, offset);
    }

    /**
     * 按 jobId 反查 {@code kg_sync_log} 最近一条记录，供 E5 作业详情使用。
     *
     * @param jobId 作业 ID（KBK1S- 前缀，由调用方校验）
     * @return 原始行列表（status / op / report / error_message / created_at / finished_at），最多 1 行
     */
    public List<Map<String, Object>> findLatestSyncLogByJobId(String jobId) {
        return jdbcTemplate.queryForList(
                "SELECT status, op, report, error_message, created_at, finished_at "
                        + "FROM ecos_knowledge.kg_sync_log WHERE job_id = ? "
                        + "ORDER BY created_at DESC LIMIT 1",
                jobId);
    }

    /**
     * 按 jobId 查抽取审计表最近 100 行（created_at 倒序），供 T6 日志查询 / 导出使用。
     *
     * @param jobId 作业 ID
     * @return 原始行列表（id / job_id / task_id / tier / mode / status / duration_ms /
     *         rows_total / rows_ok / rows_failed / mismatched / error_message / created_at）
     */
    public List<Map<String, Object>> findAuditRowsByJobId(String jobId) {
        return jdbcTemplate.queryForList(
                "SELECT id, job_id, task_id, tier, mode, status, duration_ms, "
                        + "rows_total, rows_ok, rows_failed, mismatched, error_message, created_at "
                        + "FROM ecos_knowledge.kb_extract_audit WHERE job_id = ? "
                        + "ORDER BY created_at DESC LIMIT 100",
                jobId);
    }
}
