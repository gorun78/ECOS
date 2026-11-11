package com.chinacreator.gzcm.engine.ai.wagent.approval;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code ecos_ai.agent_approval} 的 JDBC 落点（V50 建表，V326 补凭证列）。
 *
 * <p><b>一次性消费靠 CAS</b>：{@link #consume(String, String)} 用
 * {@code WHERE consumed_at IS NULL} 保证并发下只有一次成功，返回 0 即「已被消费/不存在」。</p>
 */
@Repository
public class WAgentApprovalDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.agent_approval";

    private static final String COLUMNS = "id, task_id, risk_level, status, requested_at, processed_at, "
            + "approved_by, comment, token_digest, scope_type, scope_id, requested_by, expired_at, "
            + "consumed_at, trace_id";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "id, task_id, risk_level, status, requested_at, approved_by, comment, token_digest, "
            + "scope_type, scope_id, requested_by, expired_at, trace_id) VALUES ("
            + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public int insert(WAgentApprovalDO row) {
        return insert(INSERT_SQL,
                row.getId(), row.getTaskId(), row.getRiskLevel(), row.getStatus(),
                row.getRequestedAt(), row.getApprovedBy(), row.getComment(), row.getTokenDigest(),
                row.getScopeType(), row.getScopeId(), row.getRequestedBy(), row.getExpiredAt(),
                row.getTraceId());
    }

    public Optional<WAgentApprovalDO> findByDigest(String tokenDigest) {
        List<WAgentApprovalDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE token_digest = ?", WAgentApprovalDO.class, tokenDigest);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<WAgentApprovalDO> findById(String id) {
        List<WAgentApprovalDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE id = ?", WAgentApprovalDO.class, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * 一次性消费（CAS）：只有 {@code consumed_at IS NULL} 的行会被置位。
     *
     * @return 1 = 本次消费成功；0 = 已被消费（重放）或不存在
     */
    public int consume(String id, String traceId) {
        return update("UPDATE " + TABLE
                + " SET consumed_at = CURRENT_TIMESTAMP, processed_at = CURRENT_TIMESTAMP, "
                + "status = 'CONSUMED', trace_id = COALESCE(?, trace_id) "
                + "WHERE id = ? AND consumed_at IS NULL", traceId, id);
    }

    /** 撤销未消费的凭证（审批人撤回；已消费的不可撤）。 */
    public int revoke(String id, String approvedBy) {
        return update("UPDATE " + TABLE + " SET status = 'REVOKED', processed_at = CURRENT_TIMESTAMP, "
                + "approved_by = ? WHERE id = ? AND consumed_at IS NULL", approvedBy, id);
    }

    /** 按目标对象列凭证（诊断/排障用；不含明文）。 */
    public List<WAgentApprovalDO> findByScope(String scopeType, String scopeId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE scope_type = ? AND scope_id = ? ORDER BY requested_at DESC",
                WAgentApprovalDO.class, scopeType, scopeId);
    }
}
