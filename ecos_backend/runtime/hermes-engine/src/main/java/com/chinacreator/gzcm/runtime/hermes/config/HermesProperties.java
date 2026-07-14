package com.chinacreator.gzcm.runtime.hermes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Hermes 引擎配置属性
 * <p>
 * 前缀: hermes
 * </p>
 */
@ConfigurationProperties(prefix = "hermes")
public class HermesProperties {

    /** 引擎相关配置 */
    private final Engine engine = new Engine();

    /** 网关相关配置 */
    private final Gateway gateway = new Gateway();

    /** Profile 缓存相关配置 */
    private final Profile profile = new Profile();

    public Engine getEngine() { return engine; }
    public Gateway getGateway() { return gateway; }
    public Profile getProfile() { return profile; }

    /**
     * 引擎配置
     */
    public static class Engine {
        /** 引擎总开关 */
        private boolean running = true;
        /** 默认 provider */
        private String defaultProvider = "openrouter";
        /** 默认模型 */
        private String defaultModel = "deepseek/deepseek-chat";
        /** 全局最大并发 */
        private int maxConcurrency = 5;
        /** 默认超时（秒） */
        private int defaultTimeout = 600;

        public boolean isRunning() { return running; }
        public void setRunning(boolean running) { this.running = running; }
        public String getDefaultProvider() { return defaultProvider; }
        public void setDefaultProvider(String defaultProvider) { this.defaultProvider = defaultProvider; }
        public String getDefaultModel() { return defaultModel; }
        public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
        public int getMaxConcurrency() { return maxConcurrency; }
        public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }
        public int getDefaultTimeout() { return defaultTimeout; }
        public void setDefaultTimeout(int defaultTimeout) { this.defaultTimeout = defaultTimeout; }
    }

    /**
     * 网关配置
     */
    public static class Gateway {
        /** 重试最大次数 */
        private int retryMaxAttempts = 3;
        /** 请求超时 */
        private Duration requestTimeout = Duration.ofSeconds(60);

        public int getRetryMaxAttempts() { return retryMaxAttempts; }
        public void setRetryMaxAttempts(int retryMaxAttempts) { this.retryMaxAttempts = retryMaxAttempts; }
        public Duration getRequestTimeout() { return requestTimeout; }
        public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
    }

    /**
     * Profile 缓存配置
     */
    public static class Profile {
        /** 是否启用缓存 */
        private boolean cacheEnabled = true;
        /** 缓存 TTL（分钟） */
        private int cacheTtlMinutes = 30;

        public boolean isCacheEnabled() { return cacheEnabled; }
        public void setCacheEnabled(boolean cacheEnabled) { this.cacheEnabled = cacheEnabled; }
        public int getCacheTtlMinutes() { return cacheTtlMinutes; }
        public void setCacheTtlMinutes(int cacheTtlMinutes) { this.cacheTtlMinutes = cacheTtlMinutes; }
    }
}
