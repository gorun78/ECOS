package com.chinacreator.gzcm.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 网关自有转发用 RestTemplate（定向 Bean，非默认模板）。
 *
 * <p><b>为什么必须基于 {@link JdkClientHttpRequestFactory}</b>：Spring 默认的
 * SimpleClientHttpRequestFactory 底层是 HttpURLConnection，不支持 PATCH 方法，
 * 转发场景状态迁移 {@code PATCH /api/v1/workspace/scenarios/{id}/status} 时会抛
 * {@code ProtocolException: Invalid HTTP method: PATCH}，被 RestClientException 分支
 * 折叠成 503 ECOS-WS-503-UPSTREAM（2026-11-13 活体缺陷 D1）。</p>
 *
 * <p><b>为什么不叫 restTemplate</b>：gateway 聚合宿主下 kb-engine 的
 * {@code KbEngineRestConfig.restTemplate()} 已标 {@code @Primary} 作为全仓默认模板
 * （见该类注释：13+ 候选 Bean，裸注入需唯一默认）。本类若再声明同名 Bean 会与之
 * 冲突/被覆盖（2026-11-13 实测：本类原同名 Bean 未生效，转发实际走 kb 默认模板）。
 * 故改名为定向 Bean，仅按 {@code @Qualifier} 精确消费；默认类型注入走 kb 的 @Primary 模板。</p>
 */
@Configuration
public class RestTemplateConfig {

    @Bean("gatewayForwardRestTemplate")
    public RestTemplate gatewayForwardRestTemplate() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        return new RestTemplate(factory);
    }
}
