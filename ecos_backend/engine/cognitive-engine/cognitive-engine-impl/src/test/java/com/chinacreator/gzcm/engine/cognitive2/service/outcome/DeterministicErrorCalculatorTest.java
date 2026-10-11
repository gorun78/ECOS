package com.chinacreator.gzcm.engine.cognitive2.service.outcome;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SUP-OUT-02 确定性误差计算器冻结测试
 * （《认知域与智能域补充详细设计 v1.0》§9.2 步骤 3/4 + §9.3 末段铁律）。
 *
 * <p>锁三条可复算红线：
 * <ol>
 *   <li>同输入必得同输出：BigDecimal 精确算术，relativeError 统一 4 位 HALF_UP；</li>
 *   <li>样本不适用（缺预测/缺实际/实际为 0）⇒ applicable=false + 明确原因，
 *       禁以 0 误差或 MAX 伪造（§9.2 步骤 3 / DG-6）；</li>
 *   <li>偏差分解不平 ⇒ consistent=false + 残差如实回带，禁自动摊平差额造假账（附录 B）。</li>
 * </ol>
 * 纯静态工具类，无 mock、无 I/O。</p>
 */
@DisplayName("SUP-OUT-02 确定性误差计算 (DeterministicErrorCalculator)")
class DeterministicErrorCalculatorTest {

    // ── 1. 单指标误差 compute ──

    @Test
    @DisplayName("compute 正常态: predicted=100 actual=90 ⇒ abs=10, rel=1/9 (4 位 HALF_UP), applicable=true")
    void computeNormalReturnsExactBigDecimalErrors() {
        DeterministicErrorCalculator.MetricError r =
                DeterministicErrorCalculator.compute("M_FC_PROFIT",
                        new BigDecimal("100"), new BigDecimal("90"), "calc-001");

        assertTrue(r.applicable());
        assertEquals(0, r.absoluteError().compareTo(new BigDecimal("10")));
        // 相对误差 = 10/90 = 1/9 = 0.11111... => 4 位 HALF_UP = 0.1111
        assertEquals(0, r.relativeError().compareTo(new BigDecimal("0.1111")));
        assertNull(r.notApplicableReason(), "适用样本不得带不适用原因");
        assertEquals("M_FC_PROFIT", r.metricRef());
        assertEquals("calc-001", r.calculationRef(), "引用原样回带（可复算锚点）");
    }

    @Test
    @DisplayName("compute 缺 predicted ⇒ applicable=false + 原因非空（禁 0 误差伪装预测准确）")
    void computeMissingPredictedIsNotApplicable() {
        DeterministicErrorCalculator.MetricError r = DeterministicErrorCalculator.compute(
                "M_X", null, new BigDecimal("90"), "calc-002");

        assertFalse(r.applicable());
        assertNotNull(r.notApplicableReason());
        assertTrue(r.notApplicableReason().contains("predicted missing"));
        assertNull(r.absoluteError(), "无预测值 ⇒ 绝对误差同为 null，禁写 0");
        assertNull(r.relativeError());
    }

    @Test
    @DisplayName("compute 缺 actual ⇒ applicable=false + 原因非空（未关账 ≠ 零误差）")
    void computeMissingActualIsNotApplicable() {
        DeterministicErrorCalculator.MetricError r = DeterministicErrorCalculator.compute(
                "M_X", new BigDecimal("100"), null, "calc-003");

        assertFalse(r.applicable());
        assertNotNull(r.notApplicableReason());
        assertTrue(r.notApplicableReason().contains("actual missing"));
        assertNull(r.absoluteError());
    }

    @Test
    @DisplayName("compute actual=0 ⇒ applicable=false, 原因含 actual = 0（相对误差无定义，禁伪造 0/MAX）")
    void computeZeroActualMarksRatioUndefined() {
        DeterministicErrorCalculator.MetricError r = DeterministicErrorCalculator.compute(
                "M_X", new BigDecimal("100"), BigDecimal.ZERO, "calc-004");

        assertFalse(r.applicable());
        assertNotNull(r.notApplicableReason());
        assertTrue(r.notApplicableReason().contains("actual = 0"),
                "原因必须点明 actual = 0（相对误差无定义）");
        // 绝对误差保留（|100-0|=100）；比率列必须 null，禁 0 或 MAX
        assertEquals(0, r.absoluteError().compareTo(new BigDecimal("100")));
        assertNull(r.relativeError());
    }

    // ── 2. 聚合 aggregate（零误差伪造防护）──

    @Test
    @DisplayName("aggregate 空集 ⇒ sampleCount=0 且 mae/mape=null（禁返回 0 伪装零误差）")
    void aggregateEmptyListReturnsNullMeans() {
        DeterministicErrorCalculator.Aggregate a =
                DeterministicErrorCalculator.aggregate(List.of());

        assertEquals(0, a.sampleCount());
        assertEquals(0, a.maeSampleCount());
        assertEquals(0, a.mapeSampleCount());
        assertNull(a.mae(), "无可用样本 MAE 必须 null，禁 0");
        assertNull(a.mape(), "无可用样本 MAPE 必须 null，禁 0");
    }

    @Test
    @DisplayName("aggregate 混合适用性 ⇒ 只对 applicable 样本求均值，mae/mape 分母各自正确")
    void aggregateAveragesOnlyApplicableSamples() {
        // a: applicable, abs=10, rel=0.1111   ⇒ MAE、MAPE 分子分母均含
        // b: applicable, abs=20, rel=0.6667   ⇒ 同上
        // c: not applicable（缺预测）          ⇒ 全部排除
        // d: actual=0 ⇒ compute 判 applicable=false（abs=100 保留在结果上）
        //    ⇒ MAE/MAPE 均排除（生产口径：不适用样本不参均，见报告 flag）
        DeterministicErrorCalculator.MetricError a = DeterministicErrorCalculator.compute(
                "a", new BigDecimal("100"), new BigDecimal("90"), null);
        DeterministicErrorCalculator.MetricError b = DeterministicErrorCalculator.compute(
                "b", new BigDecimal("50"), new BigDecimal("30"), null);
        DeterministicErrorCalculator.MetricError c = DeterministicErrorCalculator.compute(
                "c", null, new BigDecimal("90"), null);
        DeterministicErrorCalculator.MetricError d = DeterministicErrorCalculator.compute(
                "d", new BigDecimal("100"), BigDecimal.ZERO, null);

        DeterministicErrorCalculator.Aggregate agg =
                DeterministicErrorCalculator.aggregate(List.of(a, b, c, d));

        assertEquals(4, agg.sampleCount(), "total 含不适用项（禁静默丢弃）");
        assertEquals(2, agg.maeSampleCount());
        assertEquals(2, agg.mapeSampleCount());
        // MAE = (10 + 20) / 2 = 15.0000
        assertEquals(0, agg.mae().compareTo(new BigDecimal("15.0000")));
        // MAPE = (0.1111 + 0.6667) / 2 = 0.3889
        assertEquals(0, agg.mape().compareTo(new BigDecimal("0.3889")));
    }

    // ── 3. 偏差分解 decompose（不平只登记残差，禁摊平）──

    @Test
    @DisplayName("decompose 一致: total=10, 贡献 {a:6,b:4}, 容差 0.01 ⇒ consistent=true 无残差告警")
    void decomposeConsistentWhenSumMatchesWithinTolerance() {
        Map<String, BigDecimal> contrib = new LinkedHashMap<>();
        contrib.put("a", new BigDecimal("6"));
        contrib.put("b", new BigDecimal("4"));

        DeterministicErrorCalculator.Decomposition d = DeterministicErrorCalculator.decompose(
                new BigDecimal("10"), contrib, new BigDecimal("0.01"));

        assertTrue(d.consistent());
        assertEquals(0, d.sum().compareTo(new BigDecimal("10")));
        assertEquals(0, d.residual().compareTo(BigDecimal.ZERO));
        assertEquals(0, d.tolerance().compareTo(new BigDecimal("0.01")));
        assertNull(d.reason(), "一致时 reason 必须 null");
    }

    @Test
    @DisplayName("decompose 不一致: total=10, 贡献 {a:6} ⇒ consistent=false, residual=4, reason 非空")
    void decomposeInconsistentKeepsResidual() {
        Map<String, BigDecimal> contrib = new LinkedHashMap<>();
        contrib.put("a", new BigDecimal("6"));

        DeterministicErrorCalculator.Decomposition d = DeterministicErrorCalculator.decompose(
                new BigDecimal("10"), contrib, new BigDecimal("0.01"));

        assertFalse(d.consistent());
        assertEquals(0, d.sum().compareTo(new BigDecimal("6")));
        assertEquals(0, d.residual().compareTo(new BigDecimal("4")), "残差必须如实回带，禁摊平");
        assertNotNull(d.reason());
    }

    @Test
    @DisplayName("decompose totalDelta=null ⇒ consistent=false（fail-loud，禁默认 0 差额）")
    void decomposeNullTotalDeltaFailsLoud() {
        Map<String, BigDecimal> contrib = new LinkedHashMap<>();
        contrib.put("a", new BigDecimal("6"));

        DeterministicErrorCalculator.Decomposition d = DeterministicErrorCalculator.decompose(
                null, contrib, null);

        assertFalse(d.consistent());
        assertNotNull(d.reason());
        assertEquals(0, d.tolerance().compareTo(DeterministicErrorCalculator.DEFAULT_TOLERANCE),
                "tolerance=null 回落默认 0.0001");
    }

    @Test
    @DisplayName("decompose 空贡献 ⇒ consistent=false + reason（无可分解不是成功）")
    void decomposeEmptyContributionsFailsLoud() {
        DeterministicErrorCalculator.Decomposition d = DeterministicErrorCalculator.decompose(
                new BigDecimal("10"), new LinkedHashMap<>(), null);

        assertFalse(d.consistent());
        assertNotNull(d.reason());
    }

    // ── 4. computeAll（不适用项禁静默丢弃, DG-6 空集/故障可分）──

    @Test
    @DisplayName("computeAll 不丢弃不适用项：入 3 条（2 适用 + 1 缺预测）⇒ 出 3 条且原因保留")
    void computeAllKeepsNotApplicableItems() {
        List<DeterministicErrorCalculator.MetricInput> inputs = List.of(
                new DeterministicErrorCalculator.MetricInput("m1", new BigDecimal("1"), new BigDecimal("2"), null),
                new DeterministicErrorCalculator.MetricInput("m2", new BigDecimal("3"), new BigDecimal("3"), null),
                new DeterministicErrorCalculator.MetricInput("m3", null, new BigDecimal("3"), null));

        List<DeterministicErrorCalculator.MetricError> out =
                DeterministicErrorCalculator.computeAll(inputs);

        assertEquals(inputs.size(), out.size(), "不适用项必须保留在结果内");
        assertTrue(out.get(0).applicable());
        assertTrue(out.get(1).applicable());
        assertFalse(out.get(2).applicable());
        assertNotNull(out.get(2).notApplicableReason());
    }
}
