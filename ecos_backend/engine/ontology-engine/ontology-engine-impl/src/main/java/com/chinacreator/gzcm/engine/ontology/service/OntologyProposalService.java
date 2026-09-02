package com.chinacreator.gzcm.engine.ontology.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 本体变更提案持久化服务 — 封装 ecos_ontology_proposals 表的 SQL 操作。
 *
 * <p>由 {@link com.chinacreator.gzcm.engine.ontology.controller.OntologyProposalController}
 * 调用，Controller 层不再直接持有 {@link JdbcTemplate}。</p>
 */
@Service
public class OntologyProposalService {

    private final JdbcTemplate jdbc;

    public OntologyProposalService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 查询提案列表（可按 status / proposalType 过滤）。
     */
    public List<Map<String, Object>> listProposals(String status, String proposalType) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ecos_ontology_proposals WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            sql.append(" AND status=?");
            params.add(status);
        }
        if (proposalType != null && !proposalType.isBlank()) {
            sql.append(" AND proposal_type=?");
            params.add(proposalType);
        }
        sql.append(" ORDER BY created_at DESC");

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    /**
     * 查询单个提案详情，不存在返回 null。
     */
    public Map<String, Object> findProposalById(String id) {
        try {
            return jdbc.queryForMap(
                    "SELECT * FROM ecos_ontology_proposals WHERE id=?::bigint", id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 插入新提案（指定状态）。
     */
    public void insertProposal(String domainCode, String proposalType, String targetEntity,
                               String payloadJson, String snapshotJson, String status, String author) {
        jdbc.update(
                "INSERT INTO ecos_ontology_proposals (domain_code, proposal_type, target_entity, payload, snapshot, status, author, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, NOW(), NOW())",
                domainCode, proposalType, targetEntity, payloadJson, snapshotJson, status, author);
    }

    /**
     * 查询刚插入的提案记录（基于自增序列 currval）。
     */
    public Map<String, Object> findCreatedProposal() {
        return jdbc.queryForMap(
                "SELECT * FROM ecos_ontology_proposals WHERE id=currval('ecos_ontology_proposals_id_seq')");
    }

    /**
     * 通用更新提案（拼接 SQL），返回更新后的行。
     */
    public Map<String, Object> updateProposal(String id, StringBuilder sql, List<Object> params) {
        jdbc.update(sql.toString(), params.toArray());
        return jdbc.queryForMap(
                "SELECT * FROM ecos_ontology_proposals WHERE id=?::bigint", id);
    }

    /**
     * 删除提案。
     */
    public void deleteProposal(String id) {
        jdbc.update("DELETE FROM ecos_ontology_proposals WHERE id=?::bigint", id);
    }

    /**
     * 状态流转更新（拼接 SQL），返回更新后的行。
     */
    public Map<String, Object> transition(String id, StringBuilder sql, List<Object> params) {
        jdbc.update(sql.toString(), params.toArray());
        return jdbc.queryForMap(
                "SELECT * FROM ecos_ontology_proposals WHERE id=?::bigint", id);
    }

    /**
     * 验证后更新提案状态。
     */
    public void updateStatus(String id, String newStatus) {
        jdbc.update("UPDATE ecos_ontology_proposals SET status=? WHERE id=?::bigint", newStatus, id);
    }

    /**
     * 执行提案：更新状态为 executed。
     */
    public void execute(String id) {
        jdbc.update("UPDATE ecos_ontology_proposals SET status=?, updated_at=NOW() WHERE id=?::bigint",
                "executed", id);
    }

    /**
     * approve-and-publish: 更新为 APPROVED，记录审批信息。
     */
    public void approve(String id, String status, String reviewer, String reviewerComment) {
        jdbc.update(
                "UPDATE ecos_ontology_proposals SET status=?, reviewer=?, reviewer_comment=?, updated_at=NOW() WHERE id=?::bigint",
                status, reviewer, reviewerComment, id);
    }

    /**
     * approve-and-publish: 更新为 EXECUTED，回填 version_id。
     */
    public void markExecutedWithVersion(String id, String status, Long versionId) {
        jdbc.update(
                "UPDATE ecos_ontology_proposals SET status=?, version_id=?, updated_at=NOW() WHERE id=?::bigint",
                status, versionId, id);
    }

    /**
     * approve-and-publish: 更新为 EXECUTED（无 version_id）。
     */
    public void markExecuted(String id, String status) {
        jdbc.update("UPDATE ecos_ontology_proposals SET status=?, updated_at=NOW() WHERE id=?::bigint",
                status, id);
    }

    // ──────────────────────────────────────────────────────────────
    // PMO-29 §4.2 乐观锁扩展
    // ──────────────────────────────────────────────────────────────

    /**
     * 查询提案当前乐观锁版本号。
     *
     * @param id 提案 ID
     * @return 当前版本号；提案不存在时返回 null
     */
    public Integer findOptimisticVersion(String id) {
        try {
            return jdbc.queryForObject(
                    "SELECT optimistic_lock_version FROM ecos_ontology_proposals WHERE id=?::bigint",
                    Integer.class, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 乐观锁更新提案状态（PMO-29 §4.2）。
     * <p>
     * SQL: {@code UPDATE ... SET status=?, reviewer=?, reviewer_comment=?, updated_at=NOW(),
     *      optimistic_lock_version = optimistic_lock_version + 1
     *      WHERE id=?::bigint AND optimistic_lock_version = ?}
     * </p>
     * <p>
     * 仅当行存在 && currentVersion == expectedVersion 时才更新；否则 affected=0，
     * 调用方应抛出 IllegalStateException("OPTIMISTIC_LOCK_CONFLICT")。
     * </p>
     *
     * @param id                提案 ID
     * @param newStatus         新状态
     * @param reviewer          审批人
     * @param reviewerComment   审批意见
     * @param expectedVersion   客户端持有的期望版本号
     * @return 受影响的行数（0 = 版本号不匹配或提案不存在）
     */
    public int optimisticTransition(String id, String newStatus, String reviewer,
                                    String reviewerComment, Integer expectedVersion) {
        return jdbc.update("""
            UPDATE ecos_ontology_proposals SET
                status = ?,
                reviewer = ?,
                reviewer_comment = ?,
                updated_at = NOW(),
                optimistic_lock_version = optimistic_lock_version + 1
            WHERE id = ?::bigint
              AND optimistic_lock_version = ?
            """, newStatus, reviewer, reviewerComment, id, expectedVersion);
    }

    /**
     * 乐观锁递增版本号（不变更其他字段，仅用于状态变化时的"刷新"）。
     *
     * @param id              提案 ID
     * @param expectedVersion 期望版本号
     * @param newStatus       新状态（可选，传 null 表示不变）
     * @return 受影响的行数（0 = 冲突或不存在）
     */
    public int optimisticVersionIncrement(String id, Integer expectedVersion, String newStatus) {
        String sql;
        if (newStatus == null) {
            sql = """
                UPDATE ecos_ontology_proposals SET
                    updated_at = NOW(),
                    optimistic_lock_version = optimistic_lock_version + 1
                WHERE id = ?::bigint
                  AND optimistic_lock_version = ?
                """;
            return jdbc.update(sql, id, expectedVersion);
        }
        sql = """
            UPDATE ecos_ontology_proposals SET
                status = ?,
                updated_at = NOW(),
                optimistic_lock_version = optimistic_lock_version + 1
            WHERE id = ?::bigint
              AND optimistic_lock_version = ?
            """;
        return jdbc.update(sql, newStatus, id, expectedVersion);
    }

    /**
     * 原始查询 — 供 Controller 委托调用（PMO-E2 下沉）。
     */
    public Map<String, Object> queryForMap(String sql, Object... args) {
        return jdbc.queryForMap(sql, args);
    }

    /**
     * 原始更新 — 供 Controller 委托调用（PMO-E2 下沉）。
     */
    public int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }
}
