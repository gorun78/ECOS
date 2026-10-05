package com.chinacreator.gzcm.ai.wagent.readiness;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ReadinessGrade;
import com.chinacreator.gzcm.ai.wagent.readiness.ReadinessGapService.Gap;
import com.chinacreator.gzcm.ai.wagent.readiness.ReadinessGapService.GapTaskPort;
import com.chinacreator.gzcm.ai.wagent.readiness.ReadinessGapService.RefillOutcome;
import com.chinacreator.gzcm.ai.wagent.readiness.ReadinessGapService.SuggestedAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-09 补齐循环离线单测：refill 轮次硬顶 3，第 4 轮（currentRound≥3）
 * 停止派发（capped=true，不再向 taskPort 提交任务）。
 */
class ReadinessGapServiceTest {

    private static Gap taskGap() {
        return new Gap("D", "blocker", "gap:data.check_coverage", null,
                SuggestedAction.fetch("data.check_coverage"), ReadinessGrade.C, ReadinessGrade.A);
    }

    /** F10-09：补齐轮次封顶 3——第 4 轮（currentRound=3）返回 capped=true 且空任务。 */
    @Test
    void refillLoopCapsAtThreeRounds() {
        AtomicInteger submits = new AtomicInteger();
        GapTaskPort port = (type, target, params) -> { submits.incrementAndGet(); return "task-id-" + submits.get(); };
        List<Gap> gaps = List.of(taskGap());

        // 第 1、2、3 轮：未满顶，capped=false，round 递进。
        RefillOutcome r1 = ReadinessGapService.refill(gaps, 0, port);
        assertFalse(r1.capped(), "第 1 轮未满顶不应 cap");
        assertEquals(1, r1.refillRound());
        RefillOutcome r2 = ReadinessGapService.refill(gaps, 1, port);
        assertFalse(r2.capped(), "第 2 轮未满顶不应 cap");
        RefillOutcome r3 = ReadinessGapService.refill(gaps, 2, port);
        assertFalse(r3.capped(), "第 3 轮未满顶不应 cap");
        assertEquals(3, r3.refillRound(), "第 3 轮后 round 应递进到 3");

        // 第 4 轮（currentRound=3 = MAX_REFILL）：cap 停止，不再派发任务。
        int beforeFourth = submits.get();
        RefillOutcome r4 = ReadinessGapService.refill(gaps, 3, port);
        assertTrue(r4.capped(), "第 4 轮（currentRound=3）必须 capped=true 停止");
        assertTrue(r4.refillRound() >= ReadinessGapService.MAX_REFILL, "封顶轮次 ≥ MAX_REFILL");
        assertTrue(r4.submittedTaskIds().isEmpty(), "封顶后不得再向 taskPort 提交任务");
        assertEquals(beforeFourth, submits.get(), "封顶后 taskPort 不得再被调用");
    }
}
