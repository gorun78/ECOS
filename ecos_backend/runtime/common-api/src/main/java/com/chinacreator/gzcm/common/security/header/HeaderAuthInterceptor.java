package com.chinacreator.gzcm.common.security.header;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.security.credential.EcosServiceCredentialSigner;
import com.chinacreator.gzcm.common.security.registry.AnonymousEndpointRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * W02（详细设计-00 C.2.2，M0）— service 侧信任头认证拦截器。
 *
 * <p>职责（仅"还原上下文"，裁决归 01 册 security-engine）：
 * <ol>
 *   <li>必存在 {@code X-ECOS-USER} + {@code X-ECOS-TENANT}，否则 403 {@code ECOS-AUTH-010}</li>
 *   <li>构造 {@link EcosServiceContext}（ThreadLocal；afterCompletion 显式 clear 防线程池串号）</li>
 *   <li>请求带 {@code X-ECOS-SERVICE} → 验签，失败 403 {@code ECOS-AUTH-011}</li>
 *   <li>{@link AnonymousEndpointRegistry} 登记的匿名端点（health 族等）跳过 1~3</li>
 * </ol>
 *
 * <p>禁止：本拦截器不查安全策略表（避免 F-9 扩散）。
 * <b>注册方式</b>：不标 {@code @Component} —— 由每个承流 service 的
 * {@code WebMvcConfigurer} 显式注册一次（6 service 各自 addInterceptor，
 * 避免 5 个当前 0 controller 的空壳 service 误挂）。</p>
 */
public class HeaderAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HeaderAuthInterceptor.class);

    public static final String H_USER = "X-ECOS-USER";
    public static final String H_ROLE = "X-ECOS-ROLE";
    public static final String H_TENANT = "X-ECOS-TENANT";
    public static final String H_DOMAIN = "X-ECOS-DOMAIN";
    public static final String H_SERVICE = "X-ECOS-SERVICE";
    public static final String H_TRACE = "X-Request-Id";

    private final String serviceId;
    private final String sharedSecret;

    public HeaderAuthInterceptor(@Value("${spring.application.name:ecos-service}") String serviceId,
                                 @Value("${ecos.service.shared-secret:}") String sharedSecret) {
        this.serviceId = serviceId;
        this.sharedSecret = sharedSecret;
    }

    public HeaderAuthInterceptor(String serviceId) {
        this(serviceId, null);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = request.getRequestURI();

        // ④ 匿名清单（单源 registry）跳过 — health/孤儿端点
        if (AnonymousEndpointRegistry.isAnonymous(path)) {
            return true;
        }

        String user = request.getHeader(H_USER);
        String tenant = request.getHeader(H_TENANT);

        // ① 信任头必填 — 缺任一即 403（fail-closed，铁律 §2.4-6）
        if (user == null || user.isBlank() || tenant == null || tenant.isBlank()) {
            log.warn("header-auth deny: 缺 X-ECOS-USER/TENANT path={} remote={}",
                    path, request.getRemoteAddr());
            sendForbidden(response, "ECOS-AUTH-010", "service 侧缺少信任头 X-ECOS-USER/X-ECOS-TENANT");
            return false;
        }

        // ③ 服务凭证验签（携带才验；不携带 = 用户态经 gateway 直连的合法双跑态）
        String serviceCredential = request.getHeader(H_SERVICE);
        if (serviceCredential != null && !serviceCredential.isBlank()) {
            String secret = (sharedSecret == null || sharedSecret.isBlank())
                    ? System.getenv().getOrDefault("ECOS_SERVICE_SHARED_SECRET", "dev-only-shared-secret")
                    : sharedSecret;
            if (!EcosServiceCredentialSigner.verify(serviceCredential,
                    System.currentTimeMillis() / 1000L, secret)) {
                log.warn("header-auth deny: X-ECOS-SERVICE 验签失败 path={}", path);
                sendForbidden(response, "ECOS-AUTH-011", "服务凭证验签失败");
                return false;
            }
        }

        // ② 还原上下文（ThreadLocal，afterCompletion 清理）
        EcosServiceContext ctx = EcosServiceContext.of(
                user,
                tenant,
                header(request, H_ROLE),
                header(request, H_DOMAIN),
                header(request, H_TRACE));
        EcosServiceContext.setCurrent(ctx);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        EcosServiceContext.clear(); // 线程池复用安全：显式清理
    }

    private static String header(HttpServletRequest request, String name) {
        String v = request.getHeader(name);
        return v == null || v.isBlank() ? "" : v;
    }

    private static void sendForbidden(HttpServletResponse response, String errorCode, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(ApiResponse.error(403, errorCode, message).toJson());
    }
}
