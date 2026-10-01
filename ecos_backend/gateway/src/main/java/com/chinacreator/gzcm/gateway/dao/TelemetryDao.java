package com.chinacreator.gzcm.gateway.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * B6b 数据访问收口（架构铁律 §2.5 / IR06）：{@code TelemetryService} 内联的
 * {@code ecos_spans} / {@code ecos_token_usage} 裸 SQL 落点。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与 Service 层逐字节一致。</p>
 */
@Repository
public class TelemetryDao extends JdbcTemplateDaoSupport {

    /** 查询 ecos_spans / ecos_token_usage 的记录数 */
    public Map<String, Object> queryHealthCounts() {
        Long spanCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ecos_spans", Long.class);
        Long tokenCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ecos_token_usage", Long.class);
        return Map.of(
            "spans_stored", spanCount != null ? spanCount : 0L,
            "token_records", tokenCount != null ? tokenCount : 0L
        );
    }

    /** Trace 列表（最近 limit 条） */
    public List<Map<String, Object>> queryTraces(int limit) {
        return jdbcTemplate.queryForList(
            "SELECT DISTINCT ON (trace_id) " +
            "  trace_id, " +
            "  MIN(operation_name) AS first_operation, " +
            "  COUNT(*) AS span_count, " +
            "  MAX(http_status) AS max_status, " +
            "  SUM(duration_ms) AS total_duration_ms, " +
            "  MIN(start_time) AS first_start, " +
            "  MAX(end_time) AS last_end " +
            "FROM ecos_spans " +
            "GROUP BY trace_id " +
            "ORDER BY trace_id DESC " +
            "LIMIT ?", limit);
    }

    /** Trace 详情：指定 traceId 的所有 Span */
    public List<Map<String, Object>> queryTraceDetail(String traceId) {
        return jdbcTemplate.queryForList(
            "SELECT span_id, trace_id, parent_span_id, operation_name, " +
            "       service_name, http_method, http_path, http_status, " +
            "       start_time, end_time, duration_ms, status, attributes " +
            "FROM ecos_spans " +
            "WHERE trace_id = ? " +
            "ORDER BY start_time ASC", traceId);
    }

    /** Token 用量汇总 */
    public Map<String, Object> queryTokenSummaryTotals(int days) {
        return jdbcTemplate.queryForMap(
            "SELECT " +
            "  COALESCE(SUM(prompt_tokens), 0) AS total_prompt, " +
            "  COALESCE(SUM(completion_tokens), 0) AS total_completion, " +
            "  COALESCE(SUM(total_tokens), 0) AS grand_total, " +
            "  COUNT(*) AS record_count " +
            "FROM ecos_token_usage " +
            "WHERE created_at >= NOW() - (? || ' days')::INTERVAL", days);
    }

    /** Token 用量按模型汇总 */
    public List<Map<String, Object>> queryTokenSummaryByModel(int days) {
        return jdbcTemplate.queryForList(
            "SELECT model, " +
            "  SUM(prompt_tokens) AS prompt, " +
            "  SUM(completion_tokens) AS completion, " +
            "  SUM(total_tokens) AS total, " +
            "  COUNT(*) AS calls " +
            "FROM ecos_token_usage " +
            "WHERE created_at >= NOW() - (? || ' days')::INTERVAL " +
            "GROUP BY model " +
            "ORDER BY total DESC", days);
    }

    /** Token 用量按操作汇总 */
    public List<Map<String, Object>> queryTokenSummaryByOperation(int days) {
        return jdbcTemplate.queryForList(
            "SELECT operation, " +
            "  SUM(total_tokens) AS total, " +
            "  COUNT(*) AS calls " +
            "FROM ecos_token_usage " +
            "WHERE created_at >= NOW() - (? || ' days')::INTERVAL " +
            "GROUP BY operation " +
            "ORDER BY total DESC", days);
    }
}
