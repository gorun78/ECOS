package com.chinacreator.gzcm.engine.ai.wagent.dao;

import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunStepDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据访问收口（详细设计-10 §6.2）：{@code ecos_ai.ecos_wagent_run_step}（V234）的 JDBC 落点。
 * <p>词表单源 = {@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums}：
 * {@code step_type} StepType 六类（§4.2 核定集合）、{@code policy_effect} PolicyEffect 四值、
 * {@code automation_level} AutomationLevel 档位（应用层断言 ≤ run.automation_ceiling）。</p>
 * <p>{@code error_code} 写侧单源 = {@link
 * com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode#wireCode}（VARCHAR(24) 列宽，
 * {@code WAgentErrorCodes.fitsRunStepErrorCodeColumn()} 自检）。重试语义：同 step_key 递增 attempt，
 * uniq(run_id, step_key, attempt, is_deleted)。</p>
 */
@Repository
public class WAgentRunStepDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_wagent_run_step";

    private static final String COLUMNS = "id, run_id, step_key, attempt, step_type, tool_name, tool_version, "
            + "prompt_id, prompt_version, automation_level, status, input_ref_text, input_hash, output_ref_text, "
            + "output_hash, task_id, policy_effect, policy_rule_id, metrics_json, error_code, error_text, "
            + "start_time, end_time, "
            + "tenant_id, org_id, domain, version_no, create_time, update_time, create_by, update_by, is_deleted";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "id, run_id, step_key, attempt, step_type, tool_name, tool_version, prompt_id, prompt_version, "
            + "automation_level, status, input_ref_text, input_hash, output_ref_text, output_hash, task_id, "
            + "policy_effect, policy_rule_id, metrics_json, error_code, error_text, start_time, end_time, "
            + "tenant_id, org_id, version_no, create_by, update_by) VALUES ("
            + ":id, :runId, :stepKey, :attempt, :stepType, :toolName, :toolVersion, :promptId, :promptVersion, "
            + ":automationLevel, :status, :inputRefText, :inputHash, :outputRefText, :outputHash, :taskId, "
            + ":policyEffect, :policyRuleId, :metricsJson, :errorCode, :errorText, :startTime, :endTime, "
            + ":tenantId, :orgId, :versionNo, :createBy, :updateBy)";

    private static final String UPDATE_SQL = "UPDATE " + TABLE + " SET "
            + "run_id = :runId, step_key = :stepKey, attempt = :attempt, step_type = :stepType, "
            + "tool_name = :toolName, tool_version = :toolVersion, prompt_id = :promptId, "
            + "prompt_version = :promptVersion, automation_level = :automationLevel, status = :status, "
            + "input_ref_text = :inputRefText, input_hash = :inputHash, output_ref_text = :outputRefText, "
            + "output_hash = :outputHash, task_id = :taskId, policy_effect = :policyEffect, "
            + "policy_rule_id = :policyRuleId, metrics_json = :metricsJson, error_code = :errorCode, "
            + "error_text = :errorText, start_time = :startTime, end_time = :endTime, "
            + "version_no = :versionNo, update_by = :updateBy, update_time = CURRENT_TIMESTAMP "
            + "WHERE id = :id AND is_deleted = 0";

    public int insert(WAgentRunStepDO step) {
        return insertByBean(INSERT_SQL, step);
    }

    public Optional<WAgentRunStepDO> findById(String id) {
        List<WAgentRunStepDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE id = ? AND is_deleted = 0", WAgentRunStepDO.class, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** uniq_wagent_step_attempt 三列定位（重试幂等入口）。 */
    public Optional<WAgentRunStepDO> findByRunStepAttempt(String runId, String stepKey, Integer attempt) {
        List<WAgentRunStepDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE run_id = ? AND step_key = ? AND attempt = ? AND is_deleted = 0",
                WAgentRunStepDO.class, runId, stepKey, attempt);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** idx_wagent_step_run(run_id, status)：执行回放按开始时间正序。 */
    public List<WAgentRunStepDO> findByRunId(String runId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE run_id = ? AND is_deleted = 0 ORDER BY step_key ASC, attempt ASC",
                WAgentRunStepDO.class, runId);
    }

    public List<WAgentRunStepDO> findByRunIdAndStatus(String runId, String status) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE run_id = ? AND status = ? AND is_deleted = 0 ORDER BY step_key ASC, attempt ASC",
                WAgentRunStepDO.class, runId, status);
    }

    /** runtime-task 真值反查（wait_task 步骤回填，禁自造 task_id）。 */
    public Optional<WAgentRunStepDO> findByTaskId(String taskId) {
        List<WAgentRunStepDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE task_id = ? AND is_deleted = 0", WAgentRunStepDO.class, taskId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public int update(WAgentRunStepDO step) {
        return updateByBean(UPDATE_SQL, step);
    }
}
