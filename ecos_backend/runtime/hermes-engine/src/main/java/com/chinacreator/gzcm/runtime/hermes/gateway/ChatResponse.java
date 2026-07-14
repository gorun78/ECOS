package com.chinacreator.gzcm.runtime.hermes.gateway;

/**
 * LLM 调用响应
 */
public class ChatResponse {

    private String content;
    private int tokensInput;
    private int tokensOutput;
    private String model;
    private boolean success;
    private String errorMsg;

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
}
