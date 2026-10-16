package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolObligations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code rateLimit} 义务消费方的行为面（#142/B4-3d-4，收口差距 <b>D-B4-21</b>）。
 *
 * <p>本类验的是<b>三态不折叠</b>：配额用尽＝策略义务未满足（DENIED，403 面），
 * 义务形态不合契约＝护栏给的形态无法消费（FAIL_CLOSED，503 面）。把前者写成 503
 * 或把后者写成 403，都会让 §4.7.2「不可用 ≠ 无权限」在执行面失效，因此每个形态
 * 都同时断"是什么态"和"不是什么态"。</p>
 *
 * <p>时钟经包私构造注入（{@code AiToolRateLimitGate(LongSupplier)}）：窗口翻转与清扫
 * 若靠 sleep 验证，用例既慢又会在 CI 抖动里假红，故一律用可控时钟。</p>
 */
class AiToolRateLimitGateTest {

    private static final String KEY = GuardrailDecisionContract.RATE_LIMIT_KEY_PREFIX + "u-pj";

    private final AtomicLong now = new AtomicLong(1_700_000_000_000L);
    private final AiToolRateLimitGate gate = new AiToolRateLimitGate(now::get);

    /** 策略签发一条限流义务（rego 原文形态：obligations 为数组）。 */
    private static ToolObligations rateOnly(String key) {
        return ToolObligations.fromEvaluateData(Map.of(
                GuardrailDecisionContract.KEY_OBLIGATIONS,
                List.of(Map.of(GuardrailDecisionContract.KEY_OBLIGATION,
                        GuardrailDecisionContract.OBLIGATION_RATE_LIMIT,
                        GuardrailDecisionContract.KEY_RATE_LIMIT_KEY, key))));
    }

    @Test
    @DisplayName("未签发义务 — PASS 且不计额（页面/导出通道恒无该义务，计数只在义务存在时有语义）")
    void absentObligationPassesWithoutConsumingQuota() {
        AiToolRateLimitGate.RateOutcome outcome = gate.enforce(ToolObligations.none());

        assertEquals(AiToolRateLimitGate.Verdict.PASS, outcome.verdict());
        assertFalse(outcome.blocks());
        assertEquals(-1, outcome.remaining(), "未计额不得伪造剩余额度");
        assertTrue(gate.windowSnapshot().isEmpty(), "无义务却建了窗口＝在执行面自造计额键");

        // 连续 25 次仍全放行（>MAX_CALLS_PER_WINDOW：证明这条路根本不进计数器）
        for (int i = 0; i < 25; i++) {
            assertEquals(AiToolRateLimitGate.Verdict.PASS, gate.enforce(null).verdict());
        }
        assertTrue(gate.windowSnapshot().isEmpty());
    }

    @Test
    @DisplayName("窗口内计额 — remaining 逐次递减且键与单源前缀同源")
    void passesWithinWindowAndDecrementsRemaining() {
        AiToolRateLimitGate.RateOutcome first = gate.enforce(rateOnly(KEY));

        assertEquals(AiToolRateLimitGate.Verdict.PASS, first.verdict());
        assertEquals(AiToolRateLimitGate.MAX_CALLS_PER_WINDOW - 1, first.remaining());
        assertEquals(1, gate.usedCalls(KEY));

        gate.acquire(KEY);
        assertEquals(2, gate.usedCalls(KEY));

        AiToolRateLimitGate.RateOutcome third = gate.acquire(KEY);
        assertEquals(3, gate.usedCalls(KEY));
        assertEquals(AiToolRateLimitGate.MAX_CALLS_PER_WINDOW - 3, third.remaining(),
                "remaining = 上限 − 本窗口已用次数（含本次）");
    }

    @Test
    @DisplayName("配额用尽 — 第 21 次 DENIED 且带单源原因串（策略义务未满足，非不可用）")
    void quotaExhaustionIsDenied() {
        for (int i = 0; i < AiToolRateLimitGate.MAX_CALLS_PER_WINDOW; i++) {
            assertEquals(AiToolRateLimitGate.Verdict.PASS, gate.acquire(KEY).verdict(),
                    "第 " + (i + 1) + " 次应在额度内");
        }

        AiToolRateLimitGate.RateOutcome extra = gate.acquire(KEY);

        assertEquals(AiToolRateLimitGate.Verdict.DENIED, extra.verdict());
        assertTrue(extra.blocks());
        assertTrue(extra.reason().startsWith(AiToolRateLimitGate.REASON_QUOTA_EXCEEDED),
                "拒绝原因必须取自单点常量，禁散落自造文案: " + extra.reason());
        assertEquals(0, extra.remaining());
        assertEquals(AiToolRateLimitGate.MAX_CALLS_PER_WINDOW + 1, gate.usedCalls(KEY),
                "被拒的调用也计入观察面（供告警侧核对超调幅度）");
    }

    @Test
    @DisplayName("窗口翻转恢复额度 — 固定窗口以键首次计额时刻为起点")
    void windowRolloverRestoresQuota() {
        for (int i = 0; i < AiToolRateLimitGate.MAX_CALLS_PER_WINDOW; i++) {
            gate.acquire(KEY);
        }
        assertEquals(AiToolRateLimitGate.Verdict.DENIED, gate.acquire(KEY).verdict());

        now.set(now.get() + AiToolRateLimitGate.WINDOW_MILLIS);

        AiToolRateLimitGate.RateOutcome renewed = gate.acquire(KEY);
        assertEquals(AiToolRateLimitGate.Verdict.PASS, renewed.verdict(), "超窗必须重开窗口而非持续拒绝");
        assertEquals(1, gate.usedCalls(KEY));
    }

    @Test
    @DisplayName("键隔离 — 甲主体用尽不影响乙主体（义务键含 subjectId 的全部意义）")
    void quotaIsPerSubjectKey() {
        String keyA = GuardrailDecisionContract.RATE_LIMIT_KEY_PREFIX + "u-a";
        String keyB = GuardrailDecisionContract.RATE_LIMIT_KEY_PREFIX + "u-b";

        for (int i = 0; i <= AiToolRateLimitGate.MAX_CALLS_PER_WINDOW; i++) {
            gate.acquire(keyA);
        }
        assertEquals(AiToolRateLimitGate.Verdict.DENIED, gate.acquire(keyA).verdict());
        assertEquals(AiToolRateLimitGate.Verdict.PASS, gate.acquire(keyB).verdict());
        assertEquals(AiToolRateLimitGate.MAX_CALLS_PER_WINDOW + 2, gate.usedCalls(keyA));
        assertEquals(1, gate.usedCalls(keyB));
    }

    @Test
    @DisplayName("键缺失 — FAIL_CLOSED（不得退化成未声明后静默放行）")
    void missingKeyFailsClosed() {
        AiToolRateLimitGate.RateOutcome outcome = gate.acquire(null);

        assertEquals(AiToolRateLimitGate.Verdict.FAIL_CLOSED, outcome.verdict());
        assertNotEquals(AiToolRateLimitGate.Verdict.DENIED, outcome.verdict());
        assertEquals(AiToolRateLimitGate.REASON_KEY_MISSING, outcome.reason());
        assertEquals(-1, outcome.remaining());
    }

    @Test
    @DisplayName("义务声明了但载荷无键 — enforce 同样 FAIL_CLOSED（D-B4-21 丢义务形态的反例）")
    void declaredObligationWithoutKeyFailsClosed() {
        ToolObligations declared = ToolObligations.fromEvaluateData(Map.of(
                GuardrailDecisionContract.KEY_OBLIGATIONS,
                List.of(Map.of(GuardrailDecisionContract.KEY_OBLIGATION,
                        GuardrailDecisionContract.OBLIGATION_RATE_LIMIT))));

        assertTrue(declared.declaresRateLimit());
        assertNull(declared.rateLimitKey());

        AiToolRateLimitGate.RateOutcome outcome = gate.enforce(declared);
        assertEquals(AiToolRateLimitGate.Verdict.FAIL_CLOSED, outcome.verdict());
        assertEquals(AiToolRateLimitGate.REASON_KEY_MISSING, outcome.reason());
        assertTrue(gate.windowSnapshot().isEmpty(), "形态不合契约时禁按自造键计额");
    }

    @Test
    @DisplayName("键面漂移（前缀不符单源）— FAIL_CLOSED 且原文进 reason 供定位，不进响应面")
    void offContractPrefixFailsClosed() {
        String drifted = "tool.u-pj";
        AiToolRateLimitGate.RateOutcome outcome = gate.acquire(drifted);

        assertEquals(AiToolRateLimitGate.Verdict.FAIL_CLOSED, outcome.verdict());
        assertTrue(outcome.reason().startsWith(AiToolRateLimitGate.REASON_KEY_OFF_CONTRACT),
                outcome.reason());
        assertTrue(outcome.reason().contains(drifted), "reason 要能定位漂移键原文（只入日志/台账面）");
        assertEquals(0, gate.usedCalls(drifted));
    }

    @Test
    @DisplayName("主体段为空（ai.tool.）— FAIL_CLOSED：主体未解析时无法按主体配额")
    void emptySubjectSegmentFailsClosed() {
        AiToolRateLimitGate.RateOutcome outcome =
                gate.acquire(GuardrailDecisionContract.RATE_LIMIT_KEY_PREFIX);

        assertEquals(AiToolRateLimitGate.Verdict.FAIL_CLOSED, outcome.verdict());
        assertEquals(AiToolRateLimitGate.REASON_SUBJECT_SEGMENT_EMPTY, outcome.reason());
    }

    @Test
    @DisplayName("过期窗口清扫 — 超清扫间隔后新键计额会清掉旧窗（防 map 无界增长）")
    void sweepDropsExpiredWindows() {
        gate.acquire(KEY);
        assertEquals(1, gate.windowSnapshot().size());

        now.set(now.get() + AiToolRateLimitGate.SWEEP_INTERVAL_MILLIS);
        gate.acquire(GuardrailDecisionContract.RATE_LIMIT_KEY_PREFIX + "u-new");

        assertFalse(gate.windowSnapshot().containsKey(KEY),
                "过期窗口未清扫：长期运行下窗口表会无界增长");
        assertTrue(gate.windowSnapshot().containsKey(
                GuardrailDecisionContract.RATE_LIMIT_KEY_PREFIX + "u-new"));
    }

    @Test
    @DisplayName("reset 只清计额 — 生产调用链不调它（观察面与判定面分离）")
    void resetClearsCountersOnly() {
        gate.acquire(KEY);
        gate.reset();

        assertEquals(0, gate.usedCalls(KEY));
        assertTrue(gate.windowSnapshot().isEmpty());
        assertEquals(AiToolRateLimitGate.Verdict.PASS, gate.acquire(KEY).verdict());
    }

    @Test
    @DisplayName("三态常量与 verdict 语义互斥 — blocks() 只在非 PASS 时为真")
    void verdictStatesAreMutuallyExclusive() {
        assertTrue(AiToolRateLimitGate.RateOutcome.denied("x").blocks());
        assertTrue(AiToolRateLimitGate.RateOutcome.failClosed("x").blocks());
        assertFalse(AiToolRateLimitGate.RateOutcome.pass(1).blocks());
        assertFalse(AiToolRateLimitGate.RateOutcome.noObligation().blocks());
        assertEquals(0, AiToolRateLimitGate.RateOutcome.denied("x").remaining());
        assertEquals(-1, AiToolRateLimitGate.RateOutcome.failClosed("x").remaining());
    }
}
