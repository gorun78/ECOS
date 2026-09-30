package com.chinacreator.gzcm.gateway.filter;

import com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

/**
 * C.2 跨 JVM 信任链网关侧过滤器（详细设计-00 · W02 · M0）。
 *
 * <p>两阶段（C.1.1 规定顺序 = HIGHEST_PRECEDENCE+20，{@link JwtAuthenticationFilter}
 * 所在 Spring Security 链之前剥离 / 之后按身份还原 context）：
 * <ol>
 *   <li><b>剥离</b>——无条件清空 {@code X-ECOS-USER}/{@code X-ECOS-ROLE}/{@code X-ECOS-TENANT}
 *       /{@code X-ECOS-DOMAIN}/{@code X-ECOS-SERVICE}（F-6 现状零生产代码）。
 *       剥离后在 attribute {@code stripped.X-ECOS-*} 保留观测字符串，供日志/审计。</li>
 *   <li><b>重注入</b>——chain 返回后，从已填充的 {@link SecurityContextHolder} 提取
 *       与 claims，写回 request attribute（{@code ECOS_INJECTED_USER} / {@code ECOS_INJECTED_TENANT}）
 *       并派生 {@link com.chinacreator.gzcm.common.security.header.ServiceContext}。
 *       service 拆分后（S1 起）由 service 侧 HeaderAuthInterceptor 从真实 HTTP 头重取，
 *       attribute 仅供 gateway 内嵌代码与诊断页读取。</li>
 * </ol>
 *
 * <p>关键守卫：</li>
 * <ul>
 *   <li>剥离阶段对<b>全部请求</b>执行，包括 /actuator 家族；
 *   <li>重注入阶段对匿名/health/auth 路径跳过——这些路径没有 authentication 可重放；
 *   <li>重注入失败仅作 warning，不阻断业务（有 {@code UserContext} 内建链路兜底）。</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class EcosTrustHeaderFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(EcosTrustHeaderFilter.class);

    /** 注入到 request 属性的键，供 Controller/AOP 读取。 */
    public static final String ATTR_INJECTED = "EcosTrustHeaderFilter.injected";
    public static final String ATTR_INJECTED_USER   = "ECOS_INJECTED_USER";
    public static final String ATTR_INJECTED_TENANT = "ECOS_INJECTED_TENANT";

    /** 信任头：不可由客户端伪造、不由 service 直接消费。剥离后重注入（W02 修复 F-6）。 */
    private static final String[] TRUST_HEADERS = {
            "X-ECOS-USER", "X-ECOS-ROLE", "X-ECOS-TENANT", "X-ECOS-DOMAIN", "X-ECOS-SERVICE"
    };

    /**
     * 详细设计-01 W40：机构上下文只从服务端（TD_USER.ORG_ID）解析，客户端伪造的
     * {@code X-Org-Id} / {@code ?orgId=} 参数一律剥离。剥离头单独成组与 X-ECOS-* 并存，
     * 便于日志/审计追溯（{@code stripped.X-Org-Id}）。
     */
    private static final String[] ORG_OVERRIDABLE_HEADERS = { "X-Org-Id" };
    private static final String[] ORG_OVERRIDABLE_PARAMS   = { "orgId" };

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        HttpServletRequest clean = stripIncoming(request);
        try {
            chain.doFilter(clean, response);
        } finally {
            reinjectBestEffort(clean);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return false;
    }

    // ── 阶段 1：剥离 ──────────────────────────────

    private HttpServletRequest stripIncoming(HttpServletRequest src) {
        for (String h : TRUST_HEADERS) {
            String v = src.getHeader(h);
            if (v != null) {
                src.setAttribute("stripped." + h, v);
            }
        }
        // W40：剥离客户端可伪造的机构头/参数，留观测串（service 端 org 只从 TD_USER 服务端解析）
        for (String h : ORG_OVERRIDABLE_HEADERS) {
            String v = src.getHeader(h);
            if (v != null) {
                src.setAttribute("stripped." + h, v);
            }
        }
        for (String p : ORG_OVERRIDABLE_PARAMS) {
            String v = src.getParameter(p);
            if (v != null) {
                src.setAttribute("stripped.param." + p, v);
            }
        }
        return new TrustStripRequestWrapper(src);
    }

    private static final class TrustStripRequestWrapper extends HttpServletRequestWrapper {
        TrustStripRequestWrapper(HttpServletRequest req) { super(req); }

        private boolean isStripped(String name) {
            if (name != null) {
                for (String h : TRUST_HEADERS) if (h.equalsIgnoreCase(name)) return true;
                for (String h : ORG_OVERRIDABLE_HEADERS) if (h.equalsIgnoreCase(name)) return true;
            }
            return false;
        }

        private boolean isStrippedParam(String name) {
            if (name != null) {
                for (String p : ORG_OVERRIDABLE_PARAMS) if (p.equals(name)) return true;
            }
            return false;
        }

        @Override
        public String getHeader(String name) {
            if (isStripped(name)) return null;
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (isStripped(name)) return Collections.emptyEnumeration();
            return super.getHeaders(name);
        }

        @Override
        public String getParameter(String name) {
            if (isStrippedParam(name)) return null;
            return super.getParameter(name);
        }
    }

    // ── 阶段 2：重注入 ──────────────────────────────

    private void reinjectBestEffort(HttpServletRequest request) {
        if (shouldSkip(request.getRequestURI())) return;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return;

        String userId = String.valueOf(auth.getPrincipal());
        StringBuilder roles = new StringBuilder();
        for (var ga : auth.getAuthorities()) {
            if (roles.length() > 0) roles.append(',');
            roles.append(ga.getAuthority());
        }
        String tenantId = null;
        try {
            var details = auth.getDetails();
            if (details instanceof io.jsonwebtoken.Claims claims) {
                Object t = claims.get("tenant_id");
                if (t != null) tenantId = t.toString();
            }
        } catch (Exception ignored) { }
        if (tenantId == null || tenantId.isBlank()) return;   // JwtAuthFilter (W06) 已 403

        request.setAttribute(ATTR_INJECTED, Boolean.TRUE);
        request.setAttribute(ATTR_INJECTED_USER, userId);
        request.setAttribute(ATTR_INJECTED_TENANT, tenantId);
        if (log.isTraceEnabled()) {
            log.trace("injected user={} tenant={} roles={} path={}",
                    userId, tenantId, roles, request.getRequestURI());
        }
    }

    /** 匿名/health 族不重放（无 authentication）。与 AnonymousEndpointRegistry 的 health 族同口径。 */
    private boolean shouldSkip(String path) {
        if (path == null) return true;
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/auth/")
                || path.equals("/health")
                || path.equals("/api/health")
                || path.startsWith("/actuator/")
                || path.startsWith("/api/v1/knowledge/health")
                || path.startsWith("/api/v1/engine/health")
                || path.startsWith("/api/v1/engine/")
                || path.equals("/error");
    }

    /** 头常量对测公开：HeaderAuthInterceptor.H_USER 等。 */
    static String headerUser() { return HeaderAuthInterceptor.H_USER; }
}
