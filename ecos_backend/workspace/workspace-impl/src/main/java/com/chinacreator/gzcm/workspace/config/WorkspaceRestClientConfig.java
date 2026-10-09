package com.chinacreator.gzcm.workspace.config;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import com.chinacreator.gzcm.workspace.trace.TraceIdClientInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.util.List;
import java.util.Optional;

/**
 * 卷07 F07-17（W181 / C163）底座收口 —— workspace 跨服务 REST 客户端唯一工厂。
 *
 * <p>workspace 业务类（controller / security / knowledge / scenario）此前各自
 * {@code new RestTemplate(new SimpleClientHttpRequestFactory())} 并自带
 * {@code http://localhost:1808x} 硬编码默认值 —— 本类将其收敛为集中注册的
 * RestTemplate Bean：超时口径统一在此下发，base-url 经 runtime-access
 * {@link ServiceEndpointResolver} 两态寻址（ADR-15 制品≠承流，单真源
 * {@code /route-manifest.json}），业务侧只按 {@code @Qualifier} 注入，
 * 禁止再自建客户端（gateway {@code ModuleDependencyArchTest#businessMustNotConstructRestTemplate}
 * 与 workspace {@code RuntimeConvergenceArchTest} 双门禁）。</p>
 *
 * <p>解析链（照抄 cognitive {@code KbRestClientConfig} 形态）：
 * <ol>
 *   <li>{@code ecos.endpoints.<service>} 显式覆盖项 —— 仅作运维逃生口，默认留空；</li>
 *   <li>{@code ServiceEndpointResolver.resolve(service, RouteMode.SERVICE)} ——
 *       SERVICE 形态尊重 manifest 的 route 字段：datanet 已切流 → :18082 直连，
 *       其余 owner（sysman/buszhi/dccheng/aiming/gateway）未切流 → 自动回落
 *       monolith :8080（gateway 承流），与单体 fat-JAR 部署等价；</li>
 *   <li>双双不可得 → fail-loud 抛异常，禁止静默回落硬编码 localhost 地址。</li>
 * </ol></p>
 *
 * <p>本机端口/个人环境值禁止入库：默认值一律走上述解析链，不在代码或 yml 写死
 * {@code localhost:1808x}。</p>
 */
@Configuration
public class WorkspaceRestClientConfig {

    /** 两态寻址形态：SERVICE 尊重 manifest route 切流状态（未切流自动回落 monolith 端口）。 */
    private static final ServiceEndpointResolver.RouteMode ROUTE_MODE =
            ServiceEndpointResolver.RouteMode.SERVICE;

    // ── 运维逃生口覆盖项（默认留空 → 走 route-manifest 解析；禁止入库个人环境值） ──

    @Value("${ecos.endpoints.datanet:}")
    private String datanetOverride;

    @Value("${ecos.endpoints.buszhi:}")
    private String buszhiOverride;

    @Value("${ecos.endpoints.dccheng:}")
    private String dcchengOverride;

    @Value("${ecos.endpoints.aiming:}")
    private String aimingOverride;

    @Value("${ecos.endpoints.sysman:}")
    private String sysmanOverride;

    @Value("${ecos.endpoints.gateway:}")
    private String gatewayOverride;

    // ── 超时口径集中配置（与收口前各业务自建值逐一对齐，行为不变） ──

    /** 场景选项池联查超时（原 ScenarioOptionsController 自建 3000ms）。 */
    @Value("${ecos.endpoints.timeout.options-ms:3000}")
    private int optionsTimeoutMs;

    /** 聚合器/健康探活短超时（原 Knowledge*Aggregator 自建 1500ms）。 */
    @Value("${ecos.endpoints.timeout.probe-ms:1500}")
    private int probeTimeoutMs;

    /** 认知推理红线超时（原 DcchengClient 30s —— "推理不能 >30s" 硬约束）。 */
    @Value("${ecos.endpoints.timeout.cognitive-ms:30000}")
    private int cognitiveTimeoutMs;

    /** security-engine REST 裁决/审计超时（原 service.security.timeout-ms 默认 5000ms）。 */
    @Value("${ecos.endpoints.timeout.security-ms:5000}")
    private int securityTimeoutMs;

    private final ObjectProvider<ServiceEndpointResolver> endpointProvider;

    public WorkspaceRestClientConfig(ObjectProvider<ServiceEndpointResolver> endpointProvider) {
        this.endpointProvider = endpointProvider;
    }

    // ═══════════════ 选项池联查（ScenarioOptionsController，5 类资源跨服务 GET） ═══════════════

    /** datanet 数据源联查客户端（manifest 已切流 → :18082 直连）。 */
    @Bean("workspaceDatanetRest")
    public RestTemplate workspaceDatanetRest() {
        return restTemplate(resolveBase("datanet", datanetOverride) + "/api/v1", optionsTimeoutMs);
    }

    /** buszhi 本体实体联查客户端。 */
    @Bean("workspaceBuszhiRest")
    public RestTemplate workspaceBuszhiRest() {
        return restTemplate(resolveBase("buszhi", buszhiOverride) + "/api/v1", optionsTimeoutMs);
    }

    /** dccheng 知识资产联查客户端（/api/v1/knowledge/** 归属 owner=dccheng）。 */
    @Bean("workspaceDcchengRest")
    public RestTemplate workspaceDcchengRest() {
        return restTemplate(resolveBase("dccheng", dcchengOverride) + "/api/v1", optionsTimeoutMs);
    }

    /** aiming Agent 配置联查客户端。 */
    @Bean("workspaceAimingRest")
    public RestTemplate workspaceAimingRest() {
        return restTemplate(resolveBase("aiming", aimingOverride) + "/api/v1", optionsTimeoutMs);
    }

    /** sysman 安全策略/接口引用联查客户端。 */
    @Bean("workspaceSysmanRest")
    public RestTemplate workspaceSysmanRest() {
        return restTemplate(resolveBase("sysman", sysmanOverride) + "/api/v1", optionsTimeoutMs);
    }

    // ═══════════════ 知识工作台聚合器（短超时探活/读写） ═══════════════

    /** kb settings 读写客户端（KnowledgeConfigAggregator knowledge scope → owner=dccheng）。 */
    @Bean("workspaceKbSettingsRest")
    public RestTemplate workspaceKbSettingsRest() {
        return restTemplate(resolveBase("dccheng", dcchengOverride) + "/api/v1", probeTimeoutMs);
    }

    /** cognitive config 读写客户端（KnowledgeConfigAggregator cognitive scope → owner=aiming）。 */
    @Bean("workspaceCognitiveCfgRest")
    public RestTemplate workspaceCognitiveCfgRest() {
        return restTemplate(resolveBase("aiming", aimingOverride) + "/api/v1", probeTimeoutMs);
    }

    /** 6 引擎 health 探活客户端（KnowledgeHealthAggregator，全部经 gateway 承流路由）。 */
    @Bean("workspaceEngineHealthProbeRest")
    public RestTemplate workspaceEngineHealthProbeRest() {
        return restTemplate(resolveBase("gateway", gatewayOverride), probeTimeoutMs);
    }

    // ═══════════════ 长耗时/安全通道 ═══════════════

    /**
     * 认知推理调用客户端（DcchengClient —— diagnose/forecast/simulate/plan/counterfactual）。
     * 30s 硬超时对齐认知引擎"推理不能 >30s"红线，超时口径仅在本类集中下发。
     */
    @Bean("workspaceCognitiveInvokeRest")
    public RestTemplate workspaceCognitiveInvokeRest() {
        return restTemplate(resolveBase("aiming", aimingOverride) + "/api/v1", cognitiveTimeoutMs);
    }

    /**
     * security-engine 统一访问客户端（WorkspaceSecurityEngineClient —— RLS/CLS/ABAC/审计）。
     * 业务侧路径自带 /api/v1 前缀，故本 Bean 不预置 root path。
     */
    @Bean("workspaceSecurityEngineRest")
    public RestTemplate workspaceSecurityEngineRest() {
        return restTemplate(resolveBase("sysman", sysmanOverride), securityTimeoutMs);
    }

    // ═══════════════ 私有辅助 ═══════════════

    /**
     * base-url 解析链：显式覆盖项 → ServiceEndpointResolver 两态寻址 → fail-loud。
     * （F07-17/X-35 同源铁律：禁止静默回落硬编码 localhost 地址。）
     */
    String resolveBase(String service, String override) {
        if (override != null && !override.isBlank()) {
            return trimTrailingSlash(override.trim());
        }
        ServiceEndpointResolver resolver = endpointProvider.getIfAvailable();
        if (resolver != null) {
            Optional<String> host = resolver.resolve(service, ROUTE_MODE);
            if (host.isPresent()) {
                return trimTrailingSlash(host.get());
            }
        }
        throw new BusinessException(500, "F07-17 底座收口：无法解析 service " + service
                + " 的基址 —— ecos.endpoints." + service + " 未配置且 route-manifest 未登记该 owner"
                + "（fail-loud，禁止回落硬编码地址）");
    }

    /** 带统一超时、根地址解析器与 traceId 出站透传拦截器的 RestTemplate（业务侧只传相对路径）。 */
    private static RestTemplate restTemplate(String base, int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1000, timeoutMs));
        factory.setReadTimeout(Math.max(1000, timeoutMs));
        RestTemplate template = new RestTemplate(factory);
        template.setUriTemplateHandler(new DefaultUriBuilderFactory(base));
        template.setInterceptors(List.of(new TraceIdClientInterceptor()));
        return template;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
