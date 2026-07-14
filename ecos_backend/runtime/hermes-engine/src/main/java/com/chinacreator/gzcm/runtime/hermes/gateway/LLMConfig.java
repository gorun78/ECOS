package com.chinacreator.gzcm.runtime.hermes.gateway;

/**
 * LLM 连接配置 — provider、model、baseUrl、apiKey、temperature、maxTokens
 */
public class LLMConfig {

    private String provider;
    private String model;
    private String baseUrl;
    private String apiKey;
    private Double temperature;
    private Integer maxTokens;

    public LLMConfig() {}

    public LLMConfig(String provider, String model, String baseUrl, String apiKey,
                     Double temperature, Integer maxTokens) {
        this.provider = provider;
        this.model = model;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    /** 从 ProfileConfig 快捷构建 */
    public static LLMConfig fromProfile(com.chinacreator.gzcm.runtime.hermes.model.ProfileConfig profile) {
        LLMConfig config = new LLMConfig();
        config.setProvider(profile.getProvider());
        config.setModel(profile.getModel());
        config.setBaseUrl(profile.getBaseUrl());
        config.setApiKey(profile.getApiKeyRef());
        config.setTemperature(profile.getTemperature());
        config.setMaxTokens(profile.getMaxTokens());
        return config;
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
}
