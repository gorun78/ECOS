package com.chinacreator.gzcm.engine.ai.wagent.dao;

import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentActionDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code ecos_ai.ecos_wagent_action}（V283）的 JDBC 落点。
 *
 * <p>状态推进一律 CAS（{@code WHERE status = ?}）：并发提交时只有一条能赢，
 * 赢家才允许向属主 Engine 派发副作用。</p>
 */
@Repository
public class WAgentActionDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_wagent_action";

    private static final String COLUMNS = "id, option_id, title, owner_id, role, due_date, kpi_text, "
            + "source_type, source_object_id, publish_target, publish_mode, status, approval_token, trace_id, "
            + "tenant_id, org_id, domain, version_no, create_time, update_time, create_by, update_by, is_deleted";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "id, option_id, title, owner_id, role, due_date, kpi_text, source_type, source_object_id, "
            + "publish_target, publish_mode, status, trace_id, tenant_id, org_id, domain, version_no, "
            + "create_by, update_by) VALUES ("
            + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public int insert(WAgentActionDO row) {
        return insert(INSERT_SQL,
                row.getId(), row.getOptionId(), row.getTitle(), row.getOwnerId(), row.getRole(),
                row.getDueDate(), row.getKpiText(), row.getSourceType(), row.getSourceObjectId(),
                row.getPublishTarget(), row.getPublishMode(), row.getStatus(), row.getTraceId(),
                row.getTenantId(), row.getOrgId(), row.getDomain(), row.getVersionNo(),
                row.getCreateBy(), row.getUpdateBy());
    }

    public Optional<WAgentActionDO> findById(String id) {
        List<WAgentActionDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE id = ? AND is_deleted = 0", WAgentActionDO.class, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** CAS 状态推进；返回 1 表示本次推进生效。 */
    public int transitionStatus(String id, String fromStatus, String toStatus, String approvalToken,
                                String updateBy) {
        return update("UPDATE " + TABLE + " SET status = ?, approval_token = COALESCE(?, approval_token), "
                + "update_by = ?, update_time = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND status = ? AND is_deleted = 0",
                toStatus, approvalToken, updateBy, id, fromStatus);
    }

    /** 记录链路标识（提交时回填）。 */
    public int updateTraceId(String id, String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return 0;
        }
        return update("UPDATE " + TABLE + " SET trace_id = ? WHERE id = ? AND is_deleted = 0",
                traceId, id);
    }
}
