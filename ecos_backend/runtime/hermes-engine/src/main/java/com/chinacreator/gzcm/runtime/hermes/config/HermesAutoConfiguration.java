package com.chinacreator.gzcm.runtime.hermes.config;

import com.chinacreator.gzcm.runtime.hermes.HermesEngine;
import com.chinacreator.gzcm.runtime.hermes.HermesEngineImpl;
import com.chinacreator.gzcm.runtime.hermes.callback.CallbackExecutor;
import com.chinacreator.gzcm.runtime.hermes.metrics.AgentMetrics;
import com.chinacreator.gzcm.runtime.hermes.profile.ProfileManager;
import com.chinacreator.gzcm.runtime.hermes.scheduler.AgentScheduler;
import com.chinacreator.gzcm.runtime.hermes.session.SessionManager;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Hermes 引擎自动装配配置
 * <p>
 * 在 hermes.engine.enabled=true（默认）时自动启用。
 * 创建 OkHttpClient 和 HermesEngine Bean。
 * </p>
 */
@Configuration
@EnableConfigurationProperties(HermesProperties.class)
@ConditionalOnProperty(prefix = "hermes.engine", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HermesAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(HermesAutoConfiguration.class);

    private final HermesProperties properties;

    public HermesAutoConfiguration(HermesProperties properties) {
        this.properties = properties;
        log.info("Hermes Engine auto-configuration initialized: enabled={}, defaultProvider={}, defaultModel={}",
                properties.getEngine().isRunning(),
                properties.getEngine().getDefaultProvider(),
                properties.getEngine().getDefaultModel());
    }

    /**
     * 创建 OkHttpClient Bean — 用于 LLM API HTTP 调用
     * <p>
     * 使用 HermesProperties.Gateway 中的超时配置。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean
    public OkHttpClient hermesOkHttpClient() {
        HermesProperties.Gateway gateway = properties.getGateway();

        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        // 配置超时
        if (gateway.getRequestTimeout() != null) {
            long timeoutSeconds = gateway.getRequestTimeout().getSeconds();
            builder.connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .writeTimeout(timeoutSeconds, TimeUnit.SECONDS);
        } else {
            builder.connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS);
        }

        // 连接池
        builder.connectionPool(new okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES));

        // 重定向跟随
        builder.followRedirects(true)
                .followSslRedirects(true);

        log.info("Hermes OkHttpClient created: timeout={}, retryMaxAttempts={}",
                gateway.getRequestTimeout(), gateway.getRetryMaxAttempts());

        return builder.build();
    }

    /**
     * 创建 HermesEngine Bean
     * <p>
     * 注入所有依赖: AgentScheduler, SessionManager, ProfileManager,
     * AgentMetrics, CallbackExecutor
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(HermesEngine.class)
    public HermesEngine hermesEngine(
            AgentScheduler agentScheduler,
            SessionManager sessionManager,
            ProfileManager profileManager,
            AgentMetrics agentMetrics,
            CallbackExecutor callbackExecutor) {

        HermesEngineImpl engine = new HermesEngineImpl();

        // 通过 setter 注入（也可使用 @Autowired，但 @Bean 方法更明确）
        // 注意: HermesEngineImpl 使用了 @Autowired，所以 Spring 会自动装配
        // 这里返回实例即可，Spring 会处理 @Autowired 字段
        log.info("HermesEngine bean created and ready");
        return engine;
    }
}
