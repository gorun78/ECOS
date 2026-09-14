package com.chinacreator.gzcm.engine.cognitive2.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认知假设存储层（PMO-59 P2a / ADR-9 心智层 H 库）— 表 {@code ecos_cognitive_hypothesis}（V128）。
 *
 * <p>数据访问风格跟齐 cognitive 既有实现（JdbcTemplate 显式列名，见 {@link EvidenceStore} 类注）。
 * 铁律合规：0 个 {@code SELECT *}，0 个无条件 UPDATE，全部查询带 {@code is_deleted = 0}。</p>
 */
@Service
public class HypothesisStore {

    private static final Logger log = LoggerFactory.getLogger(HypothesisStore.class);

    /** 全部列显式清单（禁止 SELECT *；与 V128 DDL 逐列一致） */
    static final String HYPOTHESIS_COLUMNS =
            "id, hypothesis_code, tenant_scope, statement, domain, metric_ref, evidence_ids, " +
            "is_valid, invalid_at, invalid_reason, status, " +
            "create_time, update_time, create_by, update_by";

    /** 假设状态白名单 */
    public static final List<String> STATUSES = List.of("VALID", "INVALIDATED", "ARCHIVED");

    private final JdbcTemplate jdbc;

    public HypothesisStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 注册一条假设（初始 VALID / is_valid=true / 审计列服务端置位）。
     *
     * @param id             服务端生成主键（cog_hyp_ 前缀）
     * @param code           业务唯一键（幂等）
     * @param tenantScope    租户/域（null 落 default）
     * @param statement      假设陈述（业务语言，必填）
     * @param domain         业务域
     * @param metricRef      关联经营变量
     * @param evidenceIds    支撑证据 id 列表 JSON 数组文本
     */
    public void insert(String id, String code, String tenantScope, String statement,
                       String domain, String metricRef, String evidenceIds) {
        jdbc.update(
            "INSERT INTO ecos_cognitive_hypothesis (id, hypothesis_code, tenant_scope, statement, domain, " +
            "metric_ref, evidence_ids, is_valid, status, create_by, update_by, is_deleted) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, TRUE, 'VALID', 'system', 'system', 0)",
            id, code, tenantScope == null || tenantScope.isBlank() ? "default" : tenantScope,
            statement, domain, metricRef, evidenceIds);
        log.info("认知假设注册: id={} code={} domain={} metricRef={}", id, code, domain, metricRef);
    }

    /** 校验业务唯一键是否已存在（含逻辑删除行）。 */
    public boolean existsByCode(String code) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(1) FROM ecos_cognitive_hypothesis WHERE hypothesis_code = ?", Integer.class, code);
        return count != null && count > 0;
    }

    /** 假设详情（未删除）；无记录返回 null。 */
    public Map<String, Object> findById(String id) {
        try {
            return jdbc.queryForMap(
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
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 状态切换（本 P2a 单只做状态切换 + 失效时间写；失效检测联动逻辑 P2b 实现）。 */
    public boolean updateStatus(String id, String status, boolean isValid,
                                LocalDateTime invalidAt, String invalidReason) {
        return jdbc.update(
            "UPDATE ecos_cognitive_hypothesis SET status = ?, is_valid = ?, invalid_at = ?, " +
            "invalid_reason = ?, update_time = ? WHERE id = ? AND is_deleted = 0",
            status, isValid, invalidAt, invalidReason, Timestamp.valueOf(LocalDateTime.now()), id) > 0;
    }

    /**
     * 行 → VO 消费映射（evidence_ids JSONB 归一为元素列表，对齐 common-api {@code HypothesisVO}）。
     */
    public Map<String, Object> toNormalizedRow(Map<String, Object> row, EvidenceStore evidenceStore) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>(row);
        normalized.put("evidenceIds", evidenceStore.parseJsonListText(String.valueOf(row.get("evidence_ids"))));
        normalized.put("valid", evidenceStore.fieldBoolean(row, "is_valid"));
        normalized.put("invalidAt", evidenceStore.fieldTime(row, "invalid_at"));
        return normalized;
    }
}
