package com.chinacreator.gzcm.engine.ai.config;

import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;

/**
 * AI 域共享 REST 客户端工厂（F06-17 要点 2 / W148 收口）。
 *
 * <p>智能域业务类（controller/service/security）统一注入本类的共享 {@link #aiRestTemplate()}，
 * 禁止业务代码自建 {@code new RestTemplate()}（收口口径与认知域 {@code CognitiveRestClientConfig}
 * 同纪律）：base-url 经 runtime-access {@code ServiceEndpointResolver} 双模寻址，客户端经本工厂
 * 统一下发超时并<b>自动透传 {@code X-Request-Id}</b>（与 F06-14 / F06-06 咬合：出站头值取自
 * {@link EcosTraceContext#outboundHeader()}，唯一拼写入口，禁调用点各自手写头名或 ID）。</p>
 *
 * <p>超时口径 connect 3s / read 10s，与认知域共享客户端一致；移除 XML 响应转换器防解析抢占。
 * Bean 名 {@code aiRestTemplate}：与各模块既有同类型 Bean 共存时经 {@code @Qualifier} 精确装配，
 * 不引入 @Primary（避免改变其他模块既有装配语义）。</p>
 *
 * <p><b>存量登记口径</b>：{@code AiSecurityEngineClient} / {@code ToolExecutorService} 因测试装配面
 * 需自建（带各自可调超时的工厂），暂留 {@code rest-template-baseline.txt} 登记，逐条只减不增；
 * 本工厂覆盖无自建工厂需求的标准出向（诊断/工具代理）。</p>
 */
@Configuration
public class AiRestClientConfig {

    /** 连接超时（毫秒），与认知域共享客户端一致。 */
    static final int CONNECT_TIMEOUT_MS = 3000;

    /** 读取超时（毫秒），与认知域共享客户端一致。 */
    static final int READ_TIMEOUT_MS = 10000;

    /**
     * AI 域共享 RestTemplate：连接/读取超时统一在此下发，出站自动注入 {@code X-Request-Id}
     * trace 头（F06-14 七类错误分类 + F06-06 全链路 trace 贯通的出站侧）。
     *
     * @return 带超时 + trace 透传的 RestTemplate
     */
    @Bean("aiRestTemplate")
    public RestTemplate aiRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getMessageConverters().removeIf(c ->
                c instanceof org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter);
        // 出站统一注入 X-Request-Id（值 = 当前 MDC traceId，缺失时生成完整 UUID 写回，F06-06）
        restTemplate.setInterceptors(List.of(
                new ClientHttpRequestInterceptor() {
                    @Override
                    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                            ClientHttpRequestExecution execution) throws IOException {
                        EcosTraceContext.TraceHeader header = EcosTraceContext.outboundHeader();
                        request.getHeaders().set(header.name(), header.value());
                        return execution.execute(request, body);
                    }
                }));
        return restTemplate;
    }
}
