package com.chinacreator.gzcm.engine.cognitive2.service.calc;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F05-04 验收：score 确定性 + Top-N 上限 50 硬截断 + truncated 声明（C102）。
 */
class CognitiveEvalCalculatorTest {

    @Test
    void scoreEqualsProbabilityWhenWeightIsOne() {
        double s1 = CognitiveEvalCalculator.score(0.83, 1.0);
        assertEquals(0.83, s1, 1e-9);
        assertEquals(CognitiveEvalCalculator.score(0.83, 1.0), s1, 1e-9, "同输入必得同值");
    }

    @Test
    void topNTruncatesAtFiftyAndFlagsTruncated() {
        List<CognitiveEvalCalculator.Input> inputs = new ArrayList<>();
        for (int i = 1; i <= 120; i++) {
            inputs.add(new CognitiveEvalCalculator.Input("m" + i, "name" + i, i / 120.0));
        }
        // limit 未传（null）→ 默认 10，truncated 必为 true（120 > 10）
        CognitiveEvalCalculator.Result r = CognitiveEvalCalculator.topN(inputs, 1.0, null);
        assertEquals(CognitiveApiPaths.E2_LIMIT_DEFAULT, r.limit());
        assertTrue(r.truncated());
        assertEquals(10, r.items().size());

        // limit 超上限（999）→ 硬截 50 且 truncated
        CognitiveEvalCalculator.Result big = CognitiveEvalCalculator.topN(inputs, 1.0, 999);
        assertEquals(CognitiveApiPaths.E2_LIMIT_MAX, big.limit());
        assertTrue(big.truncated());
        assertEquals(50, big.items().size());

        // limit 恰 50 且输入 50 → 不 truncated
        List<CognitiveEvalCalculator.Input> exact = new ArrayList<>();
        for (int i = 1; i <= 50; i++) exact.add(new CognitiveEvalCalculator.Input("e" + i, "x" + i, 0.5));
        CognitiveEvalCalculator.Result eq = CognitiveEvalCalculator.topN(exact, 1.0, 50);
        assertFalse(eq.truncated());
        assertEquals(50, eq.items().size());

        // trend 恒 FLAT + trendSource NOT_IMPLEMENTED
        assertEquals("FLAT", r.items().get(0).trend());
        assertEquals("NOT_IMPLEMENTED", r.items().get(0).trendSource());
    }
}
