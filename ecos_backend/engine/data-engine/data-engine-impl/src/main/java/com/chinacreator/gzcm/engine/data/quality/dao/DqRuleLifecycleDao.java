package com.chinacreator.gzcm.engine.data.quality.dao;

import java.util.List;

import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * DqRuleLifecycleDao — DQ 规则生命周期数据访问层（PMO-48-B T7c）。
 * <p>
 * 架构铁律 §2.5 #1 收口：原内联在 {@code DqRuleLifecycleServiceImpl} 的 JdbcTemplate 调用迁移到本 DAO，
 * SQL 文本一律离开 Service。表前缀统一为 {@code ecos_data}。
 * <p>
 * 注意：findRule 使用原始列名（不带 AS 别名），与 Service 侧 RowMapper 的
 * {@code rs.getString("rule_name")} 等原始列名读取保持一致；不可复用
 * {@code DqRuleMapper.COLUMNS}（其带驼峰别名，仅用于 MyBatis）。
 *
 * @author PMO-48-B T7c
 */
@Repository
public class DqRuleLifecycleDao extends JdbcTemplateDaoSupport {

    /** createDraft — status 固定 DRAFT，version 固定 1。 */
    public void insertRule(String ruleId, String ruleName, String ruleCode, String category, String domain,
                           String ruleType, String severity, String targetKind, String targetId,
                           String targetTable, String targetField, String targetPipelineId,
                           String parameters, String approvedBy, String sourceType, String sourceRef,
                           String description, String operator) {
        jdbcTemplate.update(
                "INSERT INTO ecos_data.dq_rule (id, rule_name, rule_code, category, domain, rule_type," +
                " severity, target_kind, target_id, target_table, target_field, target_pipeline_id," +
                " parameters, status, version, approved_by, source_type, source_ref," +
                " description, created_by, updated_by, created_at, updated_at, is_deleted)" +
                " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?, 'DRAFT', 1, ?,?,?,?, ?,?,?,NOW(),NOW(),FALSE)",
                ruleId, ruleName, ruleCode, category, domain, ruleType,
                severity, targetKind, targetId, targetTable,
                targetField, targetPipelineId, parameters,
                approvedBy, sourceType, sourceRef,
                description, operator, operator);
    }

    /** updateDraft — 仅 DRAFT 可编辑，返回受影响行数（0 = 状态已变更）。 */
    public int updateDraft(String ruleName, String ruleCode, String category, String domain,
                           String ruleType, String severity, String targetKind, String targetId,
                           String targetTable, String targetField, String targetPipelineId,
                           String parameters, String description, String sourceType, String sourceRef,
                           String operator, String ruleId) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_rule SET rule_name=?, rule_code=?, category=?, domain=?," +
                " rule_type=?, severity=?, target_kind=?, target_id=?, target_table=?," +
                " target_field=?, target_pipeline_id=?, parameters=?, description=?," +
                " source_type=?, source_ref=?, updated_by=?, updated_at=NOW()" +
                " WHERE id=? AND is_deleted=FALSE AND status='DRAFT'",
                ruleName, ruleCode, category, domain, ruleType, severity,
                targetKind, targetId, targetTable, targetField, targetPipelineId,
                parameters, description, sourceType, sourceRef, operator, ruleId);
    }

    /** 逻辑删除，返回受影响行数。 */
    public int softDelete(String ruleId) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_rule SET is_deleted=TRUE, deleted_at=NOW(), updated_at=NOW()" +
                " WHERE id=? AND is_deleted=FALSE",
                ruleId);
    }

    /** submit：CAS 转 IN_REVIEW。 */
    public int submitReview(String submitter, String ruleId, String expectedStatus) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_rule SET status='IN_REVIEW', updated_by=?, updated_at=NOW()" +
                " WHERE id=? AND status=? AND is_deleted=FALSE",
                submitter, ruleId, expectedStatus);
    }

    /** approve step1：写版本快照（dq_rule_version，snapshot 为 JSONB）。 */
    public void insertVersionSnapshot(String versionId, String ruleId, int versionNumber,
                                      String snapshotJson, String changedBy) {
        jdbcTemplate.update(
                "INSERT INTO ecos_data.dq_rule_version (id, rule_id, version_number, snapshot, changed_by, change_note)" +
                " VALUES (?,?,?::jsonb,?, '审批通过')",
                versionId, ruleId, versionNumber, snapshotJson, changedBy);
    }

    /** approve step2：CAS IN_REVIEW → ACTIVE + 版本号 / 审批人。 */
    public int approve(String ruleId, int newVersion, String approver) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_rule SET status='ACTIVE', version=?, approved_by=?," +
                " approved_at=NOW(), applied_at=NOW(), updated_by=?, updated_at=NOW()" +
                " WHERE id=? AND status='IN_REVIEW'",
                newVersion, approver, approver, ruleId);
    }

    /** reject：CAS IN_REVIEW → REJECTED。 */
    public int reject(String rejector, String ruleId) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_rule SET status='REJECTED', updated_by=?, updated_at=NOW()" +
                " WHERE id=? AND status='IN_REVIEW' AND is_deleted=FALSE",
                rejector, ruleId);
    }

    /** deprecate：CAS 任意预期状态 → DEPRECATED。 */
    public int deprecate(String operator, String ruleId, String expectedStatus) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_rule SET status='DEPRECATED', updated_by=?, updated_at=NOW()" +
                " WHERE id=? AND status=? AND is_deleted=FALSE",
                operator, ruleId, expectedStatus);
    }

    /** supersede：CAS 任意预期状态 → SUPERSEDED。 */
    public int supersede(String operator, String ruleId, String expectedStatus) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_rule SET status='SUPERSEDED', updated_by=?, updated_at=NOW()" +
                " WHERE id=? AND status=? AND is_deleted=FALSE",
                operator, ruleId, expectedStatus);
    }

    /** disable：CAS ACTIVE → DISABLED。 */
    public int disable(String operator, String ruleId) {
        return jdbcTemplate.update(
                "UPDATE ecos_data.dq_rule SET status='DISABLED', updated_by=?, updated_at=NOW()" +
                " WHERE id=? AND status='ACTIVE' AND is_deleted=FALSE",
                operator, ruleId);
    }

    /**
     * 按 ID 读规则（列名不带驼峰别名 — 映射器在 Service 侧按原始列名读取），
     * forUpdate=true 时加 FOR UPDATE 行锁用于 approve 防并发重审。
     */
    public List<DqRuleVO> findRule(String ruleId, boolean forUpdate, RowMapper<DqRuleVO> mapper) {
        String sql = "SELECT id, rule_name, rule_code, category, domain, rule_type, severity,"
                + " target_kind, target_id, target_table, target_field, target_pipeline_id,"
                + " parameters::text AS parameters_json, status, version, approved_by,"
                + " effective_date, expiry_date, source_type, source_ref, description,"
                + " created_by, updated_by, created_at, updated_at"
                + " FROM ecos_data.dq_rule WHERE id = ? AND is_deleted = FALSE"
                + (forUpdate ? " FOR UPDATE" : "");
        return jdbcTemplate.query(sql, mapper, ruleId);
    }

    /** 检查规则是否已逻辑删除（幂等保护用），返回被删标记行数。 */
    public List<Integer> findDeletedFlag(String ruleId) {
        return jdbcTemplate.queryForList(
                "SELECT is_deleted::int FROM ecos_data.dq_rule WHERE id = ? AND is_deleted = TRUE",
                Integer.class, ruleId);
    }
}
