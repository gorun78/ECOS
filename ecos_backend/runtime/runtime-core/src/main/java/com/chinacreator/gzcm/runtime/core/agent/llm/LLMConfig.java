package com.chinacreator.gzcm.runtime.core.agent.llm;

/**
 * LLM 配置实体
 * 存储 LLM 服务的连接和运行参数
 *
 * @author CDRC Design Team
 */
public class LLMConfig {

    public enum Provider {
        OPENAI,
        AZURE_OPENAI,
        DEEPSEEK,
        LOCAL_VLLM,
        LOCAL_OLLAMA,
        CUSTOM
    }

    private Provider provider = Provider.OPENAI;
    private String apiKey;
    private String baseUrl;             // 如 https://api.openai.com
    private String model;               // 如 gpt-4, qwen2.5
    private Double temperature = 0.7;
    private Integer maxTokens = 4096;
    private Integer timeoutSeconds = 120;
    private Integer maxRetries = 3;
    private String organizationId;      // OpenAI organization（可选）
    private String deploymentName;      // Azure deployment name（可选）
    private String apiVersion;          // Azure API version（可选）

    public LLMConfig() {}

    public LLMConfig(Provider provider, String apiKey, String baseUrl, String model) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    // ── Getters & Setters ──────────────────────────

    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getDeploymentName() { return deploymentName; }
    public void setDeploymentName(String deploymentName) { this.deploymentName = deploymentName; }

    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
}
