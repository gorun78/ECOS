package com.chinacreator.gzcm.engine.ai.wagent.dao;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ToolContractStatus;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentToolContractDO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据访问收口（详细设计-10 §6.2）：{@code ecos_ai.ecos_wagent_tool_contract}（V236）的 JDBC 落点。
 * <p>词表单源 = {@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums}
 * （ToolContractStatus 六态 / ToolCategory 五值 / SideEffect 三值 / Disclosure 两值 / AutomationLevel）；
 * {@code engine} 值名录单源 = {@link
 * com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog#contractEngineCodesForDdlCheck()}。</p>
 * <p>注册面纪律：UNIQUE(name, version, is_deleted)——下架工具再注册必须递增 version；
 * commit 类工具必带 {@code rollback_plan_text}（ck_wagent_tool_rollback）；
 * endpoint 前缀合法性由服务层用 {@code WAgentEngineCatalog.resolveLiveServicePrefix} 校验（F10-02/F10-03 红线）。</p>
 */
@Repository
public class WAgentToolContractDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_wagent_tool_contract";

    private static final String COLUMNS = "id, name, version, engine, category, side_effect, min_level, "
            + "required_permission, input_schema_json, output_schema_json, timeout_ms, retry_text, is_idempotent, "
            + "cost_class, is_evidence_output, data_classification_text, rollback_plan_text, endpoint_kind, "
            + "endpoint_url, endpoint_method, owner, sla_text, status, deprecated_by, toolset, summary, "
            + "disclosure, capability_deps_text, "
            + "tenant_id, org_id, domain, version_no, create_time, update_time, create_by, update_by, is_deleted";

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "id, name, version, engine, category, side_effect, min_level, required_permission, "
            + "input_schema_json, output_schema_json, timeout_ms, retry_text, is_idempotent, cost_class, "
            + "is_evidence_output, data_classification_text, rollback_plan_text, endpoint_kind, endpoint_url, "
            + "endpoint_method, owner, sla_text, status, deprecated_by, toolset, summary, disclosure, "
            + "capability_deps_text, tenant_id, org_id, version_no, create_by, update_by) VALUES ("
            + ":id, :name, :version, :engine, :category, :sideEffect, :minLevel, :requiredPermission, "
            + ":inputSchemaJson, :outputSchemaJson, :timeoutMs, :retryText, :isIdempotent, :costClass, "
            + ":isEvidenceOutput, :dataClassificationText, :rollbackPlanText, :endpointKind, :endpointUrl, "
            + ":endpointMethod, :owner, :slaText, :status, :deprecatedBy, :toolset, :summary, :disclosure, "
            + ":capabilityDepsText, :tenantId, :orgId, :versionNo, :createBy, :updateBy)";

    private static final String UPDATE_SQL = "UPDATE " + TABLE + " SET "
            + "name = :name, version = :version, engine = :engine, category = :category, "
            + "side_effect = :sideEffect, min_level = :minLevel, required_permission = :requiredPermission, "
            + "input_schema_json = :inputSchemaJson, output_schema_json = :outputSchemaJson, "
            + "timeout_ms = :timeoutMs, retry_text = :retryText, is_idempotent = :isIdempotent, "
            + "cost_class = :costClass, is_evidence_output = :isEvidenceOutput, "
            + "data_classification_text = :dataClassificationText, rollback_plan_text = :rollbackPlanText, "
            + "endpoint_kind = :endpointKind, endpoint_url = :endpointUrl, endpoint_method = :endpointMethod, "
            + "owner = :owner, sla_text = :slaText, status = :status, deprecated_by = :deprecatedBy, "
            + "toolset = :toolset, summary = :summary, disclosure = :disclosure, "
            + "capability_deps_text = :capabilityDepsText, version_no = :versionNo, update_by = :updateBy, "
            + "update_time = CURRENT_TIMESTAMP WHERE id = :id AND is_deleted = 0";

    public int insert(WAgentToolContractDO contract) {
        return insertByBean(INSERT_SQL, contract);
    }

    public Optional<WAgentToolContractDO> findById(String id) {
        List<WAgentToolContractDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE id = ? AND is_deleted = 0", WAgentToolContractDO.class, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** uniq_wagent_tool_name_ver(name, version, is_deleted)：版本闭合定位。 */
    public Optional<WAgentToolContractDO> findByNameAndVersion(String name, String version) {
        List<WAgentToolContractDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE name = ? AND version = ? AND is_deleted = 0", WAgentToolContractDO.class, name, version);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * 按 name 取 active 最新登记（附录 A 第 21 条 {@code GET /api/v1/agent/tools/{name}} 无
     * {@code version} 参数时的缺省读法）。
     *
     * <p>状态词表取 {@link ToolContractStatus#ACTIVE} 单源（禁写字面量）；定序用
     * {@code update_time DESC, id DESC}——{@code version} 是字符串列，按字面序取"最新"会在
     * {@code 1.10.0} 与 {@code 1.9.0} 之间给出错值。</p>
     */
    public Optional<WAgentToolContractDO> findLatestActiveByName(String name) {
        List<WAgentToolContractDO> rows = queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE name = ? AND status = ? AND is_deleted = 0"
                + " ORDER BY update_time DESC, id DESC LIMIT 1",
                WAgentToolContractDO.class, name, ToolContractStatus.ACTIVE.value());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** idx_wagent_tool_engine(engine, status)：属主引擎目录面。 */
    public List<WAgentToolContractDO> findByEngineAndStatus(String engine, String status) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE engine = ? AND status = ? AND is_deleted = 0 ORDER BY name ASC, version DESC",
                WAgentToolContractDO.class, engine, status);
    }

    /** idx_wagent_tool_toolset(toolset)：工具集面（渐进披露按 toolset 组织）。 */
    public List<WAgentToolContractDO> findByToolset(String toolset) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE toolset = ? AND is_deleted = 0 ORDER BY name ASC", WAgentToolContractDO.class, toolset);
    }

    /** 披露形态过滤（F10-18 渐进披露：always_loaded 直接入提示，searchable 按需检索）。 */
    public List<WAgentToolContractDO> findByDisclosureAndStatus(String disclosure, String status) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE disclosure = ? AND status = ? AND is_deleted = 0 ORDER BY name ASC",
                WAgentToolContractDO.class, disclosure, status);
    }

    /** 全生命周期状态列表面（§5.2 #14 searchTools 无 engine/toolset 收窄时的缺省读法；关键词/权限过滤在服务层）。 */
    public List<WAgentToolContractDO> findByStatus(String status) {
        return queryList("SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE status = ? AND is_deleted = 0 ORDER BY name ASC, version DESC",
                WAgentToolContractDO.class, status);
    }

    public int update(WAgentToolContractDO contract) {
        return updateByBean(UPDATE_SQL, contract);
    }
}
