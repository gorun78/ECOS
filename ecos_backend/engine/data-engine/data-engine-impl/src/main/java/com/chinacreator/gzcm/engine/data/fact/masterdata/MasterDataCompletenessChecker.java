package com.chinacreator.gzcm.engine.data.fact.masterdata;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 主数据完备性检查器 — 详细设计-09 v1.2 §十一 H-6 交付⑤（PRD-07 主数据完备性门禁）。
 *
 * <p>逐笔校验每条阶段事实明细（dq_status≠REJECTED 的检验集）能否解析出
 * <b>合同基数</b>与<b>归属行</b>，规则（冻结测试 C224
 * {@code MasterDataCompletenessTest#everyDetailResolvesContractAndAttribution}）：
 * <ul>
 *   <li>{@code MD-C1} 合同基数缺失：明细 {@code contract_base} 为 NULL（V167 可空豁免列，
 *       豁免登记在导入侧 DQ 门禁，本检查器如实报缺）；</li>
 *   <li>{@code MD-C2} 归属缺失：projectId+period 未命中任何 is_active=1 且
 *       {@code effective_from ≤ period ≤ effective_to}（effective_to 空视为开区间）
 *       的项目经营归属行；</li>
 *   <li>{@code MD-C3} 归属超配：同一 (projectId, period) 覆盖窗内归属行
 *       Σattribution_ratio &gt; 1（项目级一次上报，逐明细不重复）；</li>
 *   <li>{@code MD-EMPTY} 检验集为空：0 笔明细可检 — 显式上报而非静默 pass=true
 *       （X-44/X-50 禁 200 造数式假成功）。</li>
 * </ul>
 *
 * <p>REJECTED 行不计入检验集，仅计数为 {@code blockedCount} —— PRD-09 §七-3 监控项
 * ③ fc_dq_block_trend（DQ BLOCK 趋势面）的数据源。
 *
 * <p><b>只读</b>：零写路径、零 DDL；纯逻辑与取数分离（取数全部经
 * {@link MasterDataCompletenessMapper} 只读 SELECT，禁旁路 SQL）。
 */
@Service
public class MasterDataCompletenessChecker {

    public static final String RULE_CONTRACT_BASE_MISSING = "MD-C1";
    public static final String RULE_ATTRIBUTION_MISSING = "MD-C2";
    public static final String RULE_ATTRIBUTION_SUM_EXCEEDED = "MD-C3";
    public static final String RULE_EMPTY_CHECK_SET = "MD-EMPTY";

    /** 明细/归属扫描上限（默认值；入参 limit 越界即钳制，禁全库无界扫描）。 */
    public static final int DEFAULT_SCAN_LIMIT = 5000;
    public static final int MAX_SCAN_LIMIT = 20000;

    private final MasterDataCompletenessMapper mapper;

    public MasterDataCompletenessChecker(MasterDataCompletenessMapper mapper) {
        this.mapper = mapper;
    }

    /** 便捷重载：默认扫描上限。 */
    public Report check(String projectId, String period) {
        return check(projectId, period, DEFAULT_SCAN_LIMIT);
    }

    /**
     * 执行完备性检查。projectId/period 空串按 null 处理（全表面扫描，limit 钳制到
     * [{@code MAX_SCAN_LIMIT}]）。
     */
    public Report check(String projectId, String period, int limit) {
        String pid = blankToNull(projectId);
        String pp = blankToNull(period);
        int capped = Math.min(Math.max(limit, 1), MAX_SCAN_LIMIT);

        List<Map<String, Object>> details = mapper.selectCompletenessDetails(pid, pp, capped);
        int blockedCount = mapper.countRejectedDetails(pid, pp);
        List<Map<String, Object>> attributions = mapper.selectActiveAttributions(pid, capped);

        Map<String, List<Attribution>> byProject = groupAttributions(attributions);

        List<Issue> issues = new ArrayList<>();
        Set<String> ratioCheckedKeys = new HashSet<>();
        int totalDetails = details == null ? 0 : details.size();

        if (totalDetails == 0) {
            issues.add(new Issue(RULE_EMPTY_CHECK_SET, null, pid, pp,
                    "检验集为空：0 笔可检明细（dq_status≠REJECTED），完备性结论不可作出"));
            return new Report(false, 0, blockedCount, issues);
        }

        for (Map<String, Object> detail : details) {
            String detailId = str(detail.get("id"));
            String dProject = str(detail.get("projectId"));
            String dPeriod = str(detail.get("period"));

            // MD-C1 合同基数
            Object contractBase = detail.get("contractBase");
            if (contractBase == null) {
                issues.add(new Issue(RULE_CONTRACT_BASE_MISSING, detailId, dProject, dPeriod,
                        "明细 contract_base 为空，无法定位合同基数"));
            }

            // MD-C2 归属覆盖窗命中
            List<Attribution> covered = coveringAttributions(
                    byProject.getOrDefault(dProject, Collections.emptyList()), dPeriod);
            if (covered.isEmpty()) {
                issues.add(new Issue(RULE_ATTRIBUTION_MISSING, detailId, dProject, dPeriod,
                        "未命中 is_active=1 且覆盖 " + dPeriod + " 的归属行（projectId="
                                + dProject + "）"));
            }

            // MD-C3 项目级 Σratio≤1（每 (projectId, period) 只报一次）
            String ratioKey = dProject + "@" + dPeriod;
            if (!covered.isEmpty() && ratioCheckedKeys.add(ratioKey)) {
                BigDecimal sum = BigDecimal.ZERO;
                for (Attribution a : covered) {
                    sum = sum.add(a.ratio);
                }
                if (sum.compareTo(BigDecimal.ONE) > 0) {
                    issues.add(new Issue(RULE_ATTRIBUTION_SUM_EXCEEDED, null, dProject, dPeriod,
                            "归属比率合计 " + sum.toPlainString() + " > 1（覆盖窗内 "
                                    + covered.size() + " 行）"));
                }
            }
        }

        return new Report(issues.isEmpty(), totalDetails, blockedCount, issues);
    }

    // ── 纯逻辑辅助（测试直接复用） ────────────────────────────────

    static Map<String, List<Attribution>> groupAttributions(List<Map<String, Object>> rows) {
        Map<String, List<Attribution>> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            String projectId = str(row.get("projectId"));
            if (projectId == null) {
                continue;
            }
            BigDecimal ratio = toDecimal(row.get("attributionRatio"));
            map.computeIfAbsent(projectId, k -> new ArrayList<>()).add(new Attribution(
                    str(row.get("id")),
                    str(row.get("contractId")),
                    str(row.get("effectiveFrom")),
                    str(row.get("effectiveTo")),
                    ratio == null ? BigDecimal.ZERO : ratio));
        }
        return map;
    }

    /** 覆盖窗匹配：effectiveFrom ≤ period 且（effectiveTo 空视为开区间或 effectiveTo ≥ period）。 */
    static List<Attribution> coveringAttributions(List<Attribution> attributions, String period) {
        List<Attribution> out = new ArrayList<>();
        if (period == null) {
            return out;
        }
        for (Attribution a : attributions) {
            if (a.effectiveFrom == null || a.effectiveFrom.compareTo(period) > 0) {
                continue;
            }
            if (a.effectiveTo == null || a.effectiveTo.compareTo(period) >= 0) {
                out.add(a);
            }
        }
        return out;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal) {
            return (BigDecimal) v;
        }
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }

    // ── 结果模型 ────────────────────────────────────────────────

    /** 归属行值对象（is_active=1 未软删）。 */
    public static final class Attribution {
        public final String id;
        public final String contractId;
        public final String effectiveFrom;
        public final String effectiveTo;
        public final BigDecimal ratio;

        public Attribution(String id, String contractId, String effectiveFrom,
                           String effectiveTo, BigDecimal ratio) {
            this.id = id;
            this.contractId = contractId;
            this.effectiveFrom = effectiveFrom;
            this.effectiveTo = effectiveTo;
            this.ratio = ratio;
        }
    }

    /** 单条不完备发现。 */
    public static final class Issue {
        public final String ruleCode;
        public final String detailId;
        public final String projectId;
        public final String period;
        public final String message;

        public Issue(String ruleCode, String detailId, String projectId, String period,
                     String message) {
            this.ruleCode = ruleCode;
            this.detailId = detailId;
            this.projectId = projectId;
            this.period = period;
            this.message = message;
        }

        public String getRuleCode() {
            return ruleCode;
        }

        public String getDetailId() {
            return detailId;
        }

        public String getProjectId() {
            return projectId;
        }

        public String getPeriod() {
            return period;
        }

        public String getMessage() {
            return message;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ruleCode", ruleCode);
            m.put("detailId", detailId);
            m.put("projectId", projectId);
            m.put("period", period);
            m.put("message", message);
            return m;
        }

        @Override
        public String toString() {
            return ruleCode + "@" + projectId + "/" + period + ": " + message;
        }
    }

    /** 检查报告（pass = 零发现；blockedCount = REJECTED 行数，DQ BLOCK 趋势面）。 */
    public static final class Report {
        private final boolean pass;
        private final int totalDetails;
        private final int blockedCount;
        private final List<Issue> issues;

        public Report(boolean pass, int totalDetails, int blockedCount, List<Issue> issues) {
            this.pass = pass;
            this.totalDetails = totalDetails;
            this.blockedCount = blockedCount;
            this.issues = issues == null ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(issues));
        }

        public boolean isPass() {
            return pass;
        }

        public int getTotalDetails() {
            return totalDetails;
        }

        public int getBlockedCount() {
            return blockedCount;
        }

        public List<Issue> getIssues() {
            return issues;
        }

        /** REST 面序列化形态（LinkedHashMap 保序，键名与详设-09 §十一 H-6 行对齐）。 */
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pass", pass);
            m.put("totalDetails", totalDetails);
            m.put("blockedCount", blockedCount);
            List<Map<String, Object>> list = new ArrayList<>();
            for (Issue i : issues) {
                list.add(i.toMap());
            }
            m.put("issues", list);
            return m;
        }

        @Override
        public String toString() {
            return "Report{pass=" + pass + ", totalDetails=" + totalDetails
                    + ", blockedCount=" + blockedCount + ", issues=" + issues.size()
                    + (Objects.equals(Boolean.TRUE, pass) ? "" : " " + issues) + "}";
        }
    }
}
