package com.chinacreator.gzcm.engine.ai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave-5.1 T-09 — Agent 行为 fallback 契约测试 (任务 5 等价: LLM 失败/工具/循环结果)。
 *
 * <p>覆盖:
 * <ol>
 *   <li>AgentLoopResult.success/maxTurnsExceeded/error 三个工厂方法字段一致</li>
 *   <li>toMap 必带 success/turns/content/toolCalls/totalTokens/sessionId 6 键</li>
 *   <li>Message.system / user / assistant / assistant(ToolCall) / toolResult 工厂字段契约
 *       (含 hasToolCalls + toMap 序列化)</li>
 *   <li>Message.toolResult 把 ToolExecutorService.ToolResult 商化前置 (content/error 降级)</li>
 * </ol>
 *
 * @author ECOS AI Engine Team
 * @since 2026-09-02 (Wave-5.1)
 */
class AgentLoopResultContractTest {

    // ── AgentLoopResult 工厂 ──

    @Test
    @DisplayName("T-09-5-1: AgentLoopResult.success — success=true, tokens/turns/content 透传")
    void successFactoryTrue() {
        AgentLoopResult r = AgentLoopResult.success("final answer", 3, "sess-1", 1200,
                List.of(Map.of("id", "call_1", "name", "search_kb")));
        assertTrue(r.isSuccess());
        assertEquals(3, r.getTurns());
        assertEquals("final answer", r.getContent());
        assertEquals("sess-1", r.getSessionId());
        assertEquals(1200, r.getTotalTokens());
        assertEquals(1, r.getToolCalls().size());
        assertNull(r.getErrorMsg());
    }

    @Test
    @DisplayName("T-09-5-2: maxTurnsExceeded — 固定 turns=5 + fixed errorMsg")
    void maxTurnsFactoryFixedFiveTurns() {
        AgentLoopResult r = AgentLoopResult.maxTurnsExceeded("sess-2", 850,
                List.of(Map.of("id", "call_2", "name", "lookup_metric")));
        assertFalse(r.isSuccess());
        assertEquals(5, r.getTurns());
        assertEquals("Agent loop exceeded maximum turns (5)", r.getErrorMsg());
        assertEquals(850, r.getTotalTokens());
        assertEquals("sess-2", r.getSessionId());
    }

    @Test
    @DisplayName("T-09-5-3: error — turns=0 + errorMsg, success=false, traceId 自定义透传")
    void errorFactoryKeepsTraceId() {
        AgentLoopResult r = AgentLoopResult.error("sess-3", "LLM 429 rate limit", 200);
        r.setTraceId("trace-xyz");
        assertFalse(r.isSuccess());
        assertEquals(0, r.getTurns());
        assertEquals("LLM 429 rate limit", r.getErrorMsg());
        assertEquals(200, r.getTotalTokens());
        assertEquals("trace-xyz", r.getTraceId());
    }

    // ── toMap 序列化 ──

    @Test
    @DisplayName("T-09-5-4: toMap 必带 6 键, success 时多不带 errorMsg/traceId")
    void successToMapOmitsErrorMsgAndTraceId() {
        AgentLoopResult r = AgentLoopResult.success("ok", 1, "s", 10, List.of());
        Map<String, Object> m = r.toMap();
        assertEquals(6, m.size(),
                "success 结果应按 turns/content/toolCalls/totalTokens/sessionId/success 共 6 键, 实际=" + m);
        assertEquals("ok", m.get("content"));
        assertEquals(true, m.get("success"));
        assertNull(m.get("errorMsg"));
        assertNull(m.get("traceId"));
    }

    @Test
    @DisplayName("T-09-5-5: error 时 toMap 携带 errorMsg + traceId (共 8 键)")
    void errorToMapIncludesErrorMsgAndTraceId() {
        AgentLoopResult r = AgentLoopResult.error("s", "boom", 0);
        r.setTraceId("t-1");
        Map<String, Object> m = r.toMap();
        assertEquals(8, m.size());
        assertEquals("boom", m.get("errorMsg"));
        assertEquals("t-1", m.get("traceId"));
        assertEquals(false, m.get("success"));
    }

    // ── Message factories ──

    @Test
    @DisplayName("T-09-5-6: Message system/user/assistant 工厂 — role + content + 无 tools")
    void basicMessageFactories() {
        Message s = Message.system("你是 ECOS 的财务分析助手");
        Message u = Message.user("帮我诊断为什么毛利率下降");
        Message a = Message.assistant("毛利率下降主要受原材料涨价影响");

        assertEquals("system", s.getRole());
        assertEquals("user", u.getRole());
        assertEquals("assistant", a.getRole());
        assertNotNull(s.getContent());
        assertNotNull(u.getContent());
        assertNotNull(a.getContent());
        assertFalse(s.hasToolCalls());
        assertFalse(a.hasToolCalls());
    }

    @Test
    @DisplayName("T-09-5-7: assistant(ToolCall) 工厂 — content=null, toolCalls=1")
    void assistantWithToolCallHasNoContent() {
        ToolCall tc = new ToolCall();
        tc.setId("call_X");
        tc.setName("search_kb");
        tc.setArguments(Map.of("q", "毛利率"));

        Message m = Message.assistant(tc);

        assertEquals("assistant", m.getRole());
        assertNull(m.getContent(), "toolCall 消息 content 必须 null");
        assertTrue(m.hasToolCalls());
        assertEquals(1, m.getToolCalls().size());
        assertEquals("search_kb", m.getToolCalls().get(0).getName());
    }

    @Test
    @DisplayName("T-09-5-8: toolResult — 透传 ToolResult.content + toolCallId + toolName (有 error 也前置 content)")
    void toolResultUsesToolResultContent() {
        ToolExecutorService.ToolResult ok = ToolExecutorService.ToolResult.ok("c1", "search_kb", "3 条命中", 42L);
        ToolExecutorService.ToolResult fail = ToolExecutorService.ToolResult.fail("c2", "lookup_metric", "metric not found", 7L);

        Message mOk = Message.toolResult(ok);
        assertEquals("tool", mOk.getRole());
        assertEquals("search_kb", mOk.getToolName());
        assertEquals("c1", mOk.getToolCallId());
        assertEquals("3 条命中", mOk.getContent());

        Message mFail = Message.toolResult(fail);
        assertEquals("c2", mFail.getToolCallId());
        assertEquals("lookup_metric", mFail.getToolName());
        assertTrue(mFail.getContent().startsWith("ERROR:"),
                "ToolResult.fail 时 content 必须是 'ERROR: ' 前缀, 实际=" + mFail.getContent());
    }

    @Test
    @DisplayName("T-09-5-9: Message.toMap 截断 2000 字, 保留 role 与 toolCalls")
    void toMapTruncatesLongContent() {
        String big = "x".repeat(2500);
        Message m = Message.user(big);
        Map<String, Object> map = m.toMap();
        assertEquals("user", map.get("role"));
        assertNotNull(map.get("content"));
        assertTrue(((String) map.get("content")).endsWith("[truncated]"));
        assertEquals(2000 + "...[truncated]".length(), ((String) map.get("content")).length());
        assertFalse(map.containsKey("toolCalls"), "无 toolCall 时 toMap 不带该键");
    }
}
