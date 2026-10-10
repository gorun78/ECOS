package com.chinacreator.gzcm.engine.ai.wagent.dao;

import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentCandidateDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据访问收口（详细设计-10 §6.2）：{@code ecos_ai.ecos_wagent_candidate}（V238）的 JDBC 落点。
 * <p>词表单源 = {@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums}：
 * {@code candidate_type} CandidateType 十二类、{@code status} CandidateStatus 状态机十值
 * （非法迁移由服务层拒绝并抛 E-WA-STATE(409)，过期同理）。</p>
 * <p>发布纪律（R-40①）：发布唯一出口 Git（{@code published_git_ref}）；
 * {@code engine_draft_ref_text} 只引用 Engine 侧草稿，禁复制正文（§6.3）；
 * 禁 Engine 侧回写本表（写方唯一 = ai-engine.wagent）。</p>
 * <p>批量审批红线：{@code rejectsBulkApprove()} 类型（commit/production 副作用类）禁批量批准，
 * 服务层逐条裁决。</p>
 */
@Repository
public class WAgentCandidateDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_wagent_candidate";

    private static final String COLUMNS = "id, candidate_type, target_engine, target_object_ref_json, "
            + "payload_json, payload_hash, generated_by_run, generated_by_step, confidence, basis_json, "
            + "validation_structure, validation_reference, validation_conflict, validation_impact, "
            + "validation_regression, status, batch_id, engine_draft_ref_text, published_ref_text, "
            + "published_git_ref, expire_time, "
            + "tenant_id, org_id, domain, version_no, create_time, update_time, create_by, update_by, is_deleted";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "id, candidate_type, target_engine, target_object_ref_json, payload_json, payload_hash, "
            + "generated_by_run, generated_by_step, confidence, basis_json, validation_structure, "
            + "validation_reference, validation_conflict, validation_impact, validation_regression, status, "
            + "batch_id, engine_draft_ref_text, published_ref_text, published_git_ref, expire_time, "
            + "tenant_id, org_id, version_no, create_by, update_by) VALUES ("
            + ":id, :candidateType, :targetEngine, :targetObjectRefJson, :payloadJson, :payloadHash, "
            + ":generatedByRun, :generatedByStep, :confidence, :basisJson, :validationStructure, "
            + ":validationReference, :validationConflict, :validationImpact, :validationRegression, :status, "
            + ":batchId, :engineDraftRefText, :publishedRefText, :publishedGitRef, :expireTime, "
            + ":tenantId, :orgId, :versionNo, :createBy, :updateBy)";

    private static final String UPDATE_SQL = "UPDATE " + TABLE + " SET "
            + "candidate_type = :candidateType, target_engine = :targetEngine, "
            + "target_object_ref_json = :targetObjectRefJson, payload_json = :payloadJson, "
            + "payload_hash = :payloadHash, generated_by_run = :generatedByRun, "
            + "generated_by_step = :generatedByStep, confidence = :confidence, basis_json = :basisJson, "
            + "validation_structure = :validationStructure, validation_reference = :validationReference, "
            + "validation_conflict = :validationConflict, validation_impact = :validationImpact, "
            + "validation_regression = :validationRegression, status = :status, batch_id = :batchId, "
            + "engine_draft_ref_text = :engineDraftRefText, published_ref_text = :publishedRefText, "
            + "published_git_ref = :publishedGitRef, expire_time = :expireTime, version_no = :versionNo, "
            + "update_by = :updateBy, update_time = CURRENT_TIMESTAMP WHERE id = :id AND is_deleted = 0";

    public int insert(WAgentCandidateDO candidate) {
        return insertByBean(INSERT_SQL, candidate);
    }

    public Optional<WAgentCandidateDO> findById(String id) {
        List<WAgentCandidateDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE id = ? AND is_deleted = 0", WAgentCandidateDO.class, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** idx_wagent_cand_status(status, expire_time)：评审中心待办面（过期时间正序）。 */
    public List<WAgentCandidateDO> findByStatus(String status) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE status = ? AND is_deleted = 0 ORDER BY expire_time ASC, create_time ASC",
                WAgentCandidateDO.class, status);
    }

    /** idx_wagent_cand_run(generated_by_run)。 */
    public List<WAgentCandidateDO> findByGeneratedByRun(String runId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE generated_by_run = ? AND is_deleted = 0 ORDER BY create_time ASC",
                WAgentCandidateDO.class, runId);
    }

    /** idx_wagent_cand_batch(batch_id)：批量审批面（批量语义仍受 CandidateType.rejectsBulkApprove 约束）。 */
    public List<WAgentCandidateDO> findByBatchId(String batchId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE batch_id = ? AND is_deleted = 0 ORDER BY create_time ASC",
                WAgentCandidateDO.class, batchId);
    }

    /** 状态迁移落库（合法性由服务层状态机判定，本方法不校验词表以外的迁移）。 */
    public int updateStatus(String id, String status, String updateBy) {
        return update("UPDATE " + TABLE + " SET status = ?, update_by = ?, update_time = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND is_deleted = 0", status, updateBy, id);
    }

    public int update(WAgentCandidateDO candidate) {
        return updateByBean(UPDATE_SQL, candidate);
    }
}
