package com.chinacreator.gzcm.engine.ontology.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.exception.NotFoundException;

/**
 * 本体变更提案持久化服务 — 封装 ecos_ontology_proposals 表的 SQL 操作。
 *
 * <p>由 {@link com.chinacreator.gzcm.engine.ontology.controller.OntologyProposalController}
 * 调用，Controller 层不再直接持有 {@link JdbcTemplate}。</p>
 *
 * <p>Wave-7 R1 修复：id 入源自清单扫描代理 <code>/x</code> 占位，PG 反串
 * <code>?::bigint</code> 抛 PSQLException 被 Spring 映射为
 * {@link org.springframework.dao.DataIntegrityViolationException} → 500。
 * 本 Service 在<b>所有 id 入参入口</b>统一调用 {@link #parseIdOrNotFound(String)}：
 * 解析失败时直接抛 {@link NotFoundException}（404）。占位符 / 非法 id 与 "资源不
 * 存在" 在 Controller 视角语义一致，避免在 Controller 重复 try/catch。</p>
 */
@Service
public class OntologyProposalService {

    private final JdbcTemplate jdbc;

    public OntologyProposalService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 校验提案 id 是否可解析为 long 主键；非法直接抛 {@link NotFoundException}。
     *
     * @param id 路径参数（String 形式）
     * @return 解析后的 long 主键
     */
    private long parseIdOrNotFound(String id) {
        try {
            return Long.parseLong(id == null ? "" : id.trim());
        } catch (NumberFormatException e) {
            throw NotFoundException.entity("Proposal", id);
        }
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
     * <p>Wave-7 R1：id 入参非法时直接抛 NotFoundException。</p>
     */
    public Map<String, Object> findProposalById(String id) {
        long proposalId = parseIdOrNotFound(id);
        try {
            return jdbc.queryForMap(
                    "SELECT * FROM ecos_ontology_proposals WHERE id=?", proposalId);
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
     * <p>Wave-7 R1：id 入参非法时直接抛 NotFoundException。</p>
     */
    public Map<String, Object> updateProposal(String id, StringBuilder sql, List<Object> params) {
        parseIdOrNotFound(id);
        jdbc.update(sql.toString(), params.toArray());
        return jdbc.queryForMap(
                "SELECT * FROM ecos_ontology_proposals WHERE id=?::bigint", id);
    }

    /**
     * 删除提案。
     * <p>Wave-7 R1：id 入参非法时直接抛 NotFoundException。</p>
     */
    public void deleteProposal(String id) {
        parseIdOrNotFound(id);
        jdbc.update("DELETE FROM ecos_ontology_proposals WHERE id=?::bigint", id);
    }

    /**
     * 状态流转更新（拼接 SQL），返回更新后的行。
     * <p>Wave-7 R1：id 入参非法时直接抛 NotFoundException。</p>
     */
    public Map<String, Object> transition(String id, StringBuilder sql, List<Object> params) {
        parseIdOrNotFound(id);
        jdbc.update(sql.toString(), params.toArray());
        return jdbc.queryForMap(
                "SELECT * FROM ecos_ontology_proposals WHERE id=?::bigint", id);
    }

    /**
     * 验证后更新提案状态。
     * <p>Wave-7 R1：id 入参非法时直接抛 NotFoundException。</p>
     */
    public void updateStatus(String id, String newStatus) {
        parseIdOrNotFound(id);
        jdbc.update("UPDATE ecos_ontology_proposals SET status=? WHERE id=?::bigint", newStatus, id);
    }

    /**
     * 执行提案：更新状态为 executed。
     * <p>Wave-7 R1：id 入参非法时直接抛 NotFoundException。</p>
     */
    public void execute(String id) {
        parseIdOrNotFound(id);
        jdbc.update("UPDATE ecos_ontology_proposals SET status=?, updated_at=NOW() WHERE id=?::bigint",
                "executed", id);
    }

    /**
     * approve-and-publish：更新为 APPROVED，记录审批信息。
     * <p>Wave-7 R1：id 入参非法时直接抛 NotFoundException。</p>
     */
    public void approve(String id, String status, String reviewer, String reviewerComment) {
        parseIdOrNotFound(id);
        jdbc.update(
                "UPDATE ecos_ontology_proposals SET status=?, reviewer=?, reviewer_comment=?, updated_at=NOW() WHERE id=?::bigint",
                status, reviewer, reviewerComment, id);
    }

    /**
     * approve-and-publish：更新为 EXECUTED，回填 version_id。
     * <p>Wave-7 R1：id 入参非法时直接抛 NotFoundException。</p>
     */
    public void markExecutedWithVersion(String id, String status, Long versionId) {
        parseIdOrNotFound(id);
        jdbc.update(
                "UPDATE ecos_ontology_proposals SET status=?, version_id=?, updated_at=NOW() WHERE id=?::bigint",
                status, versionId, id);
    }

    /**
     * approve-and-publish：更新为 EXECUTED（无 version_id）。
     * <p>Wave-7 R1：id 入参非法时直接抛 NotFoundException。</p>
     */
    public void markExecuted(String id, String status) {
        parseIdOrNotFound(id);
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
     * @return 当前版本号；提案不存在时返回 null；id 非法时抛 NotFoundException
     */
    public Integer findOptimisticVersion(String id) {
        long proposalId = parseIdOrNotFound(id);
        try {
            return jdbc.queryForObject(
                    "SELECT optimistic_lock_version FROM ecos_ontology_proposals WHERE id=?",
                    Integer.class, proposalId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 乐观锁更新提案状态（PMO-29 §4.2）。
     *
     * <p>SQL: {@code UPDATE ... SET status=?, reviewer=?, reviewer_comment=?, updated_at=NOW(),
     *      optimistic_lock_version = optimistic_lock_version + 1
     *      WHERE id=?::bigint AND optimistic_lock_version = ?}</p>
     *
     * <p>仅当行存在 && currentVersion == expectedVersion 时才更新；否则 affected=0，
     * 调用方应抛出 IllegalStateException("OPTIMISTIC_LOCK_CONFLICT")。</p>
     *
     * @return 受影响的行数（0 = 版本号不匹配或提案不存在）
     */
    public int optimisticTransition(String id, String newStatus, String reviewer,
                                    String reviewerComment, Integer expectedVersion) {
        parseIdOrNotFound(id);
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
     * @return 受影响的行数（0 = 冲突或不存在）
     */
    public int optimisticVersionIncrement(String id, Integer expectedVersion, String newStatus) {
        parseIdOrNotFound(id);
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

    // ──────────────────────────────────────────────────────────────
    // PMO-E2 下沉通道（Controller 不退化，直接委托）
    // ──────────────────────────────────────────────────────────────

    /**
     * 原始查询 — 供 Controller 委托调用（PMO-E2 下沉）。
     *
     * <p>Wave-7 R1：当 SQL 包含 <code>id=?::bigint</code> 位置标号等于最后 1 个参数时，
     * 末位 String 参数若非数字直接抛 {@link NotFoundException}。
     * 覆盖所有现存 Controller 的
     * <code>proposalService.queryForMap("SELECT ... WHERE id=?::bigint", id)</code> 用法
     * （id 始终在 args 末尾位置）。<code>reviewer_comment</code> 等其它末尾字段不冻结，
     * 因为本 Service 现存 queryForMap 调用所有 SQL 都仅含 id=?::bigint 一种类型注解。</p>
     */
    public Map<String, Object> queryForMap(String sql, Object... args) {
        if (args != null && args.length > 0
                && rejectBigintValueIfSqlTargetsIt(sql, args, args.length - 1)) {
            throw NotFoundException.entity("Proposal", String.valueOf(args[args.length - 1]));
        }
        return jdbc.queryForMap(sql, args);
    }

    /**
     * 原始更新 — 供 Controller 委托调用（PMO-E2 下沉）。
     * <p>Wave-7 R1：同 {@link #queryForMap(String, Object...)}。</p>
     */
    public int update(String sql, Object... args) {
        if (args != null && args.length > 0
                && rejectBigintValueIfSqlTargetsIt(sql, args, args.length - 1)) {
            throw NotFoundException.entity("Proposal", String.valueOf(args[args.length - 1]));
        }
        return jdbc.update(sql, args);
    }

    /**
     * 仅当 SQL 含 <code>id=?::bigint</code>/<code>id = ?::bigint</code> 且
     * <code>candidateIndex</code> 处是 String 非数字时，返回 true（表示应 404）。
     * 其它情况（无该模式 / 数字 / 非 String）返回 false（透传）。
     */
    private static boolean rejectBigintValueIfSqlTargetsIt(String sql, Object[] args, int candidateIndex) {
        if (sql == null || candidateIndex < 0 || candidateIndex >= args.length) {
            return false;
        }
        String lower = sql.toLowerCase();
        if (!lower.contains("id=?::bigint") && !lower.contains("id = ?::bigint")) {
            return false;
        }
        Object val = args[candidateIndex];
        if (!(val instanceof String s)) {
            return false;
        }
        if (s == null || s.trim().isEmpty()) {
            return false;
        }
        return !isNumeric(s.trim());
    }

    /**
     * 字符串去除首尾空白且全为数字（含负号）则返回 true。
     */
    private static boolean isNumeric(String s) {
        try {
            Long.parseLong(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
