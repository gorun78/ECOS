package com.chinacreator.gzcm.engine.ai.wagent.dao;

import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentClaimDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据访问收口（详细设计-10 §6.2）：{@code ecos_ai.ecos_wagent_claim}（V240 表②）的 JDBC 落点。
 * <p>数字一致性校验承载（F10-24）：{@code numeric_value} NUMERIC(18,6) 由 {@code BigDecimal} 承（禁浮点），
 * {@code value_source} 落实"金额唯一事实源"纪律（禁 AI 生成金额，卷 09 §4.2 同源）；
 * {@code evidence_grade} CHAR(1) = {@link
 * com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessGrade}；
 * {@code verify_status} 未通过时服务层抛 E-WA-NARRATIVE-NUM(500) 触发重生成（≤2 次，
 * 单源 = {@link com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes}）。</p>
 */
@Repository
public class WAgentClaimDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_wagent_claim";

    private static final String COLUMNS = "id, run_id, step_id, claim_text, claim_type, numeric_value, "
            + "numeric_unit, value_source, evidence_grade, verify_status, "
            + "tenant_id, org_id, domain, version_no, create_time, update_time, create_by, update_by, is_deleted";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "id, run_id, step_id, claim_text, claim_type, numeric_value, numeric_unit, value_source, "
            + "evidence_grade, verify_status, tenant_id, org_id, version_no, create_by, update_by) VALUES ("
            + ":id, :runId, :stepId, :claimText, :claimType, :numericValue, :numericUnit, :valueSource, "
            + ":evidenceGrade, :verifyStatus, :tenantId, :orgId, :versionNo, :createBy, :updateBy)";

    private static final String UPDATE_SQL = "UPDATE " + TABLE + " SET "
            + "run_id = :runId, step_id = :stepId, claim_text = :claimText, claim_type = :claimType, "
            + "numeric_value = :numericValue, numeric_unit = :numericUnit, value_source = :valueSource, "
            + "evidence_grade = :evidenceGrade, verify_status = :verifyStatus, version_no = :versionNo, "
            + "update_by = :updateBy, update_time = CURRENT_TIMESTAMP WHERE id = :id AND is_deleted = 0";

    public int insert(WAgentClaimDO claim) {
        return insertByBean(INSERT_SQL, claim);
    }

    public Optional<WAgentClaimDO> findById(String id) {
        List<WAgentClaimDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE id = ? AND is_deleted = 0", WAgentClaimDO.class, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** idx_wagent_claim_run(run_id, verify_status)：一次运行的断言集（§5.2 CLAIMS_BY_RUN 消费面）。 */
    public List<WAgentClaimDO> findByRunId(String runId) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE run_id = ? AND is_deleted = 0 ORDER BY create_time ASC", WAgentClaimDO.class, runId);
    }

    public List<WAgentClaimDO> findByRunIdAndVerifyStatus(String runId, String verifyStatus) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE run_id = ? AND verify_status = ? AND is_deleted = 0 ORDER BY create_time ASC",
                WAgentClaimDO.class, runId, verifyStatus);
    }

    /** 校验位落库（数字一致性门禁结果）。 */
    public int updateVerifyStatus(String id, String verifyStatus, String evidenceGrade, String updateBy) {
        return update("UPDATE " + TABLE + " SET verify_status = ?, evidence_grade = ?, update_by = ?, "
                + "update_time = CURRENT_TIMESTAMP WHERE id = ? AND is_deleted = 0",
                verifyStatus, evidenceGrade, updateBy, id);
    }

    public int update(WAgentClaimDO claim) {
        return updateByBean(UPDATE_SQL, claim);
    }
}
