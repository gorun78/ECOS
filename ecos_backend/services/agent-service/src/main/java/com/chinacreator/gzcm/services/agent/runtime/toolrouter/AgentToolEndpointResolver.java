package com.chinacreator.gzcm.services.agent.runtime.toolrouter;

import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * agent 域工具出向调用的<b>基址单源</b>（#142/B4-3e-8 收口 <b>D-B4-43</b>，
 * #142/B4-3e-9 扩到全部工具腿并收口 <b>D-B4-46</b>）。
 *
 * <p><b>为什么必须存在</b>：本域工具此前各自把 {@code http://localhost:8080} 整串写死在
 * 请求点上，绕开了详细设计-00 C.2.3 / ADR-15 规定的唯一寻址出口 runtime-access
 * {@link ServiceEndpointResolver}（铁律「基础设施一律收敛 runtime，禁止各自封装」）。
 * 写死 monolith 端口的直接后果是切流即断：route-manifest 把某族从 gateway 切到独立 JAR
 * 之后，这些腿仍打到 :8080，而门禁 {@code EndpointResolverUsageArchTest} 只钉
 * {@code localhost:1808x} 形态，{@code :8080} 形态全仓无门（故 D-B4-46 随批登记棘轮清单，
 * 本批把清单收空）。</p>
 *
 * <p><b>属主名从哪来</b>：{@link ServiceEndpointResolver#resolve} 是按 route-manifest 的
 * <b>owner 字段</b>（非 URL 前缀）查表的，所以本类每个访问器绑定一个属主名，族与属主的对应
 * 关系只在本文说清、不散落到各工具：
 * <ul>
 *   <li>{@code sysman} —— 同时服务 {@code /api/v1/security}（裁决面）、{@code /api/v1/audit}
 *       （审计面）与 {@code /api/v1/policy-engine}（ABAC 裁决族，security-engine-impl
 *       {@code PolicyEngineController} 由 gateway+sysman 双宿主）三族，manifest 里这三条前缀的
 *       {@code owner} 都是 {@code sysman}（ports monolith 8080 / service 18081）。
 *       不存在独立的 {@code security} 或 {@code audit} owner，禁另立键名。</li>
 *   <li>{@code buszhi} —— 服务本体族 {@code /api/v1/ontology}，以及同样由
 *       ontology-engine-impl 提供、被 gateway / buszhi / workspace 三处宿主复用的
 *       {@code /api/v1/ecos/entities}、{@code /api/v1/ecos/ontologies} 与
 *       {@code /api/v1/engine/ontology} 三族（ports monolith 8080 / service 18083）。kb 域
 *       {@code KbEntityInstanceExtractionService} 已是把 {@code buszhi} 当本体族属主的先例。
 *       <b>D-B4-48 已在 #142/B4-3e-11 关账</b>：上述前缀均已按<b>子族</b>登进 route-manifest 单源，
 *       族→属主映射不再只存在于本注释里。注意 {@code /api/v1/ecos} <b>整根属主不唯一</b>
 *       （workspace-impl {@code ObjectStateMachineController} 亦声明 {@code /objects}、
 *       {@code /state-machine} 子族，属主 {@code workspace}），故 manifest 只登记子族，
 *       本类也不得把整根判给单一属主。可达性与属主一致性由
 *       {@code AgentToolPathReachabilityTest} 钉住。</li>
 * </ul>
 * 现网形态下这些属主都还是 {@code route=monolith}，resolver 内部回落 monolith 端口，
 * 故解析结果与收口前的 :8080 逐字相同，属外形不变。</p>
 *
 * <p><b>为什么返回空而不是抛异常</b>：抛异常会被工具咽喉折成通用工具错误，调用方无从区分
 * 「服务不可解析」与「服务答了失败」；返回空则把不可用如实交回读侧（§4.7.2
 * 「不可用 ≠ 无权限」）。<b>任何情况下都不回落硬编码地址。</b></p>
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
     * 安全裁决族（{@code /api/v1/security/**}）与审计族（{@code /api/v1/audit/**}）在
     * route-manifest 的<b>属主名</b>单源（两族同一属主）。
     *
     * <p>与 llm-gateway {@code SecurityEngineBridge.SECURITY_ROUTE_OWNER} 同值同含义；
     * manifest 里 {@code /api/v1/security} 与 {@code /api/v1/audit} 的 {@code owner}
     * 都是 {@code sysman}。</p>
     */
    public static final String SYSMAN_ROUTE_OWNER = "sysman";

    /** sysman 族基址<b>覆盖项键名</b>单源（前缀与 {@code ecos.endpoints.<owner>} 同族）。 */
    public static final String SYSMAN_BASE_URL_PROPERTY = "ecos.endpoints." + SYSMAN_ROUTE_OWNER;

    /**
     * 本体族（{@code /api/v1/ontology}、{@code /api/v1/ecos}、{@code /api/v1/engine/ontology}）
     * 在 route-manifest 的<b>属主名</b>单源。
     */
    public static final String ONTOLOGY_ROUTE_OWNER = "buszhi";

    /** buszhi 族基址<b>覆盖项键名</b>单源。 */
    public static final String ONTOLOGY_BASE_URL_PROPERTY = "ecos.endpoints." + ONTOLOGY_ROUTE_OWNER;

    private final ObjectProvider<ServiceEndpointResolver> endpointProvider;

    private final String sysmanBaseUrlOverride;

    private final String ontologyBaseUrlOverride;

    public AgentToolEndpointResolver(ObjectProvider<ServiceEndpointResolver> endpointProvider,
            @Value("${" + SYSMAN_BASE_URL_PROPERTY + ":}") String sysmanBaseUrlOverride,
            @Value("${" + ONTOLOGY_BASE_URL_PROPERTY + ":}") String ontologyBaseUrlOverride) {
        this.endpointProvider = endpointProvider;
        this.sysmanBaseUrlOverride = trim(sysmanBaseUrlOverride);
        this.ontologyBaseUrlOverride = trim(ontologyBaseUrlOverride);
    }

    /**
     * security 裁决族（{@code /api/v1/security/**}）的 base-url（不含尾斜杠）。
     *
     * @return 解析到的基址；覆盖项缺失、resolver Bean 缺席或 owner 未登记时为空 ——
     *         调用方必须按「护栏不可用」处置，禁回落硬编码地址
     */
    public Optional<String> securityBaseUrl() {
        return resolveOwner(SYSMAN_ROUTE_OWNER, SYSMAN_BASE_URL_PROPERTY, sysmanBaseUrlOverride);
    }

    /**
     * audit 审计族（{@code /api/v1/audit/**}）的 base-url（不含尾斜杠）。
     *
     * <p>与 {@link #securityBaseUrl()} 同一属主（{@code sysman}）是两个族而不是两套配置：
     * manifest 里两条前缀的 owner 相同，故这里只做族名到属主的映射，不再复制解析逻辑。</p>
     */
    public Optional<String> auditBaseUrl() {
        return resolveOwner(SYSMAN_ROUTE_OWNER, SYSMAN_BASE_URL_PROPERTY, sysmanBaseUrlOverride);
    }

    /**
     * 本体族（{@code /api/v1/ontology} 与同引擎提供的 {@code /api/v1/ecos}、
     * {@code /api/v1/engine/ontology}）的 base-url（不含尾斜杠）。
     */
    public Optional<String> ontologyBaseUrl() {
        return resolveOwner(ONTOLOGY_ROUTE_OWNER, ONTOLOGY_BASE_URL_PROPERTY, ontologyBaseUrlOverride);
    }

    private Optional<String> resolveOwner(String owner, String property, String override) {
        if (!override.isEmpty()) {
            return Optional.of(trimTrailingSlash(override));
        }
        ServiceEndpointResolver resolver = endpointProvider.getIfAvailable();
        if (resolver == null) {
            log.warn("[agent-tool] ServiceEndpointResolver 缺席，{} 基址不可解析（按依赖不可用处置）：{}",
                    owner, property);
            return Optional.empty();
        }
        Optional<String> resolved = resolver.resolve(owner, ServiceEndpointResolver.RouteMode.SERVICE);
        if (resolved.isEmpty()) {
            log.warn("[agent-tool] route-manifest 未登记 owner={}，基址不可解析（按依赖不可用处置）", owner);
            return Optional.empty();
        }
        return resolved.map(AgentToolEndpointResolver::trimTrailingSlash);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
