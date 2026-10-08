package com.chinacreator.gzcm.engine.cognitive2.service.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** F05-09：无证据边 / 无统计支持的边必须归 unverifiedHypotheses（观测项，禁静默丢弃）。 */
class CausalChainEvidenceTest {

    @Test
    void unevidencedEdgeGoesToUnverifiedHypotheses() {
        List<ContributionCalculator.Edge> edges = List.of(
                new ContributionCalculator.Edge("ev1", "X", "Y", bd(10), bd(12), true),
                new ContributionCalculator.Edge("ev2", "Y", "Z", bd(10), bd(12), true),
                new ContributionCalculator.Edge("un1", "Z", "W", bd(10), bd(11), false),
                new ContributionCalculator.Edge("un2", "W", "V", bd(10), bd(11), false));

        ContributionCalculator.Partition p = ContributionCalculator.partition(edges);
        assertEquals(List.of("X→Y", "Y→Z"), p.chains().stream().map(ContributionCalculator.Chain::path).toList());
        assertEquals(List.of("un1", "un2"), p.unverifiedHypotheses());
    }

    private static BigDecimal bd(double v) { return new BigDecimal(String.valueOf(v)); }
}
