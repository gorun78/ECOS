package com.chinacreator.gzcm.runtime.core.agent.llm;

import java.util.List;
import java.util.Map;

/**
 * 对话响应
 * LLM 返回的完整响应
 *
 * @author CDRC Design Team
 */
public class ChatResponse {

    private String id;                          // 响应 ID
    private String model;                       // 使用的模型
    private String content;                     // 文本回复内容
    private List<ChatRequest.ToolCallRequest> toolCalls; // 工具调用请求
    private String finishReason;                // stop / tool_calls / length / content_filter
    private TokenUsage usage;                   // Token 使用统计
    private Map<String, Object> metadata;

    // ── Inner class ──────────────────────────

    public static class TokenUsage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;

        public TokenUsage() {}

        public TokenUsage(int promptTokens, int completionTokens, int totalTokens) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }

        public int getPromptTokens() { return promptTokens; }
        public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }

        public int getCompletionTokens() { return completionTokens; }
        public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }

        public int getTotalTokens() { return totalTokens; }
        public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
    }

    // ── Getters & Setters ──────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<ChatRequest.ToolCallRequest> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ChatRequest.ToolCallRequest> toolCalls) { this.toolCalls = toolCalls; }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public String getFinishReason() { return finishReason; }
    public void setFinishReason(String finishReason) { this.finishReason = finishReason; }

    public TokenUsage getUsage() { return usage; }
    public void setUsage(TokenUsage usage) { this.usage = usage; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
