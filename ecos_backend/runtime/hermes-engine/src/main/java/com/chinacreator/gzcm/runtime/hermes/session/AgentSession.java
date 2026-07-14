package com.chinacreator.gzcm.runtime.hermes.session;

import java.time.LocalDateTime;

/**
 * Agent 会话 — 记录一次 Agent 执行过程的上下文
 */
public class AgentSession {

    private String sessionId;
    private String subsystem;
    private String profileName;
    private String systemPrompt;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivityAt;
    private String status;  // active / closed / timedout

    public AgentSession() {}

    public AgentSession(String sessionId, String subsystem, String profileName,
                        String systemPrompt) {
        this.sessionId = sessionId;
        this.subsystem = subsystem;
        this.profileName = profileName;
        this.systemPrompt = systemPrompt;
        this.createdAt = LocalDateTime.now();
        this.lastActivityAt = this.createdAt;
        this.status = "active";
    }

    /** 更新最后活动时间 */
    public void touch() {
        this.lastActivityAt = LocalDateTime.now();
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getSubsystem() { return subsystem; }
    public void setSubsystem(String subsystem) { this.subsystem = subsystem; }

    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(LocalDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
