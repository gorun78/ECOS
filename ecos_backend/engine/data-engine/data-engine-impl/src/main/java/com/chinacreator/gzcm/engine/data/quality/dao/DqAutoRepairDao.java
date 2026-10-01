package com.chinacreator.gzcm.engine.data.quality.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * DqAutoRepairDao — DQ 自动修复数据访问层（PMO-48-C T12）。
 * <p>
 * 架构铁律 §2.5 #1 收口：原内联在 {@code DqAutoRepairServiceImpl} 的 JdbcTemplate 调用迁移到本 DAO，
 * SQL 文本一律离开 Service。表前缀统一为 {@code ecos_data}。
 *
 * @author PMO-48-C T12
 */
@Repository
public class DqAutoRepairDao extends JdbcTemplateDaoSupport {

    /** 工单行轻载（不反序列化 JSONB，仅自动修复链需要的字段）。 */
    public List<Map<String, Object>> findWorkOrder(String workOrderId) {
        return jdbcTemplate.queryForList(
                "SELECT id, alert_id AS alertId, rule_id AS ruleId, status, severity,"
                + " handling_mode AS handlingMode, repair_action AS repairAction, retry_count AS retryCount"
                + " FROM ecos_data.dq_work_order WHERE id = ? AND is_deleted = FALSE",
                workOrderId);
    }

    /** 回写 repair_status / repair_action（scenario） / repair_log。 */
    public void writeRepairLog(String status, String scenario, String repairLogJson, String workOrderId) {
        jdbcTemplate.update(
                "UPDATE ecos_data.dq_work_order SET repair_status = ?,"
                + " repair_action = COALESCE(repair_action, ?),"
                + " repair_log = ?::jsonb, updated_at = NOW() WHERE id = ?",
                status, scenario, repairLogJson, workOrderId);
    }

    /** non-success 终态（DEGRADED/NOT_WHITELIST）→ 工单 handling_mode 转 MANUAL。 */
    public void degradeToManual(String workOrderId) {
        jdbcTemplate.update(
                "UPDATE ecos_data.dq_work_order SET handling_mode = 'MANUAL',"
                + " repair_status = COALESCE(repair_status, 'DEGRADED'), updated_at = NOW()"
                + " WHERE id = ?",
                workOrderId);
    }

    /** SUCCESS 后把 retry_count 自增（代表总尝试次数，供 T13 状态机参考）。 */
    public void incrementRetryCount(String workOrderId) {
        jdbcTemplate.update(
                "UPDATE ecos_data.dq_work_order SET retry_count = retry_count + 1,"
                + " updated_at = NOW() WHERE id = ?",
                workOrderId);
    }

    /** 反查 alert 关联的 asset_id（DATASOURCE 域时即 datasourceId）。 */
    public List<String> findAssetIdByAlert(String alertId) {
        return jdbcTemplate.queryForList(
                "SELECT asset_id FROM ecos_data.dq_alert_record WHERE id = ?",
                String.class, alertId);
    }
}
