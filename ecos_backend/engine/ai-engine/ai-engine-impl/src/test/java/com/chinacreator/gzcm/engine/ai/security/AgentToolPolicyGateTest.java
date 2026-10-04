package com.chinacreator.gzcm.engine.ai.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F06-01 (X-19) 裁决闸超时与 fail-closed — {@link AgentToolPolicyGate} 验收。
 * ACCEPT：doc §X.9.0 行 278 {@code AgentToolPolicyGateTest#timeoutTwoSecondsFailClosed}。
 */
class AgentToolPolicyGateTest {

    /**
     * X-19：ABAC 截止统一为 <b>2000ms</b>（原 5000ms）。
     * 落点 = {@link AiSecurityEngineClient} 经 {@code service.security.timeout-ms}（该值缺省
     * 已在客户端侧同步调为 2000ms，X-19）；{@link AgentToolPolicyGate} 记录同一 effective 值供
     * 诊断/验收断言。本闸自身不另建 RestTemplate（§2.4-7 单通道），故超时经 client 收口。
     */
    @Test
    @DisplayName("X-19 timeoutTwoSecondsFailClosed: 生效 ABAC 截止 = 2000ms 且不可解析/不可用一律 FAIL_CLOSED")
    void timeoutTwoSecondsFailClosed() {
        AiSecurityEngineClient mockClient = mock(AiSecurityEngineClient.class);
        // 缺省 2000ms（PRD §1.2）——若将来被误调回 5000ms，此断言立即转红
        AgentToolPolicyGate gate = new AgentToolPolicyGate(
                mock(AgentToolSqlWhitelist.class), mockClient, 2000);

        // PRD §4.2+#X-19：2s 阈值（毫秒），绝对值断言防回退
        assertEquals(2000, gate.getEffectiveTimeoutMs(),
                "F06-01/X-19 裁决截止必须 = 2000ms（现网 5000ms 属 X-19 待整改，为本红线）");
        assertTrue(gate.getEffectiveTimeoutMs() <= 2000, "截止不得超过 2s（宁紧勿松）");

        // ── FAIL_CLOSED 三态（超时/异常/不可解析/明确 DENY 均归 DENY） ──────────
        // 1) security 不可用（evaluate 内部 catch → false）→ 闸判 DENY
        when(mockClient.evaluate(anyString(), any(), anyString(), any())).thenReturn(false);
        AgentToolPolicyGate.Decision d1 = gate.adjudicate("query_db", null, "user-42", null);
        assertFalse(d1.allowed());
        assertNotNull(d1.reason());

        // 明确 DENY 路径：reason 必须携带可读语义（§4.2 "可读消息"），且绝不降级放行
        assertTrue(d1.reason() != null && d1.reason().contains("DENY"),
                "FAIL_CLOSED/DENY reason 须可读并明示 DENY, got=" + d1.reason());

        // 2) 匿名/无主体 → 同样 FAIL_CLOSED（禁匿名放行，X-11 前置）
        AgentToolPolicyGate.Decision anon = gate.adjudicate("query_db", null, null, null);
        assertFalse(anon.allowed(), "无 token 主体必须 FAIL_CLOSED，不得放行");
    }
}
