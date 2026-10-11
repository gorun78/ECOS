package com.chinacreator.gzcm.engine.cognitive2.service.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 反事实归因平衡校验 + 确定性 diff（详细设计-05 F05-10 / C117 / §五"新增纯函数"），
 * 并补齐<b>三级汇总恒等断言</b>（详设-09 v1.2 §十一 H-2 / PRD-09 v1.2 §2.2，H-2b 落地）。
 *
 * <p>铁律：认知域只做 diff 归因，金额一律来自预测运行服务（RAG 推演禁作金额来源）。
 * {@code sum(contribution) == scenarioProfit − baselineProfit}（容差 ±0.01），不平即
 * {@code balanced=false} → 调用方 503（{@code ECOS-COG-502}），禁返回不平数据。
 * 覆盖范围之外（非命中 project/period）cell 必须 diff=0（只覆盖命中范围）。</p>
 *
 * <p><b>两个判据不同源，不可互相放宽</b>：</p>
 * <ul>
 *   <li><b>归因容差面</b>（{@link #balanced} / {@link #isBalanced}，±0.01）：F05-10 FC-03
 *       情景复制的 contribution 复算判据，容差由契约常量
 *       {@code CognitiveContractSchema.BALANCE_TOLERANCE} 冻结，前端对照表同源 ⇒ 本批改其语义即改 API；</li>
 *   <li><b>三级汇总恒等面</b>（{@link #checkThreeLevel} / {@link #balancedExact}，<b>容差 0</b>）：
 *       PRD-09 v1.2 §2.2「严格自下而上聚合（环节 → 项目×部门×月 → 部门×月 → 项目 → 企业），
 *       三级平衡断言 {@code |企业利润 − Σ部门利润| = 0}（<b>精确相等</b>）」⇒ 一律
 *       {@code BigDecimal.compareTo == 0}，<b>禁任何 epsilon</b>（卷 09 §十一 H-2、
 *       需求检视报告 R-64 明确"±0.01 不得当成本 REQ 的容差放宽"）。</li>
 * </ul>
 */
public final class ScenarioBalanceChecker {

    /** 一格 cell：某 project+period 的基准/情景/Δ。 */
    public record Cell(String projectId, int period, BigDecimal baseline, BigDecimal scenario) {
        public BigDecimal diff() { return scenario.subtract(baseline); }
    }

    /** 三级汇总恒等的层级标识（逐项 / 小计 / 总计）。 */
    public enum Level {
        /** L1 逐项：单格 Δ（scenario − baseline）。 */
        ITEM,
        /** L2 小计：同组逐项 Δ 之和（组键由调用方按聚合维度声明，如部门 / 项目）。 */
        SUBTOTAL,
        /** L3 总计：企业级真值（PRD-09 断言点 {@code 企业利润 − Σ部门利润 = 0}）。 */
        TOTAL
    }

    /** 逐项行 + 所属小计组键（Cell 签名已被 {@code ForecastRunSnapshotReader.RunSnapshot} 引用，禁改 ⇒ 组键外挂）。 */
    public record Item(String groupKey, Cell cell) {
        public BigDecimal diff() { return cell.diff(); }
    }

    /** 上游给定的小计（被断言对象，非计算结果 ⇒ 不平等即暴露真实差额）。 */
    public record Subtotal(String groupKey, BigDecimal amount) {}

    /** 一级不平的留痕（expected = 自下而上算出的值，actual = 上游给定的值）。 */
    public record Violation(Level level, String key, BigDecimal expected, BigDecimal actual) {
        public BigDecimal delta() { return actual.subtract(expected); }
    }

    /** 三级校验结论：{@code exact=true} ⇔ 三级全部精确相等（violations 空）。 */
    public record ThreeLevelReport(List<Violation> violations) {
        public ThreeLevelReport {
            violations = violations == null ? List.of() : List.copyOf(violations);
        }

        /** 三级恒等是否成立（精确相等，无容差）。 */
        public boolean exact() {
            return violations.isEmpty();
        }

        /** 首个失守层级（供 run 置 FAILED 时定位，禁笼统报"不平"）。 */
        public Level firstFailedLevel() {
            return violations.isEmpty() ? null : violations.get(0).level();
        }
    }

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private ScenarioBalanceChecker() {}

    private static BigDecimal round4(BigDecimal v) { return v.setScale(4, RoundingMode.HALF_UP); }

    /** 各格 contribution = cell diff（4 位小数）。 */
    public static List<BigDecimal> contributions(List<Cell> cells) {
        return cells.stream().map(c -> round4(c.diff())).toList();
    }

    /** 平衡：sum(contribution) 与 (scenarioProfit − baselineProfit) 差 ≤ 容差。 */
    public static boolean balanced(List<Cell> cells, BigDecimal baselineProfit, BigDecimal scenarioProfit) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal c : contributions(cells)) sum = sum.add(c);
        BigDecimal net = round4(scenarioProfit.subtract(baselineProfit));
        return sum.subtract(net).abs().compareTo(TOLERANCE) <= 0;
    }

    /** 归因平衡是否通过（供调用方决定 503）。 */
    public static boolean isBalanced(List<Cell> cells, BigDecimal baselineProfit, BigDecimal scenarioProfit) {
        return balanced(cells, baselineProfit, scenarioProfit);
    }

    /**
     * 只覆盖命中范围校验：非命中项目的 diff 必须为 0（否则视为归因泄漏，返回 false）。
     * {@code overriddenProjectIds} 之外的 cell 若 diff!=0 → 违反"只覆盖命中范围"。
     */
    public static boolean onlyOverriddenScopeDiffs(List<Cell> cells, List<String> overriddenProjectIds) {
        for (Cell c : cells) {
            boolean overridden = overriddenProjectIds != null && overriddenProjectIds.contains(c.projectId());
            if (!overridden && c.diff().compareTo(BigDecimal.ZERO) != 0) {
                return false;
            }
        }
        return true;
    }

    // ────────────────────────────────────────────────────────────
    // H-2b 三级汇总恒等（精确相等，容差 0）
    // ────────────────────────────────────────────────────────────

    /**
     * 归因平衡的<b>精确</b>变体（H-2b）：{@code Σ cell.diff() } 与
     * {@code scenarioProfit − baselineProfit} 必须 {@code compareTo == 0}。
     * 与 {@link #balanced} 的区别只在容差：本方法 <b>0</b>，差 0.01 也判不平。
     */
    public static boolean balancedExact(List<Cell> cells, BigDecimal baselineProfit, BigDecimal scenarioProfit) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Cell c : cells) {
            sum = sum.add(c.diff());
        }
        return sum.compareTo(scenarioProfit.subtract(baselineProfit)) == 0;
    }

    /**
     * 三级汇总恒等断言（PRD-09 v1.2 §2.2，自下而上：逐项 → 小计 → 总计）。
     *
     * <p>判据（全部 {@code compareTo == 0}，<b>禁 epsilon</b>）：</p>
     * <ol>
     *   <li><b>L1 逐项</b>：每格 Δ = scenario − baseline，且 Σ 逐项 必须等于总计；</li>
     *   <li><b>L2 小计</b>：每个小计组的 Σ 组内逐项 Δ 必须等于该组给定小计；
     *       给定的小计组若无对应逐项（或反之）本身即恒等破坏；</li>
     *   <li><b>L3 总计</b>：Σ 小计 必须等于企业级总计（{@code 企业利润 − Σ部门利润 = 0} 的断言点）。</li>
     * </ol>
     *
     * <p>纯函数、无 Spring/DB 依赖（C.8 Calculator 纪律）；金额为 null 视为数据缺陷并登记违反项，
     * 禁静默按 0 参与汇总（X-44）。</p>
     */
    public static ThreeLevelReport checkThreeLevel(List<Item> items, List<Subtotal> subtotals,
                                                   BigDecimal grandTotal) {
        List<Violation> violations = new ArrayList<>();
        List<Item> itemList = items == null ? List.of() : items;
        List<Subtotal> subtotalList = subtotals == null ? List.of() : subtotals;

        // L1 逐项：null 金额即缺陷；同时按组累加（BigDecimal 精确加法，不舍入）
        Map<String, BigDecimal> computedSubtotals = new java.util.LinkedHashMap<>();
        BigDecimal sumItems = BigDecimal.ZERO;
        for (Item item : itemList) {
            Cell cell = item.cell();
            if (cell.baseline() == null || cell.scenario() == null) {
                violations.add(new Violation(Level.ITEM,
                        cell.projectId() + "#" + cell.period(), BigDecimal.ZERO, BigDecimal.ZERO));
                continue;
            }
            BigDecimal diff = cell.diff();
            sumItems = sumItems.add(diff);
            computedSubtotals.merge(item.groupKey(), diff, BigDecimal::add);
        }

        // L2 小计：组内 Σ逐项 Δ 与上游给定小计精确相等；组集合必须两侧一致
        BigDecimal sumSubtotals = BigDecimal.ZERO;
        java.util.Set<String> coveredGroups = new java.util.HashSet<>();
        for (Subtotal st : subtotalList) {
            coveredGroups.add(st.groupKey());
            BigDecimal computed = computedSubtotals.getOrDefault(st.groupKey(), BigDecimal.ZERO);
            if (st.amount() == null) {
                violations.add(new Violation(Level.SUBTOTAL, st.groupKey(), computed, BigDecimal.ZERO));
                continue;
            }
            if (computed.compareTo(st.amount()) != 0) {
                violations.add(new Violation(Level.SUBTOTAL, st.groupKey(), computed, st.amount()));
            }
            sumSubtotals = sumSubtotals.add(st.amount());
        }
        for (String group : computedSubtotals.keySet()) {
            if (!coveredGroups.contains(group)) {
                violations.add(new Violation(Level.SUBTOTAL, group,
                        computedSubtotals.getOrDefault(group, BigDecimal.ZERO), BigDecimal.ZERO));
            }
        }

        // L3 总计：Σ 小计 与 Σ 逐项 都必须等于企业级总计
        if (grandTotal == null) {
            violations.add(new Violation(Level.TOTAL, "TOTAL", sumSubtotals, BigDecimal.ZERO));
        } else {
            if (sumSubtotals.compareTo(grandTotal) != 0) {
                violations.add(new Violation(Level.TOTAL, "TOTAL", grandTotal, sumSubtotals));
            }
            if (sumItems.compareTo(grandTotal) != 0) {
                violations.add(new Violation(Level.TOTAL, "ITEM_SUM", grandTotal, sumItems));
            }
        }
        return new ThreeLevelReport(violations);
    }
}
