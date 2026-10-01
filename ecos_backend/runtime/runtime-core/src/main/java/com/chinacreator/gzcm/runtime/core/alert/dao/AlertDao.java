package com.chinacreator.gzcm.runtime.core.alert.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 详细设计-00 F00-09 (W07) — 分级告警规则/记录的控制域持久化。
 *
 * <p>落 {@code ecos_control.ecos_runtime_alert_rule} / {@code ecos_control.ecos_runtime_alert_record}
 * （DDL = 08 册单源 V242，C.6.2 模板合规：UUID 应用侧、版本列双载、双时间戳）。
 * 仅在有 {@link JdbcTemplate} 的容器（gateway / 各 service）内生效；无数据源栈则本 Bean 不装配，
 * {@link com.chinacreator.gzcm.runtime.core.alert.service.impl.AlertServiceImpl} 退回纯进程内缓存。</p>
 *
 * <p>铁律红线 EN01/IR01：所有 slot 参数化，缺失 NOT-NULL 列给显式默认。</p>
 *
 * <p>架构铁律 §2.5#1 / IR06 收口（R2-B4c）：本类即数据访问收口点，继承 root DDL
 * runtime-core {@link JdbcTemplateDaoSupport}，使用其 {@code jdbcTemplate} 通道；
 * SQL 文本自原 {@code AlertRepository}（..alert.persist..）逐字节下沉，方法签名与行为不变。</p>
 */
@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class AlertDao extends JdbcTemplateDaoSupport {

    private static final Logger log = LoggerFactory.getLogger(AlertDao.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String V = "1.0.0";

    // ============================= 规则 =============================

    public void insertRule(String ruleCode, String metricKey, String severity,
                           java.math.BigDecimal threshold, Integer windowSec,
                           Integer upgradeMin, Integer enabled, String channel, String webhookUrl) {
        jdbcTemplate.update(
            "INSERT INTO ecos_control.ecos_runtime_alert_rule" +
            " (id, rule_code, metric_key, severity, threshold_num, window_sec, upgrade_min," +
            "  enabled, channel, webhook_url, version_no, is_deleted, domain)" +
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'default')",
            java.util.UUID.randomUUID().toString(),
            safe(ruleCode), safe(metricKey), safe(severity),
            threshold, windowSec == null ? 300 : windowSec, upgradeMin,
            enabled == null ? 1 : enabled, safe(channel), webhookUrl, V);
    }

    // ============================= 记录 =============================

    public String insertRecord(String ruleCode, String severity, String title, Map<String, Object> detail,
                               String sourceModule, String traceId, String tenantId) {
        String id = java.util.UUID.randomUUID().toString();
        jdbcTemplate.update(
            "INSERT INTO ecos_control.ecos_runtime_alert_record" +
            " (id, rule_code, severity, status, title, detail_json, source_module, trace_id," +
            "  occurred_at, version_no, is_deleted, domain, tenant_id)" +
            " VALUES (?, ?, ?, 'open', ?, ?, ?, ?, NOW(), ?, 0, 'default', ?)",
            id, safe(ruleCode), safe(severity), truncate(safe(title), 255),
            toJson(detail), safe(sourceModule), safe(traceId).isEmpty() ? null : safe(traceId), V, tenantId);
        return id;
    }

    public Map<String, Object> findRecordById(String id) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT id, rule_code AS ruleCode, severity, status, title, detail_json AS detailJson," +
                " source_module AS sourceModule, trace_id AS traceId, ack_by AS ackBy, ack_time AS ackTime," +
                " close_time AS closeTime, occurred_at AS occurredAt" +
                " FROM ecos_control.ecos_runtime_alert_record WHERE id = ? AND is_deleted = 0", id);
        } catch (Exception ex) {
            return null;
        }
    }

    public List<Map<String, Object>> listRecords(String status, String severity, int page, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE is_deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isEmpty()) { where.append("AND status = ?"); args.add(status); }
        if (severity != null && !severity.isEmpty()) { where.append("AND severity = ?"); args.add(severity); }
        String sql = "SELECT id, rule_code AS ruleCode, severity, status, title, detail_json AS detailJson," +
                " source_module AS sourceModule, trace_id AS traceId, ack_by AS ackBy, ack_time AS ackTime," +
                " close_time AS closeTime, occurred_at AS occurredAt" +
                " FROM ecos_control.ecos_runtime_alert_record" + where +
                " ORDER BY occurred_at DESC LIMIT ? OFFSET ?";
        args.add(pageSize);
        args.add((page - 1) * pageSize);
        return jdbcTemplate.query(sql, RECORD_MAPPER, args.toArray());
    }

    public long countRecords(String status, String severity) {
        StringBuilder where = new StringBuilder(" WHERE is_deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isEmpty()) { where.append("AND status = ?"); args.add(status); }
        if (severity != null && !severity.isEmpty()) { where.append("AND severity = ?"); args.add(severity); }
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_control.ecos_runtime_alert_record" + where, Long.class, args.toArray());
        return c == null ? 0 : c;
    }

    public boolean ackRecord(String id, String ackBy, String note) {
        return jdbcTemplate.update(
            "UPDATE ecos_control.ecos_runtime_alert_record SET status = 'acked', ack_by = ?, ack_time = NOW()," +
            " detail_json = ? , update_time = NOW()" +
            " WHERE id = ? AND status = 'open'",
            safe(ackBy), appendAckNote(lookupDetail(id), note), id) > 0;
    }

    public boolean closeRecord(String id) {
        return jdbcTemplate.update(
            "UPDATE ecos_control.ecos_runtime_alert_record SET status = 'closed', close_time = NOW(), update_time = NOW()" +
            " WHERE id = ? AND status IN ('open','acked')", id) > 0;
    }

    // ============================= 辅助 =============================

    private static final RowMapper<Map<String, Object>> RECORD_MAPPER = (rs, n) -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getString("id"));
        m.put("ruleCode", rs.getString("rule_code"));
        m.put("severity", rs.getString("severity"));
        m.put("status", rs.getString("status"));
        m.put("title", rs.getString("title"));
        m.put("detailJson", rs.getString("detail_json"));
        m.put("sourceModule", rs.getString("source_module"));
        m.put("traceId", rs.getString("trace_id"));
        m.put("ackBy", rs.getString("ack_by"));
        Timestamp ack = rs.getTimestamp("ack_time");
        m.put("ackTime", ack == null ? null : ack.toLocalDateTime().toString());
        Timestamp close = rs.getTimestamp("close_time");
        m.put("closeTime", close == null ? null : close.toLocalDateTime().toString());
        Timestamp occ = rs.getTimestamp("occurred_at");
        m.put("occurredAt", occ == null ? null : occ.toLocalDateTime().toString());
        return m;
    };

    private String lookupDetail(String id) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT detail_json FROM ecos_control.ecos_runtime_alert_record WHERE id = ? AND is_deleted = 0",
                String.class, id);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String appendAckNote(String existingDetail, String note) {
        if (note == null || note.isEmpty()) return existingDetail;
        Map<String, Object> m = new LinkedHashMap<>();
        if (existingDetail != null && !existingDetail.isEmpty()) {
            try { m.putAll(MAPPER.readValue(existingDetail, LinkedHashMap.class)); } catch (Exception ignore) {}
        }
        m.put("ackNote", note);
        m.put("ackedAt", java.time.LocalDateTime.now().toString());
        try { return MAPPER.writeValueAsString(m); } catch (Exception e) { return existingDetail; }
    }

    private static String toJson(Object o) {
        if (o == null) return null;
        try { return MAPPER.writeValueAsString(o); } catch (Exception e) { return String.valueOf(o); }
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String truncate(String s, int len) {
        if (s == null) return null;
        return s.length() <= len ? s : s.substring(0, len);
    }
}
