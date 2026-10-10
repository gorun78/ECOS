package com.chinacreator.gzcm.engine.ai.wagent.dao;

import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessItemDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据访问收口（详细设计-10 §6.2）：{@code ecos_ai.ecos_wagent_readiness_item}（V231）的 JDBC 落点。
 * <p>逐项检查明细：{@code layer} CHAR(1) = {@link
 * com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessLayer}（D/I/K/C），
 * {@code status} VARCHAR(10) = ReadinessCheckStatus（pass/warn/fail/blocker/unknown）；
 * {@code measured_json}/{@code threshold_json}/{@code evidence_ids_json} JSON 语义 TEXT（DR04/MC02）。</p>
 * <p>阈值口径单源在 sysman 配置组 {@code wagent.readiness}（铁律 v2.0），本表存评估时点的测量快照。</p>
 */
@Repository
public class WAgentReadinessItemDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_wagent_readiness_item";

    private static final String COLUMNS = "id, assessment_id, layer, check_code, status, measured_json, "
            + "threshold_json, evidence_ids_json, "
            + "tenant_id, org_id, domain, version_no, create_time, update_time, create_by, update_by, is_deleted";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "id, assessment_id, layer, check_code, status, measured_json, threshold_json, evidence_ids_json, "
            + "tenant_id, org_id, version_no, create_by, update_by) VALUES ("
            + ":id, :assessmentId, :layer, :checkCode, :status, :measuredJson, :thresholdJson, :evidenceIdsJson, "
            + ":tenantId, :orgId, :versionNo, :createBy, :updateBy)";

    private static final String UPDATE_SQL = "UPDATE " + TABLE + " SET "
            + "assessment_id = :assessmentId, layer = :layer, check_code = :checkCode, status = :status, "
            + "measured_json = :measuredJson, threshold_json = :thresholdJson, "
            + "evidence_ids_json = :evidenceIdsJson, version_no = :versionNo, update_by = :updateBy, "
            + "update_time = CURRENT_TIMESTAMP WHERE id = :id AND is_deleted = 0";

    public int insert(WAgentReadinessItemDO item) {
        return insertByBean(INSERT_SQL, item);
    }

    public Optional<WAgentReadinessItemDO> findById(String id) {
        List<WAgentReadinessItemDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE id = ? AND is_deleted = 0", WAgentReadinessItemDO.class, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** idx_wagent_ready_item_assess(assessment_id, layer)。 */
    public List<WAgentReadinessItemDO> findByAssessmentId(String assessmentId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE assessment_id = ? AND is_deleted = 0 ORDER BY layer ASC, check_code ASC",
                WAgentReadinessItemDO.class, assessmentId);
    }

    public List<WAgentReadinessItemDO> findByAssessmentIdAndLayer(String assessmentId, String layer) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE assessment_id = ? AND layer = ? AND is_deleted = 0 ORDER BY check_code ASC",
                WAgentReadinessItemDO.class, assessmentId, layer);
    }

    public int update(WAgentReadinessItemDO item) {
        return updateByBean(UPDATE_SQL, item);
    }
}
