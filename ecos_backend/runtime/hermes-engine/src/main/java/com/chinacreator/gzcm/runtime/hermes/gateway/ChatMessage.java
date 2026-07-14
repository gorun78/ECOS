package com.chinacreator.gzcm.runtime.hermes.gateway;

/**
 * LLM 聊天消息 — 角色 + 内容
 */
public class ChatMessage {

    private String role;
    private String content;

    public ChatMessage() {}

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
