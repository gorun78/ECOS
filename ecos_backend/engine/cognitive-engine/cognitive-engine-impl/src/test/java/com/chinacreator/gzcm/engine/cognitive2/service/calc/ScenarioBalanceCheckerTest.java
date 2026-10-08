package com.chinacreator.gzcm.engine.cognitive2.service.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F05-10 支撑：ScenarioBalanceChecker 平衡判定 + 覆盖范围校验（±0.01 容差）。 */
class ScenarioBalanceCheckerTest {

    @Test
    void contributionsSumToNetDelta() {
        List<ScenarioBalanceChecker.Cell> cells = List.of(
                new ScenarioBalanceChecker.Cell("P1", 1, bd("100"), bd("110")),
                new ScenarioBalanceChecker.Cell("P1", 2, bd("50"),  bd("60.5")),
                new ScenarioBalanceChecker.Cell("P2", 1, bd("10"),  bd("77.5")));

        BigDecimal baselineProfit = bd("160");  // 100+50+10
        BigDecimal scenarioProfit = bd("248");  // 110+60.5+77.5, net = 88
        assertTrue(ScenarioBalanceChecker.balanced(cells, baselineProfit, scenarioProfit),
                "三格 diff sum = 88 与 scenarioProfit - baselineProfit = 88，平衡");
    }

    @Test
    void unbalancedOverToleranceFails() {
        List<ScenarioBalanceChecker.Cell> cells = List.of(
                new ScenarioBalanceChecker.Cell("P1", 1, bd("0"), bd("1.10")));
        assertFalse(ScenarioBalanceChecker.balanced(cells, bd("0"), bd("1.12")),
                "差 0.02 > 容差 0.01 → 判平衡失守");
        assertTrue(ScenarioBalanceChecker.balanced(cells, bd("0"), bd("1.105")),
                "差 0.005 ≤ 容差 → 平衡");
    }

    @Test
    void onlyOverriddenProjectDiffs() {
        List<ScenarioBalanceChecker.Cell> cells = List.of(
                new ScenarioBalanceChecker.Cell("P1", 1, bd("10"), bd("12")),   // overridden
                new ScenarioBalanceChecker.Cell("P2", 1, bd("10"), bd("10"))); // 未 overridden: diff=0
        assertTrue(ScenarioBalanceChecker.onlyOverriddenScopeDiffs(cells, List.of("P1")));

        List<ScenarioBalanceChecker.Cell> leaky = List.of(
                new ScenarioBalanceChecker.Cell("P1", 1, bd("10"), bd("12")),
                new ScenarioBalanceChecker.Cell("P2", 1, bd("10"), bd("10.02"))); // 未 overridden: diff≠0
        assertFalse(ScenarioBalanceChecker.onlyOverriddenScopeDiffs(leaky, List.of("P1")),
                "未 overridden 项目 diff≠0 → 判定为归因泄漏");
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
}
