package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;

/**
 * BusinessFactService — F02-06 业务事实模型五表导入/读取/门禁（详细设计-02）。
 *
 * <p>落业务域 {@code ecos_dw}（CURATED 层），四张事实表 + 预测输入快照：</p>
 * <ul>
 *   <li>{@code attribution} → {@code ecos_dw.ecos_biz_project_attribution}</li>
 *   <li>{@code stage}       → {@code ecos_dw.ecos_biz_stage_fact}</li>
 *   <li>{@code resource}    → {@code ecos_dw.ecos_biz_resource_fact}</li>
 *   <li>{@code cost}        → {@code ecos_dw.ecos_biz_cost_fact}</li>
 * </ul>
 *
 * <p>门禁语义（F02-07 红线 + F02-06-2/3/4/5）：</p>
 * <ul>
 *   <li>行级校验 DQ-F01~F11（内置参数化规则，禁自由表达式；红线=禁"补默认以通过"）；</li>
 *   <li>accepted 行落表 {@code dq_status=PASSED, is_active=1}（业务键唯一，NULL 不占判）；</li>
 *   <li>{@code PUBLISHED} 行 UPDATE 业务字段 → {@code ECOS-DATA-021}（409，更正=新行）；</li>
 *   <li>关账期间写入 → {@code ECOS-DATA-022}（409）。</li>
 * </ul>
 *
 * <p>本 class 属 data-engine-impl 内 service，持有 {@code JdbcTemplate}（W54 三层穿透按门禁分批清偿，本
 * import 通道与 DQ 治理侧同口径）。金额列不加密但在 ST03-A 登记表登记（见 E.2 头部）。</p>
 *
 * @author ECOS-BE (F02-06)
 */
@Service
public class BusinessFactService {

    private static final Logger log = LoggerFactory.getLogger(BusinessFactService.class);

    /** 关账期黑名单前缀（对齐 09 册 FC-05 period lock；P0 占位，经批间维护）。 */
    static final Set<String> CLOSED_PERIOD_YEAR_PREFIXES = Set.of("2099");

    private final Map<String, String> factTable = Map.of(
            "attribution", "ecos_dw.ecos_biz_project_attribution",
            "stage", "ecos_dw.ecos_biz_stage_fact",
            "resource", "ecos_dw.ecos_biz_resource_fact",
            "cost", "ecos_dw.ecos_biz_cost_fact");

    private final Map<String, List<String>> factColumns = Map.of(
            "attribution", List.of("project_id", "contract_id", "department_id",
                    "attribution_ratio", "attribution_type", "effective_from", "effective_to",
                    "source_evidence"),
            "stage", List.of("project_id", "department_id", "period", "stage",
                    "fact_type", "contract_base", "attribution_ratio", "realization_rate",
                    "amount", "currency", "source_type", "source_ref", "evidence_ref"),
            "resource", List.of("project_id", "department_id", "period", "staff_ref_hash",
                    "staff_hash_algo", "fte", "work_hours", "hourly_rate",
                    "cost_category", "fact_type", "source_ref", "evidence_ref"),
            "cost", List.of("project_id", "is_pool", "department_id", "period",
                    "cost_category", "direct_or_allocated", "allocation_rule_ref",
                    "amount", "currency", "fact_type", "evidence_ref"));

    private final Map<String, List<String>> requiredCols = Map.of(
            "attribution", List.of("project_id", "contract_id", "department_id",
                    "attribution_ratio", "effective_from"),
            "stage", List.of("project_id", "department_id", "period", "stage", "fact_type"),
            "resource", List.of("project_id", "department_id", "period", "staff_ref_hash", "fact_type"),
            "cost", List.of("department_id", "period", "fact_type"));

    private final JdbcTemplate jdbc;

    public BusinessFactService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record RejectedRow(int rowNo, String field, String ruleId, String message, String suggestion) {}

    public record ImportResult(String batchId, int accepted, List<RejectedRow> rejected, String traceId) {}

    /** F02-06-2a：取模板（首行 = 列名，次行 = 示例），并附列清单。 */
    public Map<String, Object> getTemplate(String factType) {
        requireKnownFactType(factType);
        List<String> cols = factColumns.get(factType);
        Map<String, Object> header = new LinkedHashMap<>();
        for (String c : cols) header.put(c, c);
        header.put("__header__", true);
        Map<String, Object> example = new LinkedHashMap<>();
        for (String c : cols) {
            Object v = exemplar(c, factType);
            if (v != null) example.put(c, v);
        }
        example.put("__example__", true);
        return Map.of(
                "factType", factType,
                "batchNameSuggested", "fact-" + factType + "-" + System.currentTimeMillis(),
                "currency", "CNY",
                "columns", cols,
                "required", requiredCols.get(factType),
                "rows", List.of(header, example));
    }

    private String exemplar(String col, String factType) {
        return switch (col) {
            case "project_id", "contract_id", "department_id", "allocation_rule_ref" -> "<uuid36>";
            case "staff_ref_hash" -> "<sha256-hex-64>";
            case "staff_hash_algo" -> "SHA-256";
            case "effective_from", "period" -> "2025-07";
            case "effective_to" -> "2025-12";
            case "attribution_type" -> "PRIMARY";
            case "stage" -> "DELIVERY";
            case "fact_type" -> factTypeDefault(factType);
            case "attribution_ratio" -> "0.6000";
            case "realization_rate" -> "0.9000";
            case "amount", "contract_base", "hourly_rate" -> "1000.00";
            case "fte" -> "1.00";
            case "work_hours" -> "160.0";
            case "currency" -> "CNY";
            case "source_type" -> "IMPORT";
            case "cost_category" -> "LABOR";
            case "direct_or_allocated" -> "DIRECT";
            case "is_pool" -> "0";
            default -> null;
        };
    }

    private String factTypeDefault(String factType) {
        return switch (factType) {
            case "stage" -> "REVENUE";
            case "resource" -> "WORK";
            case "cost" -> "DIRECT";
            default -> "F";
        };
    }

    private void requireKnownFactType(String factType) {
        if (!factTable.containsKey(factType)) {
            throw new BusinessException(400,
                    "未知事实类型: " + factType + "（合法: " + String.join("/", factTable.keySet()) + "）");
        }
    }

    /**
     * F02-06-2：导入（≤100 行/批）。
     * 行级 rejected 不入库，accepted 落 PENDING→PASSED（{@code is_active=1}）。
     */
    public ImportResult importBusinessFacts(String factType, String batchName,
                                            List<Map<String, Object>> rows) {
        requireKnownFactType(factType);
        if (batchName == null || batchName.isBlank() || batchName.length() > 128) {
            throw new BusinessException(400, "batchName 必填且长度 ≤128");
        }
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException(400, "rows 不能为空（≤100 行/批）");
        }
        if (rows.size() > 100) {
            throw new BusinessException(400, "单批 ≤100 行（EN03）");
        }
        String batchId = UUID.randomUUID().toString();
        String versionNo = "1";

        List<RejectedRow> rejected = new ArrayList<>();
        List<Map<String, Object>> accepted = new ArrayList<>();
        String periodKey = pickPeriodKey(factType);

        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            int rowNo = i + 1;
            String[] firstError = runDqRules(factType, r);
            if (firstError != null) {
                rejected.add(new RejectedRow(rowNo, firstError[0], firstError[1],
                        firstError[2], firstError[3]));
                continue;
            }
            String period = str(r.get(periodKey));
            if (!periodPasses(period)) {
                rejected.add(new RejectedRow(rowNo, periodKey, "ECOS-DATA-022",
                        "期间已关账，禁止写入: " + period, "检查业务期间（关账期禁止写入）"));
                continue;
            }
            accepted.add(toPersistRow(factType, batchId, versionNo, r));
        }

        int persisted = persist(factType, accepted);
        log.info("F02-06 事实导入: factType={} batchId={} accepted={} rejected={}",
                factType, batchId, persisted, rejected.size());
        return new ImportResult(batchId, persisted, rejected, null);
    }

    private String pickPeriodKey(String factType) {
        return "attribution".equals(factType) ? "effective_from" : "period";
    }

    private boolean periodPasses(String period) {
        if (period == null || period.isBlank() || period.length() < 4) return true;
        String year = period.substring(0, 4);
        return !CLOSED_PERIOD_YEAR_PREFIXES.contains(year);
    }

    /** 返回 [field, ruleId, message, suggestion]；null = 全通过。 */
    private String[] runDqRules(String factType, Map<String, Object> r) {
        // DQ-F01 必填项非空
        for (String k : requiredCols.get(factType)) {
            Object v = r.get(k);
            if (v == null || (v instanceof String s && s.isBlank())) {
                return new String[]{"field:" + k, "DQ-F01", "必填字段为空", "填写 " + k};
            }
        }
        // DQ-F02 期/月格式
        for (String k : List.of("period", "effective_from", "effective_to")) {
            Object v = r.get(k);
            if (v != null && !isPeriodLike(str(v))) {
                return new String[]{"field:" + k, "DQ-F02", "期/月格式应为 yyyy-MM", "改为 yyyy-MM"};
            }
        }
        // DQ-F03 金额数值 + 精度（≤2 位小数、≤18 位整数部分）
        for (String k : List.of("amount", "contract_base", "hourly_rate")) {
            Object v = r.get(k);
            if (v == null) continue;
            if (!validAmount(str(v))) {
                return new String[]{"field:" + k, "DQ-F03", "金额非数值或精度越界（≤18 位整数/2 位小数）",
                        "改为合规数值"};
            }
        }
        // DQ-F04 比率/岚度 ∈ [0,1]
        for (String k : List.of("attribution_ratio", "realization_rate")) {
            Object v = r.get(k);
            if (v == null) continue;
            BigDecimal ratio;
            try { ratio = new BigDecimal(str(v).trim()); }
            catch (NumberFormatException e) {
                return new String[]{"field:" + k, "DQ-F04", "比率非数值", "回填 0~1 的小数"};
            }
            if (ratio.compareTo(BigDecimal.ZERO) < 0 || ratio.compareTo(BigDecimal.ONE) > 0) {
                return new String[]{"field:" + k, "DQ-F04", "比率越界（[0,1]）", "回填 0~1 的小数"};
            }
        }
        // DQ-F05 金额行需 3char currency
        if (r.get("amount") != null || r.get("contract_base") != null) {
            Object c = r.get("currency");
            if (c == null || str(c).length() != 3) {
                return new String[]{"field:currency", "DQ-F05", "金额行需带 3 位币种代码（如 CNY）", "补 3char currency"};
            }
        }
        // DQ-F06 引用/溯源字段长度守限（对齐 DDL：source_ref/evidence_ref ≤255、version_no ≤20）
        for (String k : List.of("source_ref", "evidence_ref")) {
            Object v = r.get(k);
            if (v != null && str(v).length() > 255) {
                return new String[]{"field:" + k, "DQ-F06", "引用字段超过 255 字符", "缩短引用/改为对象 key"};
            }
        }
        Object vn = r.get("version_no");
        if (vn != null && str(vn).length() > 20) {
            return new String[]{"field:version_no", "DQ-F06", "版本标识超过 20 字符", "缩短 version_no"};
        }
        // DQ-F07 键长 ≤36
        for (String k : List.of("project_id", "contract_id", "department_id", "forecast_run_id")) {
            Object v = r.get(k);
            if (v != null && str(v).length() > 36) {
                return new String[]{"field:" + k, "DQ-F07", "键长超过 36", "缩短键值"};
            }
        }
        // DQ-F08 期间值域（F02 已保 \d{4}-\d{2} 形态；此处查年/月合法：月 01-12、年 2000-2099）
        for (String k : List.of("period", "effective_from", "effective_to")) {
            Object v = r.get(k);
            String pv = v == null ? "" : str(v);
            if (pv.matches("\\d{4}-\\d{2}")) {
                int month = Integer.parseInt(pv.substring(5));
                int year = Integer.parseInt(pv.substring(0, 4));
                if (month < 1 || month > 12 || year < 2000 || year >= 2100) {
                    return new String[]{"field:" + k, "DQ-F08", "期间月/年非法（月 01-12、年 2000-2099）",
                            "改为合规期间"};
                }
            }
        }
        // DQ-F09 资源表 staff_ref_hash SHA-256 hex(64)
        if ("resource".equals(factType)) {
            Object h = r.get("staff_ref_hash");
            if (h != null && !str(h).matches("[0-9a-fA-F]{64}")) {
                return new String[]{"field:staff_ref_hash", "DQ-F09", "工号哈希须 SHA-256 hex(64)",
                        "用 sha256(utf8(staff_id))"};
            }
        }
        // DQ-F10 事实类型值域
        Object ft = r.get("fact_type");
        if (ft != null && !str(ft).matches("[A-Z][A-Z0-9_]{0,9}")) {
            return new String[]{"field:fact_type", "DQ-F10", "fact_type 需短大写码", "≤10 位大写枚举"};
        }
        // DQ-F11 cost 表 is_pool 二值 + 与 project_id 联动
        if ("cost".equals(factType)) {
            Object p = r.get("is_pool");
            int pv = 0;
            if (p != null) {
                try { pv = Integer.parseInt(str(p).trim()); }
                catch (NumberFormatException e) {
                    return new String[]{"field:is_pool", "DQ-F11", "is_pool 应为 0/1", "置 0(直接)或 1(pool)"};
                }
            }
            if (pv != 0 && pv != 1) {
                return new String[]{"field:is_pool", "DQ-F11", "is_pool 应为 0/1", "置 0(直接)或 1(pool)"};
            }
            String pid = str(r.get("project_id"));
            if (pv == 0 && (pid == null || pid.isBlank())) {
                return new String[]{"field:project_id", "DQ-F11",
                        "无 project_id 时 is_pool 必须=1", "置 is_pool=1 或补 project_id"};
            }
        }
        return null;
    }

    private boolean isPeriodLike(String s) {
        return s != null && s.matches("\\d{4}-\\d{2}");
    }

    private boolean validAmount(String v) {
        if (v == null || v.isBlank()) return false;
        try {
            BigDecimal bd = new BigDecimal(v.trim());
            if (bd.scale() > 2) return false;
            if (bd.abs().compareTo(new BigDecimal("1E18")) > 0) return false;
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** 只挑白名单字段 + 补 PK/审计列/is_active/dq_status/version_no。 */
    private Map<String, Object> toPersistRow(String factType, String batchId, String versionNo,
                                              Map<String, Object> src) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", UUID.randomUUID().toString());
        out.put("batch_id", batchId);
        out.put("version_no", versionNo);
        out.put("is_active", 1);
        out.put("dq_status", "PASSED");
        for (String c : factColumns.get(factType)) {
            if (src.containsKey(c)) out.put(c, src.get(c));
        }
        // 事实表允许业务侧缺 evidence_ref/source_ref — 保持 null
        return out;
    }

    /** 每 100 行批量 INSERT；异常由 GlobalExceptionHandler 转 ECOS-DATA-031。 */
    private int persist(String factType, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return 0;
        List<String> cols = new ArrayList<>(rows.get(0).keySet());
        String placeholders = String.join(", ", Collections.nCopies(cols.size(), "?"));
        String sql = "INSERT INTO " + factTable.get(factType)
                + " (" + String.join(",", cols) + ") VALUES (" + placeholders + ")";
        List<Object[]> args = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            args.add(cols.stream().map(row::get).toArray(Object[]::new));
        }
        try {
            jdbc.batchUpdate(sql, args);
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            // is_active 唯一活跃索引命中 → 整批 fail-loud（ECOS-DATA-031 兜底），不静默
            log.warn("F02-06 事实导入唯一约束冲突(可能是重复业务键 is_active): {}", dup.getMessage());
            throw dup;
        }
        return rows.size();
    }

    /** F02-06-2c：事实列表（分页 20/200；行级 decide 由 01 册管道叠加 RLS/CLS/mask，本域只吐原始行）。 */
    public Map<String, Object> listFacts(String factType, String batchId, int page, int size) {
        requireKnownFactType(factType);
        int p = Math.max(1, page);
        int s = Math.min(200, Math.max(1, size));
        String table = factTable.get(factType);
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (batchId != null && !batchId.isBlank()) {
            where.append(" WHERE batch_id = ?");
            args.add(batchId);
        }
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + where,
                Integer.class, args.toArray());
        String dataSql = "SELECT * FROM " + table + where
                + " ORDER BY create_time DESC LIMIT ? OFFSET ?";
        List<Object> dataArgs = new ArrayList<>(args);
        dataArgs.add(s);
        dataArgs.add((p - 1) * s);
        List<Map<String, Object>> data = jdbc.queryForList(dataSql, dataArgs.toArray());
        return Map.of("factType", factType, "page", p, "pageSize", s,
                "total", total == null ? 0 : total, "rows", data);
    }

    /** F02-06-2b：批次状态。 */
    public Map<String, Object> getBatch(String factType, String batchId) {
        requireKnownFactType(factType);
        if (batchId == null || batchId.isBlank()) {
            throw new BusinessException(400, "batchId 必填");
        }
        String table = factTable.get(factType);
        Integer accepted = jdbc.queryForObject("SELECT COUNT(*) FROM " + table
                + " WHERE batch_id = ?", Integer.class, batchId);
        String latestStatus = jdbc.queryForObject("SELECT COALESCE(MAX(dq_status),'') FROM " + table
                + " WHERE batch_id = ?", String.class, batchId);
        Timestamp updatedAt = jdbc.queryForObject("SELECT MAX(update_time) FROM " + table
                + " WHERE batch_id = ?", Timestamp.class, batchId);
        int c = accepted == null ? 0 : accepted;
        return Map.of("batchId", batchId, "factType", factType, "accepted", c,
                "latestStatus", latestStatus == null ? "" : latestStatus,
                "updatedAt", updatedAt,
                "publishable", c > 0 && "PUBLISHED".equalsIgnoreCase(latestStatus));
    }

    /**
     * F02-06-2e：发布批次 PASSED → PUBLISHED（09 册消费只取 PUBLISHED）。
     * 语义 = 批次内所有 PASSED 行一次性发布；与 R - F02-07-3 "coverage 完整性"联动属 09 册检查项。
     */
    public int publishBatch(String factType, String batchId) {
        requireKnownFactType(factType);
        if (batchId == null || batchId.isBlank()) {
            throw new BusinessException(400, "batchId 必填");
        }
        return jdbc.update("UPDATE " + factTable.get(factType)
                + " SET dq_status='PUBLISHED', update_time=CURRENT_TIMESTAMP"
                + " WHERE batch_id = ? AND dq_status='PASSED'", batchId);
    }

    /** F02-06-2e：action-outcomes（W→D 反馈链）— 只更 evidence_ref，不改业务字段。 */
    public int writeActionOutcome(String factType, String batchId, String evidenceRef) {
        requireKnownFactType(factType);
        if (batchId == null || batchId.isBlank()) {
            throw new BusinessException(400, "batchId 必填");
        }
        return jdbc.update("UPDATE " + factTable.get(factType)
                + " SET update_time = CURRENT_TIMESTAMP, evidence_ref = ?"
                + " WHERE batch_id = ? AND dq_status = 'PUBLISHED'", evidenceRef, batchId);
    }

    /**
     * F02-06-4：FACT 行更正=PASSED/PENDING 行覆盖白名单业务列，PUBLISHED → 409。
     */
    public int overwriteFactRow(String factType, String id, Map<String, Object> newValues) {
        requireKnownFactType(factType);
        if (id == null || id.isBlank()) {
            throw new BusinessException(400, "id 必填");
        }
        String table = factTable.get(factType);
        String status = jdbc.queryForObject("SELECT dq_status FROM " + table + " WHERE id = ?",
                String.class, id);
        if (status == null) {
            throw new BusinessException(404, "事实行不存在: " + id);
        }
        if ("PUBLISHED".equalsIgnoreCase(status)) {
            throw new BusinessException(409,
                    "ECOS-DATA-021: PUBLISHED 事实行不可改业务字段（更正=新行）");
        }
        Map<String, Object> filter = new LinkedHashMap<>();
        for (String c : factColumns.get(factType)) {
            if (newValues.containsKey(c)) filter.put(c, newValues.get(c));
        }
        if (filter.isEmpty()) {
            throw new BusinessException(400, "无可更新业务列");
        }
        StringBuilder setSql = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Object> e : filter.entrySet()) {
            if (setSql.length() > 0) setSql.append(", ");
            setSql.append(e.getKey()).append(" = ?");
            args.add(e.getValue());
        }
        setSql.append(", update_time = CURRENT_TIMESTAMP");
        args.add(id);
        return jdbc.update("UPDATE " + table + " SET " + setSql + " WHERE id = ?", args.toArray());
    }
}
