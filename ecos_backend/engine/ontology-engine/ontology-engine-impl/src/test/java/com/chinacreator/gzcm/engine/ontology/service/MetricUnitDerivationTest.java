package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.model.BizProfitMetrics;
import com.chinacreator.gzcm.engine.ontology.model.UnitDimension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F03-02 单位推导（W76/~C?）。纯函数按 10 项固定指标 + 反例验证推导链。
 */
class MetricUnitDerivationTest {

    private final MetricUnitAnalyzer a = new MetricUnitAnalyzer();

    @Test
    @DisplayName("F03-02 十项指标逐条推导成功，顶层单位与登记一致")
    void allTenSpecsDerive() {
        for (BizProfitMetrics.Spec s : BizProfitMetrics.all()) {
            MetricUnitAnalyzer.Result r = a.analyze(s.expression, s.leafUnits);
            assertFalse(r.conflicted, s.code + " 应推导通过: " + r.message + " chain=" + r.chain);
            assertEquals(s.derivedUnit.name(), r.derivedUnit,
                    s.code + " 顶层单位应 = " + s.derivedUnit + " chain=" + r.chain);
            assertTrue(r.chain != null && !r.chain.isEmpty(), s.code + " 应带推导链");
        }
    }

    @Test
    @DisplayName("M_FC_PROFIT：金额-金额 = 金额（正例）")
    void profitPositive() {
        BizProfitMetrics.Spec s = BizProfitMetrics.M_FC_PROFIT;
        MetricUnitAnalyzer.Result r = a.analyze(s.expression, s.leafUnits);
        assertFalse(r.conflicted);
        assertEquals("AMOUNT", r.derivedUnit);
        assertNotNull(r.chain);
    }

    @Test
    @DisplayName("比率 − 金额 → 冲突 400，错误码 ECOS-ONTO-021 + 链可读")
    void ratioMinusAmountConflict() {
        Map<String, UnitDimension> leaf = Map.of(
                "numerator", UnitDimension.AMOUNT,
                "denominator", UnitDimension.AMOUNT,
                "amount", UnitDimension.AMOUNT);
        // 比率 = 金额/金额；比率 − 金额 = 冲突
        String expr = "(numerator / denominator) - amount";
        MetricUnitAnalyzer.Result r = a.analyze(expr, leaf);
        assertTrue(r.conflicted, "比率 − 金额 必须冲突");
        assertEquals("ECOS-ONTO-021", r.errorCode);
        assertNotNull(r.message);
        assertNotNull(r.chain);
    }

    @Test
    @DisplayName("SUM(比率) → 单位 = 实参单位（RATIO），不冲突（V2a 层面）；由 V2b 拦截可加聚合")
    void sumOfRatioIsRatioUnit() {
        // 表达式内层 SUM(x/x) 类型（fc_revenue / target_revenue）→ RATIO
        BizProfitMetrics.Spec s = BizProfitMetrics.M_REALIZATION_RATE;
        MetricUnitAnalyzer.Result r = a.analyze(s.expression, s.leafUnits);
        assertFalse(r.conflicted);
        assertEquals("RATIO", r.derivedUnit);

        // 外层再 SUM(比率) → 单位仍为 RATIO
        String outer = "SUM(fc_revenue / target_revenue)";
        MetricUnitAnalyzer.Result r2 = a.analyze(outer, s.leafUnits);
        assertFalse(r2.conflicted, "SUM(RATIO) 单位层应保持 RATIO，冲突交由 V2b");
        assertEquals("RATIO", r2.derivedUnit);
    }

    @Test
    @DisplayName("金额 / 金额 = RATIO（兑现率推导）")
    void ratioDerivation() {
        MetricUnitAnalyzer.Result r = a.analyze(
                "fc_revenue / target_revenue",
                Map.of("fc_revenue", UnitDimension.AMOUNT, "target_revenue", UnitDimension.AMOUNT));
        assertFalse(r.conflicted);
        assertEquals("RATIO", r.derivedUnit);
    }

    @Test
    @DisplayName("金额 / 人数 = 金额（人均口径）")
    void amountDivHeadcountIsAmount() {
        MetricUnitAnalyzer.Result r = a.analyze(
                "salary_total / headcount",
                Map.of("salary_total", UnitDimension.AMOUNT, "headcount", UnitDimension.HEADCOUNT));
        assertFalse(r.conflicted);
        assertEquals("AMOUNT", r.derivedUnit);
    }

    @Test
    @DisplayName("月 − 月 = 月（回款周期）")
    void monthMinusMonth() {
        MetricUnitAnalyzer.Result r = a.analyze(
                "(due_month - collect_month)",
                Map.of("due_month", UnitDimension.MONTH, "collect_month", UnitDimension.MONTH));
        assertFalse(r.conflicted);
        assertEquals("MONTH", r.derivedUnit);
    }

    @Test
    @DisplayName("金额 * 金额 → 冲突（乘法未在文档白名单）")
    void multiplicationNotInWhitelist() {
        MetricUnitAnalyzer.Result r = a.analyze(
                "a * b",
                Map.of("a", UnitDimension.AMOUNT, "b", UnitDimension.AMOUNT));
        assertTrue(r.conflicted, "乘法未白名单应冲突，保护 PRD 定义的可推导集合");
    }

    @Test
    @DisplayName("常量透明：X + 100 = X（数字字面量 NONE）")
    void constantTransparency() {
        MetricUnitAnalyzer.Result r = a.analyze(
                "salary + 100",
                Map.of("salary", UnitDimension.AMOUNT));
        assertFalse(r.conflicted);
        assertEquals("AMOUNT", r.derivedUnit);
    }
}
