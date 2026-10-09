package com.chinacreator.gzcm.runtime.llm.gateway;

import com.chinacreator.gzcm.common.error.AiErrorClass;

/**
 * LLM 调用响应
 * <p>
 * 用量语义（详细设计-06 F06-06 要点 3 / F06-07 要点 1）：{@code tokensInput}/{@code tokensOutput}
 * 只有在 {@link #hasUsage()} 为真时才是可信数值 —— provider 上报（{@code usageReported}）
 * 或网关按 tokenizer 估算（{@code usageEstimated}，禁当实测上报）。两者皆 false 时
 * 调用方必须记「缺失」（-1），禁止把默认 0 冒充实测。
 * </p>
 * <p>
 * 失败语义（F06-14）：{@code errorMsg} 为内部原因摘要，<b>只入日志</b>（X-49 禁进响应体）；
 * 对外文案由调用方按 {@code errorClass} 渲染模板。{@code retriesAttempted} 记录网关内部
 * 已消耗的有界退避次数，配额在网关单源结算，引擎侧不得叠加。
 * </p>
 */
public class ChatResponse {

    /** 数值缺失标记（与"实测为 0"区分；与 common-api AgentLoopTrace.UNKNOWN 同值）。 */
    public static final long MISSING = -1L;

    private String content;
    private int tokensInput;
    private int tokensOutput;
    private String model;
    private boolean success;
    private String errorMsg;
    /** provider 在响应 usage 段上报了真值 */
    private boolean usageReported;
    /** provider 未上报，网关按 tokenizer 估算（必须与实测严格区分） */
    private boolean usageEstimated;
    /** 网关侧本次调用耗时（毫秒）；未知 {@link #MISSING} */
    private long latencyMs = MISSING;
    /** 失败分类（{@link AiErrorClass} 枚举名）；成功为 null */
    private String errorClass;
    /** 失败错误码字面量（{@code ECOS-AI-*}）；成功为 null */
    private String errorCode;
    /** 网关内部有界退避已消耗次数（配额单源，禁调用方另立重试） */
    private int retriesAttempted;
    /** 是否已在网关内部路由到备用 provider（F06-14 要点 2，引擎侧禁绕行） */
    private boolean fallbackProviderUsed;

    public ChatResponse() {}

    /** 成功响应构造器 */
    public static ChatResponse ok(String content, int tokensInput, int tokensOutput, String model) {
        ChatResponse r = new ChatResponse();
        r.content = content;
        r.tokensInput = tokensInput;
        r.tokensOutput = tokensOutput;
        r.model = model;
        r.success = true;
        return r;
    }

    /** 错误响应构造器 */
    public static ChatResponse fail(String errorMsg) {
        ChatResponse r = new ChatResponse();
        r.success = false;
        r.errorMsg = errorMsg;
        return r;
    }

    /** 带 F06-14 分类的错误响应构造器（分类即契约，调用方据此渲染模板消息与重试判定）。 */
    public static ChatResponse fail(AiErrorClass errorClass, String errorMsg) {
        ChatResponse r = fail(errorMsg);
        r.setErrorClass(errorClass);
        return r;
    }

    /** 本次失败是否属可重试类（供调用方判断"网关已用尽配额"与"根本不可重试"）。 */
    public boolean isRetryableClass() {
        AiErrorClass cls = aiErrorClass();
        return cls != null && cls.retryable();
    }

    /** 分类枚举（不可识别返回 null，禁伪造分类）。 */
    public AiErrorClass aiErrorClass() {
        if (errorClass == null || errorClass.isBlank()) {
            return null;
        }
        for (AiErrorClass c : AiErrorClass.values()) {
            if (c.name().equals(errorClass)) {
                return c;
            }
        }
        return null;
    }

    /** 设置分类并同步错误码字面量（二者单源，禁漂移）。 */
    public void setErrorClass(AiErrorClass cls) {
        this.errorClass = cls == null ? null : cls.name();
        this.errorCode = cls == null ? null : cls.errorCode();
    }

    /** 本次响应是否携带可用用量（实测或估算）。 */
    public boolean hasUsage() {
        return usageReported || usageEstimated;
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getTokensInput() { return tokensInput; }
    public void setTokensInput(int tokensInput) { this.tokensInput = tokensInput; }

    public int getTokensOutput() { return tokensOutput; }
    public void setTokensOutput(int tokensOutput) { this.tokensOutput = tokensOutput; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }

    public boolean isUsageReported() { return usageReported; }
    public void setUsageReported(boolean usageReported) { this.usageReported = usageReported; }

    public boolean isUsageEstimated() { return usageEstimated; }
    public void setUsageEstimated(boolean usageEstimated) { this.usageEstimated = usageEstimated; }

    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    public String getErrorClass() { return errorClass; }
    public void setErrorClass(String errorClass) { this.errorClass = errorClass; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public int getRetriesAttempted() { return retriesAttempted; }
    public void setRetriesAttempted(int retriesAttempted) { this.retriesAttempted = retriesAttempted; }

    public boolean isFallbackProviderUsed() { return fallbackProviderUsed; }
    public void setFallbackProviderUsed(boolean fallbackProviderUsed) {
        this.fallbackProviderUsed = fallbackProviderUsed;
    }
}

