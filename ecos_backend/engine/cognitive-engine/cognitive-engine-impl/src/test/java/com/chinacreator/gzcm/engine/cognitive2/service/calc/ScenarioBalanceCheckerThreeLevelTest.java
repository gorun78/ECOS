package com.chinacreator.gzcm.engine.cognitive2.service.calc;

import com.chinacreator.gzcm.engine.cognitive2.service.calc.ScenarioBalanceChecker.Cell;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.ScenarioBalanceChecker.Item;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.ScenarioBalanceChecker.Level;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.ScenarioBalanceChecker.Subtotal;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.ScenarioBalanceChecker.ThreeLevelReport;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.ScenarioBalanceChecker.Violation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三级汇总恒等断言单测（详设-09 v1.2 §十一 H-2b，PRD-09 v1.2 §2.2）。
 *
 * <p>本批核心判据是 <b>容差为 0</b>：{@code balanced}（归因面 ±0.01，前端对照表同源，本批未改）
 * 与 {@code balancedExact} / {@code checkThreeLevel}（汇总恒等面，精确相等）必须在同一组数据上
 * 给出相反结论 —— 差 0.01 时前者过、后者必失败，以此证明汇总面不存在 epsilon 放行。</p>
 */
class ScenarioBalanceCheckerThreeLevelTest {

    private static Cell cell(String project, int period, String baseline, String scenario) {
        return new Cell(project, period, new BigDecimal(baseline), new BigDecimal(scenario));
    }

    @Test
    @DisplayName("L1/L2/L3 全部精确相等 ⇒ exact 通过（自下而上：环节 → 部门小计 → 企业总计）")
    void threeLevelExactPassWhenAllLevelsTieOut() {
        List<Item> items = List.of(
                new Item("DEPT-1", cell("P1", 202601, "100.00", "180.00")),
                new Item("DEPT-1", cell("P2", 202601, "50.00", "70.00")),
                new Item("DEPT-2", cell("P3", 202601, "30.00", "20.00")));
        List<Subtotal> subtotals = List.of(
                new Subtotal("DEPT-1", new BigDecimal("100.00")),
                new Subtotal("DEPT-2", new BigDecimal("-10.00")));

        ThreeLevelReport report = ScenarioBalanceChecker.checkThreeLevel(items, subtotals, new BigDecimal("90.00"));

        assertTrue(report.exact(), () -> "应无违反项，实际=" + report.violations());
        assertEquals(null, report.firstFailedLevel());
    }

    @Test
    @DisplayName("总计差 0.01 必失败（L3 无 epsilon 放行），并定位到 TOTAL 层")
    void totalOffByOneCentFails() {
        List<Item> items = List.of(new Item("DEPT-1", cell("P1", 202601, "0", "100.00")));
        List<Subtotal> subtotals = List.of(new Subtotal("DEPT-1", new BigDecimal("100.00")));

        ThreeLevelReport report = ScenarioBalanceChecker.checkThreeLevel(
                items, subtotals, new BigDecimal("100.01"));

        assertFalse(report.exact());
        assertEquals(Level.TOTAL, report.firstFailedLevel());
        Violation total = report.violations().get(0);
        assertEquals(0, total.delta().abs().compareTo(new BigDecimal("0.01")));
    }

    @Test
    @DisplayName("同一组 0.01 差额：归因面（±0.01）通过、汇总恒等面（0 容差）失败 ⇒ 证明无 epsilon")
    void attributionToleranceSurfaceStaysSeparateFromExactSurface() {
        List<Cell> cells = List.of(cell("P1", 202601, "0", "100.00"));
        BigDecimal baselineProfit = BigDecimal.ZERO;
        BigDecimal scenarioProfit = new BigDecimal("100.01");

        assertTrue(ScenarioBalanceChecker.balanced(cells, baselineProfit, scenarioProfit),
                "归因面容差 ±0.01 未变（前端对照表同源，本批禁改语义）");
        assertFalse(ScenarioBalanceChecker.balancedExact(cells, baselineProfit, scenarioProfit),
                "汇总恒等面必须精确相等（PRD-09 §2.2，|企业利润 − Σ部门利润| = 0）");
        assertTrue(ScenarioBalanceChecker.balancedExact(
                cells, baselineProfit, new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("精确相等按 compareTo 判定：等值不同 scale（0.3 vs 0.30）仍算平")
    void exactCheckComparesValueNotScale() {
        List<Cell> cells = List.of(cell("P1", 202601, "0", "0.3"));
        assertTrue(ScenarioBalanceChecker.balancedExact(
                cells, BigDecimal.ZERO, new BigDecimal("0.30")));
    }

    @Test
    @DisplayName("L2 小计与组内 Σ逐项 差 0.01 ⇒ SUBTOTAL 违反，expected=算出的值、actual=上游给定的值")
    void subtotalOffByOneCentFails() {
        List<Item> items = List.of(
                new Item("DEPT-1", cell("P1", 202601, "100.00", "180.00")),
                new Item("DEPT-1", cell("P2", 202601, "50.00", "70.00")));

        ThreeLevelReport report = ScenarioBalanceChecker.checkThreeLevel(items,
                List.of(new Subtotal("DEPT-1", new BigDecimal("100.01"))), new BigDecimal("100.01"));

        assertEquals(Level.SUBTOTAL, report.firstFailedLevel());
        Violation first = report.violations().get(0);
        assertEquals(Level.SUBTOTAL, first.level());
        assertEquals(0, first.expected().compareTo(new BigDecimal("100.00")));
        assertEquals(0, first.actual().compareTo(new BigDecimal("100.01")));
    }

    @Test
    @DisplayName("逐项金额为 null ⇒ 数据缺陷记 ITEM 违反，禁按 0 参与汇总（X-44）")
    void nullCellAmountIsRecordedNotZeroFilled() {
        Cell broken = new Cell("P9", 202601, null, new BigDecimal("10"));
        ThreeLevelReport report = ScenarioBalanceChecker.checkThreeLevel(
                List.of(new Item("DEPT-1", broken)),
                List.of(new Subtotal("DEPT-1", BigDecimal.ZERO)),
                BigDecimal.ZERO);

        assertFalse(report.exact());
        assertEquals(Level.ITEM, report.firstFailedLevel());
        assertTrue(report.violations().stream().anyMatch(v -> v.key().equals("P9#202601")));
    }

    @Test
    @DisplayName("小计组与逐项组集合不一致（多出 / 缺失组）本身就是恒等破坏")
    void groupSetMismatchFails() {
        List<Item> items = List.of(new Item("DEPT-1", cell("P1", 202601, "0", "10")));

        ThreeLevelReport orphanSubtotal = ScenarioBalanceChecker.checkThreeLevel(items,
                List.of(new Subtotal("DEPT-1", new BigDecimal("10")),
                        new Subtotal("DEPT-9", new BigDecimal("5"))),
                new BigDecimal("15"));
        assertTrue(orphanSubtotal.violations().stream()
                .anyMatch(v -> v.level() == Level.SUBTOTAL && v.key().equals("DEPT-9")));

        ThreeLevelReport missingSubtotal = ScenarioBalanceChecker.checkThreeLevel(items,
                List.of(new Subtotal("DEPT-8", new BigDecimal("10"))), new BigDecimal("10"));
        assertTrue(missingSubtotal.violations().stream()
                .anyMatch(v -> v.level() == Level.SUBTOTAL && v.key().equals("DEPT-1")));
    }

    @Test
    @DisplayName("企业总计缺失（null）⇒ TOTAL 违反，禁当作 0 处理")
    void nullGrandTotalFails() {
        ThreeLevelReport report = ScenarioBalanceChecker.checkThreeLevel(
                List.of(new Item("DEPT-1", cell("P1", 202601, "0", "10"))),
                List.of(new Subtotal("DEPT-1", new BigDecimal("10"))), null);
        assertFalse(report.exact());
        assertEquals(Level.TOTAL, report.firstFailedLevel());
    }

    @Test
    @DisplayName("多部门自下而上：Σ部门利润 = 企业利润 的断言点（PRD-09 §2.2 场景）")
    void bottomUpAggregationTiesOutAcrossDepartments() {
        List<Item> items = List.of(
                new Item("DEPT-A", cell("P1", 202601, "1000", "1200")),
                new Item("DEPT-A", cell("P1", 202602, "800", "900")),
                new Item("DEPT-B", cell("P2", 202601, "500", "480")));
        ThreeLevelReport report = ScenarioBalanceChecker.checkThreeLevel(items,
                List.of(new Subtotal("DEPT-A", new BigDecimal("300")),
                        new Subtotal("DEPT-B", new BigDecimal("-20"))),
                new BigDecimal("280"));
        assertTrue(report.exact(), () -> "实际违反项=" + report.violations());
    }

    @Test
    @DisplayName("report 构造对 null 清单容错为空清单（禁 NPE 掩盖真实结论）")
    void reportNormalizesNullViolationList() {
        assertTrue(new ThreeLevelReport(null).exact());
    }
}
