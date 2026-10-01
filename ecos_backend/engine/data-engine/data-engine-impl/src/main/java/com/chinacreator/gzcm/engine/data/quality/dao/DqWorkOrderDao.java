package com.chinacreator.gzcm.engine.data.quality.dao;

import java.sql.SQLException;
import java.util.List;

import com.chinacreator.gzcm.engine.data.quality.model.DqWorkOrderVO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * DqWorkOrderDao — DQ 工单数据访问层（PMO-48-C T12 + T13）。
 * <p>
 * 架构铁律 §2.5 #1 收口：原内联在 {@code DqWorkOrderServiceImpl} 的 JdbcTemplate 调用迁移到本 DAO，
 * SQL 文本一律离开 Service。表前缀统一为 {@code ecos_data}。
 *
 * @author PMO-48-C T12 + T13
 */
@Repository
public class DqWorkOrderDao extends JdbcTemplateDaoSupport {

    /** SELECT 列清单（短别名；rca_result 强制 ::text 防 JSONB 不解）。 */
    static final String WO_COLUMNS =
            "id, order_no AS orderNo, alert_id AS alertId, rule_id AS ruleId," +
            " asset_id AS assetId, title, description, status, handling_mode AS handlingMode," +
            " severity, assigned_to AS assignedTo, assigned_at AS assignedAt," +
            " repair_action AS repairAction, repair_status AS repairStatus, repair_log AS repairLog," +
            " verified_by AS verifiedBy, verified_at AS verifiedAt, verify_pass AS verifyPass," +
            " verify_note AS verifyNote, resolved_by AS resolvedBy, resolved_at AS resolvedAt," +
            " resolution_note AS resolutionNote," +
            " rca_result::text AS rcaResult, rca_confidence AS rcaConfidence," +
            " rca_analyzed_at AS rcaAnalyzedAt," +
            " retry_count AS retryCount, created_at AS createdAt, updated_at AS updatedAt," +
            " closed_at AS closedAt";

    /** 行 → VO 映射器（原 Service 静态 ROW_MAPPER / mapVo 移入此处，SQL 与映射同仓）。 */
    static final RowMapper<DqWorkOrderVO> ROW_MAPPER = (rs, rowNum) -> mapVo(rs);

    public int insertWorkOrder(String id, String orderNo, String alertId, String ruleId, String assetId,
                               String title, String description, String status, String handlingMode,
                               String severity) {
        return jdbcTemplate.update(
                "INSERT INTO ecos_data.dq_work_order" +
                " (id, order_no, alert_id, rule_id, asset_id, title, description, status, handling_mode, severity," +
                "  retry_count, created_at, updated_at, is_deleted)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NOW(), NOW(), FALSE)",
                id, orderNo, alertId, ruleId, assetId, title, description,
                status, handlingMode, severity);
    }

    public Long countWorkOrders(String whereSql, Object[] args) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ecos_data.dq_work_order" + whereSql, Long.class, args);
    }

    public List<DqWorkOrderVO> listWorkOrders(String whereSql, Object[] pageArgs) {
        return jdbcTemplate.query(
                "SELECT " + WO_COLUMNS + " FROM ecos_data.dq_work_order" + whereSql +
                " ORDER BY created_at DESC, id LIMIT ? OFFSET ?",
                ROW_MAPPER, pageArgs);
    }

    public List<DqWorkOrderVO> findWorkOrder(String id) {
        return jdbcTemplate.query(
                "SELECT " + WO_COLUMNS +
                " FROM ecos_data.dq_work_order WHERE id = ? AND is_deleted = FALSE",
                ROW_MAPPER, id);
    }

    public int assign(String assignedTo, String handlingMode, String id, String currentStatus) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_work_order" +
                " SET status = ?, assigned_to = ?, assigned_at = NOW()," +
                "     handling_mode = ?, updated_at = NOW()" +
                " WHERE id = ? AND status = ?",
                "ASSIGNED", assignedTo, handlingMode, id, currentStatus);
    }

    public int startWork(String id, String currentStatus) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_work_order SET status = ?, updated_at = NOW()" +
                " WHERE id = ? AND status = ?",
                "IN_WORK", id, currentStatus);
    }

    public int resolve(String resolvedBy, String resolutionNote, String id, String currentStatus) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_work_order" +
                " SET status = ?, resolved_by = ?, resolved_at = NOW()," +
                "     resolution_note = ?, updated_at = NOW()" +
                " WHERE id = ? AND status = ?",
                "RESOLVED", resolvedBy, resolutionNote, id, currentStatus);
    }

    public int verify(String target, String verifiedBy, boolean pass, String note, int retry,
                      String handlingMode, String id, String currentStatus) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_work_order" +
                " SET status = ?, verified_by = ?, verified_at = NOW()," +
                "     verify_pass = ?, verify_note = ?," +
                "     retry_count = ?, handling_mode = ?," +
                "     updated_at = NOW()" +
                " WHERE id = ? AND status = ?",
                target, verifiedBy, pass, note, retry, handlingMode, id, currentStatus);
    }

    public int close(String id, String currentStatus) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_work_order SET status = ?, closed_at = NOW(), updated_at = NOW()" +
                " WHERE id = ? AND status = ?",
                "CLOSED", id, currentStatus);
    }

    public int reject(String appendReason, String id, String currentStatus) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_work_order" +
                " SET status = ?," +
                "     description = COALESCE(description, '') || ?," +
                "     updated_at = NOW()" +
                " WHERE id = ? AND status = ?",
                "REJECTED", appendReason, id, currentStatus);
    }

    public int escalateSeverity(String newSev, String id) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_work_order SET severity = ?, updated_at = NOW()" +
                " WHERE id = ? AND status = ? AND is_deleted = FALSE",
                newSev, id, "PENDING");
    }

    public int writeRcaResult(String resultJson, Double confidence, String id) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_work_order" +
                " SET rca_result = ?::jsonb, rca_confidence = ?, rca_analyzed_at = NOW()," +
                "     updated_at = NOW()" +
                " WHERE id = ? AND is_deleted = FALSE",
                resultJson, confidence, id);
    }

    public int resetRcaConfidence(String id) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_work_order SET rca_confidence = 0," +
                "  rca_analyzed_at = NOW(), updated_at = NOW()" +
                "  WHERE id = ? AND is_deleted = FALSE",
                id);
    }

    public List<java.util.Map<String, Object>> riskRanking(String sevClause, Object[] args) {
        return jdbcTemplate.query(
                "SELECT w.id AS id, w.order_no AS orderNo, w.severity AS severity," +
                "       w.status AS status, w.asset_id AS assetId, w.rule_id AS ruleId," +
                "       CASE w.severity WHEN 'CRITICAL' THEN 4 WHEN 'HIGH' THEN 3" +
                "            WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 1 ELSE 0 END AS weight," +
                "       w.retry_count AS retryCount, w.created_at AS createdAt," +
                "       w.rca_confidence AS rcaConfidence, w.title AS title" +
                "  FROM ecos_data.dq_work_order w" +
                " WHERE w.is_deleted = FALSE AND " + sevClause +
                " ORDER BY weight DESC, w.retry_count DESC, w.created_at DESC" +
                " LIMIT ?",
                (rs, rowNum) -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("orderNo", rs.getString("orderNo"));
                    m.put("severity", rs.getString("severity"));
                    m.put("status", rs.getString("status"));
                    m.put("assetId", rs.getString("assetId"));
                    m.put("ruleId", rs.getString("ruleId"));
                    m.put("weight", rs.getInt("weight"));
                    m.put("retryCount", rs.getInt("retryCount"));
                    java.sql.Timestamp ts = rs.getTimestamp("createdAt");
                    m.put("createdAt", ts != null ? ts.toLocalDateTime() : null);
                    Double conf = rs.getDouble("rcaConfidence");
                    m.put("rcaConfidence", rs.wasNull() ? null : conf);
                    m.put("title", rs.getString("title"));
                    return m;
                },
                args);
    }

    public Integer nextOrderSeq(String prefix) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(CAST(SUBSTRING(order_no FROM 'WO-\\d{8}-(\\d{3,})$') AS INTEGER)) + 1, 1)" +
                "  FROM ecos_data.dq_work_order WHERE order_no LIKE ? || '%'",
                Integer.class, prefix);
    }

    /** 行 → VO（驼峰列直读；verify_pass 用 wasNull 判空；rca_confidence 同样）。 */
    static DqWorkOrderVO mapVo(java.sql.ResultSet rs) {
        try {
            DqWorkOrderVO vo = new DqWorkOrderVO();
            vo.setId(rs.getString("id"));
            vo.setOrderNo(rs.getString("orderNo"));
            vo.setAlertId(rs.getString("alertId"));
            vo.setRuleId(rs.getString("ruleId"));
            vo.setAssetId(rs.getString("assetId"));
            vo.setTitle(rs.getString("title"));
            vo.setDescription(rs.getString("description"));
            vo.setStatus(rs.getString("status"));
            vo.setHandlingMode(rs.getString("handlingMode"));
            vo.setSeverity(rs.getString("severity"));
            vo.setAssignedTo(rs.getString("assignedTo"));
            java.sql.Timestamp ats = rs.getTimestamp("assignedAt");
            vo.setAssignedAt(ats != null ? ats.toLocalDateTime() : null);
            vo.setRepairAction(rs.getString("repairAction"));
            vo.setRepairStatus(rs.getString("repairStatus"));
            vo.setRepairLog(rs.getString("repairLog"));
            vo.setVerifiedBy(rs.getString("verifiedBy"));
            java.sql.Timestamp vat = rs.getTimestamp("verifiedAt");
            vo.setVerifiedAt(vat != null ? vat.toLocalDateTime() : null);
            vo.setVerifyPass(rs.getBoolean("verifyPass") && !rs.wasNull());
            vo.setVerifyNote(rs.getString("verifyNote"));
            vo.setResolvedBy(rs.getString("resolvedBy"));
            java.sql.Timestamp rat = rs.getTimestamp("resolvedAt");
            vo.setResolvedAt(rat != null ? rat.toLocalDateTime() : null);
            vo.setResolutionNote(rs.getString("resolutionNote"));
            // T13 RCA 字段
            vo.setRcaResult(rs.getString("rcaResult"));
            double rc = rs.getDouble("rcaConfidence");
            vo.setRcaConfidence(rs.wasNull() ? null : rc);
            java.sql.Timestamp crct = rs.getTimestamp("rcaAnalyzedAt");
            vo.setRcaAnalyzedAt(crct != null ? crct.toLocalDateTime() : null);
            int rc2 = rs.getInt("retryCount");
            vo.setRetryCount(rc2);
            java.sql.Timestamp ct = rs.getTimestamp("createdAt");
            vo.setCreatedAt(ct != null ? ct.toLocalDateTime() : null);
            java.sql.Timestamp ut = rs.getTimestamp("updatedAt");
            vo.setUpdatedAt(ut != null ? ut.toLocalDateTime() : null);
            java.sql.Timestamp clt = rs.getTimestamp("closedAt");
            vo.setClosedAt(clt != null ? clt.toLocalDateTime() : null);
            return vo;
        } catch (SQLException e) {
            // RowMapper 契约允许抛 SQLException；此处包装为 BusinessException 保留友好语义
            throw new com.chinacreator.gzcm.common.exception.BusinessException("读取 DQ 工单行失败: " + e.getMessage());
        }
    }
}
