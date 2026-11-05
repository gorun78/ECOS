package com.chinacreator.gzcm.engine.ai.dto;

import com.chinacreator.gzcm.engine.ai.service.AgentLoopResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Map;

/**
 * 单轮对话回执（{@code POST /agent-loop/chat} 与 {@code /sessions/{id}/chat}）。
 *
 * <p>键序：{@code sessionId/traceId/content/turns/totalTokens/success[/errorMsg][/errorCode]
 * [/errorClass][/retryable][/toolCalls]}。条件键：错误三件套仅在 Loop 上报时出现；
 * {@code retryable} 仅失败态出现（与旧 Map 同形）；{@code toolCalls} 仅在非空时出现，
 * 元素为属主 {@code AgentLoopResult#getToolCalls()} 的原始结构（工具记录动态，不冻结）。</p>
 */
@JsonPropertyOrder({"sessionId", "traceId", "content", "turns", "totalTokens", "success",
        "errorMsg", "errorCode", "errorClass", "retryable", "toolCalls"})
public record AgentChatResultVO(
        String sessionId,
        String traceId,
        String content,
        int turns,
        int totalTokens,
        boolean success,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorMsg,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorCode,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorClass,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean retryable,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<Map<String, Object>> toolCalls) {

    public static AgentChatResultVO of(AgentLoopResult result, String sessionId,
                                       Boolean retryable,
                                       List<Map<String, Object>> toolCalls) {
        return new AgentChatResultVO(
                sessionId,
                result.getTraceId(),
                result.getContent(),
                result.getTurns(),
                result.getTotalTokens(),
                result.isSuccess(),
                result.getErrorMsg(),
                result.getErrorCode(),
                result.getErrorClass(),
                retryable,
                toolCalls);
    }
}
