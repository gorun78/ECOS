package com.chinacreator.gzcm.engine.data.quality.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.engine.data.quality.model.DqReportVO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * DqReportDao — DQ 报告数据访问层（PMO-48-D T15）。
 * <p>
 * 架构铁律 §2.5 #1 收口：原内联在 {@code DqReportServiceImpl} 的 JdbcTemplate 调用迁移到本 DAO，
 * SQL 文本一律离开 Service。SQL 列扫描仅限
 * {@code ecos_data.dq_report / dq_rule_check / dq_alert_record / dq_score_asset / dq_score_snapshot}
 * （不跨引擎 — 铁律 2.4 数据层边界）。
 *
 * @author PMO-48-D T15
 */
@Repository
public class DqReportDao extends JdbcTemplateDaoSupport {

    public Long countReports(String whereSql, Object[] params) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ecos_data.dq_report" + whereSql, Long.class, params);
    }

    public List<DqReportVO> listReports(String whereSql, RowMapper<DqReportVO> mapper, Object[] pageParams) {
        return jdbcTemplate.query(
                "SELECT id, report_type, scope, start_date, end_date, pdf_object_key," +
                "       llm_summary, row_count, alert_count, avg_score, created_by, created_at" +
                "  FROM ecos_data.dq_report" + whereSql +
                " ORDER BY end_date DESC, created_at DESC LIMIT ? OFFSET ?",
                mapper, pageParams);
    }

    public List<DqReportVO> findReport(String id, RowMapper<DqReportVO> mapper) {
        return jdbcTemplate.query(
                "SELECT id, report_type, scope, start_date, end_date, payload_html," +
                "       pdf_object_key, llm_summary, row_count, alert_count, avg_score," +
                "       created_by, created_at" +
                "  FROM ecos_data.dq_report WHERE id = ?",
                mapper, id);
    }

    public int insertReport(String id, String reportType, String scope,
                            java.sql.Date startDate, java.sql.Date endDate, String payloadHtml,
                            String pdfObjectKey, String llmSummary,
                            int rowCount, int alertCount, double avgScore) {
        return jdbcTemplate.update(
                "INSERT INTO ecos_data.dq_report" +
                " (id, report_type, scope, start_date, end_date, payload_html," +
                "  pdf_object_key, llm_summary, row_count, alert_count, avg_score, created_by, created_at)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'system', NOW())",
                id, reportType, scope, startDate, endDate, payloadHtml,
                pdfObjectKey, llmSummary, rowCount, alertCount, avgScore);
    }

    /** 规则检查行数 — SUM(total_rows)（不在范围就 0） */
    public Integer countRuleChecks(LocalDateTime startTs, LocalDateTime endTs) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_rows), 0) FROM ecos_data.dq_rule_check" +
                " WHERE executed_at >= ? AND executed_at < ?",
                Integer.class, startTs, endTs);
    }

    /** 告警数 */
    public Integer countAlerts(LocalDateTime startTs, LocalDateTime endTs) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ecos_data.dq_alert_record" +
                " WHERE created_at >= ? AND created_at < ?",
                Integer.class, startTs, endTs);
    }

    /** 系统平均分 */
    public Double avgScore() {
        return jdbcTemplate.queryForObject(
                "SELECT AVG(rolled_score) FROM ecos_data.dq_score_asset", Double.class);
    }

    /**
     * 取最近被评估的 6 维分值（每个维度只取 evaluated_at 最大的一条）。
     * <p>dq_score_snapshot 按 dimension 分组取最近值；系统级维度（scope_type 空值→任意 TYPE）便于报告。</p>
     */
    public List<Map<String, Object>> latestDimScores(LocalDateTime endTs) {
        return jdbcTemplate.queryForList(
                "SELECT s.dimension, s.score_value FROM ecos_data.dq_score_snapshot s" +
                " WHERE (s.dimension, s.evaluated_at) IN (" +
                "   SELECT dimension, MAX(evaluated_at) FROM ecos_data.dq_score_snapshot" +
                "     WHERE evaluated_at <= ? GROUP BY dimension" +
                " );",
                endTs);
    }
}
