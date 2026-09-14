package com.chinacreator.gzcm.engine.cognitive2.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 不确定性判断存储层（PMO-59 P2a / ADR-9 心智层 P 库）— 表 {@code ecos_cognitive_belief}（V129）。
 *
 * <p>数据访问风格跟齐 cognitive 既有实现（JdbcTemplate 显式列名，见 {@link EvidenceStore} 类注）。
 * 铁律合规：0 个 {@code SELECT *}，0 个无条件 UPDATE，全部查询带 {@code is_deleted = 0}。</p>
 *
 * <p>版本语义：同一 {@code variable_name + domain} 下 version 递增（唯一索引
 * {@code uniq_ecos_cognitive_belief_variable_ver} 兜底）；查询强制带 domain（Phase 1 验收记录残留风险 4）。</p>
 */
@Service
public class BeliefStore {

    private static final Logger log = LoggerFactory.getLogger(BeliefStore.class);

    /** 全部列显式清单（禁止 SELECT *；与 V129 DDL 逐列一致） */
    static final String BELIEF_COLUMNS =
            "id, variable_name, tenant_scope, domain, distribution, version, snapshot_version, " +
            "is_manual_override, override_reason, last_evidence_id, status, " +
            "create_time, update_time, create_by, update_by";

    /** 状态白名单 */
    public static final List<String> STATUSES = List.of("ACTIVE", "ARCHIVED");

    private final JdbcTemplate jdbc;

    public BeliefStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 落库一条不确定性判断（指定 version；由 Service 层决定 version=max+1 或 first=1）。
     *
     * @param id                服务端生成主键（cog_blf_ 前缀）
     * @param variableName      不可观测经营变量名（必填）
     * @param tenantScope       租户/域（null 落 default）
     * @param domain            业务域（必填）
     * @param distributionJson  有限离散概率分布 JSON 文本（prob 和=1，Service 层强校验后传入）
     * @param version           分布版本
     * @param lastEvidenceId    触发 evidence id（可 null）
     * @param isManualOverride  人工覆写标记
     * @param overrideReason    覆写理由（非覆写为 null）
     * @param status            ACTIVE / ARCHIVED
     */
    public void insert(String id, String variableName, String tenantScope, String domain,
                       String distributionJson, int version, String lastEvidenceId,
                       boolean isManualOverride, String overrideReason, String status) {
        jdbc.update(
            "INSERT INTO ecos_cognitive_belief (id, variable_name, tenant_scope, domain, distribution, " +
            "version, snapshot_version, is_manual_override, override_reason, last_evidence_id, status, " +
            "create_by, update_by, is_deleted) " +
            "VALUES (?, ?, ?, ?, ?::jsonb, ?, NULL, ?, ?, ?, ?, 'system', 'system', 0)",
            id, variableName, tenantScope == null || tenantScope.isBlank() ? "default" : tenantScope,
            domain, distributionJson, version, isManualOverride, overrideReason, lastEvidenceId, status);
        log.info("不确定性判断落库: id={} variable={} domain={} version={} override={}",
                id, variableName, domain, version, isManualOverride);
    }

    /** 某变量某域当前最大 version（无记录返回 0）。 */
    public int maxVersion(String variableName, String domain) {
        Integer max = jdbc.queryForObject(
            "SELECT COALESCE(MAX(version), 0) FROM ecos_cognitive_belief " +
            "WHERE variable_name = ? AND domain = ? AND is_deleted = 0",
            Integer.class, variableName, domain);
        return max == null ? 0 : max;
    }

    /**
     * 当前生效版本（最新版本）；domain 必填（变量在跨 domain 下重名时不可靠单查）。
     */
    public Map<String, Object> findLatest(String variableName, String domain) {
        try {
            return jdbc.queryForMap(
                "SELECT " + BELIEF_COLUMNS + " FROM ecos_cognitive_belief " +
                "WHERE variable_name = ? AND domain = ? AND is_deleted = 0 " +
                "ORDER BY version DESC LIMIT 1",
                variableName, domain);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** 按主键查询（未删除）；无记录返回 null。 */
    public Map<String, Object> findById(String id) {
        try {
            return jdbc.queryForMap(
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
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /**
     * 行 → VO 消费映射：distribution JSONB 归一为 List&lt;Map&gt;（每项 {outcome, prob(double)}），
     * 对齐 common-api {@code BeliefDistributionVO.OutcomeProb}。
     */
    public Map<String, Object> toNormalizedRow(Map<String, Object> row, EvidenceStore evidenceStore) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>(row);
        normalized.put("distribution", parseDistribution(String.valueOf(row.get("distribution")), evidenceStore));
        normalized.put("version", ((Number) row.get("version")).intValue());
        normalized.put("manualOverride", evidenceStore.fieldBoolean(row, "is_manual_override"));
        normalized.put("lastEvidenceId", evidenceStore.fieldString(row, "last_evidence_id"));
        return normalized;
    }

    /** 分布 JSONB 文本 → 归一化分布点列表（解析失败返空列表不抛）。 */
    public List<Map<String, Object>> parseDistribution(String distributionJson, EvidenceStore evidenceStore) {
        List<Map<String, Object>> items = evidenceStore.parseJsonObjectListText(distributionJson);
        List<Map<String, Object>> normalized = new ArrayList<>(items.size());
        for (Map<String, Object> item : items) {
            LinkedHashMap<String, Object> point = new LinkedHashMap<>();
            point.put("outcome", String.valueOf(item.get("outcome")));
            Object prob = item.get("prob");
            point.put("prob", prob instanceof Number n ? n.doubleValue() : 0.0d);
            normalized.add(point);
        }
        return normalized;
    }
}
