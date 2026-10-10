package com.chinacreator.gzcm.engine.ai.capability;

import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentBaseDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 能力调用台账（《补充设计 v1.0》§10.4/10.5 / V269 {@code ecos_ai.ecos_ai_capability_task}）落点。
 *
 * <p>红线：</p>
 * <ul>
 *   <li>只存<b>引用与元数据</b>，禁复制大文件/完整敏感输入（§10.5 末段，input_ref 是引用 ID）；</li>
 *   <li>本表是调用台账，<b>非</b>模型注册表（模型注册在 CM registry / sys_config 族）；</li>
 *   <li>quality_kind 与 calibrated 分离：未校准置信度 calibrated 不得置 1（SUP-MODEL-03）；</li>
 *   <li>cost_unit 计量口径（TOKEN/CALL/INPUT_SCALE…），非 Token 模型禁伪 Token（§10.6）；</li>
 *   <li>SQL 显式列名禁 SELECT *（DR/IR 红线），RLS/CLS 在 security-engine REST 侧。</li>
 * </ul>
 */
@Repository
public class CapabilityLedgerDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_ai_capability_task";

    /** 显式列名（禁 SELECT *；domain/version_no 等基线列由 {@link WAgentBaseDO} 承载名）。 */
    private static final String COLUMNS = "id, task_kind, capability_id, tool_contract_ref, input_ref, "
            + "model_ref, model_version, output_ref, status, quality_score, quality_kind, calibrated, "
            + "cost_unit, latency_ms, degraded, warnings_text, selected_model_reason, trace_id, run_id, "
            + "submitter, tenant_id, org_id, domain, version_no, create_time, update_time, create_by, update_by, is_deleted";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "id, task_kind, capability_id, tool_contract_ref, input_ref, model_ref, model_version, "
            + "output_ref, status, quality_score, quality_kind, calibrated, cost_unit, latency_ms, "
            + "degraded, warnings_text, selected_model_reason, trace_id, run_id, submitter, "
            + "tenant_id, org_id, domain, version_no, create_by, update_by) VALUES ("
            + ":id, :taskKind, :capabilityId, :toolContractRef, :inputRef, :modelRef, :modelVersion, "
            + ":outputRef, :status, :qualityScore, :qualityKind, :calibrated, :costUnit, :latencyMs, "
            + ":degraded, :warningsText, :selectedModelReason, :traceId, :runId, :submitter, "
            + ":tenantId, :orgId, :domain, :versionNo, :createBy, :updateBy)";

    private static final String UPDATE_SQL = "UPDATE " + TABLE + " SET "
            + "status = :status, output_ref = :outputRef, model_ref = :modelRef, model_version = :modelVersion, "
            + "quality_score = :qualityScore, quality_kind = :qualityKind, calibrated = :calibrated, "
            + "cost_unit = :costUnit, latency_ms = :latencyMs, degraded = :degraded, warnings_text = :warningsText, "
            + "selected_model_reason = :selectedModelReason, version_no = :versionNo, update_by = :updateBy, "
            + "update_time = CURRENT_TIMESTAMP WHERE id = :id AND is_deleted = 0";

    public int insertTask(CapabilityTaskDO task) {
        return insertByBean(INSERT_SQL, task);
    }

    public int updateTask(CapabilityTaskDO task) {
        return updateByBean(UPDATE_SQL, task);
    }

    public Optional<CapabilityTaskDO> findById(String id) {
        List<CapabilityTaskDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE id = ? AND is_deleted = 0", CapabilityTaskDO.class, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
