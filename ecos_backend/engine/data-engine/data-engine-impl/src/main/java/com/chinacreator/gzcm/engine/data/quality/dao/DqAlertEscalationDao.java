package com.chinacreator.gzcm.engine.data.quality.dao;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * DqAlertEscalationDao — DQ 告警升级时限状态机的数据访问层（F02-10）。
 * <p>
 * 架构铁律 §2.5 #1 收口：原内联在 {@code DqAlertEscalationService} 的 JdbcTemplate 调用
 * 迁移到本 DAO（SQL 文本一律离开 Service）。
 *
 * @author ECOS Data Engine Team / F02-10
 */
@Repository
public class DqAlertEscalationDao extends JdbcTemplateDaoSupport {

    /**
     * 挑选待升级告警：{@code fromLevel} 未 acknowledge（ack_at IS NULL）且
     * {@code created_at < deadline}。
     *
     * @param fromLevel 源级别（P2 / P1）
     * @param deadline  截止时间（参数化比较，IR05/MC03）
     * @return 候选行（Map 形式，Service 侧按 key 取值）
     */
    public List<Map<String, Object>> findUnacknowledged(String fromLevel, Timestamp deadline) {
        return jdbcTemplate.queryForList(
                "SELECT id, rule_id AS ruleId, alert_level AS alertLevel,"
                + " asset_id AS assetId, rule_name AS ruleName, message, created_at AS createdAt"
                + " FROM ecos_data.dq_alert_record"
                + " WHERE alert_level = ? AND status IN ('PENDING','NOTIFIED') AND ack_at IS NULL"
                + "   AND created_at < ?",
                fromLevel, deadline);
    }

    /**
     * 单告警升级：乐观锁 UPDATE 写 escalated_to + status='ESCALATED'。
     *
     * @return 受影响行数（0 = 并发已 ack/resolve）
     */
    public int markEscalated(String toLevel, String alertId) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_alert_record"
                + " SET alert_level = ?, escalated_to = ?, status = 'ESCALATED', last_notify_at = NOW()"
                + " WHERE id = ? AND status IN ('PENDING','NOTIFIED')",
                toLevel, toLevel, alertId);
    }
}
