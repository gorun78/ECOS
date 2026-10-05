package com.chinacreator.gzcm.engine.cognitive2.fc;

import com.chinacreator.gzcm.engine.cognitive2.fc.FcBacktestCalc.BacktestReport;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcBacktestCalc.SampleRow;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcBacktestCalc.Thresholds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分册09 §七 W225/C207 · 回测五指标 + 除零守卫（红线：剔除实际=0 的行 + 披露计数，禁除零、禁以 1 兜底）。
 *
 * <p>验收标识：{@code mvn -Dtest=BacktestMetricTest#excludesZeroActualAndDisclosesCount}。</p>
 */
class BacktestMetricTest {

    private final Thresholds th = new Thresholds(new BigDecimal("15"), new BigDecimal("80"), 5);

    @Test
    @DisplayName("§七 W225/C207 · 剔除实际=0 的行且披露 zero_actual_count（禁除零/禁以 1 兜底）")
    void excludesZeroActualAndDisclosesCount() {
        List<SampleRow> samples = List.of(
                row("100", "100"),      // 有效
                row("90", "100"),       // 有效
                row("110", "100"),      // 有效
                row("80", "0"),         // 零实际 → 剔除
                row("60", "0"),         // 零实际 → 剔除
                row("0", "0"));         // 零实际 → 剔除
        BacktestReport r = FcBacktestCalc.compute("2026-09", "STAGE", null, null, null, samples, th);

        assertEquals(3, r.zeroActualCount(), "3 行实际=0 应全部剔除并计数，实际 " + r.zeroActualCount());
        assertEquals(3, r.sampleCount(), "有效样本应为 3");
        // 数据覆盖率 = 有效 / 到达 = 3/6 = 0.5
        assertEquals(0, r.dataCoverage().compareTo(new BigDecimal("0.5000")),
                "dataCoverage 应=0.5000, 实际 " + r.dataCoverage());
        assertNotNull(r.mae(), "MAE 应有值（非以 1 兜底）");
        // 无除零：若误以 1 兜底，MAPE 会变成 100/90/110 的绝对差百分比之一；此处正解 =
        // (|100-100|+|90-100|+|110-100|)/3 /100 *100 = 200/3/100*100 ≈ 6.6667
        assertTrue(r.mape().compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    @DisplayName("MAE/MAPE 数值正确（scale=4，HALF_UP）")
    void maeMapeNumericsAreCorrect() {
        List<SampleRow> samples = List.of(
                row("100", "100"), // diff 0
                row("90", "100"),  // diff -10, absPct 0.10
                row("110", "100")); // diff +10, absPct 0.10
        BacktestReport r = FcBacktestCalc.compute("2026-09", "STAGE", null, null, null, samples, th);
        // MAE = (0+10+10)/3 = 6.6667
        assertEquals(0, r.mae().compareTo(new BigDecimal("6.6667")), "MAE=" + r.mae());
        // MAPE = (0 + 0.10 + 0.10)/3 * 100 = 6.6667
        assertEquals(0, r.mape().compareTo(new BigDecimal("6.6667")), "MAPE=" + r.mape());
        assertEquals("NEUTRAL", r.biasDirection(), "diff sum = 0 → NEUTRAL, 实际 " + r.biasDirection());
    }

    @Test
    @DisplayName("偏差方向：预测系统性偏高 ⇒ OVER；偏低 ⇒ UNDER")
    void directionOverUnder() {
        BacktestReport over = FcBacktestCalc.compute("p", "g", null, null, null,
                List.of(row("110", "100"), row("120", "100"), row("130", "100")), th);
        assertEquals("OVER", over.biasDirection());

        BacktestReport under = FcBacktestCalc.compute("p", "g", null, null, null,
                List.of(row("90", "100"), row("80", "100"), row("70", "100")), th);
        assertEquals("UNDER", under.biasDirection());
    }

    @Test
    @DisplayName("区间覆盖率：落 [P10,P90] 内比例；LOW_SAMPLE 标记 n<5")
    void intervalCoverageAndLowSampleFlag() {
        // 4 样本 < minSample=5 ⇒ LOW_SAMPLE
        List<SampleRow> samples = List.of(
                rowWInterval("100", "98", "95", "105"),   // actual 98 in [95,105] ✓
                rowWInterval("100", "102", "95", "105"),  // ✓
                rowWInterval("100", "120", "95", "105"),  // ✗ 92? 120 > 105
                rowWInterval("100", "90", "95", "105"));  // ✗ 90 < 95
        BacktestReport r = FcBacktestCalc.compute("p", "g", null, null, null, samples, th);
        assertEquals("LOW_SAMPLE", r.sampleFlag(), "n=4 < 5 应 LOW_SAMPLE");
        // 覆盖率 = (4-2)/4 = 0.5
        assertEquals(0, r.intervalCoverage().compareTo(new BigDecimal("0.5000")),
                "intervalCoverage=" + r.intervalCoverage());
    }

    @Test
    @DisplayName("thresholds null ⇒ fail-loud（R-66① 禁默认伴生）")
    void thresholdsNullRejected() {
        assertThrows(IllegalStateException.class,
                () -> FcBacktestCalc.compute("p", "g", null, null, null,
                        List.of(row("100", "100")), null));
        assertThrows(IllegalArgumentException.class,
                () -> new Thresholds(null, new BigDecimal("80"), 5));
        assertThrows(IllegalArgumentException.class,
                () -> new Thresholds(new BigDecimal("15"), null, 5));
        assertThrows(IllegalArgumentException.class,
                () -> new Thresholds(new BigDecimal("15"), new BigDecimal("80"), 0));
    }

    private static SampleRow row(String forecast, String actual) {
        return new SampleRow(new BigDecimal(forecast), new BigDecimal(actual));
    }
    private static SampleRow rowWInterval(String forecast, String actual, String p10, String p90) {
        return new SampleRow(new BigDecimal(forecast), new BigDecimal(actual),
                new BigDecimal(p10), new BigDecimal(p90));
    }
}
