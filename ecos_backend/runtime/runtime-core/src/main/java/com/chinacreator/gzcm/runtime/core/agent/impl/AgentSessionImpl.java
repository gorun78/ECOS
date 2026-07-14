package com.chinacreator.gzcm.runtime.core.agent.impl;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.chinacreator.gzcm.runtime.core.agent.AgentMessage;
import com.chinacreator.gzcm.runtime.core.agent.AgentSession;

/**
 * Agent 会话实现（内存版）
 *
 * @author CDRC Design Team
 */
public class AgentSessionImpl implements AgentSession {

    private final String id;
    private String title;
    private String systemPrompt;
    private final List<AgentMessage> history;
    private int totalTokens;
    private int toolCallCount;
    private int maxIterations = 10;
    private int currentIteration;
    private boolean completed;
    private final long createdAt;
    private final Map<String, Object> metadata;

    public AgentSessionImpl(String id, String title, String systemPrompt) {
        this.id = id;
        this.title = title;
        this.systemPrompt = systemPrompt;
        this.history = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.metadata = new ConcurrentHashMap<>();

        // 自动添加系统消息
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            this.history.add(AgentMessage.system(systemPrompt));
        }
    }

    @Override
    public String getId() { return id; }

    @Override
    public String getTitle() { return title; }

    @Override
    public void setTitle(String title) { this.title = title; }

    @Override
    public List<AgentMessage> getHistory() {
        return Collections.unmodifiableList(history);
    }

    @Override
    public void addMessage(AgentMessage message) {
        history.add(message);
    }

    @Override
    public String getSystemPrompt() { return systemPrompt; }

    @Override
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        // 更新第一条系统消息
        if (!history.isEmpty() && history.get(0).getRole() == AgentMessage.Role.SYSTEM) {
            history.set(0, AgentMessage.system(systemPrompt));
        }
    }

    @Override
    public int getTotalTokens() { return totalTokens; }

    @Override
    public void addTokens(int tokens) { this.totalTokens += tokens; }

    @Override
    public int getToolCallCount() { return toolCallCount; }

    @Override
    public void incrementToolCallCount() { this.toolCallCount++; }

    @Override
    public int getMaxIterations() { return maxIterations; }

    @Override
    public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }

    @Override
    public int getCurrentIteration() { return currentIteration; }

    @Override
    public void incrementIteration() { this.currentIteration++; }

    @Override
    public boolean isCompleted() { return completed; }

    @Override
    public void complete() { this.completed = true; }

    @Override
    public long getCreatedAt() { return createdAt; }

    @Override
    public Object getMetadata(String key) { return metadata.get(key); }

    @Override
    public void setMetadata(String key, Object value) { metadata.put(key, value); }

    @Override
    public void clearHistory() {
        history.clear();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            history.add(AgentMessage.system(systemPrompt));
        }
        currentIteration = 0;
    }
}
