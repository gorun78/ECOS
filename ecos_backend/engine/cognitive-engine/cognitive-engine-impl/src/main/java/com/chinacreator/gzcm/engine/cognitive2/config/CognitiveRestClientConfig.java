package com.chinacreator.gzcm.engine.cognitive2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 认知引擎共享 REST 客户端配置（F05-15 / X-35 收口）。
 *
 * <p>认知域业务 Service（{@code EngineCapabilityRegistryImpl} / {@code EntityLinker}）与
 * KB REST 代理 Bean 工厂统一注入本类的共享 {@link RestTemplate}，禁止业务代码自建
 * {@code new RestTemplate()}。超时口径与历史 {@code KbRestClientConfig.buildRestTemplate}
 * 一致：connect 3s / read 10s；并移除 XML 响应转换器（防 kb 端 text/xml 抢占解析）。</p>
 *
 * <p>Bean 名 {@code cognitiveRestTemplate}：与 gateway {@code RestTemplateConfig#restTemplate}、
 * kb {@code KbEngineRestConfig} 等既有同类型 Bean 共存时经 {@code @Qualifier} 精确装配，
 * 不引入 @Primary（避免改变其他模块既有装配语义）。</p>
 */
@Configuration
public class CognitiveRestClientConfig {

    /** 连接超时（毫秒），与收口前业务自建客户端一致。 */
    static final int CONNECT_TIMEOUT_MS = 3000;

    /** 读取超时（毫秒），与收口前业务自建客户端一致。 */
    static final int READ_TIMEOUT_MS = 10000;

    /**
     * 认知域共享 RestTemplate（连接/读取超时统一在此下发；调用方禁止再自行 new）。
     *
     * @return 带超时配置的 RestTemplate
     */
    @Bean("cognitiveRestTemplate")
    public RestTemplate cognitiveRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        RestTemplate restTemplate = new RestTemplate(factory);
        // 防 XML 响应解析（与收口前 EngineCapabilityRegistryImpl 行为兼容）
        restTemplate.getMessageConverters().removeIf(c ->
                c instanceof org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter);
        return restTemplate;
    }
}
