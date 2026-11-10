package com.chinacreator.gzcm.runtime.llm.gateway;

import java.util.List;
import java.util.Map;

/**
 * LLM 聊天消息 — 角色 + 内容（对齐 OpenAI 兼容 messages 结构）。
 *
 * <p>原生 function calling（P0-1）：assistant 消息可携带 {@link #toolCalls}，
 * {@code role=tool} 消息可携带 {@link #toolCallId}/{@link #name}，
 * 由 {@code LLMGatewayImpl} 原样序列化到 provider 请求体，不再把工具协议压进 content 文本。</p>
 */
public class ChatMessage {

    private String role;
    private String content;
    private List<Map<String, Object>> toolCalls;
    private String toolCallId;
    private String name;

    public ChatMessage() {}

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<Map<String, Object>> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<Map<String, Object>> toolCalls) { this.toolCalls = toolCalls; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
