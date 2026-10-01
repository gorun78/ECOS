package com.chinacreator.gzcm.engine.data.quality.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.engine.data.quality.model.DqAlertVO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * DqAlertDao — DQ 告警分发数据访问层（PMO-48-C T12）。
 * <p>
 * 架构铁律 §2.5 #1 收口：原内联在 {@code DqAlertServiceImpl} 的 JdbcTemplate 调用迁移到本 DAO，
 * SQL 文本一律离开 Service。表前缀统一为 {@code ecos_data}。
 *
 * @author PMO-48-C T12
 */
@Repository
public class DqAlertDao extends JdbcTemplateDaoSupport {

    private static final String ALERT_COLUMNS =
            "SELECT id, rule_id AS ruleId, alert_level AS alertLevel, alert_type AS alertType,"
            + " asset_id AS assetId, asset_name AS assetName, rule_name AS ruleName, message,"
            + " payload::text AS payloadJson, status, escalated_to AS escalatedTo,"
            + " notify_count AS notifyCount, last_notify_at AS lastNotifyAt,"
            + " ack_by AS ackBy, ack_at AS ackAt, resolved_by AS resolvedBy,"
            + " resolved_at AS resolvedAt, resolved_note AS resolvedNote, created_at AS createdAt"
            + " FROM ecos_data.dq_alert_record";

    /** 查 check 行诊断位（pass_rate/total_rows 等）。 */
    public List<Map<String, Object>> readCheckRow(String checkId) {
        return jdbcTemplate.queryForList(
                "SELECT id, rule_id AS ruleId, passed, total_rows AS totalRows,"
                + " failed_rows AS failedRows, pass_rate AS passRate,"
                + " error_message AS errorMessage, sample_size AS sampleSize"
                + " FROM ecos_data.dq_rule_check WHERE id = ?",
                checkId);
    }

    /** 新建告警记录（P0-P3 全量落库）。 */
    public int insertAlertRecord(String alertId, String ruleId, String alertLevel, String alertType,
                                 String assetId, String assetName, String ruleName, String message,
                                 String payloadJson, String status, String notifyChannels) {
        return jdbcTemplate.update(
                "INSERT INTO ecos_data.dq_alert_record"
                + " (id, rule_id, alert_level, alert_type, asset_id, asset_name, rule_name,"
                + "  message, payload, status, notify_count, last_notify_at, notify_channels)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, 1, NOW(), ?::jsonb)",
                alertId, ruleId, alertLevel, alertType, assetId, assetName, ruleName, message,
                payloadJson, status, notifyChannels);
    }

    /** 推送成功后回写 NOTIFIED 状态。 */
    public int markNotified(String alertId) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_alert_record SET status = 'NOTIFIED',"
                + " last_notify_at = NOW() WHERE id = ?",
                alertId);
    }

    /** 判重：同 rule 窗口内 status IN (PENDING, NOTIFIED) 的最新一条 id。 */
    public List<String> findDedupAlertIds(String ruleId, String windowMinutes) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM ecos_data.dq_alert_record"
                + " WHERE rule_id = ? AND status IN ('PENDING','NOTIFIED')"
                + " AND created_at >= NOW() - (? || ' minutes')::interval"
                + " ORDER BY created_at DESC LIMIT 1",
                String.class, ruleId, windowMinutes);
    }

    /** 判重合并：notify_count++ + 刷新 last_notify_at/payload/status。 */
    public int notifyDedupAlert(String assetId, String alertId) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_alert_record SET notify_count = notify_count + 1,"
                + " last_notify_at = NOW(), status = 'NOTIFIED',"
                + " asset_id = COALESCE(?, asset_id) WHERE id = ?",
                assetId, alertId);
    }

    /** 幂等：同 alert 已有未删除工单 → 返回其 id。 */
    public List<String> findExistingWorkOrderIds(String alertId) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM ecos_data.dq_work_order WHERE alert_id = ? AND is_deleted = FALSE LIMIT 1",
                String.class, alertId);
    }

    /** 回写 workOrderId 到 alert payload。 */
    public int addWorkOrderLinkToAlert(String workOrderJsonFragment, String alertId) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_alert_record SET payload = payload || ?::jsonb WHERE id = ?",
                workOrderJsonFragment, alertId);
    }

    /** count（动态 where，参数在 where 内）。 */
    public Integer countAlerts(String where, Object[] countParams) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ecos_data.dq_alert_record" + where,
                Integer.class, countParams);
    }

    /** 分页列表（动态 where，末尾 LIMIT ? OFFSET ?）。 */
    public List<DqAlertVO> listAlerts(String where, RowMapper<DqAlertVO> mapper, Object[] listParams) {
        return jdbcTemplate.query(
                ALERT_COLUMNS + where
                + " ORDER BY created_at DESC, id LIMIT ? OFFSET ?",
                mapper, listParams);
    }

    /** 按 id 查询单条告警。 */
    public List<DqAlertVO> findAlertById(String id, RowMapper<DqAlertVO> mapper) {
        return jdbcTemplate.query(
                ALERT_COLUMNS + " WHERE id = ?", mapper, id);
    }

    /** ack 告警（乐观条件 status IN PENDING/NOTIFIED/ESCALATED）。 */
    public int ackAlert(String ackBy, String id) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_alert_record SET status = 'ACKED', ack_by = ?, ack_at = NOW()"
                + " WHERE id = ? AND status IN ('PENDING','NOTIFIED','ESCALATED')",
                ackBy, id);
    }

    /** 解决告警（乐观条件 status IN PENDING/NOTIFIED/ACKED/ESCALATED）。 */
    public int resolveAlert(String resolvedBy, String note, String id) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_alert_record SET status = 'RESOLVED', resolved_by = ?,"
                + " resolved_at = NOW(), resolved_note = ?"
                + " WHERE id = ? AND status IN ('PENDING','NOTIFIED','ACKED','ESCALATED')",
                resolvedBy, note, id);
    }
}
