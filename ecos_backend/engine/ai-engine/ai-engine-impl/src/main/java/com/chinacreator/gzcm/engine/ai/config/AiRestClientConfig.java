package com.chinacreator.gzcm.engine.ai.config;

import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import com.chinacreator.gzcm.engine.ai.wagent.readiness.EngineProbeClient;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriTemplateHandler;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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

    private static final Logger log = LoggerFactory.getLogger(AiRestClientConfig.class);

    /** 连接超时（毫秒），与认知域共享客户端一致。 */
    static final int CONNECT_TIMEOUT_MS = 3000;

    /** 读取超时（毫秒），与认知域共享客户端一致。 */
    static final int READ_TIMEOUT_MS = 10000;

    /**
     * route-manifest 里四层健康面的承流归属服务名（引擎前缀族由 gateway 路由）。
     *
     * <p>#143/B5-1 后本常量同时是 <b>W 契约工具通道</b>的基址归属单源
     * （{@code ToolExecutorService#resolveContractBaseUrl()} 引本常量，禁在调用点另写
     * {@code "gateway"} 字面量）：{@code /api/v1/engine/{layer}/health} 与契约声明的
     * {@code /api/v1/{族}/...} 都是 gateway 路由前缀。</p>
     */
    public static final String GATEWAY_OWNER_SERVICE = "gateway";

    /** gateway 基址覆盖项的<b>键名</b>单源（运维逃生口，与 {@link #GATEWAY_OWNER_SERVICE} 同族）。 */
    public static final String GATEWAY_BASE_URL_PROPERTY = "ecos.endpoints.gateway";

    /**
     * gateway 基址的<b>显式覆盖位</b>（默认空 → 走 route-manifest 解析）。
     * 仅作运维逃生口；禁止在此写本机个人环境值（文档规范 R7）。
     */
    @Value("${" + GATEWAY_BASE_URL_PROPERTY + ":}")
    private String gatewayOverride;

    /** F02-15/X-35：跨服务基址的唯一寻址出口（runtime-access，route-manifest 单源）。 */
    private final ObjectProvider<ServiceEndpointResolver> endpointProvider;

    public AiRestClientConfig(ObjectProvider<ServiceEndpointResolver> endpointProvider) {
        this.endpointProvider = endpointProvider;
    }

    /**
     * AI 域共享 RestTemplate：连接/读取超时统一在此下发，出站自动注入 {@code X-Request-Id}
     * trace 头（F06-14 七类错误分类 + F06-06 全链路 trace 贯通的出站侧）。
     *
     * @return 带超时 + trace 透传的 RestTemplate
     */
    @Bean("aiRestTemplate")
    public RestTemplate aiRestTemplate() {
        RestTemplate restTemplate = new RestTemplate(requestFactory(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS));
        restTemplate.getMessageConverters().removeIf(c ->
                c instanceof org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter);
        // 出站统一注入 X-Request-Id（值 = 当前 MDC traceId，缺失时生成完整 UUID 写回，F06-06）
        restTemplate.setInterceptors(List.of(traceInterceptor()));
        return restTemplate;
    }

    /**
     * F10-08 DIKC 四层就绪探测专用客户端（{@code EngineProbeClient} 唯一注入面）。
     *
     * <p>口径与 {@code workspaceEngineHealthProbeRest} 同形：<b>基址经 gateway 承流</b>
     * （四层的 {@code /api/v1/engine/{layer}/health} 与 {@code /api/v1/engine/knowledge/health}
     * 都是 gateway 路由前缀，禁在本类写死任何 {@code localhost:180xx}），超时按 F10-08
     * 原文单层 3s 下发（{@link EngineProbeClient#LAYER_BUDGET_MS} 单源，禁在此另立数值）。</p>
     *
     * <p><b>基址延迟解析</b>：owner=gateway 的基址在<b>首次探测</b>时才解析（而非 Bean 创建期）。
     * 理由是 aiming 独立 JAR 的 classpath 未必带 route-manifest —— 若在创建期 fail-loud，
     * 会把"探测能力缺席"升级成"整个服务起不来"，这与铁律的可用性口径相反。
     * 解析不到时由 {@link #resolveProbeBase()} 抛 {@link IllegalStateException}，
     * {@code EngineProbeClient} 将其如实折算为该层不可达（UNKNOWN），<b>绝不折成 pass</b>。</p>
     */
    @Bean("wagentEngineProbeRest")
    public RestTemplate wagentEngineProbeRest() {
        int budgetMs = (int) EngineProbeClient.LAYER_BUDGET_MS;
        RestTemplate restTemplate = new RestTemplate(requestFactory(budgetMs, budgetMs));
        restTemplate.setUriTemplateHandler(new LazyProbeBaseHandler());
        restTemplate.setInterceptors(List.of(traceInterceptor()));
        return restTemplate;
    }

    /**
     * F10-08 度量取数面专用出向（#146b：{@code ReadinessMetricFetchClient} 唯一装配位）。
     *
     * <p><b>与 {@link #wagentEngineProbeRest()} 同基址口径</b>（owner=gateway 延迟解析 +
     * fail-loud + 单层 3s 预算）：取数与探测是同一条 gateway 承流面上的两种读法，
     * 基址解析、超时、trace 透传三项纪律必须同形——另立一枚 Bean 就要再写一遍
     * Handler + 覆盖项，正是 D-B4 重复族要消灭的形态。禁在此复用业务
     * {@code aiRestTemplate}（read 10s 超时会把一次评估拖过聚合预算，且该 Bean
     * 不接探测基址 Handler）。</p>
     */
    @Bean
    public com.chinacreator.gzcm.engine.ai.wagent.readiness.ReadinessMetricFetchClient
            readinessMetricFetchClient(RestTemplate wagentEngineProbeRest) {
        return new com.chinacreator.gzcm.engine.ai.wagent.readiness.ReadinessMetricFetchClient(
                wagentEngineProbeRest);
    }

    // ── 内部 ─────────────────────────────────────────

    /** 基址解析链（F02-15/X-35）：显式覆盖项 → route-manifest 两态寻址 → fail-loud。 */
    String resolveProbeBase() {
        if (gatewayOverride != null && !gatewayOverride.isBlank()) {
            return trimTrailingSlash(gatewayOverride.trim());
        }
        ServiceEndpointResolver resolver = endpointProvider.getIfAvailable();
        if (resolver != null) {
            Optional<String> host = resolver.resolve(GATEWAY_OWNER_SERVICE,
                    ServiceEndpointResolver.RouteMode.SERVICE);
            if (host.isPresent()) {
                return trimTrailingSlash(host.get());
            }
        }
        throw new IllegalStateException("F10-08 探测基址不可解析：" + GATEWAY_OWNER_SERVICE
                + " 既无 ecos.endpoints." + GATEWAY_OWNER_SERVICE + " 覆盖项，也未在 route-manifest 登记"
                + "（fail-loud，禁止回落硬编码地址）");
    }

    private static SimpleClientHttpRequestFactory requestFactory(int connectMs, int readMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectMs);
        factory.setReadTimeout(readMs);
        return factory;
    }

    private static ClientHttpRequestInterceptor traceInterceptor() {
        return new ClientHttpRequestInterceptor() {
            @Override
            public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                    ClientHttpRequestExecution execution) throws IOException {
                EcosTraceContext.TraceHeader header = EcosTraceContext.outboundHeader();
                request.getHeaders().set(header.name(), header.value());
                return execution.execute(request, body);
            }
        };
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** 首次展开 URI 时才解析 gateway 基址并缓存（解析失败 ⇒ IllegalStateException，由探测侧如实记不可达）。 */
    private final class LazyProbeBaseHandler implements UriTemplateHandler {

        private final AtomicReference<UriTemplateHandler> delegate = new AtomicReference<>();

        @Override
        public URI expand(String uriTemplate, Map<String, ?> uriVariables) {
            return target().expand(uriTemplate, uriVariables);
        }

        @Override
        public URI expand(String uriTemplate, Object... uriVariables) {
            return target().expand(uriTemplate, uriVariables);
        }

        private UriTemplateHandler target() {
            UriTemplateHandler handler = delegate.get();
            if (handler == null) {
                String base = resolveProbeBase();
                log.info("[F10-08] 探测基址已解析: {}", base);
                handler = new DefaultUriBuilderFactory(base);
                delegate.set(handler);
            }
            return handler;
        }
    }
}
