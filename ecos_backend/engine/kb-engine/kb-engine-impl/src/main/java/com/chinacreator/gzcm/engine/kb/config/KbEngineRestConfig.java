package com.chinacreator.gzcm.engine.kb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * kb-engine REST 客户端配置。
 * <p>为 {@code KnowledgeExtractionService}（ai-engine Agent Loop）等跨引擎调用提供
 * 统一的容器注入 {@link RestTemplate} bean（F04-13 / K-35：禁各处裸 {@code new RestTemplate()}）。
 *
 * <p>read/connect 超时在此统一设置，替代既有用 {@code Executors.newSingleThreadExecutor}
 * 手工包裹 future 超时的做法（F04-14 / K-39）——超时应是传输层属性，不自建线程池。
 */
@Configuration
public class KbEngineRestConfig {

    @Bean
    public RestTemplate restTemplate(
            @Value("${ecos.kb.rest.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${ecos.kb.rest.read-timeout-ms:60000}") long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeoutMs);
        factory.setReadTimeout((int) readTimeoutMs);
        return new RestTemplate(new BufferingClientHttpRequestFactory(factory));
    }
}
