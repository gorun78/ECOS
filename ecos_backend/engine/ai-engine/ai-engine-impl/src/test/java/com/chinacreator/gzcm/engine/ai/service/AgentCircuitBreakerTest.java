package com.chinacreator.gzcm.engine.ai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave-5.1 T-09 — Agent 熔断器状态机测试。
 *
 * <p>状态机: CLOSED → (failCount >= 3) → OPEN → (5min 后) → HALF_OPEN → (1 次成功) → CLOSED。
 *
 * <p>覆盖: 无记录默认 / 阈值跳 OPEN / OPEN 忽略成功 / OPEN→HALF_OPEN 延迟 /
 * HALF_OPEN→closed(成功) / HALF_OPEN 再失败→OPEN。
 *
 * @author ECOS AI Engine Team
 * @since 2026-09-02 (Wave-5.1)
 */
class AgentCircuitBreakerTest {

    /** 反射把 CircuitState.openedAt 调到过去 6 分钟, 让 OPEN 转 HALF_OPEN。 */
    @SuppressWarnings("unchecked")
    private void ageOpenCircuit(AgentCircuitBreaker breaker, String agentId) {
        Map<String, Object> states =
                (Map<String, Object>) ReflectionTestUtils.getField(breaker, "states");
        Object stateObj = states.get(agentId);
        assertNotNull(stateObj, "熔断器必须已有 state");
        try {
            Field opened = stateObj.getClass().getDeclaredField("openedAt");
            opened.setAccessible(true);
            opened.setLong(stateObj, System.currentTimeMillis() - 6 * 60 * 1000L);
        } catch (Exception e) {
            throw new AssertionError("reflection age failed: " + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("T-09-4-1: 无记录时 isAllowed=true 且状态默认 CLOSED/failCount=0")
    void noRecordMeansClosedAndAllowed() {
        AgentCircuitBreaker b = new AgentCircuitBreaker();
        assertTrue(b.isAllowed("agent-x"));
        Map<String, Object> st = b.getStatus("agent-x");
        assertEquals("CLOSED", st.get("status"));
        assertEquals(0, ((Number) st.get("failCount")).intValue());
        assertNull(st.get("openedAt"), "无记录时无 openedAt");
    }

    @Test
    @DisplayName("T-09-4-2: 第 3 次连续失败触发 OPEN, isAllowed 返回 false")
    void thresholdOpensCircuit() {
        AgentCircuitBreaker b = new AgentCircuitBreaker();
        b.recordFailure("agent-x");
        b.recordFailure("agent-x");
        assertTrue(b.isAllowed("agent-x"), "2 次失败未到阈值, 仍允许");
        b.recordFailure("agent-x");
        assertFalse(b.isAllowed("agent-x"), "第 3 次失败必须开 OPEN");
        Map<String, Object> st = b.getStatus("agent-x");
        assertEquals("OPEN", st.get("status"));
        assertEquals(3, ((Number) st.get("failCount")).intValue());
    }

    @Test
    @DisplayName("T-09-4-3: OPEN 状态下 recordSuccess 不改状态 (恢复只能从 HALF_OPEN 走)")
    void openIgnoresSuccess() {
        AgentCircuitBreaker b = new AgentCircuitBreaker();
        b.recordFailure("agent-x");
        b.recordFailure("agent-x");
        b.recordFailure("agent-x");
        b.recordSuccess("agent-x");
        Map<String, Object> st = b.getStatus("agent-x");
        assertEquals("OPEN", st.get("status"));
        assertEquals(3, ((Number) st.get("failCount")).intValue());
    }

    @Test
    @DisplayName("T-09-4-4: OPEN 超过 5 分钟 → isAllowed 自动放行且状态转 HALF_OPEN")
    void openToHalfOpenAfterDelay() {
        AgentCircuitBreaker b = new AgentCircuitBreaker();
        b.recordFailure("agent-x");
        b.recordFailure("agent-x");
        b.recordFailure("agent-x");
        assertEquals("OPEN", b.getStatus("agent-x").get("status"));

        ageOpenCircuit(b, "agent-x");

        assertTrue(b.isAllowed("agent-x"), "HALF_OPEN 放行一次探测请求");
        assertEquals("HALF_OPEN", b.getStatus("agent-x").get("status"));
        assertEquals(0, ((Number) b.getStatus("agent-x").get("failCount")).intValue(),
                "HALF_OPEN 重置 failCount");
    }

    @Test
    @DisplayName("T-09-4-5: HALF_OPEN 下 recordSuccess → state 清空, 默认 CLOSED")
    void halfOpenSuccessClosesCircuit() {
        AgentCircuitBreaker b = new AgentCircuitBreaker();
        b.recordFailure("agent-a");
        b.recordFailure("agent-a");
        b.recordFailure("agent-a");
        ageOpenCircuit(b, "agent-a");
        b.isAllowed("agent-a"); // OPEN→HALF_OPEN
        assertEquals("HALF_OPEN", b.getStatus("agent-a").get("status"));

        b.recordSuccess("agent-a");

        assertEquals("CLOSED", b.getStatus("agent-a").get("status"));
        assertEquals(0, ((Number) b.getStatus("agent-a").get("failCount")).intValue());
        // state map 应空 → 无 openedAt
        assertNull(b.getStatus("agent-a").get("openedAt"));
    }

    @Test
    @DisplayName("T-09-4-6: HALF_OPEN 下重新累计 3 次失败 → 再次跳 OPEN")
    void halfOpenFailureReopens() {
        AgentCircuitBreaker b = new AgentCircuitBreaker();
        b.recordFailure("agent-b");
        b.recordFailure("agent-b");
        b.recordFailure("agent-b");
        ageOpenCircuit(b, "agent-b");
        b.isAllowed("agent-b"); // OPEN→HALF_OPEN

        b.recordFailure("agent-b");
        b.recordFailure("agent-b");
        assertEquals("HALF_OPEN", b.getStatus("agent-b").get("status"),
                "2 次失败未达阈值");
        b.recordFailure("agent-b");
        assertEquals("OPEN", b.getStatus("agent-b").get("status"),
                "HALF_OPEN 内 3 次失败必须再次 OPEN");
        assertFalse(b.isAllowed("agent-b"), "再次 OPEN 后, 未到期必须阻断");
    }
}
