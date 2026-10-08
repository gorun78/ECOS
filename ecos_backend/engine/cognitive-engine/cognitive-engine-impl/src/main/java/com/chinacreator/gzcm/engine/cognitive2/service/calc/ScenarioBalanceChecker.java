package com.chinacreator.gzcm.engine.cognitive2.service.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 反事实归因平衡校验 + 确定性 diff（详细设计-05 F05-10 / C117 / §五"新增纯函数"）。
 *
 * <p>铁律：认知域只做 diff 归因，金额一律来自预测运行服务（RAG 推演禁作金额来源）。
 * {@code sum(contribution) == scenarioProfit − baselineProfit}（容差 ±0.01），不平即
 * {@code balanced=false} → 调用方 503（{@code ECOS-COG-502}），禁返回不平数据。
 * 覆盖范围之外（非命中 project/period）cell 必须 diff=0（只覆盖命中范围）。</p>
 */
public final class ScenarioBalanceChecker {

    /** 一格 cell：某 project+period 的基准/情景/Δ。 */
    public record Cell(String projectId, int period, BigDecimal baseline, BigDecimal scenario) {
        public BigDecimal diff() { return scenario.subtract(baseline); }
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
}
