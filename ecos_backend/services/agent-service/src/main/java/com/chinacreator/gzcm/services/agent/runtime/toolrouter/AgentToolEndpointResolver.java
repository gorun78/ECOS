package com.chinacreator.gzcm.services.agent.runtime.toolrouter;

import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * agent 域工具出向调用的<b>基址单源</b>（#142/B4-3e-8，收口差距 <b>D-B4-43</b>）。
 *
 * <p><b>为什么必须存在</b>：本域 9 支工具（{@code runtime.toolrouter.tools}）此前各自把
 * {@code http://localhost:8080} 整串写死在请求点上，绕开了详细设计-00 C.2.3 / ADR-15
 * 规定的唯一寻址出口 runtime-access {@link ServiceEndpointResolver}（铁律「基础设施
 * 一律收敛 runtime，禁止各自封装」）。写死 monolith 端口的直接后果是切流即断：
 * route-manifest 把某族从 gateway 切到独立 JAR 之后，这些腿仍打到 :8080，
 * 而门禁 {@code EndpointResolverUsageArchTest} 只钉 {@code localhost:1808x} 形态，
 * {@code :8080} 形态全仓无门（故本批同批登记 <b>D-B4-46</b> 的棘轮清单）。</p>
 *
 * <p><b>解析链（与 workspace / ai 域同纪律）</b>：显式覆盖项
 * {@code ecos.endpoints.sysman}（运维逃生口，默认留空，禁写本机个人环境值）→
 * route-manifest 两态寻址（{@code RouteMode.SERVICE}：owner 未切流时 resolver 内部
 * 自动回落 monolith 端口，故现网形态下与收口前的 :8080 逐字相同，属外形不变）→
 * 皆不可用时返回 {@link Optional#empty}。</p>
 *
 * <p><b>为什么返回空而不是抛异常</b>：本类的消费面是<b>安全裁决腿</b>。抛异常会被工具咽喉
 * 折成通用工具错误，调用方无从区分「护栏不可用」与「护栏判了拒绝」；返回空则把不可用
 * 如实交回读侧，按 §4.7.2「不可用 ≠ 无权限」落三态里的 {@code UNEVALUATED}
 * （与 llm-gateway {@code SecurityEngineBridge} 的 fail-closed 口径同族，
 * 与 workspace 配置期 fail-loud 的口径分工不同 —— 那是启动期建 Bean，不是裁决期读数）。
 * <b>任何情况下都不回落硬编码地址。</b></p>
 *
 * <p><b>落点说明</b>：本类放在 {@code runtime.toolrouter}（与消费它的工具同域）而不是
 * {@code config} 包 —— gateway 的 {@code @ComponentScan} 是<b>白名单式</b> basePackages，
 * agent 域只登记了 {@code services.agent.runtime / .model / .dao} 三支；新建 {@code .config}
 * 包不改白名单的话，Bean 根本不会注册，工具侧在启动期即 {@code NoSuchBeanDefinitionException}。</p>
 */
@Component
public class AgentToolEndpointResolver {

    private static final Logger log = LoggerFactory.getLogger(AgentToolEndpointResolver.class);

    /**
     * security 裁决族（{@code /api/v1/security/**}）在 route-manifest 的<b>属主名</b>单源。
     *
     * <p>与 llm-gateway {@code SecurityEngineBridge.SECURITY_ROUTE_OWNER} 同值同含义；
     * manifest 里 {@code /api/v1/security} 的 {@code owner} 就是 {@code sysman}，
     * 不存在独立的 {@code security} owner，禁在此另立一套键名。</p>
     */
    public static final String SECURITY_ROUTE_OWNER = "sysman";

    /** security 基址<b>覆盖项键名</b>单源（前缀与 {@code ecos.endpoints.<owner>} 同族）。 */
    public static final String SECURITY_BASE_URL_PROPERTY = "ecos.endpoints." + SECURITY_ROUTE_OWNER;

    private final ObjectProvider<ServiceEndpointResolver> endpointProvider;

    private final String securityBaseUrlOverride;

    public AgentToolEndpointResolver(ObjectProvider<ServiceEndpointResolver> endpointProvider,
            @Value("${" + SECURITY_BASE_URL_PROPERTY + ":}") String securityBaseUrlOverride) {
        this.endpointProvider = endpointProvider;
        this.securityBaseUrlOverride = securityBaseUrlOverride == null ? "" : securityBaseUrlOverride.trim();
    }

    /**
     * security-engine 裁决腿的 base-url（不含尾斜杠）。
     *
     * @return 解析到的基址；覆盖项缺失、resolver Bean 缺席或 owner 未登记时为空 ——
     *         调用方必须按「护栏不可用」处置，禁回落硬编码地址
     */
    public Optional<String> securityBaseUrl() {
        if (!securityBaseUrlOverride.isEmpty()) {
            return Optional.of(trimTrailingSlash(securityBaseUrlOverride));
        }
        ServiceEndpointResolver resolver = endpointProvider.getIfAvailable();
        if (resolver == null) {
            log.warn("[agent-tool] ServiceEndpointResolver 缺席，{} 基址不可解析（按护栏不可用处置）：{}",
                    SECURITY_ROUTE_OWNER, SECURITY_BASE_URL_PROPERTY);
            return Optional.empty();
        }
        Optional<String> resolved = resolver.resolve(SECURITY_ROUTE_OWNER,
                ServiceEndpointResolver.RouteMode.SERVICE);
        if (resolved.isEmpty()) {
            log.warn("[agent-tool] route-manifest 未登记 owner={}，基址不可解析（按护栏不可用处置）",
                    SECURITY_ROUTE_OWNER);
            return Optional.empty();
        }
        return resolved.map(AgentToolEndpointResolver::trimTrailingSlash);
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
