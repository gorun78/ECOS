package com.chinacreator.gzcm.runtime.llm.gateway;

/**
 * LLM 调用响应
 * <p>
 * 用量语义（详细设计-06 F06-06 要点 3 / F06-07 要点 1）：{@code tokensInput}/{@code tokensOutput}
 * 只有在 {@link #hasUsage()} 为真时才是可信数值 —— provider 上报（{@code usageReported}）
 * 或网关按 tokenizer 估算（{@code usageEstimated}，禁当实测上报）。两者皆 false 时
 * 调用方必须记「缺失」（-1），禁止把默认 0 冒充实测。
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
}
