package com.chinacreator.gzcm.runtime.hermes.model;

import java.time.LocalDateTime;

/**
 * Agent Profile 配置实体 — 映射 sys_agent_profile 表
 */
public class ProfileConfig {

    private String id;
    private String profileName;
    private String subsystem;
    private Boolean enabled;
    private String description;

    // LLM 配置
    private String provider;
    private String model;
    private String baseUrl;
    private String apiKeyRef;
    private Double temperature;
    private Integer maxTokens;

    // Agent 行为
    private String systemPrompt;
    private Integer maxIterations;
    private Integer sessionTimeoutSec;
    private Boolean toolsEnabled;
    private Boolean autoApprove;

    // 工具权限 (JSON array string)
    private String allowedTools;

    // 控制
    private Integer concurrency;
    private Integer priority;

    // 审计
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;

    public ProfileConfig() {}

    // ── Builder pattern ──
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final ProfileConfig config = new ProfileConfig();
        public Builder id(String v) { config.id = v; return this; }
        public Builder profileName(String v) { config.profileName = v; return this; }
        public Builder subsystem(String v) { config.subsystem = v; return this; }
        public Builder enabled(Boolean v) { config.enabled = v; return this; }
        public Builder description(String v) { config.description = v; return this; }
        public Builder provider(String v) { config.provider = v; return this; }
        public Builder model(String v) { config.model = v; return this; }
        public Builder baseUrl(String v) { config.baseUrl = v; return this; }
        public Builder apiKeyRef(String v) { config.apiKeyRef = v; return this; }
        public Builder temperature(Double v) { config.temperature = v; return this; }
        public Builder maxTokens(Integer v) { config.maxTokens = v; return this; }
        public Builder systemPrompt(String v) { config.systemPrompt = v; return this; }
        public Builder maxIterations(Integer v) { config.maxIterations = v; return this; }
        public Builder sessionTimeoutSec(Integer v) { config.sessionTimeoutSec = v; return this; }
        public Builder toolsEnabled(Boolean v) { config.toolsEnabled = v; return this; }
        public Builder autoApprove(Boolean v) { config.autoApprove = v; return this; }
        public Builder allowedTools(String v) { config.allowedTools = v; return this; }
        public Builder concurrency(Integer v) { config.concurrency = v; return this; }
        public Builder priority(Integer v) { config.priority = v; return this; }
        public ProfileConfig build() { return config; }
    }

    // ── Getters / Setters ──
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }
    public String getSubsystem() { return subsystem; }
    public void setSubsystem(String subsystem) { this.subsystem = subsystem; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKeyRef() { return apiKeyRef; }
    public void setApiKeyRef(String apiKeyRef) { this.apiKeyRef = apiKeyRef; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public Integer getMaxIterations() { return maxIterations; }
    public void setMaxIterations(Integer maxIterations) { this.maxIterations = maxIterations; }
    public Integer getSessionTimeoutSec() { return sessionTimeoutSec; }
    public void setSessionTimeoutSec(Integer sessionTimeoutSec) { this.sessionTimeoutSec = sessionTimeoutSec; }
    public Boolean getToolsEnabled() { return toolsEnabled; }
    public void setToolsEnabled(Boolean toolsEnabled) { this.toolsEnabled = toolsEnabled; }
    public Boolean getAutoApprove() { return autoApprove; }
    public void setAutoApprove(Boolean autoApprove) { this.autoApprove = autoApprove; }
    public String getAllowedTools() { return allowedTools; }
    public void setAllowedTools(String allowedTools) { this.allowedTools = allowedTools; }
    public Integer getConcurrency() { return concurrency; }
    public void setConcurrency(Integer concurrency) { this.concurrency = concurrency; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
}
