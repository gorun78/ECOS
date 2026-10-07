package com.chinacreator.gzcm.services.agent.runtime.toolrouter;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Agent 工具统一网关客户端（B8e / 铁律 §2.5 横切收敛）。
 *
 * <p>agent-service 工具（toolrouter）跨服务访问一律走本客户端，禁在工具内直拼
 * 环境绑定的网关地址字面量（硬编码）或直连数据源 JDBC（绕过 data-engine 的
 * RLS 行级过滤 / B2 JdbcUrlPolicy 白名单 / 多租户解析）。本客户端提供三件事：</p>
 * <ol>
 *   <li><b>可配置基址</b>：base URL 取 {@code ecos.agent.gateway-base-url}（默认
 *       {@code http://127.0.0.1:8080}，循环回环同一宿主的等价写法，避免环境字面量硬编码），
 *       生产按部署注入不带走源码；</li>
 *   <li><b>租户上下文透传</b>：每次请求读取 {@link TenantContextHolder#getTenantId()}，
 *       非空即加 {@code X-Tenant-Id} 头，让 gateway/下游按租户路由与授权（fail-closed 由下游执行）；</li>
 *   <li><b>鉴权透传</b>：如调用方把 Bearer 放入 {@link #bearerTokenHolder}，透传 {@code Authorization} 头，
 *       保持下游 security-engine 的 RBAC/OPA 裁决不因本地代理丢失。</li>
 * </ol>
 *
 * <p>客户端自建 {@link RestTemplate}（带 connect/read 超时），<b>不</b>依赖外部
 * {@code RestTemplate} Bean —— agent-service 既可独立 boot 也作为 gateway fat-JAR 内 JAR
 * 加载，此处兜底装配避免 NoUniqueBean/NoSuchBean 漂移。返回体保持下游 {@code ApiResponse} 原样。</p>
 *
 * @author ECOS Agent Team
 */
@Component
public class AgentGatewayClient {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final RestTemplate restTemplate;
    private final String gatewayBaseUrl;

    /** 供上层（如 Agent 运行时控制器）在每次工具调用前设置本轮 Bearer，用完清空，防跨请求串号。 */
    static final ThreadLocal<String> bearerTokenHolder = new ThreadLocal<>();

    public AgentGatewayClient(
            @Value("${ecos.agent.gateway-base-url:http://127.0.0.1:8080}") String gatewayBaseUrl,
            @Value("${ecos.agent.gateway-connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${ecos.agent.gateway-read-timeout-ms:30000}") long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeoutMs);
        factory.setReadTimeout((int) readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
        String base = gatewayBaseUrl == null ? "" : gatewayBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.gatewayBaseUrl = base;
    }

    /** GET {@code <base><apiPath>}（apiPath 形如 {@code /api/v1/audit/logs}），透传租户/鉴权头。 */
    public Object get(String apiPath, Object... uriVars) {
        return exchange(HttpMethod.GET, apiPath, null, uriVars);
    }

    /** POST {@code <base><apiPath>}，body 作为 JSON 请求体。 */
    public Object post(String apiPath, Object body, Object... uriVars) {
        return exchange(HttpMethod.POST, apiPath, body, uriVars);
    }

    private Object exchange(HttpMethod method, String apiPath, Object body, Object... uriVars) {
        HttpHeaders headers = buildHeaders();
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        String url = gatewayBaseUrl + apiPath;
        return restTemplate.exchange(url, method, entity, Object.class, uriVars).getBody();
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null && !tenantId.isBlank()) {
            headers.set(TENANT_HEADER, tenantId);
        }
        // Bearer 透传（由 Agent 运行时控制器在每次工具调用前写入 bearerTokenHolder）
        String bearer = bearerTokenHolder.get();
        if (bearer != null && !bearer.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, bearer.startsWith("Bearer ") ? bearer : "Bearer " + bearer);
        }
        return headers;
    }
}
