package com.chinacreator.gzcm.engine.ai.wagent.dao;

import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentCandidateReviewDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据访问收口（详细设计-10 §6.2）：{@code ecos_ai.ecos_wagent_candidate_review}（V239）的 JDBC 落点。
 * <p>{@code decision} 单源 = {@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReviewDecision}
 * （APPROVE/REJECT/REVISE）。</p>
 * <p>凭证纪律：{@code approval_token} 只存引用值，<b>禁回显</b>（日志/响应/错误文案均不得携带，
 * 与 §5.3 异常卫生一致）；评审动作事件走 Kafka {@code ecos.audit}（ST06），本表只存评审事实。
 * 审批链来源（存量 {@code public.ecos_decision_approval}/{@code ecos_workflow_approval}）不建 FK（ST09），
 * 跨 schema 访问走各自服务 REST/门面（F10-03 红线）。</p>
 */
@Repository
public class WAgentCandidateReviewDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_wagent_candidate_review";

    private static final String COLUMNS = "id, candidate_id, reviewer_id, decision, comment_text, changes_json, "
            + "approval_token, review_time, "
            + "tenant_id, org_id, domain, version_no, create_time, update_time, create_by, update_by, is_deleted";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "id, candidate_id, reviewer_id, decision, comment_text, changes_json, approval_token, review_time, "
            + "tenant_id, org_id, version_no, create_by, update_by) VALUES ("
            + ":id, :candidateId, :reviewerId, :decision, :commentText, :changesJson, :approvalToken, "
            + ":reviewTime, :tenantId, :orgId, :versionNo, :createBy, :updateBy)";

    private static final String UPDATE_SQL = "UPDATE " + TABLE + " SET "
            + "candidate_id = :candidateId, reviewer_id = :reviewerId, decision = :decision, "
            + "comment_text = :commentText, changes_json = :changesJson, approval_token = :approvalToken, "
            + "review_time = :reviewTime, version_no = :versionNo, update_by = :updateBy, "
            + "update_time = CURRENT_TIMESTAMP WHERE id = :id AND is_deleted = 0";

    public int insert(WAgentCandidateReviewDO review) {
        return insertByBean(INSERT_SQL, review);
    }

    public Optional<WAgentCandidateReviewDO> findById(String id) {
        List<WAgentCandidateReviewDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE id = ? AND is_deleted = 0", WAgentCandidateReviewDO.class, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** idx_wagent_cand_rev_cand(candidate_id, review_time)：最新评审在前（REVISE 链路追溯）。 */
    public List<WAgentCandidateReviewDO> findByCandidateId(String candidateId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE candidate_id = ? AND is_deleted = 0 ORDER BY review_time DESC",
                WAgentCandidateReviewDO.class, candidateId);
    }

    /** 同一候选是否已被该决定裁决过（幂等复评检测）。 */
    public List<WAgentCandidateReviewDO> findByCandidateIdAndDecision(String candidateId, String decision) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE candidate_id = ? AND decision = ? AND is_deleted = 0 ORDER BY review_time DESC",
                WAgentCandidateReviewDO.class, candidateId, decision);
    }

    public int update(WAgentCandidateReviewDO review) {
        return updateByBean(UPDATE_SQL, review);
    }
}
