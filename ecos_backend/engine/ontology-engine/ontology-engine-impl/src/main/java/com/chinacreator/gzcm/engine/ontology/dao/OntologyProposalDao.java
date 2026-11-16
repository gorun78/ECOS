package com.chinacreator.gzcm.engine.ontology.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

/**
 * B3-a 数据访问收口（R2-B3 / 架构铁律 §2.5 #1 / IR06）：{@code OntologyProposalService}
 * （本体变更提案表）的 JDBC 落点。
 *
 * <p><b>D137（2026-11-16 活体验证）</b>：V174 已建合规表
 * {@code ecos_ontology.ecos_ontology_proposal}（VARCHAR(36) PK / {@code payload_json TEXT} /
 * DR06 审计列），但本 DAO 一直读写旧复数表 {@code ecos_ontology_proposals}
 * （BIGSERIAL PK + {@code jsonb} 列 + 无 {@code is_deleted}）。后果三条活体症状：
 * POST 创建 409（UUID 撞 {@code bigint} 主键）、GET 详情 500（{@code is_deleted} 列不存在）、
 * 列表永不返回（读 0 行旧表）。本批按 V174 口径切到合规表。</p>
 *
 * <p>列名按合规表（{@code payload_json} / {@code snapshot_json} / {@code create_time} /
 * {@code update_time}），投影别名回旧键面 {@code payload} / {@code snapshot} /
 * {@code created_at} / {@code updated_at}，下游 toVO 与门禁的取键不变。</p>
 *
 * <p>MC06 schema（{@code ${ecos.ontology.control-schema:ecos_ontology}}）随 DAO 注入；
 * id 空值守卫（404 语义）保留在 Service 层。</p>
 */
@Repository
public class OntologyProposalDao extends JdbcTemplateDaoSupport {

    /** 提案表裸表名（V174 合规表单数口径；旧复数表 {@code ecos_ontology_proposals} 已停写）。 */
    static final String TABLE = "ecos_ontology_proposal";

    /**
     * IR04 显式列清单（替代旧 {@code SELECT *}）。
     *
     * <p>列名取合规表真名，别名回旧键面，使 {@code toVO} / 发布门禁无需同时改键。</p>
     */
    static final String ID_COLUMNS =
            "id, domain_code, proposal_type, target_entity, "
                    + "payload_json AS payload, snapshot_json AS snapshot, status, "
                    + "author, reviewer, reviewer_comment, version_id, optimistic_lock_version, "
                    + "create_time AS created_at, update_time AS updated_at";

    private final String schema;

    public OntologyProposalDao(
            @Value("${ecos.ontology.control-schema:ecos_ontology}") String schema) {
        this.schema = schema;
    }

    /** schema 限定表名（§四附则1）。 */
    String qualifiedTable() {
        return schema + "." + TABLE;
    }

    /**
     * 查询提案列表（可按 status / proposalType 过滤）。
     * <p>D137：同 {@link #findProposalById}，只读未软删行（V174 DR05）。</p>
     */
    public List<Map<String, Object>> listProposals(String status, String proposalType) {
        StringBuilder sql = new StringBuilder("SELECT " + ID_COLUMNS + " FROM " + qualifiedTable()
                + " WHERE is_deleted=0");
        List<Object> params = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            sql.append(" AND status=?");
            params.add(status);
        }
        if (proposalType != null && !proposalType.isBlank()) {
            sql.append(" AND proposal_type=?");
            params.add(proposalType);
        }
        sql.append(" ORDER BY create_time DESC");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    /**
     * 查询单个提案详情，不存在返回 null。
     * <p>过滤已删除行（{@code is_deleted=0}，V174 DR05 逻辑删除口径）。</p>
     */
    public Map<String, Object> findProposalById(String id) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT " + ID_COLUMNS + " FROM " + qualifiedTable() + " WHERE id=? AND is_deleted=0", id.trim());
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 插入新提案（指定状态）。
     * <p>W72：主键 {@code id} 由应用侧生成 UUID（V174 VARCHAR(36) 主键口径），不再依赖 DB 自增序列；
     * JSON 列以 String 文本绑定（移除 {@code ?::jsonb} 强转）。返回生成并落库的 id。</p>
     * <p>D137：落 V174 合规表真列名（{@code payload_json} / {@code snapshot_json} /
     * {@code create_time} / {@code update_time}）。</p>
     */
    public String insertProposal(String id, String domainCode, String proposalType, String targetEntity,
                                 String payloadJson, String snapshotJson, String status, String author) {
        jdbcTemplate.update(
                "INSERT INTO " + qualifiedTable()
                        + " (id, domain_code, proposal_type, target_entity, payload_json, snapshot_json, status, author, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, domainCode, proposalType, targetEntity, payloadJson, snapshotJson, status, author);
        return id;
    }

    /**
     * 追加 {@code WHERE id=?} 后执行 UPDATE 并读回更新后的行。
     */
    public Map<String, Object> updateAndFind(String sql, List<Object> params, String id) {
        String full = sql + " WHERE id=?";
        List<Object> all = new ArrayList<>(params);
        all.add(id.trim());
        jdbcTemplate.update(full, all.toArray());
        return jdbcTemplate.queryForMap("SELECT " + ID_COLUMNS + " FROM " + qualifiedTable() + " WHERE id=?", id.trim());
    }

    /**
     * 删除提案。
     * <p>D137：V174 DR05 逻辑删除口径——{@code is_deleted=1}，不物理删行。</p>
     */
    public void deleteProposal(String id) {
        jdbcTemplate.update("UPDATE " + qualifiedTable()
                + " SET is_deleted=1, update_time=CURRENT_TIMESTAMP WHERE id=?", id.trim());
    }

    /**
     * 验证后更新提案状态。
     */
    public void updateStatus(String id, String newStatus) {
        jdbcTemplate.update("UPDATE " + qualifiedTable() + " SET status=? WHERE id=?", newStatus, id.trim());
    }

    /**
     * 执行提案：更新状态为 executed。
     */
    public void execute(String id) {
        jdbcTemplate.update("UPDATE " + qualifiedTable() + " SET status=?, update_time=CURRENT_TIMESTAMP WHERE id=?",
                "executed", id.trim());
    }

    /**
     * approve-and-publish：更新为 APPROVED，记录审批信息。
     */
    public void approve(String id, String status, String reviewer, String reviewerComment) {
        jdbcTemplate.update(
                "UPDATE " + qualifiedTable()
                        + " SET status=?, reviewer=?, reviewer_comment=?, update_time=CURRENT_TIMESTAMP WHERE id=?",
                status, reviewer, reviewerComment, id.trim());
    }

    /**
     * approve-and-publish：更新为 EXECUTED，回填 version_id。
     *
     * <p>version_id 存版本 ID 字符串（V174 {@code VARCHAR(64)}，关联
     * {@code ecos_ontology_version.id}），不是 semver 版本号（D139）。</p>
     */
    public void markExecutedWithVersion(String id, String status, String versionId) {
        jdbcTemplate.update(
                "UPDATE " + qualifiedTable() + " SET status=?, version_id=?, update_time=CURRENT_TIMESTAMP WHERE id=?",
                status, versionId, id.trim());
    }

    /**
     * approve-and-publish：更新为 EXECUTED（无 version_id）。
     */
    public void markExecuted(String id, String status) {
        jdbcTemplate.update("UPDATE " + qualifiedTable() + " SET status=?, update_time=CURRENT_TIMESTAMP WHERE id=?",
                status, id.trim());
    }

    /**
     * 查询提案当前乐观锁版本号。
     *
     * @return 当前版本号；提案不存在时返回 null
     */
    public Integer findOptimisticVersion(String id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT optimistic_lock_version FROM " + qualifiedTable() + " WHERE id=?",
                    Integer.class, id.trim());
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 乐观锁更新提案状态（PMO-29 §4.2）。
     *
     * <p>SQL: {@code UPDATE ... SET status=?, reviewer=?, reviewer_comment=?, update_time=CURRENT_TIMESTAMP,
     *      optimistic_lock_version = optimistic_lock_version + 1
     *      WHERE id=? AND optimistic_lock_version = ?}</p>
     *
     * @return 受影响的行数（0 = 版本号不匹配或提案不存在）
     */
    public int optimisticTransition(String id, String newStatus, String reviewer,
                                    String reviewerComment, Integer expectedVersion) {
        return jdbcTemplate.update("""
            UPDATE %s SET
                status = ?,
                reviewer = ?,
                reviewer_comment = ?,
                update_time = CURRENT_TIMESTAMP,
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
        if (newStatus == null) {
            String sql = """
                UPDATE %s SET
                    update_time = CURRENT_TIMESTAMP,
                    optimistic_lock_version = optimistic_lock_version + 1
                WHERE id = ?
                  AND optimistic_lock_version = ?
                """.formatted(qualifiedTable());
            return jdbcTemplate.update(sql, id.trim(), expectedVersion);
        }
        String sql = """
            UPDATE %s SET
                status = ?,
                update_time = CURRENT_TIMESTAMP,
                optimistic_lock_version = optimistic_lock_version + 1
            WHERE id = ?
              AND optimistic_lock_version = ?
            """.formatted(qualifiedTable());
        return jdbcTemplate.update(sql, newStatus, id.trim(), expectedVersion);
    }

    /**
     * 原始查询 — 供 Controller 委托调用（PMO-E2 下沉）。
     */
    public Map<String, Object> queryForMap(String sql, Object... args) {
        return jdbcTemplate.queryForMap(sql, args);
    }

    /**
     * 原始更新 — 供 Controller 委托调用（PMO-E2 下沉，id 空值守卫在 Service 层）。
     */
    public int update(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
    }
}
