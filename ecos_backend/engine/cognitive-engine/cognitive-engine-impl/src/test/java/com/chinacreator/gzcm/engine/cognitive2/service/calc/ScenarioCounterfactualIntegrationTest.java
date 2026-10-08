package com.chinacreator.gzcm.engine.cognitive2.service.calc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 设计-05 F05-10 反事实归因冻结验收（确定性重算纯函数层）。
 *
 * <p>要点1：{@code overrides} 只覆盖命中范围，其余继承基准快照（非命中 cell diff 必须=0）；
 * 要点3：{@code sum(contribution) == scenarioProfit − baselineProfit}（容差 ±0.01），
 * 不平即 FAIL → 调用方 503（ECOS-COG-502），禁返回不平数据。</p>
 */
class ScenarioCounterfactualIntegrationTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static ScenarioBalanceChecker.Cell cell(String project, int period, String baseline, String scenario) {
        return new ScenarioBalanceChecker.Cell(project, period, bd(baseline), bd(scenario));
    }

    @Test
    @DisplayName("F05-10①：overrides 只覆盖命中范围 — 其他项目/期数 diff 必为 0")
    void onlyOverriddenScopeDiffs() {
        List<ScenarioBalanceChecker.Cell> cells = List.of(
                cell("p1", 202601, "100.00", "120.00"),
                cell("p1", 202602, "100.00", "110.00"),
                cell("p2", 202601, "80.00", "80.00"));
        assertTrue(ScenarioBalanceChecker.onlyOverriddenScopeDiffs(cells, List.of("p1")),
                "p2 全期继承基准（diff=0）应判合规");

        List<ScenarioBalanceChecker.Cell> leak = List.of(
                cell("p1", 202601, "100.00", "120.00"),
                cell("p2", 202601, "80.00", "88.00"));
        assertFalse(ScenarioBalanceChecker.onlyOverriddenScopeDiffs(leak, List.of("p1")),
                "非命中项目 diff!=0 = 归因泄漏，必须判 false");
    }

    @Test
    @DisplayName("F05-10③：sum(contribution) 与净利差容差 ±0.01 内平衡；不平 → FAIL（调用方 503）")
    void contributionSumBalancesWithinCent() {
        List<ScenarioBalanceChecker.Cell> cells = List.of(
                cell("p1", 202601, "100.0001", "120.0000"),
                cell("p2", 202601, "50.0000", "59.9999"));
        // sum(diff) = 19.9999 + 9.9999 = 29.9998；net = 30.0000；|Δ| = 0.0002 ≤ 0.01
        assertTrue(ScenarioBalanceChecker.isBalanced(cells, bd("150.00"), bd("180.00")),
                "分位级舍入差必须在 ±0.01 容差内判平衡");

        List<ScenarioBalanceChecker.Cell> off = List.of(cell("p1", 202601, "100.00", "120.00"));
        assertFalse(ScenarioBalanceChecker.isBalanced(off, bd("100.00"), bd("120.02")),
                "0.02 不平超容差 → 必判 FAIL（响应前自检失败走 503，禁返回不平数据）");
    }
}
