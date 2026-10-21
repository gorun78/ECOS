package com.chinacreator.gzcm.runtime.llm.abac;

import com.chinacreator.gzcm.runtime.llm.config.LLMGatewayProperties;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGatewayImpl;
import com.chinacreator.gzcm.runtime.llm.security.SecurityEngineBridge;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 详细设计-06 F06-22 要点 1 —— llm-gateway 侧「@SpringBootTest 薄切片装配」。
 *
 * <p>与 ai-engine-impl 侧 {@code AiTestHarnessConfig} 同型（P-1 前置门禁）：
 * <b>只装配裁决咽喉所需的最小 bean 集</b>（OkHttpClient / ObjectMapper /
 * {@link SecurityEngineBridge} / {@link LLMGatewayImpl}），不 pull 数据源、不 pull
 * Kafka、不 pull session/scheduler/profile 全家 —— 从而本网关的 ABAC 咽喉切片
 * 可在<b>本机离线</b>起（{@code webEnvironment=MOCK}，无真实 provider TCP）。</p>
 *
 * <p>关键点：security REST 桩 {@link FakeSecurityHttpServer} 是一个<b>真 bean</b>，
 * {@link #securityEngineBridge} 通过 bean 依赖在<b>装配期</b>把桩的实时端口
 * 注入base-url —— 于是「桩端口写死」与「@Value 读隔离 yml」两个假阳性路径
 * （X-73/X-86）都被堵死：桩是边界、基址来自边界，测试只翻 verdict 而<b>不</b>
 * 伪造 {@code evaluateVerdict} 返回三态（R-18 反假阳性口径，与
 * {@code SecurityEngineBridgeAbacTest.Harness} 同源）。</p>
 */
@Configuration
@EnableConfigurationProperties(LLMGatewayProperties.class)
public class LlmAbacSliceConfig {

    /** 真实 HTTP 桩端口（可切 ALLOW/DENY/429/不可解析三态）—— 边界 bean。 */
    @Bean(destroyMethod = "close")
    public FakeSecurityHttpServer fakeSecurity() throws java.io.IOException {
        return new FakeSecurityHttpServer();
    }

    @Bean
    public OkHttpClient sliceOkHttpClient() {
        return new OkHttpClient();
    }

    @Bean
    public ObjectMapper sliceObjectMapper() {
        return new ObjectMapper();
    }

    /**
     * 生产同构四参构造（对齐 {@code SecurityEngineBridgeAbacTest.BridgeOnlyConfig}）：
     * base-url 来自桩 bean 的实时端口，ObjectProvider 来自切片上下文。
     */
    @Bean
    public SecurityEngineBridge securityEngineBridge(
            ApplicationContext applicationContext,
            ObjectProvider<OkHttpClient> httpClientProvider,
            FakeSecurityHttpServer fakeSecurity,
            ObjectMapper objectMapper) {
        return new SecurityEngineBridge(
                applicationContext, httpClientProvider, fakeSecurity.baseUrl(), objectMapper);
    }

    /**
     * {@link LLMGatewayImpl} 作为切片咽喉：@Autowired 字段（objectMapper / okHttpClient /
     * securityEngineBridge）由本 config 的同型 bean 装配；其余 required=false 依赖缺席
     * 即生产「底座缺失」形态（计缺席 → 失败而非假放行）。
     */
    @Bean
    public LLMGatewayImpl llmGatewayImpl() {
        return new LLMGatewayImpl();
    }
}
