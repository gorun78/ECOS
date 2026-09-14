package com.chinacreator.gzcm.engine.cognitive2.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认知证据存储层（PMO-59 P2a / ADR-9 心智层）— 表 {@code ecos_cognitive_evidence}（V127）。
 *
 * <p>数据访问风格跟齐 cognitive 既有实现（{@link ModelRegistryService}）：JdbcTemplate 显式列名，
 * 不使用 MyBatis Mapper（cognitive 模块无 mapper 包既有基建，gateway @MapperScan 亦未登记 cognitive 包）。
 * 铁律合规：0 个 {@code SELECT *}，0 个无条件 UPDATE，全部查询带 {@code is_deleted = 0} 逻辑删除过滤。</p>
 *
 * <p>JSONB 交互：列显式 {@code ?::jsonb} 绑定，读取经 {@link #parseJsonListText}/{@link #parseJsonObjectText}
 * 归一为字符串文本（对齐 common-api {@code EvidenceRecordVO} 的 String 契约，避免 PGobject 依赖漂移）。</p>
 */
@Service
public class EvidenceStore {

    private static final Logger log = LoggerFactory.getLogger(EvidenceStore.class);

    /** 全部列显式清单（禁止 SELECT *；与 V127 DDL 逐列一致） */
    private static final String EVIDENCE_COLUMNS =
            "id, evidence_code, tenant_scope, source_type, source_ref, blob, confidence, " +
            "is_conflict, refuting_evidence_ids, effective_time, expire_time, status, " +
            "create_time, update_time, create_by, update_by";

    /** 证据来源类型白名单（外部方案层1感知接入层口径） */
    public static final List<String> SOURCE_TYPES =
            Arrays.asList("SYSTEM_DATA", "NEWS", "EXPERT", "PIPELINE");

    /** 证据状态白名单 */
    public static final List<String> STATUSES =
            Arrays.asList("ACTIVE", "CONFLICTED", "ARCHIVED");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public EvidenceStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * 登记一条结构化证据（服务端生成 id 与审计列）。
     *
     * @param id            服务端生成的主键（cog_ev_ 前缀）
     * @param evidenceCode  业务唯一键（幂等）
     * @param tenantScope   租户/域（null 落 default）
     * @param sourceType    来源类型（白名单外由 Store 拒绝）
     * @param sourceRef     来源定位
     * @param blob          结构化事实载荷 JSON 文本
     * @param confidence    可信度 0~1
     * @param isConflict    冲突标记
     * @param refutingIds   冲突对方证据 id JSON 数组文本
     * @param effectiveTime 生效时间
     * @param expireTime    失效时间
     * @param status        落库状态（ACTIVE / CONFLICTED）
     */
    public void insert(String id, String evidenceCode, String tenantScope, String sourceType,
                       String sourceRef, String blob, BigDecimal confidence, boolean isConflict,
                       String refutingIds, LocalDateTime effectiveTime, LocalDateTime expireTime,
                       String status) {
        jdbc.update(
            "INSERT INTO ecos_cognitive_evidence (id, evidence_code, tenant_scope, source_type, source_ref, " +
            "blob, confidence, is_conflict, refuting_evidence_ids, effective_time, expire_time, status, " +
            "create_by, update_by, is_deleted) " +
            "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?, ?, ?, 'system', 'system', 0)",
            id, evidenceCode, tenantScope == null || tenantScope.isBlank() ? "default" : tenantScope,
            sourceType, sourceRef, blob, confidence, isConflict, refutingIds, effectiveTime, expireTime, status);
        log.info("认知证据落库: id={} code={} sourceType={} confidence={} status={}",
                id, evidenceCode, sourceType, confidence, status);
    }

    /** 校验业务唯一键是否已存在（含逻辑删除行，唯一索引不可复用）。 */
    public boolean existsByCode(String evidenceCode) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(1) FROM ecos_cognitive_evidence WHERE evidence_code = ?", Integer.class, evidenceCode);
        return count != null && count > 0;
    }

    /** 证据详情（未删除）；无记录返回 null。 */
    public Map<String, Object> findById(String id) {
        try {
            return jdbc.queryForMap(
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
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /**
     * 冲突标记联动：把 newId 追加进对方证据的 refuting_evidence_ids 并置 CONFLICTED（幂等：已含则跳过）。
     *
     * @param oppositeId 冲突对方证据 id
     * @param newId      本次登记的新证据 id（追加为对方 refuting 一员）
     * @return 是否真的发生了更新
     */
    public boolean markRefuted(String oppositeId, String newId) {
        Map<String, Object> row = findById(oppositeId);
        if (row == null) {
            return false;
        }
        String refutingText = String.valueOf(row.get("refuting_evidence_ids"));
        if (refutingText.contains("\"" + newId + "\"")) {
            return false;
        }
        String updated = addToJsonArrayText(refutingText, newId);
        return jdbc.update(
            "UPDATE ecos_cognitive_evidence SET refuting_evidence_ids = ?::jsonb, status = ?, " +
            "update_time = ? WHERE id = ? AND is_deleted = 0",
            updated, "CONFLICTED", Timestamp.valueOf(LocalDateTime.now()), oppositeId) > 0;
    }

    /**
     * 同 fact+metric 事实匹配查找（新证据登记时的自动冲突检测输入）：
     * 未删除、ACTIVE/CONFLICTED、blob 中 fact 与 metric 键均相等、value 不相等的证据 id 列表。
     */
    public List<String> findConflictingIds(String fact, String metric, String factValue) {
        if (fact == null || fact.isBlank()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> candidates = jdbc.queryForList(
            "SELECT id, blob FROM ecos_cognitive_evidence " +
            "WHERE is_deleted = 0 AND status IN ('ACTIVE','CONFLICTED') AND blob ->> 'fact' = ? " +
            "ORDER BY create_time DESC",
            fact);
        List<String> conflicting = new ArrayList<>();
        for (Map<String, Object> row : candidates) {
            Map<String, Object> blob = parseJsonObjectText(String.valueOf(row.get("blob")));
            String candidateMetric = metric == null ? null : asString(blob.get("metric"));
            if (metric == null ? candidateMetric != null : !metric.equals(candidateMetric)) {
                continue;
            }
            String candidateValue = asString(blob.get("value"));
            if (candidateValue != null && candidateValue.equals(factValue)) {
                continue; // 数值一致 = 相互印证，非冲突
            }
            conflicting.add(String.valueOf(row.get("id")));
        }
        return conflicting;
    }

    // ── row → VO 转换（三 Service 共用的行映射基元） ──

    /** 行 → 结构化字段映射（evidence 专用，JSONB 列归一为文本）。 */
    public Map<String, Object> toNormalizedRow(Map<String, Object> row) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>(row);
        normalized.put("blob", String.valueOf(row.getOrDefault("blob", "{}")));
        normalized.put("refutingEvidenceIds", parseJsonListText(String.valueOf(row.get("refuting_evidence_ids"))));
        normalized.put("confidence", ((Number) row.get("confidence")).doubleValue());
        return normalized;
    }

    /** JSONB 数组列 → VO 消费的元素列表（如证据 id 引用列表）。 */
    public List<String> parseJsonListText(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<Object> list = objectMapper.readValue(jsonText, new TypeReference<List<Object>>() {});
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
            return result;
        } catch (Exception e) {
            log.warn("JSON 数组列解析失败, 按空列表兜底: err={}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** JSONB 对象列 → 字段 Map（blob 解析，解析失败返空 Map 不抛）。 */
    public Map<String, Object> parseJsonObjectText(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(jsonText, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            log.warn("JSON 对象列解析失败, 按空对象兜底: err={}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /** JSONB 对象数组列 → 元素 Map 列表（如 belief.distribution 分布点，解析失败返空列表不抛）。 */
    public List<Map<String, Object>> parseJsonObjectListText(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(jsonText, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("JSON 对象数组列解析失败, 按空列表兜底: err={}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String addToJsonArrayText(String jsonText, String element) {
        try {
            List<Object> list = objectMapper.readValue(jsonText, new TypeReference<List<Object>>() {});
            list.add(element);
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("refuting 数组解析失败, 重建单元素数组: err={}", e.getMessage());
            return "[\"" + element + "\"]";
        }
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** 取 Map 中指定键的字符串值（不存在返回 null）。 */
    public String fieldString(Map<String, Object> row, String key) {
        Object v = row == null ? null : row.get(key);
        return v == null ? null : String.valueOf(v);
    }

    /** 取 Map 中指定键的布尔值（PG boolean; 不存在返回 false）。 */
    public boolean fieldBoolean(Map<String, Object> row, String key) {
        Object v = row == null ? null : row.get(key);
        return v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
    }

    /** 取 Map 中指定键的时间值（PG timestamp → LocalDateTime; 不存在返回 null）。 */
    public LocalDateTime fieldTime(Map<String, Object> row, String key) {
        Object v = row == null ? null : row.get(key);
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt;
        }
        return null;
    }
}
