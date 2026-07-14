package com.chinacreator.gzcm.aimod;

import java.time.LocalDateTime;

/**
 * Agent 配置持久化实体
 * <p>
 * 对应 ecos_agent 表，存储 Agent 的模型配置、工具绑定、知识库绑定等。
 * tools 和 knowledge 以 JSON 字符串存储（PostgreSQL JSON/Text 列）。
 * </p>
 */
public class AgentEntity {

    private String id;
    private String name;
    private String modelProvider;
    private String modelName;
    private String systemPrompt;
    private String tools;      // JSON array string: ["search_knowledge", "query_object"]
    private String knowledge;  // JSON array string: ["kb-supplier"]
    private String status;     // draft | published
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AgentEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getModelProvider() { return modelProvider; }
    public void setModelProvider(String modelProvider) { this.modelProvider = modelProvider; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public String getTools() { return tools; }
    public void setTools(String tools) { this.tools = tools; }

    public String getKnowledge() { return knowledge; }
    public void setKnowledge(String knowledge) { this.knowledge = knowledge; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
