package com.chinacreator.gzcm.engine.ai.wagent.dao;

import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据访问收口（详细设计-10 §6.2）：{@code ecos_ai.ecos_wagent_run}（V233）的 JDBC 落点。
 * <p>词表单源 = {@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums}：
 * {@code status} 13 态 RunStatus（应用层状态机迁移校验 + ck_wagent_run_status 二段防线）、
 * {@code plan_source} 两值 PlanSource；{@code budget_json}/{@code consumption_json}/
 * {@code degradations_json}/{@code situation_snapshot_json} JSON 语义 TEXT（DR04/MC02）。</p>
 * <p>幂等：uniq_wagent_run_idem(tenant_id, initiated_by, idempotency_key, is_deleted)——
 * 命中已存键由服务层返回既有 Run 或抛 E-IDEMPOTENT-CONFLICT(409)（§5.3 单源 = WAgentErrorCodes）。
 * 本 DAO 不自愈冲突、不吞异常。</p>
 */
@Repository
public class WAgentRunDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_wagent_run";

    private static final String COLUMNS = "id, question_id, mission_id, parent_run_id, previous_run_id, "
            + "plan_source, plan_json, status, automation_ceiling, initiated_by, idempotency_key, budget_json, "
            + "consumption_json, readiness_id, degradations_json, situation_snapshot_json, result_ref_text, "
            + "start_time, end_time, "
            + "tenant_id, org_id, domain, version_no, create_time, update_time, create_by, update_by, is_deleted";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "id, question_id, mission_id, parent_run_id, previous_run_id, plan_source, plan_json, status, "
            + "automation_ceiling, initiated_by, idempotency_key, budget_json, consumption_json, readiness_id, "
            + "degradations_json, situation_snapshot_json, result_ref_text, start_time, end_time, "
            + "tenant_id, org_id, version_no, create_by, update_by) VALUES ("
            + ":id, :questionId, :missionId, :parentRunId, :previousRunId, :planSource, :planJson, :status, "
            + ":automationCeiling, :initiatedBy, :idempotencyKey, :budgetJson, :consumptionJson, :readinessId, "
            + ":degradationsJson, :situationSnapshotJson, :resultRefText, :startTime, :endTime, "
            + ":tenantId, :orgId, :versionNo, :createBy, :updateBy)";

    private static final String UPDATE_SQL = "UPDATE " + TABLE + " SET "
            + "question_id = :questionId, mission_id = :missionId, parent_run_id = :parentRunId, "
            + "previous_run_id = :previousRunId, plan_source = :planSource, plan_json = :planJson, "
            + "status = :status, automation_ceiling = :automationCeiling, initiated_by = :initiatedBy, "
            + "idempotency_key = :idempotencyKey, budget_json = :budgetJson, consumption_json = :consumptionJson, "
            + "readiness_id = :readinessId, degradations_json = :degradationsJson, "
            + "situation_snapshot_json = :situationSnapshotJson, result_ref_text = :resultRefText, "
            + "start_time = :startTime, end_time = :endTime, version_no = :versionNo, update_by = :updateBy, "
            + "update_time = CURRENT_TIMESTAMP WHERE id = :id AND is_deleted = 0";

    public int insert(WAgentRunDO run) {
        return insertByBean(INSERT_SQL, run);
    }

    public Optional<WAgentRunDO> findById(String id) {
        List<WAgentRunDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE id = ? AND is_deleted = 0", WAgentRunDO.class, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** uniq_wagent_run_idem 三列定位（幂等键复用检测，E-IDEMPOTENT-CONFLICT 判定入口）。 */
    public Optional<WAgentRunDO> findByIdempotencyKey(String tenantId, String initiatedBy, String idempotencyKey) {
        List<WAgentRunDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE tenant_id = ? AND initiated_by = ? AND idempotency_key = ? AND is_deleted = 0",
                WAgentRunDO.class, tenantId, initiatedBy, idempotencyKey);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** idx_wagent_run_question(question_id, status)。 */
    public List<WAgentRunDO> findByQuestionId(String questionId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE question_id = ? AND is_deleted = 0 ORDER BY create_time DESC",
                WAgentRunDO.class, questionId);
    }

    public List<WAgentRunDO> findByQuestionIdAndStatus(String questionId, String status) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE question_id = ? AND status = ? AND is_deleted = 0 ORDER BY create_time DESC",
                WAgentRunDO.class, questionId, status);
    }

    /** idx_wagent_question_mission 同族：按 Mission 取全部 Run（口径锁判定链 Goal→Mission→Run 用，F10-07）。 */
    public List<WAgentRunDO> findByMissionId(String missionId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE mission_id = ? AND is_deleted = 0 ORDER BY create_time DESC",
                WAgentRunDO.class, missionId);
    }

    /** 子运行（sub_run 步骤展开，父子链逻辑引用同表）。 */
    public List<WAgentRunDO> findByParentRunId(String parentRunId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE parent_run_id = ? AND is_deleted = 0 ORDER BY create_time ASC",
                WAgentRunDO.class, parentRunId);
    }

    /** 状态迁移落库（乐观并发由服务层 version_no/状态前置校验保证，本方法不做迁移合法性判断）。 */
    public int updateStatus(String id, String status, String updateBy) {
        return update("UPDATE " + TABLE + " SET status = ?, update_by = ?, update_time = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND is_deleted = 0", status, updateBy, id);
    }

    public int update(WAgentRunDO run) {
        return updateByBean(UPDATE_SQL, run);
    }
}
