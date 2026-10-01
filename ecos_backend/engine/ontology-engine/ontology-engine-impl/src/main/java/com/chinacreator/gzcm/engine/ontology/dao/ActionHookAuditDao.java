package com.chinacreator.gzcm.engine.ontology.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * B3c 数据访问收口（R2/B3c）：{@code ActionHookExecutor} 审计钩子（NOTIFICATION / AUDIT_LOG）
 * 写入 {@code td_audit_log} 的 JDBC 落点。
 *
 * <p>两个钩子共用同一 INSERT 模板，仅 event_type 与 details 不同，故统一为一个
 * {@link #insertAuditLog} 方法（event_type 由调用方传入）。SQL 文本、参数序原样搬运。</p>
 */
@Repository
public class ActionHookAuditDao extends JdbcTemplateDaoSupport {

    /**
     * 写入 td_audit_log（event_type=NOTIFICATION 或 ACTION_EXECUTE）。
     *
     * @param logId       应用侧 UUID
     * @param eventType   NOTIFICATION / ACTION_EXECUTE
     * @param userId      当前用户或 "system"
     * @param resource    实体 code
     * @param action      action code/name
     * @param result      SUCCESS / FAILED
     * @param details     JSON 明细
     */
    public void insertAuditLog(String logId, String eventType, String userId,
                               String resource, String action, String result, String details) {
        jdbcTemplate.update(
            "INSERT INTO td_audit_log (log_id, event_type, timestamp, user_id, resource, action, result, details) " +
            "VALUES (?, ?, NOW(), ?, ?, ?, ?, ?)",
            logId,
            eventType,
            userId,
            resource,
            action,
            result,
            details);
    }
}
