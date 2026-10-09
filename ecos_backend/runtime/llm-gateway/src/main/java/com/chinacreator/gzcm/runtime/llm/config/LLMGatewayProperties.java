package com.chinacreator.gzcm.runtime.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hermes 引擎配置属性
 * <p>
 * 前缀: hermes
 * </p>
 */
@ConfigurationProperties(prefix = "llm")
public class LLMGatewayProperties {

    /** 引擎相关配置 */
    private final Engine engine = new Engine();

    /** 网关相关配置 */
    private final Gateway gateway = new Gateway();

    /** Profile 缓存相关配置 */
    private final Profile profile = new Profile();

    /** 模型目录（详细设计-06 F06-09 要点 1：上下文上限唯一来源，禁引擎内硬编码） */
    private final Models models = new Models();

    public Engine getEngine() { return engine; }
    public Gateway getGateway() { return gateway; }
    public Profile getProfile() { return profile; }
    public Models getModels() { return models; }

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
        /** 嵌入模型（POST /embeddings 用）；为空时由调用方传入 */
        private String embeddingModel = "text-embedding-3-small";

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
        public String getEmbeddingModel() { return embeddingModel; }
        public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    }

    /**
     * 网关配置
     */
    public static class Gateway {
        /** 重试最大次数 */
        private int retryMaxAttempts = 3;
        /** 请求超时 */
        private Duration requestTimeout = Duration.ofSeconds(60);
        /**
         * 全局默认 provider 密钥<b>引用</b>（PMO-74 N14 / 铁律 §2.4）：
         * 配置里只放引用（如 {@code secret:<secretId>} 或 {@code enc:<keyId>:<cipher>}），
         * 真实密钥经 security-engine 密钥服务在服务端解析；<b>禁止</b>在此填明文密钥。
         * 环境注入：{@code LLM_GATEWAY_DEFAULT_API_KEY_REF}。
         */
        private String defaultApiKeyRef;

        public int getRetryMaxAttempts() { return retryMaxAttempts; }
        public void setRetryMaxAttempts(int retryMaxAttempts) { this.retryMaxAttempts = retryMaxAttempts; }
        public Duration getRequestTimeout() { return requestTimeout; }
        public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
        public String getDefaultApiKeyRef() { return defaultApiKeyRef; }
        public void setDefaultApiKeyRef(String defaultApiKeyRef) { this.defaultApiKeyRef = defaultApiKeyRef; }
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

    /**
     * 模型目录配置（详细设计-06 F06-09 要点 1）。
     *
     * <p>压缩触发式 {@code estimatedContextTokens > modelLimit × threshold} 里的 {@code modelLimit}
     * <b>只能</b>从本目录读：引擎侧不得出现任何模型上限常量（联动 X-71 配置单源）。
     * 目录属部署期形态（provider 侧事实），故留在 Spring yml 面而非 sys_config 字典，
     * 边界口径见迁移脚本 V264 头注。</p>
     *
     * <p><b>未登记模型</b>：{@link #defaultContextLimit} 默认 0 = 目录不可用，调用方须按
     * "不判定"处理并留痕，禁据此推断"无需压缩"。</p>
     */
    public static class Models {
        /** 目录外模型的上下文上限；{@code <=0} = 未登记模型一律不判定（禁引擎侧兜底常量） */
        private int defaultContextLimit = 0;

        /**
         * 模型名 → 目录条目；key 为 provider 侧模型名（如 {@code deepseek-chat}）。
         *
         * <p>这里的代码默认值是<b>目录属主（llm-gateway）</b>的出厂目录，不是引擎侧兜底常量：
         * 任何消费方都只能经 {@code LLMGateway#modelContextLimit} 取数，禁自行抄写数值
         * （F06-09 要点 1 / X-71 配置单源）。运维可用 {@code llm.models.catalog.*} 覆盖。</p>
         */
        private Map<String, Model> catalog = defaultCatalog();

        /**
         * 出厂目录：DeepSeek V3 系公开上下文窗口 64K token（输入+输出合计口径）。
         * 目录外模型不猜值 —— {@link #defaultContextLimit} 保持 0，读数即不可用。
         */
        private static Map<String, Model> defaultCatalog() {
            Map<String, Model> map = new LinkedHashMap<>();
            map.put("deepseek-chat", model(64_000));
            map.put("deepseek-reasoner", model(64_000));
            return map;
        }

        private static Model model(int contextLimit) {
            Model m = new Model();
            m.setContextLimit(contextLimit);
            return m;
        }

        public int getDefaultContextLimit() { return defaultContextLimit; }
        public void setDefaultContextLimit(int defaultContextLimit) {
            this.defaultContextLimit = defaultContextLimit;
        }
        public Map<String, Model> getCatalog() { return catalog; }
        public void setCatalog(Map<String, Model> catalog) { this.catalog = catalog; }
    }

    /** 单个模型的目录条目 */
    public static class Model {
        /** 上下文窗口上限（token，输入+输出合计口径） */
        private int contextLimit = 0;

        public int getContextLimit() { return contextLimit; }
        public void setContextLimit(int contextLimit) { this.contextLimit = contextLimit; }
    }
}
