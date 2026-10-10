package com.chinacreator.gzcm.engine.ai.wagent.dao;

import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessGapDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据访问收口（详细设计-10 §6.2）：{@code ecos_ai.ecos_wagent_readiness_gap}（V232）的 JDBC 落点。
 * <p>缺口与补全路径：{@code layer}/{@code unlock_from}/{@code unlock_to} CHAR(1) = ReadinessLayer 档位迁移，
 * {@code severity} = 缺口严重度（词表随 R2-B 定版，单源 = WAgentEnums）；
 * {@code status} = 缺口处置态（open/…）。</p>
 * <p>缺口修复动作经 Candidate/工具面落地（禁直连 Engine 表，§6.3）；本表只登记事实与差距。</p>
 */
@Repository
public class WAgentReadinessGapDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_wagent_readiness_gap";

    private static final String COLUMNS = "id, assessment_id, layer, severity, description_text, impact_text, "
            + "suggested_action_text, suggested_owner, effort_estimate, unlock_from, unlock_to, status, "
            + "resolved_time, "
            + "tenant_id, org_id, domain, version_no, create_time, update_time, create_by, update_by, is_deleted";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "id, assessment_id, layer, severity, description_text, impact_text, suggested_action_text, "
            + "suggested_owner, effort_estimate, unlock_from, unlock_to, status, resolved_time, "
            + "tenant_id, org_id, version_no, create_by, update_by) VALUES ("
            + ":id, :assessmentId, :layer, :severity, :descriptionText, :impactText, :suggestedActionText, "
            + ":suggestedOwner, :effortEstimate, :unlockFrom, :unlockTo, :status, :resolvedTime, "
            + ":tenantId, :orgId, :versionNo, :createBy, :updateBy)";

    private static final String UPDATE_SQL = "UPDATE " + TABLE + " SET "
            + "assessment_id = :assessmentId, layer = :layer, severity = :severity, "
            + "description_text = :descriptionText, impact_text = :impactText, "
            + "suggested_action_text = :suggestedActionText, suggested_owner = :suggestedOwner, "
            + "effort_estimate = :effortEstimate, unlock_from = :unlockFrom, unlock_to = :unlockTo, "
            + "status = :status, resolved_time = :resolvedTime, version_no = :versionNo, update_by = :updateBy, "
            + "update_time = CURRENT_TIMESTAMP WHERE id = :id AND is_deleted = 0";

    public int insert(WAgentReadinessGapDO gap) {
        return insertByBean(INSERT_SQL, gap);
    }

    public Optional<WAgentReadinessGapDO> findById(String id) {
        List<WAgentReadinessGapDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE id = ? AND is_deleted = 0", WAgentReadinessGapDO.class, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** idx_wagent_gap_assess(assessment_id, status)。 */
    public List<WAgentReadinessGapDO> findByAssessmentId(String assessmentId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE assessment_id = ? AND is_deleted = 0 ORDER BY layer ASC, severity ASC",
                WAgentReadinessGapDO.class, assessmentId);
    }

    public List<WAgentReadinessGapDO> findByAssessmentIdAndStatus(String assessmentId, String status) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE assessment_id = ? AND status = ? AND is_deleted = 0 ORDER BY severity ASC",
                WAgentReadinessGapDO.class, assessmentId, status);
    }

    /** idx_wagent_gap_tenant(tenant_id, severity)：跨问题的缺口汇总面（补全看板）。 */
    public List<WAgentReadinessGapDO> findByTenantAndSeverity(String tenantId, String severity) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE tenant_id = ? AND severity = ? AND is_deleted = 0 ORDER BY create_time DESC",
                WAgentReadinessGapDO.class, tenantId, severity);
    }

    public int update(WAgentReadinessGapDO gap) {
        return updateByBean(UPDATE_SQL, gap);
    }
}
