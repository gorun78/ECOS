package com.chinacreator.gzcm.engine.ontology.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.exception.NotFoundException;

/**
 * 本体变更提案持久化服务 — 封装提案表的 SQL 操作。
 *
 * <p>由 {@link com.chinacreator.gzcm.engine.ontology.controller.OntologyProposalController}
 * 调用，Controller 层不再直接持有 {@link JdbcTemplate}。</p>
 *
 * <p><b>W72 / 数据库访问规范合规化（MC03 / IR04 / §四附则1）</b>：
 * <ul>
 *   <li><b>MC03</b> — 移除全部 PG 私有类型强转（旧 <code>?::jsonb</code> / <code>?::bigint</code>）。
 *       JSON 文本作为 {@code String} 绑定；主键 <code>id</code> 自 V174 起为 VARCHAR(36) UUID，
 *       直接按 String 绑定（不再 {@code Long.parseLong}）。</li>
 *   <li><b>IR04</b> — 禁止 {@code SELECT *}，统一显式列清单 {@link #ID_COLUMNS}。</li>
 *   <li><b>§四附则1 / ST07</b> — 表名一律 schema 限定
 *       {@link #qualifiedTable()}（{@code ${ecos.ontology.control-schema:ecos_ontology}}）。</li>
 * </ul></p>
 *
 * <p>原 Wave-7 R1 的 {@code parseIdOrNotFound}（long 主键反串校验）已随 UUID 主键作废：
 * 主键为不透明字符串，null / 空白 id 仍按「资源不存在」语义抛 {@link NotFoundException}（404）。</p>
 */
@Service
public class OntologyProposalService {

    /**
     * 控制域 schema（ST07 5+1 / §四附则1）：{@code ecos.ontology.control-schema}，默认 {@code ecos_ontology}。
     * 以构造器参数注入（不改既有方法签名，保持 API 只增不改）。
     */
    private final String schema;

    private final JdbcTemplate jdbc;

    /** 提案表裸表名（V4.1/V108 建，代码现行读写目标；V174 合规新表见迁移脚本）。 */
    static final String TABLE = "ecos_ontology_proposals";

    /** IR04 显式列清单（替代旧 {@code SELECT *}）。字段顺序与 toVO 读取一致。 */
    static final String ID_COLUMNS =
            "id, domain_code, proposal_type, target_entity, payload, snapshot, status, "
                    + "author, reviewer, reviewer_comment, version_id, optimistic_lock_version, "
                    + "created_at, updated_at";

    public OntologyProposalService(JdbcTemplate jdbc,
                                   @Value("${ecos.ontology.control-schema:ecos_ontology}") String schema) {
        this.jdbc = jdbc;
        this.schema = schema;
    }

    /** schema 限定表名（§四附则1）。 */
    String qualifiedTable() {
        return schema + "." + TABLE;
    }

    /**
     * id 入参守卫：null / 空白直接抛 {@link NotFoundException}（与「资源不存在」语义一致，404）。
     */
    private void requireNonNullId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw NotFoundException.entity("Proposal", id);
        }
    }

    /**
     * 查询提案列表（可按 status / proposalType 过滤）。
     */
    public List<Map<String, Object>> listProposals(String status, String proposalType) {
        StringBuilder sql = new StringBuilder("SELECT " + ID_COLUMNS + " FROM " + qualifiedTable() + " WHERE 1=1");
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
     * <p>过滤已删除行（{@code is_deleted=0}，V174 DR05 逻辑删除口径）。</p>
     */
    public Map<String, Object> findProposalById(String id) {
        requireNonNullId(id);
        try {
            return jdbc.queryForMap(
                    "SELECT " + ID_COLUMNS + " FROM " + qualifiedTable() + " WHERE id=? AND is_deleted=0", id.trim());
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 插入新提案（指定状态）。
     * <p>W72：主键 {@code id} 由应用侧生成 UUID（V174 VARCHAR(36) 主键口径），不再依赖 DB 自增序列；
     * JSON 列以 String 文本绑定（移除 {@code ?::jsonb} 强转）。返回生成并落库的 id。
     */
    public String insertProposal(String domainCode, String proposalType, String targetEntity,
                                 String payloadJson, String snapshotJson, String status, String author) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO " + qualifiedTable()
                        + " (id, domain_code, proposal_type, target_entity, payload, snapshot, status, author, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                id, domainCode, proposalType, targetEntity, payloadJson, snapshotJson, status, author);
        return id;
    }

    /**
     * 通用更新提案（拼接 SQL），返回更新后的行。
     *
     * @param id     提案 id（VARCHAR UUID）
     * @param sql    调用方拼装的完整 UPDATE（不含 WHERE；本方法统一追加 {@code WHERE id=?}）
     * @param params 与 {@code sql} 占位符一一对应，末尾追加 id
     */
    public Map<String, Object> updateProposal(String id, StringBuilder sql, List<Object> params) {
        requireNonNullId(id);
        String full = sql.toString() + " WHERE id=?";
        List<Object> all = new ArrayList<>(params);
        all.add(id.trim());
        jdbc.update(full, all.toArray());
        return jdbc.queryForMap("SELECT " + ID_COLUMNS + " FROM " + qualifiedTable() + " WHERE id=?", id.trim());
    }

    /**
     * 删除提案。
     */
    public void deleteProposal(String id) {
        requireNonNullId(id);
        jdbc.update("DELETE FROM " + qualifiedTable() + " WHERE id=?", id.trim());
    }

    /**
     * 状态流转更新（拼接 SQL），返回更新后的行。
     */
    public Map<String, Object> transition(String id, StringBuilder sql, List<Object> params) {
        requireNonNullId(id);
        String full = sql.toString() + " WHERE id=?";
        List<Object> all = new ArrayList<>(params);
        all.add(id.trim());
        jdbc.update(full, all.toArray());
        return jdbc.queryForMap("SELECT " + ID_COLUMNS + " FROM " + qualifiedTable() + " WHERE id=?", id.trim());
    }

    /**
     * 验证后更新提案状态。
     */
    public void updateStatus(String id, String newStatus) {
        requireNonNullId(id);
        jdbc.update("UPDATE " + qualifiedTable() + " SET status=? WHERE id=?", newStatus, id.trim());
    }

    /**
     * 执行提案：更新状态为 executed。
     */
    public void execute(String id) {
        requireNonNullId(id);
        jdbc.update("UPDATE " + qualifiedTable() + " SET status=?, updated_at=NOW() WHERE id=?",
                "executed", id.trim());
    }

    /**
     * approve-and-publish：更新为 APPROVED，记录审批信息。
     */
    public void approve(String id, String status, String reviewer, String reviewerComment) {
        requireNonNullId(id);
        jdbc.update(
                "UPDATE " + qualifiedTable()
                        + " SET status=?, reviewer=?, reviewer_comment=?, updated_at=NOW() WHERE id=?",
                status, reviewer, reviewerComment, id.trim());
    }

    /**
     * approve-and-publish：更新为 EXECUTED，回填 version_id。
     */
    public void markExecutedWithVersion(String id, String status, Long versionId) {
        requireNonNullId(id);
        jdbc.update(
                "UPDATE " + qualifiedTable() + " SET status=?, version_id=?, updated_at=NOW() WHERE id=?",
                status, versionId, id.trim());
    }

    /**
     * approve-and-publish：更新为 EXECUTED（无 version_id）。
     */
    public void markExecuted(String id, String status) {
        requireNonNullId(id);
        jdbc.update("UPDATE " + qualifiedTable() + " SET status=?, updated_at=NOW() WHERE id=?",
                status, id.trim());
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
        requireNonNullId(id);
        try {
            return jdbc.queryForObject(
                    "SELECT optimistic_lock_version FROM " + qualifiedTable() + " WHERE id=?",
                    Integer.class, id.trim());
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 乐观锁更新提案状态（PMO-29 §4.2）。
     *
     * <p>SQL: {@code UPDATE ... SET status=?, reviewer=?, reviewer_comment=?, updated_at=NOW(),
     *      optimistic_lock_version = optimistic_lock_version + 1
     *      WHERE id=? AND optimistic_lock_version = ?}</p>
     *
     * <p>仅当行存在 && currentVersion == expectedVersion 时才更新；否则 affected=0，
     * 调用方应抛出 IllegalStateException("OPTIMISTIC_LOCK_CONFLICT")。</p>
     *
     * @return 受影响的行数（0 = 版本号不匹配或提案不存在）
     */
    public int optimisticTransition(String id, String newStatus, String reviewer,
                                    String reviewerComment, Integer expectedVersion) {
        requireNonNullId(id);
        return jdbc.update("""
            UPDATE %s SET
                status = ?,
                reviewer = ?,
                reviewer_comment = ?,
                updated_at = NOW(),
                optimistic_lock_version = optimistic_lock_version + 1
            WHERE id = ?
              AND optimistic_lock_version = ?
            """.formatted(qualifiedTable()), newStatus, reviewer, reviewerComment, id.trim(), expectedVersion);
    }

    /**
     * 乐观锁递增版本号（不变更其他字段，仅用于状态变化时的"刷新"）。
     *
     * @return 受影响的行数（0 = 冲突或不存在）
     */
    public int optimisticVersionIncrement(String id, Integer expectedVersion, String newStatus) {
        requireNonNullId(id);
        if (newStatus == null) {
            String sql = """
                UPDATE %s SET
                    updated_at = NOW(),
                    optimistic_lock_version = optimistic_lock_version + 1
                WHERE id = ?
                  AND optimistic_lock_version = ?
                """.formatted(qualifiedTable());
            return jdbc.update(sql, id.trim(), expectedVersion);
        }
        String sql = """
            UPDATE %s SET
                status = ?,
                updated_at = NOW(),
                optimistic_lock_version = optimistic_lock_version + 1
            WHERE id = ?
              AND optimistic_lock_version = ?
            """.formatted(qualifiedTable());
        return jdbc.update(sql, newStatus, id.trim(), expectedVersion);
    }

    // ──────────────────────────────────────────────────────────────
    // PMO-E2 下沉通道（Controller 委托调用）
    // ──────────────────────────────────────────────────────────────

    /**
     * 原始查询 — 供 Controller 委托调用（PMO-E2 下沉）。
     * <p>W72：id 为 VARCHAR 主键，直接透传（不再做 long 反串判定）。</p>
     */
    public Map<String, Object> queryForMap(String sql, Object... args) {
        return jdbc.queryForMap(sql, args);
    }

    /**
     * 原始更新 — 供 Controller 委托调用（PMO-E2 下沉）。
     * <p>W72：id 为 VARCHAR 主键，直接透传（不再做 long 反串判定）。
     * 末位若为 null / 空白提案 id，按 404 语义抛 {@link NotFoundException}（与既有 id 守卫一致）。</p>
     */
    public int update(String sql, Object... args) {
        if (args != null && args.length > 0) {
            Object last = args[args.length - 1];
            if (last instanceof String s && (s == null || s.trim().isEmpty())) {
                throw NotFoundException.entity("Proposal", s);
            }
        }
        return jdbc.update(sql, args);
    }
}
