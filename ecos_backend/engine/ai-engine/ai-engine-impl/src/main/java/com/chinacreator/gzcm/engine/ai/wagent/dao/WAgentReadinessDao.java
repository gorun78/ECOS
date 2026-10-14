package com.chinacreator.gzcm.engine.ai.wagent.dao;

import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据访问收口（详细设计-10 §6.2）：{@code ecos_ai.ecos_wagent_readiness}（V230）的 JDBC 落点。
 * <p>DIK-C 四层评估头表：{@code grade} CHAR(1) = {@link
 * com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessGrade}（A/B/C/D），
 * {@code layer_*} VARCHAR(10) = ReadinessCheckStatus；{@code degradations_json} 承载 DG 码集（F10-10，
 * 单源 = WAgentEnums.DegradeCode），JSON 语义一律 TEXT（DR04/MC02）。</p>
 * <p>配置组 {@code wagent.degrade} 在 sysman 单源（铁律 v2.0），本表只存评估事实不存阈值。</p>
 */
@Repository
public class WAgentReadinessDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_wagent_readiness";

    private static final String COLUMNS = "id, question_id, run_id, profile_id, profile_version, grade, "
            + "layer_d, layer_i, layer_k, layer_c, layer_summary_json, degradations_json, computed_time, "
            + "valid_until, superseded_by, is_pinned, "
            + "tenant_id, org_id, domain, version_no, create_time, update_time, create_by, update_by, is_deleted";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "id, question_id, run_id, profile_id, profile_version, grade, layer_d, layer_i, layer_k, layer_c, "
            + "layer_summary_json, degradations_json, computed_time, valid_until, superseded_by, is_pinned, "
            + "tenant_id, org_id, version_no, create_by, update_by) VALUES ("
            + ":id, :questionId, :runId, :profileId, :profileVersion, :grade, :layerD, :layerI, :layerK, :layerC, "
            + ":layerSummaryJson, :degradationsJson, :computedTime, :validUntil, :supersededBy, :isPinned, "
            + ":tenantId, :orgId, :versionNo, :createBy, :updateBy)";

    private static final String UPDATE_SQL = "UPDATE " + TABLE + " SET "
            + "question_id = :questionId, run_id = :runId, profile_id = :profileId, "
            + "profile_version = :profileVersion, grade = :grade, layer_d = :layerD, layer_i = :layerI, "
            + "layer_k = :layerK, layer_c = :layerC, layer_summary_json = :layerSummaryJson, "
            + "degradations_json = :degradationsJson, computed_time = :computedTime, valid_until = :validUntil, "
            + "superseded_by = :supersededBy, is_pinned = :isPinned, version_no = :versionNo, "
            + "update_by = :updateBy, update_time = CURRENT_TIMESTAMP WHERE id = :id AND is_deleted = 0";

    public int insert(WAgentReadinessDO assessment) {
        return insertByBean(INSERT_SQL, assessment);
    }

    public Optional<WAgentReadinessDO> findById(String id) {
        List<WAgentReadinessDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE id = ? AND is_deleted = 0", WAgentReadinessDO.class, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** idx_wagent_ready_question(question_id, valid_until)：按有效期倒序（最新评估在前；DESC 期 NULL 居末，方言中性）。 */
    public List<WAgentReadinessDO> findByQuestionId(String questionId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE question_id = ? AND is_deleted = 0 ORDER BY valid_until DESC, create_time DESC",
                WAgentReadinessDO.class, questionId);
    }

    /** 当前有效评估（未过期且未被替代；pinned 评估优先）。 */
    public Optional<WAgentReadinessDO> findCurrentByQuestionId(String questionId) {
        List<WAgentReadinessDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE question_id = ? AND is_deleted = 0 AND superseded_by IS NULL "
                + "AND (valid_until IS NULL OR valid_until > CURRENT_TIMESTAMP) "
                + "ORDER BY is_pinned DESC, valid_until DESC", WAgentReadinessDO.class, questionId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** idx_wagent_ready_tenant(tenant_id, grade)。 */
    public List<WAgentReadinessDO> findByTenantAndGrade(String tenantId, String grade) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE tenant_id = ? AND grade = ? AND is_deleted = 0 ORDER BY computed_time DESC",
                WAgentReadinessDO.class, tenantId, grade);
    }

    public int update(WAgentReadinessDO assessment) {
        return updateByBean(UPDATE_SQL, assessment);
    }

    /**
     * 只追加纪律的替代位回写（F10-08 硬规则④「评估记录只追加 + {@code superseded_by}」）。
     *
     * <p>旧行<b>不删不改内容</b>，只写指针。三个前置条件都是 {@code WHERE} 里的闸而非服务层自觉：
     * {@code superseded_by IS NULL}（禁二次替代，历史链单向前进）、{@code is_pinned = 0}
     * （被 Decision 钉住的评估不参与重评清理，F10-07 硬规则④后半句）。返回 0 ⇒ 目标行是
     * pinned 或已被替代，调用方记 WARN 而非抛错（钉住是运维显式意图，不是故障）。</p>
     */
    public int markSuperseded(String id, String supersededBy, String updateBy) {
        return update("UPDATE " + TABLE + " SET superseded_by = ?, update_by = ?, "
                + "update_time = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND is_deleted = 0 AND superseded_by IS NULL AND is_pinned = 0",
                supersededBy, updateBy, id);
    }
}
