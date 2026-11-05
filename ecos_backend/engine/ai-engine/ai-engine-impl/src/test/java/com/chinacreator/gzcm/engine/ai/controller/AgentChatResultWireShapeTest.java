package com.chinacreator.gzcm.engine.ai.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.dto.AgentChatResultVO;
import com.chinacreator.gzcm.engine.ai.service.AgentLoopResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单轮对话回执 wire 保真（DI-2 #147DW）。
 *
 * <p>成功腿 6 键；失败腿按条件键补齐错误三件套 + {@code retryable}；{@code toolCalls} 仅非空时出现且为尾键；
 * 泛型 {@code fromLoopResult} 与 Map 重载同一信封/状态行规则（失败不落 200 伪成功）。</p>
 */
class AgentChatResultWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> BASE_KEYS = List.of("sessionId", "traceId", "content",
            "turns", "totalTokens", "success");

    @Test
    @DisplayName("成功腿：6 键（无错误键/无 retryable/无 toolCalls）")
    void successKeepsBaseKeys() throws Exception {
        AgentLoopResult result = new AgentLoopResult();
        result.setSuccess(true);
        result.setSessionId("sess-1");
        result.setTraceId("trace-1");
        result.setContent("你好");
        result.setTurns(1);
        result.setTotalTokens(12);

        ResponseEntity<ApiResponse<AgentChatResultVO>> resp =
                AiResponseSupport.fromLoopResult(result, AgentChatResultVO.of(result, "sess-1", null, null));

        assertEquals(200, resp.getStatusCode().value());
        JsonNode data = MAPPER.valueToTree(resp.getBody()).get("data");
        assertEquals(BASE_KEYS, fieldNames(data));
        assertEquals("你好", data.get("content").asText());
    }

    @Test
    @DisplayName("失败腿：错误三件套 + retryable + toolCalls 尾键；状态行同分类")
    void failureKeepsConditionalKeys() throws Exception {
        AgentLoopResult result = new AgentLoopResult();
        result.setSuccess(false);
        result.setSessionId("sess-1");
        result.setTraceId("trace-1");
        result.setErrorClass("LLM_TIMEOUT");
        result.setErrorCode("ECOS-AI-504");
        result.setErrorMsg("模型超时");
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("name", "search");
        toolCalls.add(call);

        ResponseEntity<ApiResponse<AgentChatResultVO>> resp = AiResponseSupport.fromLoopResult(
                result, AgentChatResultVO.of(result, "sess-1",
                        AiResponseSupport.resolveClass(result).retryable(), toolCalls));

        assertEquals(AiResponseSupport.resolveClass(result).httpStatus(),
                resp.getStatusCode().value(), "失败不落 200 伪成功");
        JsonNode data = MAPPER.valueToTree(resp.getBody()).get("data");
        assertEquals(List.of("sessionId", "traceId", "content", "turns", "totalTokens", "success",
                "errorMsg", "errorCode", "errorClass", "retryable", "toolCalls"), fieldNames(data));
        assertTrue(data.has("content") && data.get("content").isNull(), "空内容键仍出现（旧 Map 同形）");
        assertEquals("search", data.get("toolCalls").get(0).get("name").asText());
    }

    @Test
    @DisplayName("空 toolCalls 不出键；失败但无分类时按兜底分类渲染")
    void emptyToolCallsOmittedAndFallbackClass() throws Exception {
        AgentLoopResult result = new AgentLoopResult();
        result.setSuccess(false);
        result.setSessionId("sess-2");

        ResponseEntity<ApiResponse<AgentChatResultVO>> resp = AiResponseSupport.fromLoopResult(
                result, AgentChatResultVO.of(result, "sess-2",
                        AiResponseSupport.resolveClass(result).retryable(), null));

        JsonNode data = MAPPER.valueToTree(resp.getBody()).get("data");
        assertFalse(data.has("toolCalls"));
        assertEquals("sess-2", data.get("sessionId").asText());
        assertTrue(resp.getStatusCode().value() >= 400);
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
