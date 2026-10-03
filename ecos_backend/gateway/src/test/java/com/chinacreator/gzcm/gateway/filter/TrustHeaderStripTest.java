package com.chinacreator.gzcm.gateway.filter;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W02（docs/30-设计/详细设计-00-平台入口与横切底座-2026-09-28.md §F F00-03 / C.1.3/C.2，M0）
 * — 网关信任头过滤器「剥离 → 鉴权 → 重注入」核心契约
 * （{@link EcosTrustHeaderFilter.StrippedRequest} + {@code resolveInjectedIdentity}）。
 *
 * <p>4 用例（验收命令 {@code -Dtest='TrustHeaderStripTest'}）：
 * <ol>
 *   <li>外部携带 X-ECOS-USER/TENANT/ROLE/DOMAIN/SERVICE 五个头 → StrippedRequest
 *       getHeader 全部 null，getHeaderNames 不含 X-ECOS-*</li>
 *   <li>非信任头（Authorization/X-Request-Id/Content-Type）剥离后保留</li>
 *   <li>SUC：预置 SecurityContext + TenantContextHolder + path=/api/v1/security/policy →
 *       resolveInjectedIdentity 返 USER/TENANT/ROLE（升序）/DOMAIN=control</li>
 *   <li>未认证（SecurityContextHolder 空）→ resolveInjectedIdentity 返空 Map（C.1.3 不透传）</li>
 * </ol>
 * </p>
 */
class TrustHeaderStripTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    private static EcosTrustHeaderFilter.StrippedRequest wrapped(MockHttpServletRequest original) {
        return new EcosTrustHeaderFilter.StrippedRequest(original);
    }

    /** tc1：外部 X-ECOS-* 剥离后全部不可见（getHeader=null，getHeaderNames 剔除） */
    @Test
    @DisplayName("tc1 外部 X-ECOS-USER/TENANT/ROLE/DOMAIN/SERVICE 五个头剥离后全部不可见")
    void externalTrustHeadersAreStripped() {
        MockHttpServletRequest raw = new MockHttpServletRequest();
        raw.addHeader("X-ECOS-USER", "attacker");
        raw.addHeader("X-ECOS-TENANT", "attacker-tenant");
        raw.addHeader("X-ECOS-ROLE", "attacker-role");
        raw.addHeader("X-ECOS-DOMAIN", "attacker-domain");
        raw.addHeader("X-ECOS-SERVICE", "attacker.svc.sig");

        EcosTrustHeaderFilter.StrippedRequest stripped = wrapped(raw);

        for (String h : new String[]{"X-ECOS-USER", "X-ECOS-TENANT", "X-ECOS-ROLE",
                "X-ECOS-DOMAIN", "X-ECOS-SERVICE"}) {
            assertEquals(null, stripped.getHeader(h), h + " 剥离后必须 getHeader=null");
            assertTrue(stripped.getHeaders(h).hasMoreElements() == false,
                    h + " 剥离后必须 getHeaders 为空");
        }
        // getHeaderNames 必须不含 X-ECOS-*
        java.util.Enumeration<String> names = stripped.getHeaderNames();
        while (names.hasMoreElements()) {
            String n = names.nextElement();
            assertFalse(EcosTrustHeaderFilter.isTrustHeaderName(n),
                    "getHeaderNames 必须剔除外部信任头，实际剩 " + n);
        }
    }

    /** tc2：非信任头（Authorization/X-Request-Id/Content-Type）剥离后保留透传 */
    @Test
    @DisplayName("tc2 非信任头（Authorization/X-Request-Id/Content-Type）剥离后保留")
    void nonTrustHeadersArePreserved() {
        MockHttpServletRequest raw = new MockHttpServletRequest();
        raw.addHeader("Authorization", "Bearer abc.def.ghi");
        raw.addHeader("X-Request-Id", "req-001");
        raw.addHeader("Content-Type", "application/json;charset=UTF-8");
        // 混入一条 X-ECOS 证明剔除不影响其它
        raw.addHeader("X-ECOS-USER", "spoof");

        EcosTrustHeaderFilter.StrippedRequest stripped = wrapped(raw);

        assertEquals("Bearer abc.def.ghi", stripped.getHeader("Authorization"));
        assertEquals("req-001", stripped.getHeader("X-Request-Id"));
        assertEquals("application/json;charset=UTF-8", stripped.getHeader("Content-Type"));
        assertEquals(null, stripped.getHeader("X-ECOS-USER"));

        boolean seenAuth = false;
        boolean seenRequestId = false;
        java.util.Enumeration<String> names = stripped.getHeaderNames();
        while (names.hasMoreElements()) {
            String n = names.nextElement();
            if ("Authorization".equals(n)) seenAuth = true;
            if ("X-Request-Id".equals(n)) seenRequestId = true;
        }
        assertTrue(seenAuth && seenRequestId, "getHeaderNames 必须保留非信任头");
    }

    /** tc3：SUC 已认证 + 控制域路径 → resolveInjectedIdentity 还原 USER/TENANT/ROLE/DOMAIN */
    @Test
    @DisplayName("tc3 SUC 已认证 + /api/v1/security/policy → USER/TENANT/ROLE=(升序逗号)/DOMAIN=control")
    void successPathInjectsFullIdentity() {
        // 预置 SecurityContext：u1 + [ROLE_B, ROLE_A]（乱序，验证注入时排序）
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "u1", null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_B"),
                        new SimpleGrantedAuthority("ROLE_A")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        // 预置 TenantContextHolder（C.2.1 TENANT 来源之一）
        TenantContextHolder.setTenantId("t-1");

        String path = "/api/v1/security/policy";
        Map<String, String> injected = EcosTrustHeaderFilter.resolveInjectedIdentity(path);

        assertEquals("u1", injected.get(EcosTrustHeaderFilter.H_USER));
        assertEquals("t-1", injected.get(EcosTrustHeaderFilter.H_TENANT));
        // 升序逗号分隔
        assertEquals("ROLE_A,ROLE_B", injected.get(EcosTrustHeaderFilter.H_ROLE));
        // 控制域（C.2.1：/api/v1/security/** 含 control 前缀 "security"）
        assertEquals("control", injected.get(EcosTrustHeaderFilter.H_DOMAIN));
    }

    /** tc4：SecurityContextHolder 空 → resolveInjectedIdentity 返空 Map（C.1.3 未认证不透传） */
    @Test
    @DisplayName("tc4 未认证（SecurityContext 空） → resolveInjectedIdentity 返空 Map")
    void anonymousReturnsEmptyInjectedMap() {
        // 刻意清 SecurityContext（@AfterEach 也会清，双保险）
        SecurityContextHolder.clearContext();
        // 即使 TenantContextHolder 有值也不能作身份（无 auth 不注入）
        TenantContextHolder.setTenantId("t-ghost");

        Map<String, String> injected = EcosTrustHeaderFilter.resolveInjectedIdentity("/api/v1/data/list");

        assertTrue(injected == null || injected.isEmpty(),
                "未认证必须返空 Map（C.1.3 不透传；任意 X-ECOS-* 不注入）");
    }

    /** 辅助占位：保留 HttpServletRequest 引用避免未用类型警告（可选） */
    @SuppressWarnings("unused")
    private static HttpServletRequest unusedTypeAnchor() {
        return new MockHttpServletRequest();
    }
}
