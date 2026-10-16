package com.chinacreator.gzcm.engine.data.quality.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * DqScheduleDao — DQ 监控调度数据访问层（PMO-48-C T11）。
 * <p>
 * 架构铁律 §2.5 #1 收口：原内联在 {@code DqScheduleServiceImpl} 的 JdbcTemplate 调用迁移到本 DAO，
 * SQL 文本一律离开 Service。表前缀统一为 {@code ecos_data}。
 * Schedule CRUD 仍走 MyBatis（DqScheduleMapper），此处仅承接原内联 SQL 的
 * check 行读写与 rule_ids 回查。
 *
 * @author PMO-48-C T11
 */
@Repository
public class DqScheduleDao extends JdbcTemplateDaoSupport {

    /**
     * F02-09：读最近 1h 内该规则的最后一条 dq_rule_check（跨窗污染防护，与评分窗口对齐），
     * 无行时返回空列表，由 Service 置 contextIncomplete。
     */
    public List<Map<String, Object>> findLastCheck(String ruleId) {
        return jdbcTemplate.queryForList(
                "SELECT id, rule_id AS ruleId, schedule_id AS scheduleId, trigger_type AS triggerType,"
                + " executed_at AS executedAt, passed, total_rows AS totalRows,"
                + " failed_rows AS failedRows, pass_rate AS passRate, sample_size AS sampleSize,"
                + " sample_failures::text AS sampleFailuresJson, error_message AS errorMessage"
                + " FROM ecos_data.dq_rule_check WHERE rule_id = ?"
                + "   AND executed_at >= NOW() - interval '1 hour'"
                + " ORDER BY executed_at DESC, id LIMIT 1",
                ruleId);
    }

    /** 写 dq_rule_check（通过/未通过/评估失败统一语义）。 */
    public void insertCheckRow(String checkId, String ruleId, String scheduleId, String triggerType,
                               boolean passed, long totalRows, long failedRows, double passRate,
                               int latencyMs, String errorMessage, int sampleSize) {
        jdbcTemplate.update(
                "INSERT INTO ecos_data.dq_rule_check" +
                " (id, rule_id, schedule_id, trigger_type, executed_at, passed, total_rows, failed_rows," +
                "  pass_rate, latency_ms, error_message, sample_size, sample_failures)" +
                " VALUES (?, ?, ?, ?, NOW(), ?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
                checkId, ruleId, scheduleId, triggerType, passed,
                totalRows, failedRows, passRate, latencyMs,
                errorMessage, sampleSize, "{}");
    }

    /**
     * 回查 schedule.rule_ids JSON 原文（MyBatis 在 VO 无 ruleIdsJson 字段时 JSON 文本读入
     * List&lt;String&gt; 会静默失败，此处统一走 DB 回查保证内容可取）。
     */
    public String selectRuleIdsJson(String scheduleId) {
        List<String> rows = jdbcTemplate.query(
                "SELECT rule_ids::text FROM ecos_data.dq_schedule WHERE id = ? AND is_deleted = FALSE",
                (rs, i) -> rs.getString(1),
                scheduleId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
