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

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.sysman.dao.AuthLookupDao;
import com.chinacreator.gzcm.sysman.iam.context.TenantContext;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 租户/身份上下文请求级收敛门（§2.9.8⑥ 环境·租户·用户隔离；Q-4 同族 · #147AA）。
 *
 * <p><b>缺陷实况</b>：`TenantContextHolder` / `UserContext` / `TenantContext` 三枚 ThreadLocal
 * 在 {@link JwtAuthenticationFilter} 里只写不擦，而 servlet 容器线程是复用的——于是
 * 「上一个已认证请求的租户」会留在池化线程上：紧随其后的<b>无令牌请求</b>（permitAll 面、
 * 健康检查、401 早退路径）读到的仍是上一个用户的租户与身份。权限缓存的 {@code scope}
 * 也取自该上下文，因此这条泄漏会直接把缓存键钉到错误的租户上。</p>
 *
 * <p>本门钉住两条不变式：<b>请求起点必清零</b>（不继承同线程残留）、
 * <b>请求结束必清零</b>（不把本请求身份留给下一个请求），并验证正常认证期间上下文确实在位。</p>
 *
 * @author ECOS Sysman #147AA
 */
class JwtAuthenticationFilterTenantContextCleanupTest {

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

    private static HttpServletRequest requestWith(String path, String authorization) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(path);
        when(request.getHeader("Authorization")).thenReturn(authorization);
        return request;
    }

    @Test
    @DisplayName("认证期间上下文在位；请求结束后三枚 ThreadLocal 全部清空")
    void contextIsVisibleInsideChainAndClearedAfterwards() throws Exception {
        JwtTokenProvider provider = mock(JwtTokenProvider.class);
        when(provider.validateToken("good-token")).thenReturn(accessClaims("u-1", "tenant-a"));
        AuthLookupDao lookup = mock(AuthLookupDao.class);
        when(lookup.isTokenBlacklisted("good-token")).thenReturn(false);
        when(lookup.loadUserPermissions("u-1")).thenReturn(List.of("system:user:query"));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider, lookup);

        AtomicReference<String> tenantInsideChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> tenantInsideChain.set(TenantContextHolder.getTenantId());

        filter.doFilter(requestWith("/api/v1/system/users", "Bearer good-token"),
                mock(HttpServletResponse.class), chain);

        assertEquals("tenant-a", tenantInsideChain.get(), "认证链内必须能读到本请求租户");
        assertNull(TenantContextHolder.getTenantId(), "请求结束必须清空，否则同线程下一请求继承本租户");
        assertNull(UserContext.getCurrent(), "UserContext 同为请求级上下文，必须清空");
        assertNull(TenantContext.getTenantId(), "sysman 侧 TenantContext 由 UserContext 连带写入，须一并清空");
    }

    @Test
    @DisplayName("反证：同线程残留会在请求起点被清零——无令牌请求绝不继承上一请求的租户")
    void staleContextFromPreviousRequestIsNotInherited() throws Exception {
        TenantContextHolder.setTenantId("tenant-stale");
        UserContext stale = new UserContext();
        stale.setUserId("stale-user");
        stale.setTenantId("tenant-stale");
        UserContext.setCurrent(stale);
        TenantContext.setTenantId("tenant-stale");

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                mock(JwtTokenProvider.class), mock(AuthLookupDao.class));

        AtomicReference<String> tenantInsideChain = new AtomicReference<>("unset");
        FilterChain chain = (req, res) -> tenantInsideChain.set(TenantContextHolder.getTenantId());

        filter.doFilter(requestWith("/api/health", null), mock(HttpServletResponse.class), chain);

        assertNull(tenantInsideChain.get(), "无令牌请求不得继承同线程上一请求的租户（跨租户泄漏）");
        assertNull(TenantContextHolder.getTenantId());
        assertNull(UserContext.getCurrent());
        assertNull(TenantContext.getTenantId());
    }
}
