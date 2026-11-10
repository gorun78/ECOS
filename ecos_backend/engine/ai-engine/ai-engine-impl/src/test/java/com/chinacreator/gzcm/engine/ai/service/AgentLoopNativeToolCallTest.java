package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-1：原生 function calling 解析（ChatResponse.tool_calls 优先于文本协议）。
 */
@DisplayName("P0-1 原生 tool_calls 解析：provider 结构优先、arguments 双形态、文本兜底保留")
class AgentLoopNativeToolCallTest {

    @Test
    @DisplayName("ChatResponse.tool_calls（arguments=JSON 字符串）→ 优先解析为 ToolCall")
    void nativeToolCallsArePreferred() {
        AgentLoopService service = new AgentLoopService();
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", "query_db");
        fn.put("arguments", "{\"sql\":\"select 1\"}");
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("id", "call_1");
        call.put("type", "function");
        call.put("function", fn);
        ChatResponse resp = ChatResponse.ok(null, 0, 0, "deepseek-chat");
        resp.setToolCalls(List.of(call));

        List<ToolCall> parsed = ReflectionTestUtils.invokeMethod(service, "parseToolCalls", resp);

        assertNotNull(parsed);
        assertEquals(1, parsed.size());
        assertEquals("query_db", parsed.get(0).getName());
        assertEquals("call_1", parsed.get(0).getId());
        assertEquals("select 1", parsed.get(0).getArguments().get("sql"));
    }

    @Test
    @DisplayName("arguments 为 Map（流式聚合后的形态）同样解析")
    void nativeArgumentsAsMapAlsoParse() {
        AgentLoopService service = new AgentLoopService();
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", "invoke_rest");
        fn.put("arguments", Map.of("url", "https://api.local/x"));
        ChatResponse resp = ChatResponse.ok("", 0, 0, "deepseek-chat");
        resp.setToolCalls(List.of(Map.of("id", "call_2", "type", "function", "function", fn)));

        List<ToolCall> parsed = ReflectionTestUtils.invokeMethod(service, "parseToolCalls", resp);

        assertNotNull(parsed);
        assertEquals(1, parsed.size());
        assertEquals("invoke_rest", parsed.get(0).getName());
        assertEquals("https://api.local/x", parsed.get(0).getArguments().get("url"));
    }

    @Test
    @DisplayName("无原生 tool_calls 时保留文本协议兜底（<tool_call> 标记）")
    void textFallbackPreserved() {
        AgentLoopService service = new AgentLoopService();
        ChatResponse resp = ChatResponse.ok(
                "<tool_call>{\"name\":\"read_file\",\"arguments\":{\"path\":\"a.md\"}}</tool_call>",
                0, 0, "deepseek-chat");

        List<ToolCall> parsed = ReflectionTestUtils.invokeMethod(service, "parseToolCalls", resp);

        assertNotNull(parsed);
        assertEquals(1, parsed.size());
        assertEquals("read_file", parsed.get(0).getName());
        assertEquals("a.md", parsed.get(0).getArguments().get("path"));
    }

    @Test
    @DisplayName("纯文本回复不产生工具调用")
    void plainTextIsNotAToolCall() {
        AgentLoopService service = new AgentLoopService();
        ChatResponse resp = ChatResponse.ok("营收 12 亿元。", 0, 0, "deepseek-chat");

        List<ToolCall> parsed = ReflectionTestUtils.invokeMethod(service, "parseToolCalls", resp);

        assertTrue(parsed == null || parsed.isEmpty());
    }
}
