package com.chinacreator.gzcm.engine.cognitive2.service.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** F05-09：contribution 必须可从同一 snapshot 确定性复算（禁 LLM 数值）。 */
class CausalChainDeterminismTest {

    @Test
    void contributionIsRecomputableFromSnapshot() {
        List<ContributionCalculator.Edge> snapshot = List.of(
                new ContributionCalculator.Edge("e1", "A", "B", bd(100.0), bd(137.5), true),
                new ContributionCalculator.Edge("e2", "B", "C", bd(20.0),  bd(24.0),  true),
                new ContributionCalculator.Edge("e3", "A", "C", bd(5.0),   bd(3.25),  false));

        // 复算两次必得同值
        List<BigDecimal> first = ContributionCalculator.contributions(snapshot);
        List<BigDecimal> second = ContributionCalculator.contributions(snapshot);

        assertEquals(first, second, "同快照复算必得同值");
        assertEquals(0, first.get(0).compareTo(bd(37.5)));
        assertEquals(0, first.get(1).compareTo(bd(4.0)));
        assertEquals(0, first.get(2).compareTo(bd(-1.75)));
        // partition：有证据 e1/e2 → chains；无证据 e3 → unverified
        ContributionCalculator.Partition p = ContributionCalculator.partition(snapshot);
        assertEquals(2, p.chains().size());
        assertEquals(List.of("e3"), p.unverifiedHypotheses());
        assertEquals("A→B", p.chains().get(0).path());
    }

    private static BigDecimal bd(double v) { return new BigDecimal(String.valueOf(v)); }
}
