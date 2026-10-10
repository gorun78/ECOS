package com.chinacreator.gzcm.engine.ai.wagent.dao;

import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentQuestionDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据访问收口（详细设计-10 §6.2）：{@code ecos_ai.ecos_wagent_question}（V229）的 JDBC 落点。
 * <p>词表单源：{@code intent} = {@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.GoalIntent}
 * （八意图，应用层先校验 + ck_wagent_question_intent 二段防线）；{@code latest_grade} CHAR(1) = ReadinessGrade。</p>
 * <p>模式同 {@code AiToolRegistryDao}：schema 限定、显式 COLUMNS、参数化；基类 {@link JdbcTemplateDaoSupport}。</p>
 */
@Repository
public class WAgentQuestionDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_wagent_question";

    private static final String COLUMNS = "id, mission_id, raw_text, intent, intent_confidence, slots_json, "
            + "depends_on_json, status, latest_run_id, latest_grade, answer_summary_text, "
            + "tenant_id, org_id, domain, version_no, create_time, update_time, create_by, update_by, is_deleted";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "id, mission_id, raw_text, intent, intent_confidence, slots_json, depends_on_json, status, "
            + "latest_run_id, latest_grade, answer_summary_text, tenant_id, org_id, version_no, create_by, update_by) "
            + "VALUES (:id, :missionId, :rawText, :intent, :intentConfidence, :slotsJson, :dependsOnJson, :status, "
            + ":latestRunId, :latestGrade, :answerSummaryText, :tenantId, :orgId, :versionNo, :createBy, :updateBy)";

    private static final String UPDATE_SQL = "UPDATE " + TABLE + " SET "
            + "mission_id = :missionId, raw_text = :rawText, intent = :intent, intent_confidence = :intentConfidence, "
            + "slots_json = :slotsJson, depends_on_json = :dependsOnJson, status = :status, "
            + "latest_run_id = :latestRunId, latest_grade = :latestGrade, "
            + "answer_summary_text = :answerSummaryText, version_no = :versionNo, update_by = :updateBy, "
            + "update_time = CURRENT_TIMESTAMP WHERE id = :id AND is_deleted = 0";

    public int insert(WAgentQuestionDO question) {
        return insertByBean(INSERT_SQL, question);
    }

    public Optional<WAgentQuestionDO> findById(String id) {
        List<WAgentQuestionDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE id = ? AND is_deleted = 0", WAgentQuestionDO.class, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** idx_wagent_question_mission(mission_id)。 */
    public List<WAgentQuestionDO> findByMissionId(String missionId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE mission_id = ? AND is_deleted = 0 ORDER BY create_time ASC",
                WAgentQuestionDO.class, missionId);
    }

    /** idx_wagent_question_tenant(tenant_id, status)。 */
    public List<WAgentQuestionDO> findByTenantAndStatus(String tenantId, String status) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE tenant_id = ? AND status = ? AND is_deleted = 0 ORDER BY create_time DESC",
                WAgentQuestionDO.class, tenantId, status);
    }

    public int update(WAgentQuestionDO question) {
        return updateByBean(UPDATE_SQL, question);
    }
}
