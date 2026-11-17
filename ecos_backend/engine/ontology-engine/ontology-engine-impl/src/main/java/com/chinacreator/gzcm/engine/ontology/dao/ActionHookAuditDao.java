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
        // D143（2026-11-17）：与 sysman 的 AuditLog-sql.xml 同批统一到 ecos_audit_log。
        // 此前本处写 td_audit_log，而列表读源已切 ecos_audit_log ⇒ 若不同改，
        // 本体动作钩子的审计会落进「没人读的表」，重新制造 D143 的双源分裂。
        jdbcTemplate.update(
            "INSERT INTO ecos_audit_log (id, event_type, user_id, resource, action, result, detail, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())",
            logId,
            eventType,
            userId,
            resource,
            action,
            result,
            details);
    }
}
