package com.chinacreator.gzcm.common.error;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 智能域一次错误判定的不可变结果（详细设计-06 F06-14；{@code ErrorCodeMapper#classify} 的返回类型）。
 *
 * <p>{@code rawReason} 只允许进入日志（PRD §5.1 PARSE_FAILURE「原文只入日志」、X-49 禁外泄），
 * 对外三视图（HTTP body / SSE {@code error} 帧 / 结构化日志行）一律只使用
 * {@code errorCode + errorClass + message(渲染后的模板) + traceId + retryable}。</p>
 *
 * @param errorClass   七类之一或 D.5 补充类
 * @param traceId      MDC traceId（诊断码唯一源，F06-06）
 * @param attemptsMade 已消耗的重试/重生成次数
 * @param rawReason    内部原因摘要，禁入响应体
 * @param service      下游服务名（{@code TOOL_BACKEND_ERROR} 的 {service} 占位）
 */
public record AiError(AiErrorClass errorClass, String traceId, int attemptsMade,
                      String rawReason, String service) {

    public static AiError of(AiErrorClass errorClass, String traceId) {
        return new AiError(errorClass, traceId, 0, null, null);
    }

    public static AiError of(AiErrorClass errorClass, String traceId, String rawReason) {
        return new AiError(errorClass, traceId, 0, rawReason, null);
    }

    public static AiError toolBackend(String traceId, String service, String rawReason) {
        return new AiError(AiErrorClass.TOOL_BACKEND_ERROR, traceId, 0, rawReason, service);
    }

    public static AiError withAttempts(AiErrorClass errorClass, String traceId, int attemptsMade,
                                       String rawReason) {
        return new AiError(errorClass, traceId, attemptsMade, rawReason, null);
    }

    public String errorCode() {
        return errorClass.errorCode();
    }

    public int httpStatus() {
        return errorClass.httpStatus();
    }

    public boolean retryable() {
        return errorClass.canRetry(attemptsMade);
    }

    public String userMessage() {
        return errorClass.renderMessage(traceId, service);
    }

    /** 是否还可继续重试/重生成（false = 必须终止 Loop 并落错误态）。 */
    public boolean exhausted() {
        return !retryable();
    }

    /** HTTP 响应体形态：仅 errorCode + 诊断码，无原文（验收 {@code AiExceptionHygieneTest#responseBodyNeverContainsRawExceptionMessage}）。 */
    public Map<String, Object> toResponseBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("errorCode", errorCode());
        body.put("errorClass", errorClass.name());
        body.put("message", userMessage());
        body.put("traceId", traceId == null ? "-" : traceId);
        body.put("retryable", retryable());
        return body;
    }

    /** SSE {@code error} 帧 data 形态（详细设计-06 §5.4）。 */
    public Map<String, Object> toSseErrorFrame() {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("errorCode", errorCode());
        frame.put("class", errorClass.name());
        frame.put("message", userMessage());
        frame.put("retryable", retryable());
        return frame;
    }
}
