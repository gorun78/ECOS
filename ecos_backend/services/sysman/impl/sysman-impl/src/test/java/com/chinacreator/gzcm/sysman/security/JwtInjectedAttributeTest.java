package com.chinacreator.gzcm.sysman.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.sysman.dao.AuthLookupDao;
import com.chinacreator.gzcm.sysman.iam.context.TenantContext;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;

/**
 * D112（2026-11-15 活体实证）门禁：已认证身份必须写进**请求属性**，供同进程出站凭证中继消费。
 *
 * <p><b>缺陷实况</b>：{@code GET /api/v1/workspace/calibers} 在 gateway fat jar 内由内嵌
 * {@code CaliberContractController} 处理（不是转发到 :18090），其出站 {@code security/rls/apply}
 * 经 {@code CallerCredentialSource} 取凭证。入站请求只带 {@code Authorization}，
 * {@code X-ECOS-USER/TENANT} 由 {@code EcosTrustHeaderFilter} 无条件剥离（防伪造，正确），
 * 而该 Filter 的 {@code MIN_VALUE+20} 顺序在 Spring Security 链（-100）之外层——无论前置还是
 * {@code finally} 重注入，控制器执行期 {@code SecurityContext} 都是空的 ⇒ 属性恒空 ⇒ 出站缺信任头
 * ⇒ security-engine 403 {@code ECOS-AUTH-010} ⇒ workspace 折叠成 PERMISSION_DENIED。
 * 活体 traceId {@code f8fa91d97d9941c18bf1dd44ca793498} 是证据。</p>
 *
 * <p><b>钉住的行为</b>：认证成功后立即写 {@code ECOS_INJECTED_USER/TENANT}（控制器之前可见）；
 * 未认证路径不写（不得凭空造主语）。属性名与 gateway {@code EcosTrustHeaderFilter} 的
 * 已发布常量同值——两侧跨模块不可直引（sysman-impl 不依赖 gateway），字面量对齐由本测试守门。</p>
 */
class JwtInjectedAttributeTest {

    @AfterEach
    void clearAll() {
        TenantContextHolder.clear();
        UserContext.clear();
        TenantContext.clear();
    }

    private static Claims accessClaims(String userId, String tenantId) {
        return Jwts.claims()
                .subject(userId)
                .add("type", "access")
                .add("tenant_id", tenantId)
                .add("username", "tester")
                .build();
    }

    private static JwtAuthenticationFilter filterFor(String token, Claims claims) {
        JwtTokenProvider provider = mock(JwtTokenProvider.class);
        when(provider.validateToken(token)).thenReturn(claims);
        AuthLookupDao lookup = mock(AuthLookupDao.class);
        when(lookup.isTokenBlacklisted(token)).thenReturn(false);
        when(lookup.loadUserPermissions(claims.getSubject())).thenReturn(List.of("system:user:query"));
        return new JwtAuthenticationFilter(provider, lookup);
    }

    @Test
    @DisplayName("认证成功：ECOS_INJECTED_USER/TENANT 写入请求属性，链内（=控制器视角）可读")
    void injectsAttributesBeforeChainProceeds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/workspace/calibers");
        request.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> userInsideChain = new AtomicReference<>();
        AtomicReference<String> tenantInsideChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            userInsideChain.set(String.valueOf(req.getAttribute("ECOS_INJECTED_USER")));
            tenantInsideChain.set(String.valueOf(req.getAttribute("ECOS_INJECTED_TENANT")));
        };

        filterFor("good-token", accessClaims("u-admin", "tenant-a"))
                .doFilter(request, response, chain);

        assertEquals("u-admin", userInsideChain.get(),
                "控制器执行期必须读得到注入用户——否则出站中继无主语（D112 复现）");
        assertEquals("tenant-a", tenantInsideChain.get(),
                "控制器执行期必须读得到注入租户——否则 security-engine 403 ECOS-AUTH-010");
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("反证：未认证请求不写注入属性（不得凭空造主语）")
    void doesNotInjectWhenNoToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Object> userInsideChain = new AtomicReference<>("unset");
        FilterChain chain = (req, res) -> userInsideChain.set(req.getAttribute("ECOS_INJECTED_USER"));

        new JwtAuthenticationFilter(mock(JwtTokenProvider.class), mock(AuthLookupDao.class))
                .doFilter(request, response, chain);

        assertNull(userInsideChain.get(), "无令牌路径不得写注入属性（fail-closed 不退化）");
        assertNull(request.getAttribute("ECOS_INJECTED_TENANT"));
    }
}
