package com.chinacreator.gzcm.runtime.llm.config;

import com.chinacreator.gzcm.runtime.llm.LLMGatewayService;
import com.chinacreator.gzcm.runtime.llm.LLMGatewayServiceImpl;
import com.chinacreator.gzcm.runtime.llm.callback.CallbackExecutor;
import com.chinacreator.gzcm.runtime.llm.metrics.AgentMetrics;
import com.chinacreator.gzcm.runtime.llm.profile.ProfileManager;
import com.chinacreator.gzcm.runtime.llm.scheduler.AgentScheduler;
import com.chinacreator.gzcm.runtime.llm.session.SessionManager;
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
 * 创建 OkHttpClient 和 LLMGatewayService Bean。
 * </p>
 */
@Configuration
@EnableConfigurationProperties(LLMGatewayProperties.class)
@ConditionalOnProperty(prefix = "llm.engine", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LLMGatewayAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LLMGatewayAutoConfiguration.class);

    private final LLMGatewayProperties properties;

    public LLMGatewayAutoConfiguration(LLMGatewayProperties properties) {
        this.properties = properties;
        log.info("Hermes Engine auto-configuration initialized: enabled={}, defaultProvider={}, defaultModel={}",
                properties.getEngine().isRunning(),
                properties.getEngine().getDefaultProvider(),
                properties.getEngine().getDefaultModel());
    }

    /**
     * 创建 OkHttpClient Bean — 用于 LLM API HTTP 调用
     * <p>
     * 使用 LLMGatewayProperties.Gateway 中的超时配置。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean
    public OkHttpClient hermesOkHttpClient() {
        LLMGatewayProperties.Gateway gateway = properties.getGateway();

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
     * 创建 LLMGatewayService Bean
     * <p>
     * 注入所有依赖: AgentScheduler, SessionManager, ProfileManager,
     * AgentMetrics, CallbackExecutor
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(LLMGatewayService.class)
    public LLMGatewayService hermesEngine(
            AgentScheduler agentScheduler,
            SessionManager sessionManager,
            ProfileManager profileManager,
            AgentMetrics agentMetrics,
            CallbackExecutor callbackExecutor) {

        LLMGatewayServiceImpl engine = new LLMGatewayServiceImpl();

        // 通过 setter 注入（也可使用 @Autowired，但 @Bean 方法更明确）
        // 注意: LLMGatewayServiceImpl 使用了 @Autowired，所以 Spring 会自动装配
        // 这里返回实例即可，Spring 会处理 @Autowired 字段
        log.info("LLMGatewayService bean created and ready");
        return engine;
    }
}
