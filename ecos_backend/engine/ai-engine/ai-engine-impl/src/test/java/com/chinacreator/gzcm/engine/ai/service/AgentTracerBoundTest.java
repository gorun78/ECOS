package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.common.trace.AgentLoopTrace;
import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-06 F06-10 验收（文档冻结的可执行形态）：
 * {@code traceMapNeverExceedsMaxAndEvictsByInsertionOrder}。
 *
 * <p>X-36 的旧形态是「{@code ConcurrentHashMap} + {@code keys().nextElement()} 淘汰」：
 * 迭代序是哈希序而非插入序（淘汰到谁不确定，甚至可能踢掉正在写入的最新 trace），
 * 且"判容量→取首→移除"三步非原子（并发下可越界）。现改为
 * 单调 {@code insertSeq} 最小者出局 + 临界区串行，两个登记点（{@code newTrace}/{@code record}）
 * 都判上限 —— 只在 {@code newTrace} 判会留下 {@code record} 直接建桶的绕过面。</p>
 */
@DisplayName("F06-10 轨迹调试视图有界（LRU-by-insertSeq）")
class AgentTracerBoundTest {

    /** 超容量多少条足以触发多轮淘汰，又不拖慢单测 */
    private static final int OVERFLOW = 50;

    @AfterEach
    void resetView() {
        AgentTracer.clearAll();
        MDC.remove(EcosTraceContext.MDC_KEY_TRACE_ID);
    }

    @Test
    @DisplayName("要点3/X-36: 视图条目数恒不越上限，且按插入序淘汰最旧")
    void traceMapNeverExceedsMaxAndEvictsByInsertionOrder() {
        AgentTracer.clearAll();

        String[] ids = new String[AgentTracer.MAX_TRACES + OVERFLOW];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = "trace-bound-" + i;
            AgentTracer.record(ids[i], 1, AgentLoopTrace.Op.PLAN, AgentLoopTrace.UNKNOWN,
                    AgentLoopTrace.UNKNOWN, AgentLoopTrace.UNKNOWN, null,
                    AgentLoopTrace.Decision.NA);
            assertTrue(AgentTracer.trackedTraceCount() <= AgentTracer.MAX_TRACES,
                    "第 " + i + " 次登记后即越上限：count=" + AgentTracer.trackedTraceCount()
                            + " max=" + AgentTracer.MAX_TRACES);
        }

        assertEquals(AgentTracer.MAX_TRACES, AgentTracer.trackedTraceCount(),
                "越容量登记后应恰好收敛到上限（禁静默保留更多）");
        for (int i = 0; i < OVERFLOW; i++) {
            assertTrue(AgentTracer.getTrace(ids[i]).isEmpty(),
                    "最早插入的条目必须先出局（插入序淘汰），实际仍在：" + ids[i]);
        }
        for (int i = OVERFLOW; i < ids.length; i++) {
            assertFalse(AgentTracer.getTrace(ids[i]).isEmpty(),
                    "较新条目不得被淘汰：淘汰依据只能是 insertSeq，不是哈希迭代序（X-36）");
        }
    }

    @Test
    @DisplayName("两个登记点都受上限约束（record 建桶不得绕过 newTrace 的判定）")
    void bothRegistrationPointsAreBounded() {
        AgentTracer.clearAll();
        for (int i = 0; i < AgentTracer.MAX_TRACES; i++) {
            MDC.put(EcosTraceContext.MDC_KEY_TRACE_ID, "trace-new-" + i);
            AgentTracer.newTrace();
        }
        assertEquals(AgentTracer.MAX_TRACES, AgentTracer.trackedTraceCount());

        MDC.put(EcosTraceContext.MDC_KEY_TRACE_ID, "trace-new-overflow");
        AgentTracer.newTrace();
        assertEquals(AgentTracer.MAX_TRACES, AgentTracer.trackedTraceCount(),
                "newTrace 侧同样不得越界");

        // 已存在的旧 trace 走 record 补记时若恰被淘汰 ⇒ 不写孤儿桶（视图仍守恒，不抛穿调用方）
        AgentTracer.record("trace-new-0", 2, AgentLoopTrace.Op.FINAL, 1L, 1L, 1L,
                null, AgentLoopTrace.Decision.ALLOW);
        assertTrue(AgentTracer.trackedTraceCount() <= AgentTracer.MAX_TRACES);
    }
}
