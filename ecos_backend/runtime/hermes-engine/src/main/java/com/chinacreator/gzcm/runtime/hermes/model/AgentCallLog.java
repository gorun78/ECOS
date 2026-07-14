package com.chinacreator.gzcm.runtime.hermes.model;

import java.time.LocalDateTime;

/**
 * Agent 调用日志实体 — 映射 sys_agent_call_log 表
 * <p>
 * 每次 Agent 内部 LLM 调用（包括工具回调）记录一条日志，
 * 用于 Token 统计、耗时分析、调用链追踪。
 * </p>
 */
public class AgentCallLog {

    private String id;
    private String subsystem;
    private String profileName;
    private String sessionId;
    private String userMessage;
    private int tokensInput;
    private int tokensOutput;
    private int durationMs;
    private String status;        // success / failed / timeout
    private String errorMsg;
    private LocalDateTime createdTime;

    public AgentCallLog() {}

    // ── Builder pattern ──

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final AgentCallLog log = new AgentCallLog();

        public Builder id(String v) { log.id = v; return this; }
        public Builder subsystem(String v) { log.subsystem = v; return this; }
        public Builder profileName(String v) { log.profileName = v; return this; }
        public Builder sessionId(String v) { log.sessionId = v; return this; }
        public Builder userMessage(String v) { log.userMessage = v; return this; }
        public Builder tokensInput(int v) { log.tokensInput = v; return this; }
        public Builder tokensOutput(int v) { log.tokensOutput = v; return this; }
        public Builder durationMs(int v) { log.durationMs = v; return this; }
        public Builder status(String v) { log.status = v; return this; }
        public Builder errorMsg(String v) { log.errorMsg = v; return this; }
        public Builder createdTime(LocalDateTime v) { log.createdTime = v; return this; }

        public AgentCallLog build() { return log; }
    }

    // ── Getters / Setters ──

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSubsystem() { return subsystem; }
    public void setSubsystem(String subsystem) { this.subsystem = subsystem; }

    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }

    public int getTokensInput() { return tokensInput; }
    public void setTokensInput(int tokensInput) { this.tokensInput = tokensInput; }

    public int getTokensOutput() { return tokensOutput; }
    public void setTokensOutput(int tokensOutput) { this.tokensOutput = tokensOutput; }

    public int getDurationMs() { return durationMs; }
    public void setDurationMs(int durationMs) { this.durationMs = durationMs; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
}
