package com.chinacreator.gzcm.ai.wagent.tool;

import com.chinacreator.gzcm.ai.wagent.tool.CircuitBreakerView.CallRecord;
import com.chinacreator.gzcm.ai.wagent.tool.CircuitBreakerView.Phase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-24 工具级熔断离线单测：60s 滑动窗口内样本 ≥ 20 且 errorRate &gt; 0.5
 * （err*2 &gt; total）⇒ 熔断相切 OPEN。
 */
class CircuitBreakerViewTest {

    /** F10-24：1 min 跨度内 10 ok + 11 fail（21 样本，错误 11/21 &gt; 50%）⇒ OPEN。 */
    @Test
    void opensAtFiftyPercentErrorRateAfterTwentyCalls() {
        CircuitBreakerView cb = new CircuitBreakerView();
        long t0 = 1_000_000L;

        // 前 10 次成功，保持 CLOSED。
        for (int i = 0; i < 10; i++) {
            cb.record(new CallRecord(t0 + i, true));
        }
        assertEquals(Phase.CLOSED, cb.phase(), "10 ok / 0 err 应维持 CLOSED");

        // 后 11 次失败：窗口内累计 21 样本（总 ≥ MIN_SAMPLES=20），错误 11*2=22 > 21 ⇒ OPEN。
        for (int i = 0; i < 11; i++) {
            cb.record(new CallRecord(t0 + 10 + i, false));   // 全部落在 1 min 窗口内
        }
        assertEquals(Phase.OPEN, cb.phase(),
                "21 样本中 11 失败（>50%）且 ≥20 ⇒ 熔断应 OPEN，实际 " + cb.phase());
    }
}
