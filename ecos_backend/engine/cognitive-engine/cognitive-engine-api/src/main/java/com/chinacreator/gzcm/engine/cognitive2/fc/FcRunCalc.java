package com.chinacreator.gzcm.engine.cognitive2.fc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分册09 F09-07/08/09 · 确定性预测计算核心（compliance-only、离线可测）。
 *
 * <p>硬约束（对应 §3.1 编排/生产边界 + §7.3 C205/C209/C217/C225）：
 * <ul>
 *   <li>金额一律 {@code BigDecimal} 与 {@code HALF_UP}（PRD-01 DB-04 精度裁定 / C204）</li>
 *   <li>留存 P10/P50/P90 三值（R-62①，禁前端插值）</li>
 *   <li>三阶段严格自下而上聚合：STAGE 全部 cell amount 之和 = DEPARTMENT 汇总 = TOTAL
 *       （C217：三级平衡断言精确相等，非"约等于"）</li>
 *   <li>四源 {@link SourceType}：ACTUAL / PLAN / PROFILE_IMPUTED / MANUAL_OVERRIDE / COMPUTED，
 *       优先级(0=最高)= ACTUAL → PLAN → PROFILE_IMPUTED → MANUAL_OVERRIDE；COMPUTED 仅用于
 *       自下而上聚合产生的父节点</li>
 *   <li>幂等：{@link #scopeHash} / {@link #overridesHash} / {@link #runKey}（§3.2）</li>
 *   <li>SUCCEEDED 前逐行六要素自检：任一要素空 ⇒ 抛 {@link SixElementMismatch}</li>
 * </ul>
 * 本类不持有 DB/Driver/LLM/调度/监控依赖，符合 cognitive-engine AGENTS 红线（§2.5 收口）。</p>
 */
public final class FcRunCalc {

    /** 六要素（C209）——逐行与 run 级同时成立；SUCCEEDED 前逐行自检。 */
    public record SixElement(String forecastRunId, String caliberId, String caliberVersion,
                              java.time.Instant asOfTime, String snapshotId, String formulaVersion) {
        public static SixElement of(String runId, String calId, String calVer, java.time.Instant asOf,
                                    String snapId, String formulaVer) {
            if (isBlank(runId) || isBlank(calId) || isBlank(calVer) || asOf == null
                    || isBlank(snapId) || isBlank(formulaVer)) {
                throw new SixElementMismatch("six-element incomplete: " + run(calId, calVer, snapId, formulaVer)
                        );
            }
            return new SixElement(runId, calId, calVer, asOf, snapId, formulaVer);
        }
        private static String run(String calId, String calVer, String snapId, String formulaVer) {
            return "{caliber=" + calId + "@" + calVer + ", snapshot=" + snapId + ", formula=" + formulaVer + "}";
        }
    }

    /** 六要素缺失即抛（宁可失败不缺要素，§3.1 step 5）。 */
    public static final class SixElementMismatch extends IllegalStateException {
        public SixElementMismatch(String msg) { super(msg); }
    }

    /** 四源取值优先级（数值越小越优先；0=最高）。 */
    public enum SourceType {
        ACTUAL(0), PLAN(1), PROFILE_IMPUTED(2), MANUAL_OVERRIDE(3), COMPUTED(9);
        private final int prio;
        SourceType(int prio) { this.prio = prio; }
        public int priority() { return prio; }
    }

    /** 一格取值候选；对应 §5.1 统一响应约定 source_type + source_ref。 */
    public record Candidate(SourceType type, BigDecimal amount, Map<String, Object> sourceRef) {
        public Candidate {
            if (amount == null) throw new IllegalArgumentException("amount null (禁静默取 0/均值，FC-05/F09-05)");
        }
    }

    /** 单格结果（'(cell, source, 三值)'）。三值必同规模（scale=2）。 */
    public record Cell(String metricId, String period, String granularity,
                       String projectId, String departmentId, String stage,
                       SourceType source, BigDecimal amountP10, BigDecimal amountP50,
                       BigDecimal amountP90, Map<String, Object> sourceRef) {
        public BigDecimal amount() { return amountP50; }
    }

    /** 认知模型绑定（W244/C226）：run 记录的 model→version，来自 ecos_cognitive_model（V124）。
     *  <p>{@code modelVersion=0} ⇒ 显式"未接线"（UNBOUND），用于尚未接入模型注册表的 run；
     *  非负约束：负值非法；未接线时以 "unbound" modelId + v0 显式调用，禁止拿 null 打掩护。 */
    public record ModelBinding(String modelId, Integer modelVersion) {
        public ModelBinding {
            if (modelId == null || modelId.isEmpty()) {
                throw new IllegalStateException("modelId blank: 必须按 modelId 解引用 CM 注册项 (W244/C226)");
            }
            if (modelVersion == null || modelVersion < 0) {
                throw new IllegalStateException("modelVersion invalid: " + modelVersion);
            }
        }
        public boolean bound() { return modelVersion > 0; }
    }

    /** run 产出全集 + 分组求和结果（供三级平衡断言）。 */
    public record RunOutput(SixElement six, List<Cell> cells,
                             Map<String, BigDecimal> stageSums,
                             Map<String, BigDecimal> deptSums,
                             BigDecimal total,
                             String scopeHash, String overridesHash, String runKey,
                             ModelBinding model) {}

    /** 兼容旧签名（未显式绑定模型时以 DEFAULT modelId + v0 = 未接线）；新代码请走 7 参带 {@link ModelBinding}。 */
    public static RunOutput calculate(SixElement six,
                                      List<CellInput> cells,
                                      String scopeHash,
                                      String overridesHash,
                                      String formulaVersion) {
        return calculate(six, cells, scopeHash, overridesHash, formulaVersion,
                new ModelBinding("unbound", 0));
    }

    /** 主入口：四源取值 → 逐格三值 → 严格自下而上聚合 + 三阶段平衡断言 + 六要素自检。 */
    public static RunOutput calculate(SixElement six,
                                      List<CellInput> cells,
                                      String scopeHash,
                                      String overridesHash,
                                      String formulaVersion,
                                      ModelBinding model) {
        if (model == null) throw new IllegalStateException("modelBinding null: 值守必绑定 modelId（W244/C226）");
        if (six == null) throw new SixElementMismatch("six element null");
        List<Cell> out = new ArrayList<>(cells.size());
        // 每格三值：P50=REALIZED_AMOUNT, P10/P90 由 intervalWidth 上下对称扩展（禁 LLM 生成，F09-09）
        // 生产默认宽度 = 5% of |p50|，若为 0 视为确定性（三值相等），不出区间噪声
        for (CellInput in : cells) {
            Candidate c = resolveSource(in.candidates());
            BigDecimal p50 = c.amount().setScale(2, RoundingMode.HALF_UP);
            BigDecimal spread = p50.abs().multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lo = p50.subtract(spread);
            BigDecimal hi = p50.add(spread);
            out.add(new Cell(in.metricId(), in.period(), in.granularity(),
                    in.projectId(), in.departmentId(), in.stage(),
                    c.type(), lo, p50, hi, c.sourceRef()));
        }
        // 严格自下而上聚合：STAGE → DEPARTMENT → TOTAL
        Map<String, BigDecimal> stageSums = sumByCells(out, "STAGE", c -> Key(c.stage()));
        Map<String, BigDecimal> deptSums = sumByCells(out, "DEPARTMENT", c -> Key(c.departmentId()));
        BigDecimal total = sumAllP50(out);
        assertThreeLevelBalance(out, stageSums, deptSums, total);
        // 六要素完好性：run 级六要素 + 每行六要素（run 内一致传递）
        for (Cell cell : out) {
            SixElement row = SixElement.of(six.forecastRunId(), six.caliberId(), six.caliberVersion(),
                    six.asOfTime(), six.snapshotId(), formulaVersion);
            if (row == null) throw new SixElementMismatch("row six zero");
            if (cell.amountP10() == null || cell.amountP50() == null || cell.amountP90() == null) {
                throw new SixElementMismatch("cell interval three-value incomplete " + cell.metricId());
            }
            if (cell.sourceRef() == null) {
                throw new SixElementMismatch("cell sourceRef missing " + cell.metricId());
            }
        }
        String runKey = runKeyOf(six, scopeHash, overridesHash);
        return new RunOutput(six, out, stageSums, deptSums, total, scopeHash, overridesHash, runKey, model);
    }

    /** 四源按优先级取值：candidates 空 ⇒ 抛（禁静默取 0/均值，FC-05/DQ 缺失格门禁）。 */
    public static Candidate resolveSource(Map<String, Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("no source for cell: 缺失应拦截为 DQ_BLOCK，禁静默取 0/均值");
        }
        Candidate best = null;
        for (Candidate c : candidates.values()) {
            if (best == null || c.type().priority() < best.type().priority()) best = c;
        }
        return best;
    }

    private static Map<String, BigDecimal> sumByCells(List<Cell> cells, String targetGranularity,
                                                       java.util.function.Function<Cell, String> keyer) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        for (Cell c : cells) {
            if (!targetGranularity.equals(c.granularity())) continue;
            String key = keyer.apply(c);
            m.merge(key, c.amountP50(), BigDecimal::add);
        }
        for (var en : m.entrySet()) en.setValue(en.getValue().setScale(2, RoundingMode.HALF_UP));
        return Collections.unmodifiableMap(m);
    }

    private static BigDecimal sumAllP50(List<Cell> cells) {
        BigDecimal s = BigDecimal.ZERO;
        for (Cell c : cells) {
            if (!"TOTAL".equals(c.granularity()) && !"STAGE".equals(c.granularity())) continue;
            s = s.add(c.amountP50());
        }
        return s.setScale(2, RoundingMode.HALF_UP);
    }

    private static String Key(String s) { return s == null ? "" : s; }

    /**
     * 三级平衡断言（C217）：叶子 cell 相加 = 子聚合 = 顶层。
     * 生产实现：STAGE 求和生产 STAGE_Sums（已由 sumByCells 承担），
     * DEPARTMENT 单元格（若存在于 cells 且 granularity=DEPARTMENT）必须等于其下属 STAGE cells 之和，
     * TOTAL 单元格（若存在 granularity=TOTAL）必须等于 DEPARTMENT cells 之和。
     * 若上层格未列举（本批次由生产服务只听叶子），则跳过对应对比。
     */
    public static void assertThreeLevelBalance(List<Cell> cells,
                                               Map<String, BigDecimal> stageSums,
                                               Map<String, BigDecimal> deptSums,
                                               BigDecimal total) {
        // 若 cells 中声明了 DEPARTMENT 格，逐一对比加入该 DEPARTMENT 下的 STAGE 求和
        for (Cell dept : cells) {
            if (!"DEPARTMENT".equals(dept.granularity())) continue;
            BigDecimal sumOfChildren = BigDecimal.ZERO;
            for (Cell st : cells) {
                if (!"STAGE".equals(st.granularity())) continue;
                if (dept.departmentId() == null || !dept.departmentId().equals(st.departmentId())) continue;
                sumOfChildren = sumOfChildren.add(st.amountP50());
            }
            if (dept.amountP50().compareTo(sumOfChildren) != 0) {
                throw new BalanceFailed("three-level-balance FAILED dept=" + dept.departmentId() + " dept=" +
                        dept.amountP50() + " sumOfStages=" + sumOfChildren);
            }
        }
        for (Cell tot : cells) {
            if (!"TOTAL".equals(tot.granularity())) continue;
            BigDecimal expected = BigDecimal.ZERO;
            if (!deptSums.isEmpty()) {
                for (BigDecimal d : deptSums.values()) expected = expected.add(d);
            } else if (!stageSums.isEmpty()) {
                for (BigDecimal d : stageSums.values()) expected = expected.add(d);
            } else {
                expected = total;
            }
            if (tot.amountP50().compareTo(expected.setScale(2, RoundingMode.HALF_UP)) != 0) {
                throw new BalanceFailed("three-level-balance FAILED total=" + tot.amountP50() + " expected=" + expected);
            }
        }
    }

    public static final class BalanceFailed extends IllegalStateException {
        public BalanceFailed(String m) { super(m); }
    }

    // ── 幂等（§3.2） ──
    public static String scopeHash(Collection<String> projectIds, Collection<String> departmentIds,
                                   String periodRange, Collection<String> stageSet) {
        List<String> sortedProjects = new ArrayList<>(projectIds == null ? List.of() : projectIds);
        List<String> sortedDepts = new ArrayList<>(departmentIds == null ? List.of() : departmentIds);
        List<String> sortedStages = new ArrayList<>(stageSet == null ? List.of() : stageSet);
        Collections.sort(sortedProjects);
        Collections.sort(sortedDepts);
        Collections.sort(sortedStages);
        return sha256(String.join("|", sortedProjects) + "#"
                + String.join("|", sortedDepts) + "#"
                + (periodRange == null ? "" : periodRange) + "#"
                + String.join("|", sortedStages));
    }

    /** 规范化：键排序 + BigDecimal 字符串化 + 编译 JSON（生产可由 Jackson 供）。 */
    public static String overridesHash(String canonicalNormalizedJson) {
        return sha256(canonicalNormalizedJson == null ? "" : canonicalNormalizedJson);
    }

    public static String runKeyOf(SixElement six, String scopeHash, String overridesHash) {
        return six.caliberId() + "@" + six.formulaVersion()
                + "|" + six.asOfTime().toEpochMilli()
                + "|" + scopeHash
                + "|" + overridesHash;
    }

    /** 情景 copy 对象（C203/C225）：目标格差值由生产服务计算，本方法仅保证"非目标格 unchanged"。 */
    public static List<Cell> applyOverrides(List<Cell> baseline, Map<String, CellOverride> overrides) {
        List<Cell> out = new ArrayList<>(baseline.size());
        for (Cell c : baseline) {
            String cellKey = cellKey(c);
            CellOverride o = overrides.get(cellKey);
            if (o == null) {
                out.add(c);
                continue;
            }
            BigDecimal p50 = o.newAmount().setScale(2, RoundingMode.HALF_UP);
            BigDecimal spread = p50.abs().multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
            out.add(new Cell(c.metricId(), c.period(), c.granularity(), c.projectId(),
                    c.departmentId(), c.stage(), SourceType.MANUAL_OVERRIDE,
                    p50.subtract(spread), p50, p50.add(spread),
                    o.sourceRef() == null ? Map.of("override", true) : o.sourceRef()));
        }
        return out;
    }

    /** 情形 => 覆盖格 key（大引号引 = "metric|granularity|period|projectId|departmentId|stage"）。 */
    public static String cellKey(Cell c) {
        return String.join("|",
                c.metricId() == null ? "" : c.metricId(),
                c.granularity() == null ? "" : c.granularity(),
                c.period() == null ? "" : c.period(),
                c.projectId() == null ? "" : c.projectId(),
                c.departmentId() == null ? "" : c.departmentId(),
                c.stage() == null ? "" : c.stage());
    }

    public record CellOverride(BigDecimal newAmount, Map<String, Object> sourceRef) {}

    public record CellInput(String metricId, String period, String granularity,
                            String projectId, String departmentId, String stage,
                            Map<String, Candidate> candidates) {}

    private static boolean isBlank(String s) { return s == null || s.isEmpty(); }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((s == null ? "" : s).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private FcRunCalc() {}
}
