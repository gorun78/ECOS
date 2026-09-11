package com.chinacreator.gzcm.engine.ai.oag.dto;

import com.chinacreator.gzcm.engine.ai.oag.OagPipelineContext;

/**
 * OAG 强类型对话响应 DTO。
 *
 * <p>用于 {@code POST /api/v1/oag/chat} 和 {@code POST /api/v1/oag/chat/v2} 的响应体。
 * 通过 {@link #from(OagPipelineContext)} 管道上下文工厂方法构建。</p>
 *
 * <p>字段对齐旧版 {@code buildResponse(OagPipelineContext)} 输出，
 * 外层由 {@code ApiResponse<OagChatResponse>} 包装。</p>
 */
public class OagChatResponse {

    /** 追踪 ID */
    private String traceId;

    /** 会话 ID */
    private String sessionId;

    /** 管道状态：COMPLETED / BLOCKED / FAILED */
    private String status;

    /** 意图分类结果 */
    private String intent;

    /** 安全检查是否通过 */
    private boolean securityPassed;

    /** 管道总耗时（ms） */
    private long elapsedMs;

    /** LLM 生成的回复内容 */
    private String content;

    /** 错误信息（FAILED 时） */
    private String errorMessage;

    /** 安全阻止原因（BLOCKED 时） */
    private String blockReason;

    /** 实际使用的 LLM 模型 */
    private String model;

    /** LLM 消耗 token 数 */
    private Integer tokensUsed;

    /** 推理节点耗时（ms） */
    private Long reasoningLatencyMs;

    // ── 工厂方法 ──────────────────────────────────────────

    /**
     * 从 {@link OagPipelineContext} 构建强类型响应。
     *
     * @param ctx 管道上下文（已完成执行的 8 步 DAG）
     * @return 强类型 OAG 响应
     */
    public static OagChatResponse from(OagPipelineContext ctx) {
        OagChatResponse r = new OagChatResponse();
        r.traceId = ctx.getTraceId();
        r.sessionId = ctx.getSessionId();
        r.status = ctx.getStatus();
        r.intent = ctx.getIntent();
        r.securityPassed = ctx.isSecurityPassed();
        r.elapsedMs = ctx.getElapsedMs();
        r.content = ctx.getFinalResponse();
        r.errorMessage = ctx.getErrorMessage();
        r.blockReason = ctx.getSecurityBlockReason();

        java.util.Map<String, Object> reasoning = ctx.getReasoningResult();
        if (reasoning != null) {
            Object model = reasoning.get("model");
            if (model instanceof String s) {
                r.model = s;
            }
            Object tokens = reasoning.get("tokensUsed");
            if (tokens instanceof Number n) {
                r.tokensUsed = n.intValue();
            }
            Object latency = reasoning.get("latencyMs");
            if (latency instanceof Number n) {
                r.reasoningLatencyMs = n.longValue();
            }
        }
        return r;
    }

    // ── getter / setter ──────────────────────────────────

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public boolean isSecurityPassed() { return securityPassed; }
    public void setSecurityPassed(boolean securityPassed) { this.securityPassed = securityPassed; }

    public long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(long elapsedMs) { this.elapsedMs = elapsedMs; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getBlockReason() { return blockReason; }
    public void setBlockReason(String blockReason) { this.blockReason = blockReason; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Integer getTokensUsed() { return tokensUsed; }
    public void setTokensUsed(Integer tokensUsed) { this.tokensUsed = tokensUsed; }

    public Long getReasoningLatencyMs() { return reasoningLatencyMs; }
    public void setReasoningLatencyMs(Long reasoningLatencyMs) { this.reasoningLatencyMs = reasoningLatencyMs; }
}
