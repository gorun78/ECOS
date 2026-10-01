package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * B2-b 数据访问收口（架构铁律 §2.5 #1 / IR06）：{@code EvidenceStore}（PMO-59 P2a /
 * ADR-9 心智层）内联的 {@code ecos_cognitive_evidence}（V127）SQL 落点。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与原 Store 逐字节一致；
 * JSONB 解析 / 行归一化等混在 Store 的共用基元（{@code parseJsonListText} 等）留在 Store 层。</p>
 */
@Repository
public class EvidenceDao extends JdbcTemplateDaoSupport {

    /** 全部列显式清单（禁止 SELECT *；与 V127 DDL 逐列一致） */
    static final String EVIDENCE_COLUMNS =
            "id, evidence_code, tenant_scope, source_type, source_ref, blob, confidence, " +
            "is_conflict, refuting_evidence_ids, effective_time, expire_time, status, " +
            "create_time, update_time, create_by, update_by";

    /** 登记一条结构化证据（审计列服务端置位）。 */
    public void insert(String id, String evidenceCode, String tenantScope, String sourceType,
                       String sourceRef, String blob, BigDecimal confidence, boolean isConflict,
                       String refutingIds, LocalDateTime effectiveTime, LocalDateTime expireTime,
                       String status) {
        jdbcTemplate.update(
            "INSERT INTO ecos_cognitive_evidence (id, evidence_code, tenant_scope, source_type, source_ref, " +
            "blob, confidence, is_conflict, refuting_evidence_ids, effective_time, expire_time, status, " +
            "create_by, update_by, is_deleted) " +
            "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?, ?, ?, 'system', 'system', 0)",
            id, evidenceCode, tenantScope == null || tenantScope.isBlank() ? "default" : tenantScope,
            sourceType, sourceRef, blob, confidence, isConflict, refutingIds, effectiveTime, expireTime, status);
    }

    /** 校验业务唯一键是否已存在（含逻辑删除行，唯一索引不可复用）。 */
    public boolean existsByCode(String evidenceCode) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM ecos_cognitive_evidence WHERE evidence_code = ?", Integer.class, evidenceCode);
        return count != null && count > 0;
    }

    /** 证据详情（未删除）；无记录返回 null。 */
    public Map<String, Object> findById(String id) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT " + EVIDENCE_COLUMNS + " FROM ecos_cognitive_evidence WHERE id = ? AND is_deleted = 0",
                id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** 证据列表（未删除；status/sourceType 可选过滤，create_time 倒序）。 */
    public List<Map<String, Object>> list(String status, String sourceType) {
        StringBuilder sql = new StringBuilder("SELECT " + EVIDENCE_COLUMNS +
            " FROM ecos_cognitive_evidence WHERE is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status.toUpperCase());
        }
        if (sourceType != null && !sourceType.isBlank()) {
            sql.append(" AND source_type = ?");
            args.add(sourceType.toUpperCase());
        }
        sql.append(" ORDER BY create_time DESC, id");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    /**
     * 冲突标记联动：把 newId 追加进对方证据的 refuting_evidence_ids 并置 CONFLICTED。
     *
     * @param oppositeId 冲突对方证据 id
     * @param updated    追加后的 refuting 数组 JSON 文本（Service 层预计算）
     * @return 是否真的发生了更新
     */
    public boolean markRefuted(String oppositeId, String updated, LocalDateTime updateTime) {
        return jdbcTemplate.update(
            "UPDATE ecos_cognitive_evidence SET refuting_evidence_ids = ?::jsonb, status = ?, " +
            "update_time = ? WHERE id = ? AND is_deleted = 0",
            updated, "CONFLICTED", updateTime, oppositeId) > 0;
    }

    /** 同 fact 事实候选（未删除、ACTIVE/CONFLICTED，blob->>'fact' 等值，create_time 倒序）。 */
    public List<Map<String, Object>> findConflictCandidates(String fact) {
        return jdbcTemplate.queryForList(
            "SELECT id, blob FROM ecos_cognitive_evidence " +
            "WHERE is_deleted = 0 AND status IN ('ACTIVE','CONFLICTED') AND blob ->> 'fact' = ? " +
            "ORDER BY create_time DESC",
            fact);
    }
}
