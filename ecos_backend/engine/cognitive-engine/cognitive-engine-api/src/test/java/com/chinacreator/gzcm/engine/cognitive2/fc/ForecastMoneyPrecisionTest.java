package com.chinacreator.gzcm.engine.cognitive2.fc;

import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.Candidate;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.Cell;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.CellInput;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.RunOutput;
import com.chinacreator.gzcm.engine.cognitive2.fc.FcRunCalc.SixElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分册09 §九 · 金额精度（PRD-01 DB-04/C204）：金额一律 BigDecimal + HALF_UP scale=2。
 *
 * <p>验收标识 {@code mvn -Dtest=ForecastMoneyPrecisionTest#amountFieldIsBigDecimalScaled2}：</p>
 * <ul>
 *   <li>Candidate.amount 不可为 null（禁静默 0/均值，FC-05 红线）</li>
 *   <li>3 值输出（P10/P50/P90）保持 scale=2 与 HALF_UP</li>
 *   <li>输入含 3 位小数时 p50 应归一到 2 位 HALF_UP</li>
 * </ul>
 */
class ForecastMoneyPrecisionTest {

    private static final SixElement SIX = SixElement.of(
            "run-1", "R6.1", "v1", Instant.parse("2026-10-01T00:00:00Z"),
            "snap-1", "f1");

    @Test
    @DisplayName("§九 · amount 字段应 BigDecimal 且 scale=2 HALF_UP")
    void amountFieldIsBigDecimalScaled2() {
        // 3 位小数输入 → 出参 scale=2, HALF_UP
        CellInput in = new CellInput("M1", "2026-10", "STAGE",
                "P1", "D1", "S1",
                Map.of("act", new Candidate(FcRunCalc.SourceType.ACTUAL,
                        new BigDecimal("1234.567"), Map.of("src", "fact"))));
        RunOutput out = FcRunCalc.calculate(SIX, withoutStageAggregate(in), "sh", "oh", "f1");
        Cell cell = out.cells().get(0);
        assertEquals(2, cell.amountP50().scale(), "p50 scale 应为 2");
        assertEquals(0, cell.amountP50().compareTo(new BigDecimal("1234.57")),
                "HALF_UP 应用: 1234.567→1234.57, 实际 " + cell.amountP50());
        assertEquals(2, cell.amountP10().scale(), "p10 scale 应为 2");
        assertEquals(2, cell.amountP90().scale(), "p90 scale 应为 2");
    }

    @Test
    void nullAmountRejected() {
        // Candidate 构造器即禁 null
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new Candidate(FcRunCalc.SourceType.ACTUAL, null, Map.of()));
    }

    @Test
    @DisplayName("输入整数也应 scale=2，禁 double 传染")
    void integerAmountStillScales2() {
        CellInput in = new CellInput("M1", "2026-10", "STAGE",
                "P1", "D1", "S1",
                Map.of("act", new Candidate(FcRunCalc.SourceType.ACTUAL,
                        new BigDecimal("5000"), Map.of("src", "fact"))));
        RunOutput out = FcRunCalc.calculate(SIX, List.of(in), "sh", "oh", "f1");
        assertEquals(2, out.cells().get(0).amountP50().scale());
        assertEquals(0, out.cells().get(0).amountP50().compareTo(new BigDecimal("5000.00")));
    }

    /** §3.2 幂等：同输入同输出（same-run key + same cell identity for BigDecimal）。 */
    @Test
    @DisplayName("§3.2 同输入 ⇒ 同 runKey（幂等基础）")
    void sameInputProducesSameRunKey() {
        String h1 = FcRunCalc.scopeHash(List.of("P2", "P1"), List.of("D1"), "2026-10", List.of("S1"));
        String h2 = FcRunCalc.scopeHash(List.of("P1", "P2"), List.of("D1"), "2026-10", List.of("S1"));
        assertEquals(h1, h2, "排序后 scopeHash 应相等，证明幂等稳定");
        String rk1 = FcRunCalc.runKeyOf(SIX, h1, "oh");
        String rk2 = FcRunCalc.runKeyOf(SIX, h2, "oh");
        assertEquals(rk1, rk2);
    }

    /** 辅助：加入 STAGE 叶子 + 未加 DEPARTMENT/TOTAL 聚合的输入集合，避免平衡断言阻塞。 */
    private static List<CellInput> withoutStageAggregate(CellInput leaf) {
        return List.of(leaf);
    }
}
