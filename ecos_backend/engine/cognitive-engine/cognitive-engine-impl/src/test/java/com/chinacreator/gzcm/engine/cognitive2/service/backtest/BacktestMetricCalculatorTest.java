package com.chinacreator.gzcm.engine.cognitive2.service.backtest;

import com.chinacreator.gzcm.engine.cognitive.api.backtest.BacktestMetricSummary;
import com.chinacreator.gzcm.engine.cognitive.api.backtest.DeviationDirection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回测三指标 + {@code LOW_SAMPLE} 守卫的确定性冻结测试（详设-09 v1.2 §十一 H-4 要点 1/2，W225）。
 *
 * <p>本测试锁的是四条<b>诚实语义</b>，不是数值巧合：</p>
 * <ol>
 *   <li>不可得的比率一律 {@code null} + fail-loud 告警，禁写 0 或 100%（DG-6 / X-44）；</li>
 *   <li>区间三值缺位只进排除集，绝不由 P50 或邻值合成（R-62 ①）；</li>
 *   <li>样本不足时越限断言进 {@code suppressedAssertions} 而非结论（守卫"不参与告警"）；</li>
 *   <li>阈值未配置 ⇒ 该闸不评估，禁代码缺省兜底（R-66 ① 的对偶面）。</li>
 * </ol>
 */
@DisplayName("H-4 回测三指标与 LOW_SAMPLE 守卫（BacktestMetricCalculator）")
class BacktestMetricCalculatorTest {

    private static final String K_MIN_SAMPLES = "ecos.fc.backtest.review_threshold.min_samples";
    private static final String K_MAPE = "ecos.fc.backtest.review_threshold.mape_ratio";
    private static final String K_BAND = "ecos.fc.backtest.review_threshold.on_target_band";
    private static final String K_MIN_INTERVAL = "ecos.fc.backtest.review_threshold.min_interval_coverage";
    private static final String K_MIN_DATA = "ecos.fc.backtest.review_threshold.min_data_coverage";

    private static BacktestMetricCalculator.Sample sample(String ref, String predicted, String actual,
                                                          String p10, String p90, String sourceRef) {
        BigDecimal pd = predicted == null ? null : new BigDecimal(predicted);
        BigDecimal ac = actual == null ? null : new BigDecimal(actual);
        return new BacktestMetricCalculator.Sample(ref, pd, ac,
                p10 == null ? null : new BigDecimal(p10),
                p90 == null ? null : new BigDecimal(p90),
                sourceRef, pd != null && ac != null);
    }

    private static BacktestMetricCalculator.Thresholds thresholds(BigDecimal band, BigDecimal mape,
                                                                  Integer minSamples, BigDecimal minInterval,
                                                                  BigDecimal minData) {
        Map<String, String> src = new java.util.LinkedHashMap<>();
        src.put(K_BAND, band == null ? K_BAND + "(UNSET)" : K_BAND);
        src.put(K_MAPE, mape == null ? K_MAPE + "(UNSET)" : K_MAPE);
        src.put(K_MIN_SAMPLES, minSamples == null ? K_MIN_SAMPLES + "(UNSET)" : K_MIN_SAMPLES);
        src.put(K_MIN_INTERVAL, minInterval == null ? K_MIN_INTERVAL + "(UNSET)" : K_MIN_INTERVAL);
        src.put(K_MIN_DATA, minData == null ? K_MIN_DATA + "(UNSET)" : K_MIN_DATA);
        return new BacktestMetricCalculator.Thresholds(band, mape, minSamples, minInterval, minData, src);
    }

    @Test
    @DisplayName("指标一：偏差方向取 Σ(预测−实际) 符号，逐行计数与聚合同尺")
    void deviationDirectionUsesSignedDeltaSign() {
        List<BacktestMetricCalculator.Sample> rows = List.of(
                sample("gm.revenue", "120", "100", null, null, null),   // OVER
                sample("gm.cost", "80", "90", null, null, null),        // UNDER
                sample("gm.profit", "50", "50", null, null, null));     // BALANCED（净差恰为 0 才算命中）
        BacktestMetricCalculator.Result r = BacktestMetricCalculator.compute(
                rows, 3, thresholds(null, null, null, null, null), null);

        BacktestMetricSummary s = r.summary();
        assertEquals(Map.of("OVER_FORECAST", 1, "UNDER_FORECAST", 1,
                "BALANCED", 1, "NOT_DETERMINABLE", 0), s.getDeviationDirectionCounts());
        // 净差 = +20 −10 +0 = +10 ⇒ 聚合高估（PRD 原文符号口径，非逐行投票）
        assertEquals(DeviationDirection.OVER_FORECAST.name(), s.getAggregateDeviationDirection());
        assertEquals(0, new BigDecimal("10").compareTo(s.getNetSignedDelta()));
        assertFalse(s.getNumericCaliber().contains("on_target_band ="),
                "未配置死区时口径声明必须是纯符号，禁读者误当死区判定");
        assertTrue(s.getNumericCaliber().contains(BacktestMetricCalculator.SIGN_ONLY_CALIBER));
    }

    @Test
    @DisplayName("指标一：on_target_band 配置后才用死区收窄 BALANCED（阈值单源，禁代码缺省）")
    void onTargetBandOnlyAppliesWhenConfigured() {
        BacktestMetricCalculator.Sample row = sample("gm.revenue", "102", "100", null, null, null);
        BacktestMetricCalculator.Result withoutBand = BacktestMetricCalculator.compute(
                List.of(row), 1, thresholds(null, null, null, null, null), null);
        assertEquals(DeviationDirection.OVER_FORECAST.name(),
                withoutBand.summary().getAggregateDeviationDirection());

        // 死区 5% ⇒ |2| <= 100×0.05 ⇒ BALANCED
        BacktestMetricCalculator.Result withBand = BacktestMetricCalculator.compute(
                List.of(row), 1, thresholds(new BigDecimal("0.05"), null, null, null, null), null);
        assertEquals(DeviationDirection.BALANCED.name(), withBand.summary().getAggregateDeviationDirection());
        assertEquals(0, new BigDecimal("0.05").compareTo(withBand.summary().getOnTargetBand()));
    }

    @Test
    @DisplayName("D7：预测或实际缺一侧 ⇒ NOT_DETERMINABLE，禁冒充 BALANCED")
    void missingSideIsNotDeterminable() {
        BacktestMetricCalculator.Result r = BacktestMetricCalculator.compute(
                List.of(sample("gm.revenue", "120", null, null, null, null)), 1,
                thresholds(null, null, null, null, null), null);
        assertEquals(DeviationDirection.NOT_DETERMINABLE.name(), r.summary().getAggregateDeviationDirection());
        assertEquals(1, r.summary().getDeviationDirectionCounts().get("NOT_DETERMINABLE"));
        assertNull(r.summary().getNetSignedDelta());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("AGGREGATE_DIRECTION_NOT_DETERMINABLE")
                && w.contains("never as BALANCED")));
    }

    @Test
    @DisplayName("指标二：区间覆盖率 = 命中格子/可判格子，分母只含三值齐备行")
    void intervalCoverageDividesByAssessableOnly() {
        List<BacktestMetricCalculator.Sample> rows = List.of(
                sample("a", "100", "95", "80", "120", "run-detail:1"),   // 覆盖
                sample("b", "100", "130", "80", "120", "run-detail:2"),  // 越界
                sample("c", "100", "100", null, null, null));            // 缺三值 ⇒ 排除
        BacktestMetricCalculator.Result r = BacktestMetricCalculator.compute(
                rows, 3, thresholds(null, null, null, null, null), null);
        BacktestMetricSummary s = r.summary();
        assertEquals(1, s.getIntervalCoveredCount());
        assertEquals(2, s.getIntervalAssessableSampleCount());
        assertEquals(1, s.getIntervalExcludedCount());
        assertEquals(0, new BigDecimal("0.5000").compareTo(s.getIntervalCoverage()));
        assertTrue(s.getIntervalExclusionReason().contains("c:missing p10/p90/actual triple"));
        assertEquals("run-detail:1", s.getIntervalValueSource());
    }

    @Test
    @DisplayName("D4：全行缺三值 ⇒ 覆盖率 null + seam-absent 来源，禁插值也禁冒充 0%")
    void intervalCoverageStaysNullWhenSeamAbsent() {
        BacktestMetricCalculator.Result r = BacktestMetricCalculator.compute(
                List.of(sample("a", "100", "95", null, null, null)), 1,
                thresholds(null, null, null, null, null), null);
        assertNull(r.summary().getIntervalCoverage());
        assertTrue(r.summary().getIntervalExclusionReason().contains("no assessable sample"));
        assertTrue(r.summary().getIntervalValueSource()
                .contains("interval-triple-seam-not-wired(ForecastIntervalTripleReader bean absent)"));
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("INTERVAL_COVERAGE_UNAVAILABLE")
                && w.contains("interpolation of P10/P90 is forbidden by R-62")));
    }

    @Test
    @DisplayName("数据缺陷 p10>p90 进排除集，不参与覆盖判定")
    void invertedQuantilePairIsExcludedNotGuessed() {
        BacktestMetricCalculator.Result r = BacktestMetricCalculator.compute(
                List.of(sample("a", "100", "95", "120", "80", "run-detail:1")), 1,
                thresholds(null, null, null, null, null), null);
        assertEquals(0, r.summary().getIntervalAssessableSampleCount());
        assertEquals(1, r.summary().getIntervalExcludedCount());
        assertTrue(r.summary().getIntervalExclusionReason().contains("inverted-quantile-pair p10>p90"));
    }

    @Test
    @DisplayName("指标三：数据覆盖率 = ACTUAL 格子/应预测格子，缺值 ref 逐枚列明")
    void dataCoverageCountsActualCellsAndListsMissingRefs() {
        List<BacktestMetricCalculator.Sample> rows = List.of(
                sample("a", "100", "95", null, null, null),
                sample("b", "100", "0", null, null, null),      // 实测为零 ≠ 未回灌
                sample("c", "100", null, null, null, null));    // 未回灌
        BacktestMetricCalculator.Result r = BacktestMetricCalculator.compute(
                rows, 4, thresholds(null, null, null, null, null), null);
        BacktestMetricSummary s = r.summary();
        assertEquals(4, s.getDataCoverageExpectedCount());
        assertEquals(2, s.getDataCoverageAvailableCount());
        assertEquals(0, new BigDecimal("0.5000").compareTo(s.getDataCoverage()));
        assertEquals(List.of("c"), s.getDataCoverageMissingRefs());
        assertEquals(1, s.getZeroActualCount());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("BACKTEST_SAMPLE_INCOMPLETE")
                && w.contains("null means unresolved, 0 means measured zero")));
    }

    @Test
    @DisplayName("应预测格子数为 0 ⇒ 覆盖率 null + 告警，禁冒充 100%")
    void dataCoverageStaysNullWithoutDeclaredMetrics() {
        BacktestMetricCalculator.Result r = BacktestMetricCalculator.compute(
                List.of(), 0, thresholds(null, null, null, null, null), null);
        assertNull(r.summary().getDataCoverage());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("DATA_COVERAGE_UNAVAILABLE")
                && w.contains("never reported as 100%")));
    }

    @Test
    @DisplayName("LOW_SAMPLE：实测有效样本 < min_samples ⇒ 守卫触发且越限断言被抑制、不入结论")
    void lowSampleSuppressesBreaches() {
        List<BacktestMetricCalculator.Sample> rows = List.of(
                sample("a", "130", "100", "80", "120", "run-detail:1"),
                sample("b", "100", null, null, null, null));
        BacktestMetricCalculator.Thresholds t = thresholds(null, new BigDecimal("0.15"), 5,
                new BigDecimal("0.80"), new BigDecimal("0.80"));
        BacktestMetricCalculator.Result r = BacktestMetricCalculator.compute(rows, 2, t, new BigDecimal("0.30"));
        BacktestMetricSummary s = r.summary();
        assertTrue(s.isLowSample());
        assertEquals(1, s.getMeasuredSampleCount(), "守卫按双侧齐备实测数，不是请求声明数");
        assertEquals(List.of(), s.getBreachedAssertions());
        assertEquals(2, s.getSuppressedAssertions().size());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("LOW_SAMPLE_SUPPRESSED")
                && w.contains("withheld from conclusion")));
        assertTrue(s.getLowSampleReason().contains("measured=1 < min_samples=5"));
        // 计算器仍如实回显候选越限（供审计），只是不作为结论
        assertEquals(2, r.breaches().size());
    }

    @Test
    @DisplayName("min_samples 未配置 ⇒ fail-closed 判 LOW_SAMPLE，禁代码缺省兜底")
    void unsetMinSampleIsFailClosedNotDefaulted() {
        BacktestMetricCalculator.Result r = BacktestMetricCalculator.compute(
                List.of(sample("a", "100", "100", null, null, null)), 1,
                thresholds(null, null, null, null, null), null);
        assertTrue(r.summary().isLowSample());
        assertTrue(r.summary().getLowSampleReason().contains("min-sample threshold unset"));
        assertTrue(r.summary().getLowSampleReason().contains(K_MIN_SAMPLES + "(UNSET)"));
    }

    @Test
    @DisplayName("样本充足才把越限项作为结论（覆盖率/MAPE 三闸各自可判）")
    void breachesBecomeFindingsOnlyWhenSampleSufficient() {
        List<BacktestMetricCalculator.Sample> rows = List.of(
                sample("a", "100", "150", "80", "120", "run-detail:1"),   // 区间未覆盖
                sample("b", "100", "150", "80", "120", "run-detail:2"),
                sample("c", "100", "100", "80", "120", "run-detail:3"),
                sample("d", "100", "100", "80", "120", "run-detail:4"),
                sample("e", "100", "90", "80", "120", "run-detail:5"),
                sample("f", "100", "90", "80", "120", "run-detail:6"));
        BacktestMetricCalculator.Thresholds t = thresholds(null, new BigDecimal("0.15"), 5,
                new BigDecimal("0.80"), new BigDecimal("0.90"));
        BacktestMetricCalculator.Result r = BacktestMetricCalculator.compute(rows, 6, t, new BigDecimal("0.20"));
        BacktestMetricSummary s = r.summary();
        assertFalse(s.isLowSample());
        assertEquals(6, s.getMeasuredSampleCount());
        assertEquals(List.of(), s.getSuppressedAssertions());
        assertEquals(2, s.getBreachedAssertions().size());
        assertTrue(s.getBreachedAssertions().stream()
                .anyMatch(b -> b.startsWith("MIN_INTERVAL_COVERAGE measured=0.6667 threshold=0.80 (covered=4/6)")));
        assertTrue(s.getBreachedAssertions().stream()
                .anyMatch(b -> b.startsWith("MAX_MAPE measured=0.20 threshold=0.15")));
        assertEquals(0, new BigDecimal("0.6667").compareTo(s.getIntervalCoverage()), "scale=4 HALF_UP");
        assertEquals(0, new BigDecimal("1.0000").compareTo(s.getDataCoverage()));
        assertTrue(s.getDataCoverageMissingRefs().isEmpty());
    }

    @Test
    @DisplayName("阈值配了但既有评估面没产出 MAPE ⇒ 该闸不评估，禁本类重算或代 0")
    void mapeGateUnevaluableWithoutExistingMape() {
        List<BacktestMetricCalculator.Sample> rows = List.of(
                sample("a", "100", "100", null, null, null), sample("b", "100", "100", null, null, null),
                sample("c", "100", "100", null, null, null), sample("d", "100", "100", null, null, null),
                sample("e", "100", "100", null, null, null));
        BacktestMetricCalculator.Result r = BacktestMetricCalculator.compute(
                rows, 5, thresholds(null, new BigDecimal("0.15"), 5, null, null), null);
        assertEquals(List.of(), r.summary().getBreachedAssertions());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("MAPE_GATE_UNEVALUABLE")
                && w.contains("never substituted with 0 or 1")));
    }

    @Test
    @DisplayName("覆盖率闸无可判样本 ⇒ 既不算通过也不算越限（两态严格可分）")
    void coverageGateUnevaluableWhenNoAssessableSample() {
        List<BacktestMetricCalculator.Sample> rows = List.of(
                sample("a", "100", "100", null, null, null), sample("b", "100", "100", null, null, null),
                sample("c", "100", "100", null, null, null), sample("d", "100", "100", null, null, null),
                sample("e", "100", "100", null, null, null));
        BacktestMetricCalculator.Result r = BacktestMetricCalculator.compute(
                rows, 5, thresholds(null, null, 5, new BigDecimal("0.80"), null), null);
        assertEquals(List.of(), r.summary().getBreachedAssertions());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("INTERVAL_COVERAGE_GATE_UNEVALUABLE")
                && w.contains("neither passed nor failed")));
    }

    @Test
    @DisplayName("逐行偏差方向：缺侧不可判、死区命中、纯符号三态")
    void perRowDirectionHandlesAllThreeStates() {
        assertEquals(DeviationDirection.NOT_DETERMINABLE,
                BacktestMetricCalculator.deviationDirection(sample("a", null, "100", null, null, null), null));
        assertEquals(DeviationDirection.UNDER_FORECAST,
                BacktestMetricCalculator.deviationDirection(sample("a", "90", "100", null, null, null), null));
        assertEquals(DeviationDirection.BALANCED,
                BacktestMetricCalculator.deviationDirection(sample("a", "101", "100", null, null, null),
                        new BigDecimal("0.05")));
        assertNotNull(DeviationDirection.from("OVER"));
        assertEquals(DeviationDirection.NOT_DETERMINABLE, DeviationDirection.from("whatever"));
    }

    @Test
    @DisplayName("行缺 metricRef ⇒ 显式跳过并告警，禁静默计入分母")
    void rowWithoutMetricRefIsSkippedWithWarning() {
        BacktestMetricCalculator.Result r = BacktestMetricCalculator.compute(
                List.of(new BacktestMetricCalculator.Sample("  ", BigDecimal.ONE, BigDecimal.ONE,
                        null, null, null, true)), 1,
                thresholds(null, null, null, null, null), null);
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("BACKTEST_ROW_SKIPPED")));
        assertEquals(0, r.summary().getDeviationAssessedSampleCount());
    }
}
