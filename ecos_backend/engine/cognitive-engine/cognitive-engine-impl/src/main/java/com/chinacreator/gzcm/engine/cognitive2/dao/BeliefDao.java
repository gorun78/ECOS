package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * B2-b 数据访问收口（架构铁律 §2.5 #1 / IR06）：{@code BeliefStore}（PMO-59 P2a /
 * ADR-9 心智层 P 库）内联的 {@code ecos_cognitive_belief}（V129）SQL 落点。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与原 Service 逐字节一致；
 * 行 → VO 归一化（{@code toNormalizedRow} / {@code parseDistribution}）留在 Store 层。</p>
 */
@Repository
public class BeliefDao extends JdbcTemplateDaoSupport {

    /** 全部列显式清单（禁止 SELECT *；与 V129 DDL 逐列一致） */
    static final String BELIEF_COLUMNS =
            "id, variable_name, tenant_scope, domain, distribution, version, snapshot_version, " +
            "is_manual_override, override_reason, last_evidence_id, status, " +
            "create_time, update_time, create_by, update_by";

    /** 落库一条不确定性判断（指定 version；由 Service 层决定 version=max+1 或 first=1）。 */
    public void insert(String id, String variableName, String tenantScope, String domain,
                       String distributionJson, int version, String lastEvidenceId,
                       boolean isManualOverride, String overrideReason, String status) {
        jdbcTemplate.update(
            "INSERT INTO ecos_cognitive_belief (id, variable_name, tenant_scope, domain, distribution, " +
            "version, snapshot_version, is_manual_override, override_reason, last_evidence_id, status, " +
            "create_by, update_by, is_deleted) " +
            "VALUES (?, ?, ?, ?, ?::jsonb, ?, NULL, ?, ?, ?, ?, 'system', 'system', 0)",
            id, variableName, tenantScope == null || tenantScope.isBlank() ? "default" : tenantScope,
            domain, distributionJson, version, isManualOverride, overrideReason, lastEvidenceId, status);
    }

    /** 某变量某域当前最大 version（无记录返回 0）。 */
    public int maxVersion(String variableName, String domain) {
        Integer max = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(version), 0) FROM ecos_cognitive_belief " +
            "WHERE variable_name = ? AND domain = ? AND is_deleted = 0",
            Integer.class, variableName, domain);
        return max == null ? 0 : max;
    }

    /** 当前生效版本（最新版本）；domain 必填；无记录返回 null。 */
    public Map<String, Object> findLatest(String variableName, String domain) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT " + BELIEF_COLUMNS + " FROM ecos_cognitive_belief " +
                "WHERE variable_name = ? AND domain = ? AND is_deleted = 0 " +
                "ORDER BY version DESC LIMIT 1",
                variableName, domain);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** 指定版本查询（时间回放只读取数）；无记录返回 null（由 Service 转 404）。 */
    public Map<String, Object> findIfExists(String variableName, String domain, int version) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT " + BELIEF_COLUMNS + " FROM ecos_cognitive_belief " +
                "WHERE variable_name = ? AND domain = ? AND version = ? AND is_deleted = 0",
                variableName, domain, version);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** 按主键查询（未删除）；无记录返回 null。 */
    public Map<String, Object> findById(String id) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT " + BELIEF_COLUMNS + " FROM ecos_cognitive_belief WHERE id = ? AND is_deleted = 0", id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** 列表（未删除；domain 必填过滤，status 可选，update_time 倒序）。 */
    public List<Map<String, Object>> list(String domain, String status) {
        StringBuilder sql = new StringBuilder("SELECT " + BELIEF_COLUMNS +
            " FROM ecos_cognitive_belief WHERE is_deleted = 0 AND domain = ?");
        List<Object> args = new ArrayList<>();
        args.add(domain);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status.toUpperCase());
        }
        sql.append(" ORDER BY variable_name, version DESC, update_time DESC");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    /** 按变量名跨域列出（未删除，domain/version 倒序——补算扫描输入）。 */
    public List<Map<String, Object>> listByVariable(String variableName) {
        return jdbcTemplate.queryForList(
            "SELECT " + BELIEF_COLUMNS + " FROM ecos_cognitive_belief " +
            "WHERE is_deleted = 0 AND variable_name = ? ORDER BY domain, version DESC",
            variableName);
    }
}
