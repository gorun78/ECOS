package com.chinacreator.gzcm.common.security.header;

import com.chinacreator.gzcm.common.base.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;

/**
 * 跨 JVM 信任链还原拦截器（C.2.2 目标态）。
 *
 * <p>六个 v2 service（sysman/datanet/buszhi/aiming/dccheng/workspace）各需注册本拦截器
 * 一次，负责：
 * <ol>
 *   <li>强制存在 {@code X-ECOS-USER} + {@code X-ECOS-TENANT}，缺其一返回
 *       <b>403 ECOS-AUTH-010</b>（承载 ADR-7 的 fail-closed 内网契约）</li>
 *   <li>构造 {@link ServiceContext}（ThreadLocal），在 {@link #afterCompletion}
 *       <b>显式 clear</b>（M0 红线：防线程池串号）</li>
 *   <li>若请求带 {@code X-ECOS-SERVICE} 头，调用 {@link EcosServiceCredentialSigner#verify}
 *       验签；验签失败 403 ECOS-AUTH-011</li>
 *   <li>对 {@link EcosAnonymousPaths#HEALTH} 内的端点跳过 ①~③（保持 health 匿名可达）</li>
 * </ol>
 *
 * <p><b>本拦截器不查安全策略表</b>（避免 F-9 扩散）——所有 RLS/CLS/OPA 裁决交 01 册，
 * 与 C.2.2 契约一致。</p>
 *
 * <p>注册示例（任一 service 的 App class）：
 * <pre>
 * &#64;Bean
 * WebMvcConfigurer ecosHeaderAuthMvc() { return new WebMvcConfigurer() {
 *     &#64;Override public void addInterceptors(InterceptorRegistry r) {
 *         r.addInterceptor(new HeaderAuthInterceptor())
 *          .addPathPatterns(&quot;/api/**&quot;);
 *     }
 * };
 * </pre>
 * </p>
 */
public class HeaderAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HeaderAuthInterceptor.class);

    /** 网关 → 服务：原始用户身份（必填） */
    public static final String H_USER    = "X-ECOS-USER";
    /** 网关 → 服务：逗号分隔角色（可选，缺失视为空集） */
    public static final String H_ROLE    = "X-ECOS-ROLE";
    /** 网关 → 服务：租户（必填，缺失 403） */
    public static final String H_TENANT  = "X-ECOS-TENANT";
    /** 网关 → 服务：业务域（可选） */
    public static final String H_DOMAIN  = "X-ECOS-DOMAIN";
    /** 服务 → 服务：HMAC 凭证（可选但验签严格 fail-closed） */
    public static final String H_SERVICE = "X-ECOS-SERVICE";
    /** 端到端：traceId（可选，缺失时本层不生成——由 RequestContextFilter 兜底） */
    public static final String H_TRACE   = "X-Request-Id";

    /** 校验器：缺省默认实现使用空 secret 直连模式（dev 用）；生产由 6 service 各自注入 keyed 实现。 */
    private final EcosServiceCredentialVerifier serviceVerifier;

    public HeaderAuthInterceptor() {
        this(EcosServiceCredentialVerifier.TRUST_DEV);
    }

    public HeaderAuthInterceptor(EcosServiceCredentialVerifier verifier) {
        this.serviceVerifier = verifier;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String path = request.getRequestURI();
        // 匿名 health 族豁免
        if (isAnonymousHealth(path)) {
            return true;
        }

        String userId = trimToNull(request.getHeader(H_USER));
        String tenant = trimToNull(request.getHeader(H_TENANT));
        String trace  = trimToNull(request.getHeader(H_TRACE));

        if (userId == null || tenant == null) {
            log.warn("HeaderAuthInterceptor deny: missing trust headers path={} userPresent={} tenantPresent={}",
                    path, userId != null, tenant != null);
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    ApiResponse.error(403, "ECOS-AUTH-010",
                            "缺少信任头（X-ECOS-USER 或 X-ECOS-TENANT）"));
            return false;
        }

        String roles = trimToNull(request.getHeader(H_ROLE));
        String domain = trimToNull(request.getHeader(H_DOMAIN));
        String serviceHeader = trimToNull(request.getHeader(H_SERVICE));

        String serviceId = null;
        if (serviceHeader != null) {
            // 格式: <serviceId>.<hmac-base64url>
            int dot = serviceHeader.indexOf('.');
            if (dot <= 0 || dot == serviceHeader.length() - 1) {
                writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                        ApiResponse.error(403, "ECOS-AUTH-011",
                                "服务凭证格式非法（需 serviceId.sig）"));
                return false;
            }
            serviceId = serviceHeader.substring(0, dot);
            String sig = serviceHeader.substring(dot + 1);
            String body = request.getRequestURI() + "\n" + request.getMethod()
                    + "\n" + trace;
            if (!serviceVerifier.verify(serviceId, body, sig)) {
                log.warn("HeaderAuthInterceptor sign failed: path={} service={}", path, serviceId);
                writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                        ApiResponse.error(403, "ECOS-AUTH-011",
                                "服务凭证验签失败"));
                return false;
            }
        }

        ServiceContext ctx = ServiceContext.of(trace, userId,
                roles == null ? "" : roles, tenant, domain, serviceId);
        ServiceContext.set(ctx);
        if (log.isTraceEnabled()) {
            log.trace("HeaderAuthInterceptor ok: {} path={}", ctx, path);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 无论成功失败都需清，防线程池串号
        ServiceContext.clear();
    }

    /**
     * 匿名 health 族：{@code /health}、{@code /actuator/health*}、
     * {@code /api/health}、{@code /api/v1/health}、
     * {@code /api/v1/engine/health}、{@code /api/v1/knowledge/health}。
     * 保持与服务方 SecurityConfig permitAll 中 health 族同一口径；
     * 完整匿名清单由 {@link com.chinacreator.gzcm.common.security.anonymous.AnonymousEndpointRegistry}
     * 单源管理，此处只做局部缩略集合。
     */
    static boolean isAnonymousHealth(String path) {
        if (path == null) return false;
        if (path.equals("/health") || path.equals("/api/health")
                || path.equals("/actuator/health")) return true;
        if (path.startsWith("/actuator/health")) return true;
        if (path.startsWith("/api/v1/health")) return true;
        if (path.startsWith("/api/v1/knowledge/health")) return true;
        if (path.startsWith("/api/v1/engine/health")) return true;
        return false;
    }

    static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static void writeJson(HttpServletResponse resp, int status, ApiResponse<?> body)
            throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(body.toJson());
    }

    /** 服务凭证验证器 SPI——生产实现需持有共享 secret，dev 实现始终放行。 */
    public interface EcosServiceCredentialVerifier {
        /**
         * 例外场景：dev/本地无密钥时应显式使用 TRUST_DEV，且要求调用方在文档中登记。
         */
        EcosServiceCredentialVerifier TRUST_DEV = (serviceId, body, sign) -> sign != null && !sign.isBlank();

        boolean verify(String serviceId, String canonicalBody, String sign);

        static EcosServiceCredentialVerifier fromAad(String sharedSecret) {
            return (serviceId, body, sign) -> {
                try {
                    return EcosServiceCredentialSigner.hmacSha256Base64Url(sharedSecret, body)
                            .equals(sign);
                } catch (Exception e) {
                    return false;
                }
            };
        }
    }
}
