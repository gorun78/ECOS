package com.chinacreator.gzcm.engine.data.fact.dq;

import com.chinacreator.gzcm.engine.data.fact.FactType;
import com.chinacreator.gzcm.engine.data.fact.model.RowRejection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * DQ-F01..F11 行级门禁实现（详细设计-02 §F02-07 / PRD-02 §二 REQ-DATA-02）。
 *
 * <p>本实现承接 {@link DqRowGate#checkRows(FactType, List, Map)}，逐行执行 PRD-02 §2.1 定义的
 * 11 条规则中<b>除 DQ-F05</b>（同批单币种 = 批级不变量，由 {@code FactImportService} 内建
 * pre-check，属整批 400 通道）之外的 10 条：
 * <ul>
 *   <li>F01 UNIQUENESS — 批内业务键重复（各表 uniq_* 的子集：批内去重；DB 级 uniq 冲突由
 *       mapper 侧兜底）；</li>
 *   <li>F02 VALIDITY   — 比率列（attributionRatio / realizationRate）∈ [0,1]；</li>
 *   <li>F03 VALIDITY   — 金额/费率列 scale ≤ 2 且 ≥ 0（浮点污染 = 精度破坏 → REJECT）；</li>
 *   <li>F04 VALIDITY   — period 列格式 YYYY-MM，若 batchCtx 提供 targetPeriod 则同年同月；</li>
 *   <li>F05 CONSISTENCY — 批级，DqRowGate 不做（在 FactImportService.checkDqF05BatchCurrency，
 *       该类 Javadoc 单测已覆盖）；</li>
 *   <li>F06 CONSISTENCY — ATTRIBUTION：同 (projectId, contractId) 归 sum(attributionRatio) ≤ 1；</li>
 *   <li>F07 COMPLETENESS — 必填列非空（PRD-02 §三 模板 * 列）；</li>
 *   <li>F08 COMPLETENESS — COST 且 directOrAllocated=ALLOCATED 必带 allocationRuleRef；</li>
 *   <li>F09 COMPLETENESS — COST 与 STAGE/RESOURCE factType=ACTUAL 必带 evidenceRef 或 sourceRef；</li>
 *   <li>F10 ACCURACY   — contractBase 与主数据比对（batchCtx.key=contractMasterBase 传入
 *       Map&lt;contractId, BigDecimal&gt;，若不一致 → WARN 日志，<b>不阻断</b>，落待处理队列由
 *       上游 F02-07 承接，不产出 RowRejection）；</li>
 *   <li>F11 FRESHNESS — factType=ACTUAL 的 period 不得晚于当前月（预测未来期间的实际值 = 语义
 *       错配）。</li>
 * </ul></p>
 *
 * <p><b>红线（PRD-02 §2.2）</b>：本实现<b>禁止</b>补默认值/补 0 以让校验通过；任一字段缺席
 * 一律按缺失进入 F07 拦截（或对应规则拒收），而非 {@code Map.putIfAbsent(field, 0)}。
 * {@code NoSilentDefaultFillArchTest} 侧护栏源码禁 {@code putIfAbsent<anything>, 0}}
 * 在 fact 写入路径出现。</p>
 *
 * <p>Bean 名 {@code "dqRowGate"}（DqRowGate 接口 Javadoc 跨 Angle 契约）。</p>
 */
@Component("dqRowGate")
public class DqRowGateImpl implements DqRowGate {

    private static final Logger log = LoggerFactory.getLogger(DqRowGateImpl.class);

    static final String R_F01 = "DQ-F01";
    static final String R_F02 = "DQ-F02";
    static final String R_F03 = "DQ-F03";
    static final String R_F04 = "DQ-F04";
    static final String R_F06 = "DQ-F06";
    static final String R_F07 = "DQ-F07";
    static final String R_F08 = "DQ-F08";
    static final String R_F09 = "DQ-F09";
    static final String R_F10 = "DQ-F10";
    static final String R_F11 = "DQ-F11";

    private static final Pattern PERIOD_PAT = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    /** PRD-02 §三 模板必填列（* 列）— 按 factType 分组，F07 门禁依据。 */
    private static final Map<FactType, Set<String>> REQUIRED_FIELDS = buildRequiredFields();

    /** 比率列（F02，∈ [0,1]）。 */
    private static final Set<String> RATIO_FIELDS = Set.of("attributionRatio", "realizationRate");

    /** 金额/费率列（F03，scale ≤2 &amp; ≥ 0；PREFIX 匹配 currency 无关）。 */
    private static final Set<String> AMOUNT_FIELDS = Set.of("amount", "hourlyRate", "contractBase");

    @Override
    public List<RowRejection> checkRows(FactType type, List<Map<String, Object>> rows,
                                        Map<String, Object> batchCtx) {
        Map<String, Object> ctx = batchCtx == null ? Map.of() : batchCtx;
        String targetPeriod = asString(ctx.get("targetPeriod"));
        YearMonth curMonth = YearMonth.now();
        List<RowRejection> out = new ArrayList<>();

        if (type == null || rows == null || rows.isEmpty()) {
            return out;
        }
        Set<String> required = REQUIRED_FIELDS.getOrDefault(type, Set.of());

        // ---------- F01 批内唯一性 ----------
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            String key = businessKey(type, r);
            if (key == null) {
                continue;
            }
            if (!seen.add(key)) {
                out.add(new RowRejection(i + 1, "-", R_F01,
                        "第" + (i + 1) + "行与批内已有记录重复（业务键 " + key + "）",
                        "同一业务键一行只允许存活一条，重复行请在线修复后重传（同 batchId 幂等去重）"));
            }
        }

        // ---------- 逐行列级规则 ----------
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            int rowNo = i + 1;

            // F07 必填列
            for (String f : required) {
                Object v = r.get(f);
                if (v == null || (v instanceof String s && s.isBlank())) {
                    out.add(new RowRejection(rowNo, f, R_F07,
                            "第" + rowNo + "行缺必填字段 " + f,
                            "补齐 " + f + " 后重传；模板必填列见 GET /api/v1/datanet/facts/"
                                    + type.name().toLowerCase(Locale.ROOT) + "/template"));
                    break; // 每行只报首个缺失，避免噪声
                }
            }

            // F02 比率 ∈ [0,1]
            for (String field : RATIO_FIELDS) {
                Object v = r.get(field);
                BigDecimal bd = toBigDecimal(v);
                if (v != null && bd == null) {
                    out.add(new RowRejection(rowNo, field, R_F02,
                            "第" + rowNo + "行 " + field + " 非数值: " + v,
                            "改为数值 0~1（如 0.85）"));
                    continue;
                }
                if (bd != null && (bd.compareTo(BigDecimal.ZERO) < 0
                        || bd.compareTo(BigDecimal.ONE) > 0)) {
                    out.add(new RowRejection(rowNo, field, R_F02,
                            "第" + rowNo + "行 " + field + "=" + bd + " 超出 0~1",
                            "改为 0~1 区间内的比率（如 attribution_ratio=0.6）"));
                }
            }

            // F03 金额 scale ≤2 & ≥0
            for (String field : AMOUNT_FIELDS) {
                Object v = r.get(field);
                if (v == null) {
                    continue;
                }
                BigDecimal bd = toBigDecimal(v);
                if (bd == null) {
                    out.add(new RowRejection(rowNo, field, R_F03,
                            "第" + rowNo + "行 " + field + " 非数值: " + v,
                            "改为十进制金额（最多 2 位小数）"));
                    continue;
                }
                BigDecimal stripped = bd.stripTrailingZeros();
                if (stripped.scale() > 2) {
                    out.add(new RowRejection(rowNo, field, R_F03,
                            "第" + rowNo + "行 " + field + "=" + bd + " 精度破坏（scale="
                                    + stripped.scale() + " > 2）",
                            "金额按 NUMERIC(p,2) 上报，最多 2 位小数"));
                } else if (bd.signum() < 0) {
                    out.add(new RowRejection(rowNo, field, R_F03,
                            "第" + rowNo + "行 " + field + "=" + bd + " 为负（事实层存原值+标志）",
                            "负数会折入 anomaly 标志流程；默认导入要求非负"));
                }
            }

            // F04 period 格式 YYYY-MM
            Object periodRaw = r.get("period");
            if (periodRaw != null && !"".equals(String.valueOf(periodRaw))) {
                String p = String.valueOf(periodRaw).trim();
                if (!PERIOD_PAT.matcher(p).matches()) {
                    out.add(new RowRejection(rowNo, "period", R_F04,
                            "第" + rowNo + "行 period='" + p + "' 非法（应 YYYY-MM）",
                            "补齐 period 为标准 YYYY-MM 格式，如 2026-09"));
                } else if (targetPeriod != null && !targetPeriod.isEmpty()) {
                    YearMonth tp;
                    try {
                        tp = YearMonth.parse(targetPeriod);
                    } catch (Exception e) {
                        tp = null;
                    }
                    YearMonth pm;
                    try {
                        pm = YearMonth.parse(p);
                    } catch (Exception e) {
                        pm = null;
                    }
                    if (tp != null && pm != null && !tp.equals(pm)) {
                        out.add(new RowRejection(rowNo, "period", R_F04,
                                "第" + rowNo + "行 period=" + p + " 与批次 targetPeriod=" + targetPeriod
                                        + " 不同月（rescope 一致）",
                                "改为 period=" + targetPeriod + " 或换批次"));
                    }
                }
            }

            // F11 factType=ACTUAL 期间 ≤ 当前月
            Object factTypeRaw = r.get("factType");
            if (factTypeRaw != null
                    && "ACTUAL".equalsIgnoreCase(String.valueOf(factTypeRaw).trim())) {
                Object p = r.get("period");
                if (p != null) {
                    String ps = String.valueOf(p).trim();
                    if (PERIOD_PAT.matcher(ps).matches()) {
                        try {
                            YearMonth ym = YearMonth.parse(ps);
                            if (ym.isAfter(curMonth)) {
                                out.add(new RowRejection(rowNo, "period", R_F11,
                                        "第" + rowNo + "行 ACTUAL factType 期间 " + ps
                                                + " 晚于当前月 " + curMonth,
                                        "实际值不得上报到未来期间；改 PLAN 或回退 period"));
                            }
                        } catch (Exception ignored) {
                            // 已经被 F04 拦截
                        }
                    }
                }
            }

            // F08 ALLOCATED 成本必带 allocation_rule_ref
            if (type == FactType.COST) {
                Object doa = r.get("directOrAllocated");
                if (doa != null && "ALLOCATED".equalsIgnoreCase(String.valueOf(doa).trim())) {
                    Object arr = r.get("allocationRuleRef");
                    if (arr == null || String.valueOf(arr).isBlank()) {
                        out.add(new RowRejection(rowNo, "allocationRuleRef", R_F08,
                                "第" + rowNo + "行 ALLOCATED 成本缺分摊规则引用",
                                "补齐 allocationRuleRef（分流键 music/rollup rule 或专有规则名）"));
                    }
                }
            }

            // F09 成本/实际值必带 evidence_ref 或 source_ref
            boolean costLike = type == FactType.COST;
            boolean actualLike = factTypeRaw != null
                    && "ACTUAL".equalsIgnoreCase(String.valueOf(factTypeRaw).trim());
            if (costLike || actualLike) {
                Object ev = r.get("evidenceRef");
                Object sr = r.get("sourceRef");
                boolean hasEv = ev != null && !String.valueOf(ev).isBlank();
                boolean hasSr = sr != null && !String.valueOf(sr).isBlank();
                if (!hasEv && !hasSr) {
                    out.add(new RowRejection(rowNo, "evidenceRef", R_F09,
                            "第" + rowNo + "行成本/实际值缺证据引用",
                            "补齐 evidenceRef（口径文件 / ALP 链接 / 附件 URI）或 sourceRef"));
                }
            }

            // F10 主数据比对 → WARN only（不阻断，见 PRD-02 §2.1 处置列）
            Object contractId = r.get("contractId");
            Object contractBase = r.get("contractBase");
            if (contractId != null && contractBase != null) {
                Object masterBase = ctx.get("contractMasterBase");
                if (masterBase instanceof Map<?, ?> map) {
                    Object expected = map.get(String.valueOf(contractId));
                    BigDecimal exp = toBigDecimal(expected);
                    BigDecimal act = toBigDecimal(contractBase);
                    if (exp != null && act != null
                            && exp.subtract(act).abs().compareTo(new BigDecimal("0.01")) > 0) {
                        log.warn("DQ-F10 主数据比对不一致：row={} contract={} masterBase={} rowBase={}"
                                + " → 进待处理队列（不阻断），本批量 WARN 计数",
                                rowNo, contractId, exp, act);
                    }
                }
            }
        }

        // ---------- F06 归 sum ratio ≤1 ----------
        if (type == FactType.ATTRIBUTION) {
            Map<String, BigDecimal> sums = new HashMap<>();
            Map<String, List<Integer>> groups = new LinkedHashMap<>();
            for (int i = 0; i < rows.size(); i++) {
                Map<String, Object> r = rows.get(i);
                String pi = asString(r.get("projectId"));
                String ci = asString(r.get("contractId"));
                if (pi == null || ci == null) {
                    continue;
                }
                String gk = pi + "|" + ci;
                BigDecimal ratio = toBigDecimal(r.get("attributionRatio"));
                if (ratio != null) {
                    sums.merge(gk, ratio, BigDecimal::add);
                    groups.computeIfAbsent(gk, k -> new ArrayList<>()).add(i);
                }
            }
            for (Map.Entry<String, BigDecimal> e : sums.entrySet()) {
                if (e.getValue().compareTo(BigDecimal.ONE) > 0) {
                    for (Integer idx : groups.get(e.getKey())) {
                        out.add(new RowRejection(idx + 1, "attributionRatio", R_F06,
                                "归合计 " + e.getValue() + " 超过 100%（group=" + e.getKey() + "）",
                                "同 (projectId, contractId) Σ attribution_ratio ≤ 1，调整比例后重传"));
                    }
                }
            }
        }

        return out;
    }

    // ── internal ─

    // —— internal ————————————————————————————————

    private static String businessKey(FactType type, Map<String, Object> r) {
        List<String> cols = switch (type) {
            case ATTRIBUTION -> List.of("projectId", "contractId", "departmentId", "attributionType");
            case STAGE -> List.of("projectId", "departmentId", "period", "stage", "factType");
            case RESOURCE -> List.of("projectId", "departmentId", "period", "staffRefHash", "factType");
            case COST -> List.of("projectId", "departmentId", "period", "costCategory",
                    "directOrAllocated", "factType");
            case FORECAST_SNAPSHOT -> List.of("forecastRunId", "asOfTime", "scopeHash");
        };
        StringBuilder sb = new StringBuilder(type.name()).append('|');
        for (String c : cols) {
            Object v = r.get(c);
            sb.append(v == null ? "" : String.valueOf(v).trim()).append('');
        }
        return sb.toString();
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return new BigDecimal(String.valueOf(v).trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String asString(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private static Map<FactType, Set<String>> buildRequiredFields() {
        Map<FactType, Set<String>> m = new EnumMap<>(FactType.class);
        m.put(FactType.STAGE, Set.of("projectId", "departmentId", "period", "stage",
                "factType", "contractBase", "attributionRatio", "currency"));
        m.put(FactType.RESOURCE, Set.of("projectId", "departmentId", "period",
                "costCategory", "factType"));
        m.put(FactType.COST, Set.of("departmentId", "period", "costCategory",
                "directOrAllocated", "amount", "currency"));
        m.put(FactType.ATTRIBUTION, Set.of("projectId", "attributionRatio"));
        m.put(FactType.FORECAST_SNAPSHOT, Set.of("forecastRunId", "asOfTime"));
        return m;
    }
}
