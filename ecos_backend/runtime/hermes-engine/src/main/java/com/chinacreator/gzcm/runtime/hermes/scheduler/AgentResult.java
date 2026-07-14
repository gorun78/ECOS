package com.chinacreator.gzcm.runtime.hermes.scheduler;

import org.apache.ibatis.type.Alias;

/**
 * Agent 调度执行结果
 */
@Alias("HermesAgentResult")
public class AgentResult {

    private String sessionId;
    private boolean success;
    private String content;
    private int tokensInput;
    private int tokensOutput;
    private long durationMs;
    private String errorMsg;

    public AgentResult() {}

    /** 成功结果构造器 */
    public static AgentResult ok(String sessionId, String content,
                                  int tokensInput, int tokensOutput, long durationMs) {
        AgentResult r = new AgentResult();
        r.sessionId = sessionId;
        r.success = true;
        r.content = content;
        r.tokensInput = tokensInput;
        r.tokensOutput = tokensOutput;
        r.durationMs = durationMs;
        return r;
    }

    /** 错误结果构造器 */
    public static AgentResult fail(String sessionId, String errorMsg, long durationMs) {
        AgentResult r = new AgentResult();
        r.sessionId = sessionId;
        r.success = false;
        r.errorMsg = errorMsg;
        r.durationMs = durationMs;
        return r;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getTokensInput() { return tokensInput; }
    public void setTokensInput(int tokensInput) { this.tokensInput = tokensInput; }

    public int getTokensOutput() { return tokensOutput; }
    public void setTokensOutput(int tokensOutput) { this.tokensOutput = tokensOutput; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
}
