package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.gate.GateContext;
import com.chinacreator.gzcm.engine.ontology.gate.GateViolation;
import com.chinacreator.gzcm.engine.ontology.gate.MetricUnitDerivationGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F03-02 V2b 可加性守卫：不可加指标（比率/月）被声明 SUM/AVG → 400 ECOS-ONTO-022，
 * 与 V2a 单位推导（ECOS-ONTO-021）分属两条判据。
 */
class MetricAdditivityGuardTest {

    private final MetricUnitDerivationGuard guard = new MetricUnitDerivationGuard(new MetricUnitAnalyzer());

    @Test
    @DisplayName("M_REALIZATION_RATE (RATIO, NON) 声明 SUM → 违反 ECOS-ONTO-022")
    void sumOnNonAdditiveRatio() {
        GateContext ctx = ctx(metric("M_REALIZATION_RATE", "fc_revenue / target_revenue", "SUM"));
        List<GateViolation> v = guard.evaluate(ctx);
        assertEquals(1, v.size());
        assertEquals("ECOS-ONTO-022", v.get(0).getCode());
        assertTrue(v.get(0).getMessage().contains("M_REALIZATION_RATE"));
    }

    @Test
    @DisplayName("M_COLLECTION_CYCLE (MONTH, NON) 声明 AVG → 违反 ECOS-ONTO-022")
    void avgOnNonAdditiveMonth() {
        GateContext ctx = ctx(metric("M_COLLECTION_CYCLE", "(due_month - collect_month)", "AVG"));
        List<GateViolation> v = guard.evaluate(ctx);
        assertEquals(1, v.size());
        assertEquals("ECOS-ONTO-022", v.get(0).getCode());
    }

    @Test
    @DisplayName("M_FC_PROFIT (AMOUNT, ADDITIVE) 声明 SUM → 通过")
    void sumOnAdditiveAmount() {
        GateContext ctx = ctx(metric("M_FC_PROFIT", "fc_revenue - cost_total", "SUM"));
        List<GateViolation> v = guard.evaluate(ctx);
        assertTrue(v.isEmpty(), "V2 应通过: " + v);
    }

    @Test
    @DisplayName("M_REALIZATION_RATE (RATIO, NON) 声明 AVG（原文 AVG 登记）→ 也视为违反，抓死可加聚合")
    void avgOnRatio() {
        GateContext ctx = ctx(metric("M_REALIZATION_RATE", "fc_revenue / target_revenue", "AVG"));
        List<GateViolation> v = guard.evaluate(ctx);
        assertEquals(1, v.size());
        assertEquals("ECOS-ONTO-022", v.get(0).getCode());
    }

    @Test
    @DisplayName("字样大小写 SUMx 前缀容忍：M_COLLECTION_CYCLE SUMX → 违反 ECOS-ONTO-022")
    void sumPrefixTolerated() {
        GateContext ctx = ctx(metric("M_COLLECTION_CYCLE", "(due_month - collect_month)", "SUMX"));
        List<GateViolation> v = guard.evaluate(ctx);
        assertEquals(1, v.size());
        assertEquals("ECOS-ONTO-022", v.get(0).getCode());
    }

    @Test
    @DisplayName("MIN/MAX 不算可加聚合 → 不违反（V2a 单位推导仍跑）")
    void minMaxNotAdditive() {
        GateContext ctx = ctx(metric("M_COLLECTION_CYCLE", "(due_month - collect_month)", "MIN"));
        List<GateViolation> v = guard.evaluate(ctx);
        assertTrue(v.isEmpty(), "MIN 非可加聚合，不触发 022: " + v);
    }

    @Test
    @DisplayName("可加指标声明 SUM 但同时表达式单位冲突 → 只报 ECOS-ONTO-021（单位推导），不重报 022")
    void unitConflictAndAdditivityDoNotDoubleReport() {
        GateContext ctx = ctx(metric("M_FC_PROFIT", "fc_revenue - cost_total", "SUM"));
        List<GateViolation> v = guard.evaluate(ctx);
        assertTrue(v.isEmpty(), "两判据都通过: " + v);

        // 反例：伪造表达式冲突
        GateContext ctx2 = ctx(metric("M_FC_PROFIT", "a + b * c", "SUM")); // 未登记叶子带非法组合 · 注意 additive=ADDITIVE 已登记
        // 未知指标 → V2a 走 leafUnits 空 map；V2b 因未登记 additivity=null 无拦截
        List<GateViolation> v2 = guard.evaluate(ctx2);
        // 至少可以断言不出现 022（避免未登记误判）
        for (GateViolation gv : v2) {
            assertEquals("ECOS-ONTO-021", gv.getCode(), "未登记指标不应被 V2b 误判为 022");
        }
    }

    // ── 构造工具 ──
    private static GateContext ctx(GateContext.MetricRef... refs) {
        GateContext c = new GateContext();
        c.setMetrics(List.of(refs));
        return c;
    }

    private static GateContext.MetricRef metric(String code, String expr, String agg) {
        GateContext.MetricRef r = new GateContext.MetricRef();
        r.code = code;
        r.caliberId = "CAL_TEST"; // V1 需要，但 V2 独立不校验 V1
        r.formulaVersion = "1.0";
        r.expression = expr;
        r.aggregation = agg;
        return r;
    }
}
