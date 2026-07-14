package com.chinacreator.gzcm.runtime.core.agent;

import java.util.Date;
import java.util.Map;

/**
 * Agent 执行结果
 * 包含 Agent 运行的完整输出和执行元数据
 *
 * @author CDRC Design Team
 */
public class AgentResult {

    private String sessionId;
    private String finalAnswer;          // 最终回答
    private int totalTokens;             // Token 总消耗
    private int toolCalls;               // 工具调用次数
    private long durationMs;             // 执行耗时（毫秒）
    private boolean success;             // 是否成功
    private String errorMessage;         // 错误消息（失败时）
    private Map<String, Object> metadata;
    private Date completedAt;

    public AgentResult() {
        this.completedAt = new Date();
    }

    // ── Getters & Setters ──────────────────────────

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getFinalAnswer() { return finalAnswer; }
    public void setFinalAnswer(String finalAnswer) { this.finalAnswer = finalAnswer; }

    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }

    public int getToolCalls() { return toolCalls; }
    public void setToolCalls(int toolCalls) { this.toolCalls = toolCalls; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public Date getCompletedAt() { return completedAt; }
    public void setCompletedAt(Date completedAt) { this.completedAt = completedAt; }

    // ── Factory methods ──────────────────────────

    public static AgentResult success(String sessionId, String answer, int tokens, int toolCalls, long durationMs) {
        AgentResult r = new AgentResult();
        r.sessionId = sessionId;
        r.finalAnswer = answer;
        r.totalTokens = tokens;
        r.toolCalls = toolCalls;
        r.durationMs = durationMs;
        r.success = true;
        return r;
    }

    public static AgentResult error(String sessionId, String errorMessage) {
        AgentResult r = new AgentResult();
        r.sessionId = sessionId;
        r.errorMessage = errorMessage;
        r.success = false;
        return r;
    }
}
