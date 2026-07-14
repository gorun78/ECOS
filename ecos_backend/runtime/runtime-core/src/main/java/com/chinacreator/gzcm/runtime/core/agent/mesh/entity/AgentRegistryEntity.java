package com.chinacreator.gzcm.runtime.core.agent.mesh.entity;

import java.time.LocalDateTime;

/**
 * Agent注册实体 — 映射 ecos_agent_registry (PG实际结构)
 */
public class AgentRegistryEntity {

    private String id;
    private String name;
    private String role;
    private String capability;    // JSON string
    private String status;
    private String endpoint;
    private String metadata;      // JSON string
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AgentRegistryEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getCapability() { return capability; }
    public void setCapability(String capability) { this.capability = capability; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // ── 兼容旧代码的派生方法 ──

    /** 从 capability JSON 或 metadata JSON 提取 system prompt */
    public String getSystemPrompt() {
        if (metadata != null && metadata.contains("systemPrompt")) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
                var node = m.readTree(metadata);
                if (node.has("systemPrompt")) return node.get("systemPrompt").asText();
            } catch (Exception ignored) {}
        }
        return capability != null ? capability : "";
    }

    /** 从 metadata JSON 提取 model */
    public String getModel() {
        if (metadata != null && metadata.contains("model")) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
                var node = m.readTree(metadata);
                if (node.has("model")) return node.get("model").asText();
            } catch (Exception ignored) {}
        }
        return "deepseek-chat";
    }

    /** 从 metadata JSON 提取 maxIterations */
    public Integer getMaxIterations() {
        if (metadata != null && metadata.contains("maxIterations")) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
                var node = m.readTree(metadata);
                if (node.has("maxIterations")) return node.get("maxIterations").asInt();
            } catch (Exception ignored) {}
        }
        return 10;
    }

    /** 从 metadata JSON 提取 description */
    public String getDescription() {
        if (metadata != null && metadata.contains("description")) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
                var node = m.readTree(metadata);
                if (node.has("description")) return node.get("description").asText();
            } catch (Exception ignored) {}
        }
        return name;
    }
}
