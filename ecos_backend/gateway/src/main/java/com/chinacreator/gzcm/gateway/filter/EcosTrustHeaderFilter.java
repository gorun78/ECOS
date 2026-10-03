package com.chinacreator.gzcm.gateway.filter;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import io.jsonwebtoken.Claims;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.stream.Collectors;

/**
 * W02（详细设计-00 C.2 信任链，M0）— 网关信任头过滤器：剥离 → 鉴权 → 重注入。
 *
 * <h3>链序（C.1.1 规定序 3）：{@code HIGHEST_PRECEDENCE+20}</h3>
 * <pre>
 * 剥离开于鉴权：本 filter 以 Servlet 容器 Filter 链先于 Spring Security 链执行，
 *               外部携带的任何 X-ECOS-* 头在到达 JWT 认证前即被隐藏（无条件剥离，非拒绝）。
 * 重注入晚于鉴权：chain.doFilter 返回时认证已完成，本 filter 从 SecurityContext
 *               还原 user/tenant/roles/domain 并写入请求属性 {@link #INJECTED_HEADERS}。
 * 未认证：不注入任何 X-ECOS-*（C.1.3 失败语义总表：不透传）。
 * </pre>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class EcosTrustHeaderFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(EcosTrustHeaderFilter.class);

    /** 请求属性 key：鉴权后重注入的信任头快照（Map&lt;String,String&gt;） */
    public static final String INJECTED_HEADERS = "com.chinacreator.gzcm.X_ECOS_INJECTED_HEADERS";

    public static final String H_USER = "X-ECOS-USER";
    public static final String H_ROLE = "X-ECOS-ROLE";
    public static final String H_TENANT = "X-ECOS-TENANT";
    public static final String H_DOMAIN = "X-ECOS-DOMAIN";
    public static final String H_SERVICE = "X-ECOS-SERVICE";

    private static final String STRIP_PREFIX = "X-ECOS-";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        StrippedRequest stripped = new StrippedRequest(request);
        chain.doFilter(stripped, response);
        // ── chain 返回后：认证已由 SecurityFilterChain 完成，重注入身份（晚于鉴权）──
        Map<String, String> injected = resolveInjectedIdentity(request.getRequestURI());
        if (!injected.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("trust-inject: path={} userId={} tenant={}",
                        request.getRequestURI(), injected.get(H_USER), injected.get(H_TENANT));
            }
        }
        request.setAttribute(INJECTED_HEADERS, injected);
    }

    /**
     * 从 SecurityContext 还原注入身份（C.2.1 契约）：
     * USER=JWT sub；TENANT=TenantContextHolder/JWT claim；ROLE=角色逗号分隔（缺失视为空集）；
     * DOMAIN=控制域/业务域二分。未认证/匿名返回空 Map（不注入）。
     */
    static Map<String, String> resolveInjectedIdentity(String path) {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth instanceof AnonymousAuthenticationToken) {
                return out;
            }
            String userId = auth.getName();
            if (userId == null || userId.isBlank() || "anonymousUser".equals(userId)) {
                return out;
            }
            out.put(H_USER, userId);

            List<String> roles = auth.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .filter(a -> a != null && !a.isBlank())
                    .sorted()
                    .collect(Collectors.toList());
            out.put(H_ROLE, String.join(",", roles));

            String tenant = TenantContextHolder.getTenantId();
            if (tenant == null || tenant.isBlank()) {
                Object details = auth.getDetails();
                if (details instanceof Claims claims && claims.get("tenant_id") != null) {
                    tenant = claims.get("tenant_id", String.class);
                }
            }
            out.put(H_TENANT, tenant == null ? "" : tenant);

            out.put(H_DOMAIN, domainOf(path));
        } catch (Exception e) {
            log.warn("trust-inject 还原身份失败，按未认证处理: {}", e.getMessage());
            out.clear();
        }
        return out;
    }

    /** C.2.1 DOMAIN：控制域/业务域二分（ST07 5+1 口径） */
    static String domainOf(String path) {
        if (path == null) {
            return "business";
        }
        String p = path.startsWith("/api/v1/") ? path.substring("/api/v1/".length())
                : path.startsWith("/api/") ? path.substring("/api/".length()) : path;
        for (String control : CONTROL_PREFIXES) {
            if (p.startsWith(control)) {
                return "control";
            }
        }
        return "business";
    }

    private static final String[] CONTROL_PREFIXES =
            {"auth", "security", "audit", "system/", "engine/", "monitor"};

    /** 外部携带的 X-ECOS-* 头一律视为不可信（剥离判定入口，诊断页可消费） */
    public static boolean isTrustHeaderName(String name) {
        return name != null && name.regionMatches(true, 0, STRIP_PREFIX, 0, STRIP_PREFIX.length());
    }

    /**
     * 剥离包装器：外部 X-ECOS-* 对下游链不可见（getHeader 返 null / getHeaders 空 /
     * getHeaderNames 剔除）。外部携带不拒绝、不记错误 —— 避免误伤浏览器插件，
     * 统一走"剥离后覆盖"语义（C.1.3），重注入值来自 SecurityContext 重建。
     */
    static class StrippedRequest extends HttpServletRequestWrapper {

        private final Enumeration<String> visibleNames;

        StrippedRequest(HttpServletRequest request) {
            super(request);
            Vector<String> names = new Vector<>();
            Enumeration<String> all = request.getHeaderNames();
            while (all != null && all.hasMoreElements()) {
                String n = all.nextElement();
                if (!isTrustHeaderName(n)) {
                    names.add(n);
                }
            }
            this.visibleNames = names.isEmpty() ? Collections.emptyEnumeration() : names.elements();
        }

        @Override
        public String getHeader(String name) {
            if (isTrustHeaderName(name)) {
                return null;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (isTrustHeaderName(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return visibleNames;
        }
    }
}
