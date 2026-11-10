package com.chinacreator.gzcm.engine.ai.agent.mesh.entity;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Agent注册实体 — 映射 ecos_agent_registry (PG实际结构)
 */
public class AgentRegistryEntity {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistryEntity.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    // ── 兼容旧代码的派生属性写面（P1-Copilot D1） ──
    // 派生 getter 读 metadata；若无对应 setter，Jackson 会把这些属性当只读而静默丢弃，
    // 导致 AI 工作台保存 prompt/model/description 全部丢失。以下 setter 统一读-改-写 metadata JSON，
    // 不动其他键（routes/rag/toolWhitelist 等）。

    public void setSystemPrompt(String systemPrompt) {
        putMetadataText("systemPrompt", systemPrompt);
    }

    public void setModel(String model) {
        putMetadataText("model", model);
    }

    public void setDescription(String description) {
        putMetadataText("description", description);
    }

    public void setMaxIterations(Integer maxIterations) {
        if (maxIterations == null) {
            return;
        }
        ObjectNode node = metadataNode();
        node.put("maxIterations", maxIterations);
        this.metadata = node.toString();
    }

    private void putMetadataText(String key, String value) {
        if (value == null) {
            return;
        }
        ObjectNode node = metadataNode();
        node.put(key, value);
        this.metadata = node.toString();
    }

    private ObjectNode metadataNode() {
        if (metadata != null && !metadata.isBlank()) {
            try {
                JsonNode parsed = MAPPER.readTree(metadata);
                if (parsed instanceof ObjectNode objectNode) {
                    return objectNode;
                }
            } catch (Exception ignored) {
                log.debug("Agent metadata JSON 解析失败，重建对象: id={} reason={}", id, ignored.getMessage());
            }
        }
        return MAPPER.createObjectNode();
    }

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
            } catch (Exception ignored) {
                log.debug("Agent metadata JSON 解析失败，systemPrompt 回退 capability: id={} reason={}", id, ignored.getMessage());
            }
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
            } catch (Exception ignored) {
                log.debug("Agent metadata JSON 解析失败，model 回退 deepseek-chat: id={} reason={}", id, ignored.getMessage());
            }
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
            } catch (Exception ignored) {
                log.debug("Agent metadata JSON 解析失败，maxIterations 回退 10: id={} reason={}", id, ignored.getMessage());
            }
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
            } catch (Exception ignored) {
                log.debug("Agent metadata JSON 解析失败，description 回退 name: id={} reason={}", id, ignored.getMessage());
            }
        }
        return name;
    }
}
