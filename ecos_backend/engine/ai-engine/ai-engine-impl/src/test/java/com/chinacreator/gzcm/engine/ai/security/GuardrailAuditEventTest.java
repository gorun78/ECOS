package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.event.EventTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F06-03（详细设计-06 §294-301）— GUARDRAIL_EVAL 审计事件接线验收。
 *
 * <p>验收三点（对应设计 §301）：
 * <ol>
 *   <li><b>allowDenyAndFailClosedEachEmitOneEvent</b>：ALLOW / DENY / FAIL_CLOSED 三态
 *       各恰好推送一条 GUARDRAIL_EVAL 事件（EventType 常量来自 common-api，禁 ai-engine
 *       自定义字符串）。</li>
 *   <li><b>auditPublishFailureFailsClosed</b>：桩 {@code publishGuardrailEventOrThrow} 抛
 *       {@link AiSecurityEngineClient.EngineUnavailableException} → 裁决整体 FAIL_CLOSED
 *       （reason 必含 FAIL_CLOSED 关键字），不返回 ALLOW。</li>
 *   <li><b>eventCarriesNoRawPayload</b>：detail 段只含 tool/decision/policyId/latencyMs/
 *       obligationsCount（+可选 errorClass），禁 sql/content/arguments/result/raw 键。</li>
 * </ol>
 *
 * <p>不触 DB/网络；{@link AiSecurityEngineClient} 全 Mockito 桩。</p>
 */
class GuardrailAuditEventTest {

    // ── 验收 1：allowDenyAndFailClosedEachEmitOneEvent ──────────────────

    @Test
    @DisplayName("允许/拒绝/主体缺失三态各恰好发一条 GUARDRAIL_EVAL 事件（EventType 来自 common-api）")
    void allowDenyAndFailClosedEachEmitOneEvent() {
        AiSecurityEngineClient client = mock(AiSecurityEngineClient.class);
        AgentToolPolicyGate gate = new AgentToolPolicyGate(
                mock(AgentToolSqlWhitelist.class), client, 2000);

        // ALLOW：evaluate=true
        when(client.evaluate(anyString(), any(), anyString(), any())).thenReturn(true);
        AgentToolPolicyGate.Decision dAllow = gate.adjudicate("query_db", null, "user-42", "t-1");
        assertTrue(dAllow.allowed(), "evaluate=true 应放行");

        // DENY：evaluate=false
        when(client.evaluate(anyString(), any(), anyString(), any())).thenReturn(false);
        AgentToolPolicyGate.Decision dDeny = gate.adjudicate("query_db", null, "user-42", "t-1");
        assertFalse(dDeny.allowed(), "evaluate=false 必须拒绝");

        // FAIL_CLOSED：主体缺失（token 上下文无主体路径）
        AgentToolPolicyGate.Decision dAnon = gate.adjudicate("query_db", null, null, "t-1");
        assertFalse(dAnon.allowed(), "无主体必须 FAIL_CLOSED");

        // 断言三次 publish 全部命中；捕获 detail 逐条验 decision
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detailCap = ArgumentCaptor.forClass(Map.class);
        verify(client, times(3)).publishGuardrailEventOrThrow(any(), detailCap.capture());
        List<Map<String, Object>> captured = detailCap.getAllValues();
        assertEquals(3, captured.size(), "三态各一条 GUARDRAIL_EVAL");

        long allowCount = captured.stream()
                .filter(d -> EventTypes.Guardrail.Decision.ALLOW.equals(d.get("decision")))
                .count();
        long denyCount = captured.stream()
                .filter(d -> EventTypes.Guardrail.Decision.DENY.equals(d.get("decision")))
                .count();
        long failClosedCount = captured.stream()
                .filter(d -> EventTypes.Guardrail.Decision.FAIL_CLOSED.equals(d.get("decision")))
                .count();
        assertEquals(1, allowCount, "ALLOW 恰好一条");
        assertEquals(1, denyCount, "DENY 恰好一条");
        assertEquals(1, failClosedCount, "FAIL_CLOSED（主体缺失）恰好一条");

        // 恒带 tool 名（summary-only 契约），且 subject 传的是 userId 常量（非不能裁决的 anonymousUser）
        for (Map<String, Object> d : captured) {
            assertEquals("query_db", d.get("tool"));
            assertNotNull(d.get("latencyMs"));
            assertNotNull(d.get("obligationsCount"));
        }
    }

    // ── 验收 2：auditPublishFailureFailsClosed ──────────────────────────

    @Test
    @DisplayName("GUARDRAIL_EVAL 发布失败 → 裁决整体 FAIL_CLOSED（reason 必含关键字，绝不放行）")
    void auditPublishFailureFailsClosed() {
        AiSecurityEngineClient client = mock(AiSecurityEngineClient.class);
        AgentToolPolicyGate gate = new AgentToolPolicyGate(
                mock(AgentToolSqlWhitelist.class), client, 2000);

        // ABAC 允许，但审计发布上抛 → 咽喉必须转 FAIL_CLOSED
        when(client.evaluate(anyString(), any(), anyString(), any())).thenReturn(true);
        doThrow(new AiSecurityEngineClient.EngineUnavailableException("kafka broker 不可达（测试桩）"))
                .when(client).publishGuardrailEventOrThrow(anyString(), any());

        AgentToolPolicyGate.Decision d = gate.adjudicate("query_db", null, "user-42", "t-1");

        assertFalse(d.allowed(),
                "审计发布失败必须 FAIL_CLOSED（红线，禁降级放行 §2.4-6 + 分册06 F06-03 §297）");
        assertNotNull(d.reason());
        assertTrue(d.reason().contains("FAIL_CLOSED"),
                "reason 必须携带 FAIL_CLOSED 指纹，got=" + d.reason());
        assertTrue(d.reason().contains("audit") || d.reason().contains("GUARDRAIL"),
                "reason 应显式指出审计失败原因，got=" + d.reason());
    }

    // ── 验收 3：eventCarriesNoRawPayload ────────────────────────────────

    @Test
    @DisplayName("detail 只带摘要与 ID，禁 sql / content / arguments / result / raw 原文键")
    void eventCarriesNoRawPayload() {
        AiSecurityEngineClient client = mock(AiSecurityEngineClient.class);
        AgentToolPolicyGate gate = new AgentToolPolicyGate(
                mock(AgentToolSqlWhitelist.class), client, 2000);

        when(client.evaluate(anyString(), any(), anyString(), any())).thenReturn(true);
        Map<String, Object> payloadAttrs = Map.of(
                "sql", "SELECT * FROM ecos_dw.t",            // 假设性原文（咽喉禁据此构造 detail）
                "content", "sensitive-raw-payload",
                "arguments", "raw-llm-args",
                "resource", "agent_tool:query_db");
        AgentToolPolicyGate.Decision d =
                gate.adjudicate("query_db", payloadAttrs, "user-42", "t-1");
        assertTrue(d.allowed());

        ArgumentCaptor<Map<String, Object>> detailCap = ArgumentCaptor.forClass(Map.class);
        verify(client, times(1)).publishGuardrailEventOrThrow(eq("user-42"), detailCap.capture());
        Map<String, Object> detail = detailCap.getValue();

        // detail Keys 白名单：{tool, decision, policyId, latencyMs, obligationsCount, errorClass?}
        for (String rawKey : new String[]{"sql", "content", "arguments", "result", "raw",
                "input", "output"}) {
            assertFalse(detail.containsKey(rawKey),
                    "detail 禁止携带原始键: " + rawKey + "，防止审计库变敏感面（X-58）");
        }
        // 且 value 中禁出现原文片段
        String jsonish = String.valueOf(detail.values());
        assertFalse(jsonish.contains("ecOS_dw.t".replace("ecOS", "ecos")),
                "detail values 禁携带 SQL 原文片段");
        assertFalse(jsonish.contains("sensitive-raw-payload"),
                "detail values 禁携带 content 原文片段");
        assertFalse(jsonish.contains("raw-llm-args"),
                "detail values 禁携带 arguments 原文片段");
    }
}
