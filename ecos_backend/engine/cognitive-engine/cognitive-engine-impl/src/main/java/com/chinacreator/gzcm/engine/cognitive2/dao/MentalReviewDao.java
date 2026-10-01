package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * B2-b 数据访问收口（架构铁律 §2.5 #1 / IR06）：{@code MentalReviewService}
 * （周一故障复盘聚合）内联的三表版本链 + V130 impact 表 + V131 告警表只读 SQL 落点。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与原 Service 逐字节一致；
 * 分组 / 归一化 / summary 统计留在 Service 层。
 * {@code warnAlerts} 查询在 Service 层保留 try-catch 降级空列表（V131 表不可用不阻断复盘）。</p>
 */
@Repository
public class MentalReviewDao extends JdbcTemplateDaoSupport {

    /** belief 版本时间线（variable 分组输入；since 过滤按版本行 update_time）。 */
    public List<Map<String, Object>> beliefTimelines(Timestamp since) {
        StringBuilder sql = new StringBuilder(
            "SELECT variable_name, domain, version, distribution, is_manual_override, " +
            "last_evidence_id, status, update_time FROM ecos_cognitive_belief " +
            "WHERE is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (since != null) {
            sql.append(" AND update_time >= ?");
            args.add(since);
        }
        sql.append(" ORDER BY variable_name, domain, version");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    /** 假设时间线（since 按 invalid_at/update_time；全列显式清单，0 SELECT *）。 */
    public List<Map<String, Object>> hypotheses(Timestamp since) {
        StringBuilder sql = new StringBuilder(
            "SELECT h.id, h.hypothesis_code, h.domain, h.metric_ref, h.status, h.is_valid, " +
            "h.invalid_at, h.invalid_reason, h.update_time, h.evidence_ids " +
            "FROM ecos_cognitive_hypothesis h WHERE h.is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (since != null) {
            sql.append(" AND (h.invalid_at IS NULL OR h.invalid_at >= ? OR h.update_time >= ?)");
            args.add(since);
            args.add(since);
        }
        sql.append(" ORDER BY h.update_time DESC, h.id");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    /** 证据登记时间线（since 按 create_time）。 */
    public List<Map<String, Object>> evidenceTimeline(Timestamp since) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, evidence_code, source_type, confidence, is_conflict, status, " +
            "effective_time, create_time FROM ecos_cognitive_evidence WHERE is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (since != null) {
            sql.append(" AND create_time >= ?");
            args.add(since);
        }
        sql.append(" ORDER BY create_time ASC, id");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    /** run 作废留痕（V130 impact 表，since 按 superseded_at）。 */
    public List<Map<String, Object>> runImpacts(Timestamp since) {
        StringBuilder sql = new StringBuilder(
            "SELECT event_id, hypothesis_id, run_id, auto_detected, superseded_at, detail " +
            "FROM ecos_cognitive_run_invalidation WHERE is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (since != null) {
            sql.append(" AND superseded_at >= ?");
            args.add(since);
        }
        sql.append(" ORDER BY superseded_at DESC, id");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    /** 告警留痕（runtime-monitor {@code ecos_warn_log} V131 只读 join，tag 等值 + 时间下界）。 */
    public List<Map<String, Object>> warnAlerts(String reviewTag, Timestamp since) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, log_id, warn_type, warn_level, warn_objid, warn_objname, warn_message, " +
            "fault_context, review_tag, warn_time FROM ecos_warn_log " +
            "WHERE is_deleted = 0 AND review_tag = ?");
        List<Object> args = new ArrayList<>();
        args.add(reviewTag);
        if (since != null) {
            sql.append(" AND warn_time >= ?");
            args.add(since);
        }
        sql.append(" ORDER BY warn_time DESC, id");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }
}
