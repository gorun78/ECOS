package com.chinacreator.gzcm.engine.cognitive2.fc;

import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.Candidate;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.Cell;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.CellInput;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.RunOutput;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.SixElement;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分册09 §七 W243/C225 · 区间口径统一为三值 P10/P50/P90 全存全展（R-62① 已批准）。
 *
 * <p>验收标识：{@code mvn -Dtest=ForecastIntervalShapeTest#resultCarriesP10P50P90}。</p>
 *
 * <p>红线：禁前端插值 / 双事实源。三值必同 scale=2 且 p90 ≥ p50 ≥ p10（保序）。</p>
 */
class ForecastIntervalShapeTest {

    private static final SixElement SIX = SixElement.of(
            "run-int", "R6.1", "v1", Instant.parse("2026-11-01T00:00:00Z"),
            "snap-1", "f1");

    @Test
    @DisplayName("§七 W243/C225 · 结果必携 P10/P50/P90 三值且保序")
    void resultCarriesP10P50P90() {
        // 差异测试：p10 != p50 != p90（非确定性下应当分布），若 p50=0 也允许（三值相等）
        CellInput in = new CellInput("M1", "2026-11", "STAGE", "P1", "D1", "S1",
                Map.of("act", new Candidate(SourceType.ACTUAL,
                        new BigDecimal("10000.00"), Map.of("src", "fact"))));
        RunOutput out = FcRunCalc.calculate(SIX, List.of(in), "sh", "oh", "f1");

        Cell c = out.cells().get(0);
        assertNotNull(c.amountP10(), "P10 必非空（禁前端插值，R-62①）");
        assertNotNull(c.amountP50(), "P50 必非空");
        assertNotNull(c.amountP90(), "P90 必非空");

        assertTrue(c.amountP90().compareTo(c.amountP50()) >= 0,
                "p90 ≥ p50:  p90=" + c.amountP90() + " p50=" + c.amountP50());
        assertTrue(c.amountP50().compareTo(c.amountP10()) >= 0,
                "p50 ≥ p10:  p50=" + c.amountP50() + " p10=" + c.amountP10());
        assertEquals(0, c.amountP90().subtract(c.amountP50()).abs()
                .compareTo(c.amountP50().subtract(c.amountP10()).abs()),
                "对称区间：|p90-p50|=|p50-p10|（本批次 5% 固定对称）");
    }

    @Test
    @DisplayName("三值 scale=2；p50=0 时三值均 0（确定性无区间噪声）")
    void zeroValueYieldsAllThreeZero() {
        CellInput in = new CellInput("M1", "2026-11", "STAGE", "P1", "D1", "S1",
                Map.of("act", new Candidate(SourceType.ACTUAL,
                        BigDecimal.ZERO, Map.of("src", "fact"))));
        RunOutput out = FcRunCalc.calculate(SIX, List.of(in), "sh", "oh", "f1");
        Cell c = out.cells().get(0);
        assertEquals(0, c.amountP10().compareTo(BigDecimal.ZERO));
        assertEquals(0, c.amountP50().compareTo(BigDecimal.ZERO));
        assertEquals(0, c.amountP90().compareTo(BigDecimal.ZERO));
        assertEquals(2, c.amountP50().scale());
    }

    @Test
    @DisplayName("三值同规模：p10/p50/p90 scale 全=2（禁前端插值 on 不同精度）")
    void allThreeShareScaleTwo() {
        CellInput in = new CellInput("M1", "2026-11", "STAGE", "P1", "D1", "S1",
                Map.of("act", new Candidate(SourceType.ACTUAL,
                        new BigDecimal("1234.567"), Map.of("src", "fact"))));
        RunOutput out = FcRunCalc.calculate(SIX, List.of(in), "sh", "oh", "f1");
        Cell c = out.cells().get(0);
        assertEquals(2, c.amountP10().scale());
        assertEquals(2, c.amountP50().scale());
        assertEquals(2, c.amountP90().scale());
    }
}
