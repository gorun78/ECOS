package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * B2-b 数据访问收口（架构铁律 §2.5 #1 / IR06）：{@code HypothesisStore}（PMO-59 P2a /
 * ADR-9 心智层 H 库）内联的 {@code ecos_cognitive_hypothesis}（V128）SQL 落点。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与原 Store 逐字节一致；
 * 行 → VO 归一化（{@code toNormalizedRow}）留在 Store 层。</p>
 */
@Repository
public class HypothesisDao extends JdbcTemplateDaoSupport {

    /** 全部列显式清单（禁止 SELECT *；与 V128 DDL 逐列一致） */
    static final String HYPOTHESIS_COLUMNS =
            "id, hypothesis_code, tenant_scope, statement, domain, metric_ref, evidence_ids, " +
            "is_valid, invalid_at, invalid_reason, status, " +
            "create_time, update_time, create_by, update_by";

    /** 注册一条假设（初始 VALID / is_valid=true / 审计列服务端置位）。 */
    public void insert(String id, String code, String tenantScope, String statement,
                       String domain, String metricRef, String evidenceIds) {
        jdbcTemplate.update(
            "INSERT INTO ecos_cognitive_hypothesis (id, hypothesis_code, tenant_scope, statement, domain, " +
            "metric_ref, evidence_ids, is_valid, status, create_by, update_by, is_deleted) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, TRUE, 'VALID', 'system', 'system', 0)",
            id, code, tenantScope == null || tenantScope.isBlank() ? "default" : tenantScope,
            statement, domain, metricRef, evidenceIds);
    }

    /** 校验业务唯一键是否已存在（含逻辑删除行）。 */
    public boolean existsByCode(String code) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM ecos_cognitive_hypothesis WHERE hypothesis_code = ?", Integer.class, code);
        return count != null && count > 0;
    }

    /** 假设详情（未删除）；无记录返回 null。 */
    public Map<String, Object> findById(String id) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT " + HYPOTHESIS_COLUMNS + " FROM ecos_cognitive_hypothesis WHERE id = ? AND is_deleted = 0", id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** 假设列表（未删除；domain/status 可选过滤，update_time 倒序）。 */
    public List<Map<String, Object>> list(String domain, String status) {
        StringBuilder sql = new StringBuilder("SELECT " + HYPOTHESIS_COLUMNS +
            " FROM ecos_cognitive_hypothesis WHERE is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (domain != null && !domain.isBlank()) {
            sql.append(" AND domain = ?");
            args.add(domain);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status.toUpperCase());
        }
        sql.append(" ORDER BY update_time DESC, id");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    /** 状态切换（状态切换 + 失效时间写）。 */
    public boolean updateStatus(String id, String status, boolean isValid,
                                LocalDateTime invalidAt, String invalidReason, Timestamp updateTime) {
        return jdbcTemplate.update(
            "UPDATE ecos_cognitive_hypothesis SET status = ?, is_valid = ?, invalid_at = ?, " +
            "invalid_reason = ?, update_time = ? WHERE id = ? AND is_deleted = 0",
            status, isValid, invalidAt, invalidReason, updateTime, id) > 0;
    }

    // ── 《认知域与智能域补充详细设计 v1.0》§8 增量（只增不改上方既有方法；V268 列位）──

    /** 生命周期读投影（resolve/状态机用；V268 lifecycle_state + legacy status）。 */
    public Map<String, Object> findLifecycleStateById(String id) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT id, hypothesis_code, domain, status, is_valid, lifecycle_state " +
                "FROM ecos_cognitive_hypothesis WHERE id = ? AND is_deleted = 0", id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** 治理态落库（V268 lifecycle_state 唯一权威列；0 行受影响 ⇒ 调用方 fail-loud）。 */
    public int updateLifecycleState(String id, String lifecycleState, String operator) {
        return jdbcTemplate.update(
            "UPDATE ecos_cognitive_hypothesis SET lifecycle_state = ?, update_by = ?, update_time = CURRENT_TIMESTAMP " +
            "WHERE id = ? AND is_deleted = 0",
            lifecycleState, operator, id);
    }

    // ── 《认知域与智能域补充详细设计 v1.0》§9 增量（只增不改上方既有方法）──

    /**
     * 按指标引用批量取假设（§9.2 步骤 5「关联当时使用的 Hypothesis」）。
     *
     * <p>只读、显式列清单（IR04）、{@code LIMIT 200} 有界（禁无界扫描）。
     * 空入参直接返回空列表，禁拼出 {@code IN ()} 非法 SQL。</p>
     */
    public List<Map<String, Object>> listByMetricRefs(List<String> metricRefs) {
        if (metricRefs == null || metricRefs.isEmpty()) {
            return List.of();
        }
        List<String> distinct = metricRefs.stream()
                .filter(m -> m != null && !m.isBlank())
                .distinct()
                .toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(distinct.size(), "?"));
        return jdbcTemplate.queryForList(
            "SELECT " + HYPOTHESIS_COLUMNS + " FROM ecos_cognitive_hypothesis " +
            "WHERE is_deleted = 0 AND metric_ref IN (" + placeholders + ") " +
            "ORDER BY update_time DESC, id LIMIT 200",
            distinct.toArray());
    }
}
