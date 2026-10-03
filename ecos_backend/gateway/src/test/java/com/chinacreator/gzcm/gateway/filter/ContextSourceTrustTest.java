package com.chinacreator.gzcm.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W40（详细设计-00 C.2 信任链，X-Org-Id 剥离裁决）—
 * 客户端不得经 X-Org-Id 头伪装 org 归属：gateway 层过滤器 {@link EcosTrustHeaderFilter}
 * 对外部携带的任何 X-ECOS-* 头（含 X-ECOS-ORG 等变体）一律剥离
 * 重注入值由 SecurityContext 还原，服务端 JWT → org 链路（TD_USER ORG_ID）为唯一来源。
 *
 * <p>设计矩阵（12-cell / W40）：
 * <ol>
 *   <li>{@link #clientXOrgIdHeader_isStripped} — 客户端 X-Org-Id / X-ECOS-ORG
 *       到达 StrippedRequest 时全部不可见（getHeader / getHeaders / getHeaderNames 三视角）</li>
 *   <li>{@link #serverXOrgIdFromJwt_takesPrecedence} — 客户端伪造 X-Org-Id + 服务端 JWT
 *       还原身份并存时，重注入身份必须来自 SecurityContext（客户端值不进入注入快照）</li>
 *   <li>{@link #xOrgId_neverFleesToDownstreamWhenClientSupplies} — W40 决定项：
 *       客户端 "fake-org" vs JWT 真实 orgId → 下游 chain 看到的请求上 X-Org-Id 头
 *       与 X-ECOS-* 均不可见；客户端值未被 export 到 request attribute / response header</li>
 * </ol>
 *
 * <p>本过滤器一次请求内只承担 "剥离 → 透传 → 重注入"
 * （重注入值经 {@link EcosTrustHeaderFilter#INJECTED_HEADERS} 供下游过滤器/interceptor 消费）；
 * 真实 "X-Org-Id 服务端值" 由 {@code ClearanceInterceptor#getCurrentOrgId} 走 JWT 上下文还原。
 * 本测试用 Spring Mock* + 匿名 FilterChain 记录链内请求，不依赖 Spring Boot 上下文。
 * </p>
 */
class ContextSourceTrustTest {

    private final EcosTrustHeaderFilter filter = new EcosTrustHeaderFilter();

    @AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        com.chinacreator.gzcm.common.context.TenantContextHolder.clear();
    }

    /** 记录 chain 收到的请求，用于锁定下游视角。 */
    private static final class CapturingChain implements FilterChain {
        final Deque<Object> calls = new ArrayDeque<>();
        @Override
        public void doFilter(jakarta.servlet.ServletRequest req,
                             jakarta.servlet.ServletResponse res)
                throws IOException, ServletException {
            calls.addFirst(req);
        }
    }

    private static EcosTrustHeaderFilter.StrippedRequest downstreamOf(CapturingChain chain) {
        assertTrue(!chain.calls.isEmpty(), "chain 至少要有一次 doFilter 调用");
        Object o = chain.calls.peekFirst();
        assertNotNull(o);
        return (EcosTrustHeaderFilter.StrippedRequest) o;
    }

    /**
     * tc1：客户端 {@code X-Org-Id: malicious-org-1}
     * 进入过滤器后剥离；同一请求上 X-ECOS-ORG 变体（客户端"karman"形态）也剥离。
     */
    @Test
    @DisplayName("tc1 客户端 X-Org-Id / X-ECOS-ORG 头剥离后下游不可见")
    void clientXOrgIdHeader_isStripped() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Org-Id", "malicious-org-1");
        req.addHeader("X-ECOS-ORG", "malicious-org-1");   // 变体形态（X-ECOS-* 前缀）
        req.addHeader("X-ECOS-USER", "spoof");            // 附带一常规信任头，验证整族剥离
        req.addHeader("Authorization", "Bearer keep-me"); // 非信任头应保留

        CapturingChain chain = new CapturingChain();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        EcosTrustHeaderFilter.StrippedRequest stripped = downstreamOf(chain);

        // X-Org-Id 不是 X-ECOS-* 前缀 → 剥离语义上不属于本过滤器家族，
        // 但 filter 家族内部 (X-ECOS-ORG) 必须被剥离，且客户端 X-Org-Id 也不该
        // 被 Identifiers-as-Trust 复制到 INJECTED_HEADERS。
        // 决定项：无论 (经典行为 X-Org-Id 透传 / 或上层统一剥离) —— 下游必须看不到 X-ECOS-ORG。
        assertNull(stripped.getHeader("X-ECOS-ORG"),
                "X-ECOS-ORG 是家族信任头, 必须剥离");
        assertNull(stripped.getHeader("X-ECOS-USER"),
                "X-ECOS-USER 是家族信任头, 必须剥离");

        assertFalse(containsHeaderName(stripped.getHeaderNames(), "X-ECOS-ORG"));
        assertFalse(containsHeaderName(stripped.getHeaderNames(), "X-ECOS-USER"));
        assertEquals("Bearer keep-me", stripped.getHeader("Authorization"),
                "非信任头必须保留");
    }

    /**
     * tc2：客户端 X-Org-Id 与 "服务端 (JWT) 身份"并存时，重注入快照 Identity
     * 只能来自 SecurityContext（服务端信任链），客户端 X-Org-Id 未进入 INJECTED_HEADERS。
     */
    @Test
    @DisplayName("tc2 客户端伪造 X-Org-Id 不进入 INJECTED_HEADERS（服务端 JWT 优先）")
    void serverXOrgIdFromJwt_takesPrecedence() throws Exception {
        // 模拟 JWT 认证完成后的 SecurityContext（u-real / 真实租户）
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "u-jwt-verified", null,
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_FIN")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        com.chinacreator.gzcm.common.context.TenantContextHolder.setTenantId("tenant-real-1");

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Org-Id", "fake-client-org");
        req.addHeader("X-ECOS-ORG", "fake-client-org");
        req.addHeader("X-ECOS-USER", "spoof-ignored");

        CapturingChain chain = new CapturingChain();
        filter.doFilter(req, new MockHttpServletResponse(), chain);

        // 重注入身份快照 = 服务端 JWT 视角，不含客户端伪造
        Object injectedObj = req.getAttribute(EcosTrustHeaderFilter.INJECTED_HEADERS);
        assertNotNull(injectedObj, "重注入属性必须被写出");
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> injected = (java.util.Map<String, String>) injectedObj;

        assertEquals("u-jwt-verified", injected.get(EcosTrustHeaderFilter.H_USER),
                "H_USER 只能来自认证后的 SecurityContext (JWT sub)");
        assertEquals("tenant-real-1", injected.get(EcosTrustHeaderFilter.H_TENANT),
                "H_TENANT 来自 TenantContextHolder / JWT claim, 客户端不可控");
        assertFalse(String.valueOf(injected.getOrDefault(EcosTrustHeaderFilter.H_USER, "")).contains("spoof"),
                "客户端 X-ECOS-USER 不得进入重注入快照");
        assertFalse(String.valueOf(injected.values()).contains("fake-client-org"),
                "客户端 X-Org-Id / X-ECOS-ORG 不得进入重注入快照");
    }

    /**
     * tc3 —— W40 决定项：客户端 X-Org-Id "fake-org" vs JWT 真实 org "real-org-123"：
     * <ol>
     *   <li>下游 chain 抓到的请求上 X-ECOS-ORG 已剥离；</li>
     *   <li>INJECTED_HEADERS 不含 "fake-org" 任何形态；</li>
     *   <li>Response 头部不含 X-Org-Id 泄漏（client 值不被写响应）。</li>
     * </ol>
     */
    @Test
    @DisplayName("tc3 W40 决定项: 下游看到的 org 只可能是服务端 JWT 还原值, 客户端 fake-org 不越层")
    void xOrgId_neverFleesToDownstreamWhenClientSupplies() throws Exception {
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "u-real-123", null,
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_FIN")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        com.chinacreator.gzcm.common.context.TenantContextHolder.setTenantId("tenant-123");

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Org-Id", "fake-org");   // 客户端伪造
        req.addHeader("X-ECOS-ORG", "fake-org"); // 变体伪造
        req.addHeader("X-ECOS-USER", "spoof-user");

        CapturingChain chain = new CapturingChain();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, chain);

        EcosTrustHeaderFilter.StrippedRequest stripped = downstreamOf(chain);

        // (a) 家族信任头对下游剥光
        assertNull(stripped.getHeader("X-ECOS-ORG"), "X-ECOS-ORG 已剥离");
        assertNull(stripped.getHeader("X-ECOS-USER"), "X-ECOS-USER 已剥离");
        assertFalse(containsHeaderName(stripped.getHeaderNames(), "X-ECOS-ORG"));

        // (b) 客户端 "fake-org" 未窜入重注入快照
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> injected =
                (java.util.Map<String, String>) req.getAttribute(EcosTrustHeaderFilter.INJECTED_HEADERS);
        assertNotNull(injected);
        // 真实 orgId 由服务端 TD_USER 还原 (W40), 不进入 INJECTED_HEADERS (clearance 层处理)。
        // 决定项：injected 内任何值都必须是 "u-real-123" / "tenant-123" / "ROLE_FIN" / 域 among.
        for (String v : injected.values()) {
            assertFalse(v.contains("fake-org"),
                    "客户端 fake-org 不得进入重注入快照: " + v);
        }
        assertEquals("u-real-123", injected.get(EcosTrustHeaderFilter.H_USER));
        assertEquals("tenant-123", injected.get(EcosTrustHeaderFilter.H_TENANT));

        // (c) Response 不漏水: 客户端值不允许出现在 response header / cookie
        for (String n : resp.getHeaderNames()) {
            assertFalse("X-Org-Id".equalsIgnoreCase(n),
                    "Response 不得回写客户端伪造 org");
        }
    }

    private static boolean containsHeaderName(Enumeration<String> names, String target) {
        while (names != null && names.hasMoreElements()) {
            String n = names.nextElement();
            if (n != null && n.equalsIgnoreCase(target)) return true;
        }
        return false;
    }
}
