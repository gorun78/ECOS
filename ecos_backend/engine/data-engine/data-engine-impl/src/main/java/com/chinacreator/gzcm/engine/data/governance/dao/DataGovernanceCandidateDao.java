package com.chinacreator.gzcm.engine.data.governance.dao;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;

/**
 * V272 数据治理候选 DAO — {@code ecos_data.ecos_data_governance_candidate}。
 *
 * <p>《ECOS 认知域与智能域补充详细设计 v1.0》§7.1/7.2 数据治理候选落地。
 * 纪律：MC01 UUID 主键由应用侧传入；IR04 显式列清单（禁 SELECT *）；
 * schema 限定 {@code ${ecos.data.control-schema:ecos_data}}（ST07 控制域）。
 * 幂等：表内唯一索引 uk_v272_cand_idem (tenant_id, idempotency_key, is_deleted)，
 * 应用层 catch {@code DuplicateKeyException} 后回读同 key 记录（§7.5 重试不重复创建）。</p>
 */
@Repository
public class DataGovernanceCandidateDao extends JdbcTemplateDaoSupport {

    static final String TABLE = "ecos_data_governance_candidate";

    /** IR04 显式列清单（列顺序与本类 toRow 读取一致）。 */
    static final String COLUMNS =
            "id, tenant_id AS tenantId, org_id AS orgId, candidate_type AS candidateType,"
                    + " target_ref AS targetRef, proposal_text AS proposalText,"
                    + " evidence_refs_text AS evidenceRefsText, confidence_value AS confidenceValue,"
                    + " confidence_kind AS confidenceKind, calibrated, assumptions_text AS assumptionsText,"
                    + " conflicts_text AS conflictsText, impact_text AS impactText,"
                    + " preview_pass_rate AS previewPassRate, rollback_plan_text AS rollbackPlanText,"
                    + " status, tool_contract_ref AS toolContractRef, approval_ref AS approvalRef,"
                    + " source_run_id AS sourceRunId, trace_id AS traceId, idempotency_key AS idempotencyKey,"
                    + " domain, version_no AS versionNo, create_time AS createTime,"
                    + " update_time AS updateTime, create_by AS createBy, update_by AS updateBy,"
                    + " is_deleted AS isDeleted";

    private final String schema;

    public DataGovernanceCandidateDao(
            @Value("${ecos.data.control-schema:ecos_data}") String schema) {
        this.schema = schema;
    }

    String qualifiedTable() {
        return schema + "." + TABLE;
    }

    /**
     * 插入新候选（主键由调用方生成 UUID）。
     * 命中 uk_v272_cand_idem 时抛 {@code DataAccessException}（在 Service 层转幂等语义）。
     */
    public int insert(String id, String tenantId, String orgId, String candidateType,
                      String targetRef, String proposalText, String evidenceRefsText,
                      Double confidenceValue, String confidenceKind, Integer calibrated,
                      String assumptionsText, String conflictsText, String impactText,
                      Double previewPassRate, String rollbackPlanText, String status,
                      String toolContractRef, String approvalRef, String sourceRunId,
                      String traceId, String idempotencyKey, String domain,
                      String versionNo, String createBy) {
        return insert(
                "INSERT INTO " + qualifiedTable()
                        + " (id, tenant_id, org_id, candidate_type, target_ref, proposal_text,"
                        + "  evidence_refs_text, confidence_value, confidence_kind, calibrated,"
                        + "  assumptions_text, conflicts_text, impact_text, preview_pass_rate,"
                        + "  rollback_plan_text, status, tool_contract_ref, approval_ref,"
                        + "  source_run_id, trace_id, idempotency_key, domain, version_no,"
                        + "  create_time, update_time, create_by, update_by, is_deleted)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "NOW(), NOW(), ?, ?, 0)",
                id, tenantId, orgId, candidateType, targetRef, proposalText, evidenceRefsText,
                confidenceValue, confidenceKind, calibrated, assumptionsText, conflictsText,
                impactText, previewPassRate, rollbackPlanText, status, toolContractRef,
                approvalRef, sourceRunId, traceId, idempotencyKey, domain, versionNo,
                createBy, createBy);
    }

    /** 按主键查询（is_deleted=0），不存在返回 null。 */
    public Map<String, Object> findByCandidateId(String id) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT " + COLUMNS + " FROM " + qualifiedTable()
                            + " WHERE id=? AND is_deleted=0", id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** 按租户 + 幂等键回读既有候选（幂等重试路径）。 */
    public Map<String, Object> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT " + COLUMNS + " FROM " + qualifiedTable()
                            + " WHERE tenant_id=? AND idempotency_key=? AND is_deleted=0",
                    tenantId, idempotencyKey);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 列表（tenantId 必填，status / candidateType / batchId 可选）。
     * batchId = source_run_id（一次 W Agent Run 产出的候选批次，§13 列表筛选）。
     */
    public List<Map<String, Object>> list(String tenantId, String status, String candidateType,
                                          String batchId) {
        StringBuilder sql = new StringBuilder(
                "SELECT " + COLUMNS + " FROM " + qualifiedTable() + " WHERE tenant_id=? AND is_deleted=0");
        List<Object> params = new java.util.ArrayList<>();
        params.add(tenantId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status=?");
            params.add(status);
        }
        if (candidateType != null && !candidateType.isBlank()) {
            sql.append(" AND candidate_type=?");
            params.add(candidateType);
        }
        if (batchId != null && !batchId.isBlank()) {
            sql.append(" AND source_run_id=?");
            params.add(batchId);
        }
        sql.append(" ORDER BY create_time DESC LIMIT 200");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    /** CAS 状态迁移：仅当当前状态 = fromStatus 时更新，返回受影响行数（0 = 状态不符/不存在）。 */
    public int transitionStatus(String id, String fromStatus, String toStatus,
                                String approvalRef, String updateBy) {
        StringBuilder sql = new StringBuilder(
                "UPDATE " + qualifiedTable()
                        + " SET status=?, \"update_time\"=NOW(), update_by=?");
        List<Object> params = new java.util.ArrayList<>();
        params.add(toStatus);
        params.add(updateBy);
        if (approvalRef != null && !approvalRef.isBlank()) {
            sql.append(", approval_ref=?");
            params.add(approvalRef);
        }
        sql.append(" WHERE id=? AND status=? AND is_deleted=0");
        params.add(id);
        params.add(fromStatus);
        return update(sql.toString(), params.toArray());
    }

    /** 回写 DQ_PREVIEW 只读预览通过率（SUP-DATA-01：预览只写本候选行，不触正式资产）。 */
    public int updatePreviewPassRate(String id, Double passRate) {
        return update(
                "UPDATE " + qualifiedTable()
                        + " SET preview_pass_rate=?, \"update_time\"=NOW()"
                        + " WHERE id=? AND is_deleted=0",
                passRate, id);
    }

    // ── DATA_PROFILE 只读源（§7.2：列类型/行数/覆盖率；只读元数据 + PG 估计，不触正式资产）──

    /**
     * 资产登记行（resource_id 或 source_path 定位；只读）。不存在返回 null。
     * <p>数据库/剖析源不可用（DataAccessException）时<b>原样上抛</b>，由 Service 转真 503，
     * 禁止以空数据伪装剖析成功（补充设计 §13.1 状态码真值化纪律）。</p>
     */
    public Map<String, Object> readAssetCatalog(String assetRef) {
        if (assetRef == null || assetRef.isBlank()) {
            return null;
        }
        return jdbcTemplate.queryForMap(
                "SELECT resource_id AS resourceId, resource_name AS resourceName,"
                        + " resource_type AS resourceType, source_path AS sourcePath,"
                        + " description AS description, tags, status,"
                        + " field_count AS fieldCount, record_count AS recordCount,"
                        + " layer, zone, storage_kind AS storageKind, carrier_ref AS carrierRef,"
                        + " last_sync_time AS lastSyncTime"
                        + " FROM td_data_resource"
                        + " WHERE (resource_id=? OR source_path=?) ORDER BY create_time DESC LIMIT 1",
                assetRef.trim(), assetRef.trim());
    }

    /**
     * PG 表行数/可见性估计（schema.table 形态 source_path 时用；只读 pg_class）。
     * 命中不到返回 null（非 PG 内表形态属正常，非错误）。
     */
    public Map<String, Object> readPgStats(String sourcePath) {
        if (sourcePath == null || !sourcePath.contains(".") || sourcePath.length() > 260) {
            return null;
        }
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT c.relkind AS relKind, c.reltuples::bigint AS relTuples,"
                            + " n.nspname AS schemaName"
                            + " FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace"
                            + " WHERE n.nspname=? AND c.relname=? AND c.relkind IN ('r','p','v','m') LIMIT 1",
                    sourcePath.substring(0, sourcePath.indexOf('.')),
                    sourcePath.substring(sourcePath.indexOf('.') + 1));
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
