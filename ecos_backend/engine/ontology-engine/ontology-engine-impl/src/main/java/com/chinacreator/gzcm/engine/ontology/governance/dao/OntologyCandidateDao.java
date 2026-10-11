package com.chinacreator.gzcm.engine.ontology.governance.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;

/**
 * V273 本体 AI 候选 DAO — {@code ${schema}.ecos_ontology_candidate}。
 *
 * <p>《ECOS 认知域与智能域补充详细设计 v1.0》§7.3/7.4/7.5 本体候选落地。
 * 纪律：MC01 UUID 主键由应用侧传入；IR04 显式列清单（禁 SELECT *）；
 * schema 限定 {@code ${ecos.ontology.control-schema:ecos_ontology}}（ST07 控制域）。
 * 幂等：表内唯一索引 uk_v273_onto_idem (tenant_id, idempotency_key, is_deleted)，
 * 应用层 catch {@code DuplicateKeyException} 后回读同 key 记录（§7.5 重试不重复创建）。
 * 桥接：ONTOLOGY_PROPOSAL_CREATE 回填 proposal_ref（逻辑引用，ST09 禁跨表 FK）。</p>
 */
@Repository
public class OntologyCandidateDao extends JdbcTemplateDaoSupport {

    static final String TABLE = "ecos_ontology_candidate";

    /** IR04 显式列清单（列顺序与读取语义一致）。 */
    static final String COLUMNS =
            "id, tenant_id AS tenantId, org_id AS orgId, candidate_type AS candidateType,"
                    + " subject_type AS subjectType, predicate, object_type AS objectType,"
                    + " target_ref AS targetRef, proposal_text AS proposalText,"
                    + " evidence_refs_text AS evidenceRefsText, confidence_value AS confidenceValue,"
                    + " confidence_kind AS confidenceKind, calibrated, conflicts_text AS conflictsText,"
                    + " compatibility_text AS compatibilityText, diff_preview_text AS diffPreviewText,"
                    + " validation_text AS validationText, status, proposal_ref AS proposalRef,"
                    + " tool_contract_ref AS toolContractRef, approval_ref AS approvalRef,"
                    + " source_run_id AS sourceRunId, trace_id AS traceId, idempotency_key AS idempotencyKey,"
                    + " domain, version_no AS versionNo, create_time AS createTime,"
                    + " update_time AS updateTime, create_by AS createBy, update_by AS updateBy,"
                    + " is_deleted AS isDeleted";

    private final String schema;

    public OntologyCandidateDao(
            @Value("${ecos.ontology.control-schema:ecos_ontology}") String schema) {
        this.schema = schema;
    }

    String qualifiedTable() {
        return schema + "." + TABLE;
    }

    /**
     * 插入新候选（主键由调用方生成 UUID）。
     * 命中 uk_v273_onto_idem 时抛 {@code DataAccessException}（在 Service 层转幂等语义）。
     */
    public int insert(String id, String tenantId, String orgId, String candidateType,
                      String subjectType, String predicate, String objectType,
                      String targetRef, String proposalText, String evidenceRefsText,
                      Double confidenceValue, String confidenceKind, Integer calibrated,
                      String conflictsText, String compatibilityText, String diffPreviewText,
                      String validationText, String status, String proposalRef,
                      String toolContractRef, String approvalRef, String sourceRunId,
                      String traceId, String idempotencyKey, String domain,
                      String versionNo, String createBy) {
        return jdbcTemplate.update(
                "INSERT INTO " + qualifiedTable()
                        + " (id, tenant_id, org_id, candidate_type, subject_type, predicate,"
                        + "  object_type, target_ref, proposal_text, evidence_refs_text,"
                        + "  confidence_value, confidence_kind, calibrated, conflicts_text,"
                        + "  compatibility_text, diff_preview_text, validation_text, status,"
                        + "  proposal_ref, tool_contract_ref, approval_ref, source_run_id,"
                        + "  trace_id, idempotency_key, domain, version_no,"
                        + "  create_time, update_time, create_by, update_by, is_deleted)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "NOW(), NOW(), ?, ?, 0)",
                id, tenantId, orgId, candidateType, subjectType, predicate, objectType,
                targetRef, proposalText, evidenceRefsText, confidenceValue, confidenceKind,
                calibrated, conflictsText, compatibilityText, diffPreviewText, validationText,
                status, proposalRef, toolContractRef, approvalRef, sourceRunId, traceId,
                idempotencyKey, domain, versionNo, createBy, createBy);
    }

    /** 按主键查询（is_deleted=0），不存在返回 null。 */
    public Map<String, Object> findByCandidateId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
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
     * batchId = source_run_id（一次 W Agent Run 产出的候选批次）。
     */
    public List<Map<String, Object>> list(String tenantId, String status, String candidateType,
                                          String batchId) {
        StringBuilder sql = new StringBuilder(
                "SELECT " + COLUMNS + " FROM " + qualifiedTable() + " WHERE tenant_id=? AND is_deleted=0");
        List<Object> params = new ArrayList<>();
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
        List<Object> params = new ArrayList<>();
        params.add(toStatus);
        params.add(updateBy);
        if (approvalRef != null && !approvalRef.isBlank()) {
            sql.append(", approval_ref=?");
            params.add(approvalRef);
        }
        sql.append(" WHERE id=? AND status=? AND is_deleted=0");
        params.add(id);
        params.add(fromStatus);
        return jdbcTemplate.update(sql.toString(), params.toArray());
    }

    /** 回写 ONTOLOGY_CANDIDATE_VALIDATE 产物（自检 JSON）。 */
    public int updateValidationText(String id, String validationJson) {
        return jdbcTemplate.update(
                "UPDATE " + qualifiedTable()
                        + " SET validation_text=?, \"update_time\"=NOW()"
                        + " WHERE id=? AND is_deleted=0",
                validationJson, id);
    }

    /** 回写 ONTOLOGY_DIFF_PREVIEW 只读差异快照（禁直接应用；SUP-ONTO-01）。 */
    public int updateDiffPreviewText(String id, String diffPreviewJson) {
        return jdbcTemplate.update(
                "UPDATE " + qualifiedTable()
                        + " SET diff_preview_text=?, \"update_time\"=NOW()"
                        + " WHERE id=? AND is_deleted=0",
                diffPreviewJson, id);
    }

    /** ONTOLOGY_PROPOSAL_CREATE 桥接：回填 proposals 表逻辑引用（ST09 无 FK）。 */
    public int updateProposalRef(String id, String proposalRef) {
        return jdbcTemplate.update(
                "UPDATE " + qualifiedTable()
                        + " SET proposal_ref=?, \"update_time\"=NOW()"
                        + " WHERE id=? AND is_deleted=0",
                proposalRef, id);
    }

    // ── 只读源（ONTOLOGY_GAP_ANALYZE / ONTOLOGY_CANDIDATE_VALIDATE 用；只读，不触正式资产）──

    /** 全量在线实体 code（去重；gap 分析比对基准）。不存在/结构异常上抛由 Service 转真 5xx。 */
    public List<String> listEntityCodes() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT e.code FROM " + schema + ".ecos_ontology_entity e"
                        + " WHERE e.is_deleted=0 ORDER BY e.code", String.class);
    }

    /** 实体全部属性（code + type）；无实体返回空表。 */
    public List<Map<String, Object>> listPropertiesByEntityId(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
                "SELECT code, property_type AS propertyType, required_flag AS requiredFlag,"
                        + " unique_flag AS uniqueFlag, enum_values AS enumValues"
                        + " FROM " + schema + ".ecos_ontology_property"
                        + " WHERE entity_id=? ORDER BY sort_order, code",
                entityId);
    }

    /** 与已有关系的谓词（code）重叠检测：按 code 前缀（不区分大小写）匹配，返回重叠项。 */
    public List<Map<String, Object>> findRelationshipCodeCollisions(String predicate, int limit) {
        if (predicate == null || predicate.isBlank()) {
            return List.of();
        }
        String prefix = predicate.trim().toUpperCase(java.util.Locale.ROOT);
        return jdbcTemplate.queryForList(
                "SELECT r.id, r.code, r.name, r.relationship_type AS relationshipType,"
                        + " s.code AS sourceEntityCode, t.code AS targetEntityCode"
                        + " FROM " + schema + ".ecos_ontology_relationship r"
                        + " JOIN " + schema + ".ecos_ontology_entity s ON s.id = r.source_entity_id"
                        + " JOIN " + schema + ".ecos_ontology_entity t ON t.id = r.target_entity_id"
                        + " WHERE r.code IS NOT NULL AND UPPER(r.code) LIKE ?"
                        + " ORDER BY r.id LIMIT ?",
                prefix + "%", Math.max(1, Math.min(limit, 50)));
    }

    /** 端点探测：实体 code 是否存在（id 或 code 双匹配，与 OntologyMappingDao 同口径）。 */
    public Map<String, Object> findEntityByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT id, code, name, entity_type AS entityType, status"
                            + " FROM " + schema + ".ecos_ontology_entity"
                            + " WHERE is_deleted=0 AND (id=? OR code=?) LIMIT 1",
                    code.trim(), code.trim());
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
