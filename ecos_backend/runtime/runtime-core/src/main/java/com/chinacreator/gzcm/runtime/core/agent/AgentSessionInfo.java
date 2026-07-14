package com.chinacreator.gzcm.runtime.core.agent;

import java.util.Date;

/**
 * Agent 会话信息 DTO
 * 用于会话列表展示，只包含摘要信息，不包含完整对话历史
 *
 * @author CDRC Design Team
 */
public class AgentSessionInfo {

    private String sessionId;
    private String title;
    private String systemPrompt;
    private int messageCount;
    private int totalTokens;
    private int toolCallCount;
    private int currentIteration;
    private int maxIterations;
    private boolean completed;
    private Date createdAt;

    public AgentSessionInfo() {
    }

    /**
     * 从 AgentSession 创建会话信息摘要
     */
    public static AgentSessionInfo from(AgentSession session) {
        AgentSessionInfo info = new AgentSessionInfo();
        info.sessionId = session.getId();
        info.title = session.getTitle();
        info.systemPrompt = session.getSystemPrompt();
        info.messageCount = session.getHistory() != null ? session.getHistory().size() : 0;
        info.totalTokens = session.getTotalTokens();
        info.toolCallCount = session.getToolCallCount();
        info.currentIteration = session.getCurrentIteration();
        info.maxIterations = session.getMaxIterations();
        info.completed = session.isCompleted();
        info.createdAt = new Date(session.getCreatedAt());
        return info;
    }

    // ── Getters & Setters ──────────────────────────

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public int getMessageCount() { return messageCount; }
    public void setMessageCount(int messageCount) { this.messageCount = messageCount; }

    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }

    public int getToolCallCount() { return toolCallCount; }
    public void setToolCallCount(int toolCallCount) { this.toolCallCount = toolCallCount; }

    public int getCurrentIteration() { return currentIteration; }
    public void setCurrentIteration(int currentIteration) { this.currentIteration = currentIteration; }

    public int getMaxIterations() { return maxIterations; }
    public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
