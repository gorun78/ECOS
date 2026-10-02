package com.chinacreator.gzcm.sysman.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 详细设计-00 F00-02 / W06 — JWT 校验失败一律 DENY（fail-closed）门禁。
 *
 * <p>6 用例覆盖 {@link JwtAuthenticationFilter} 的收紧语义：
 * <ol>
 *   <li>签名/过期无效 → 401 ECOS-AUTH-002</li>
 *   <li>token 类型非 access → 401 ECOS-AUTH-003</li>
 *   <li>jti 命中黑名单 → 401 ECOS-AUTH-004</li>
 *   <li>黑名单/权限查询抛 DataAccessException → 503 ECOS-AUTH-005（fail-closed，非 fail-open）</li>
 *   <li>缺 tenant_id 且 DB 回查为空 → 403 ECOS-AUTH-006（废弃 "tenant-a" 兜底）</li>
 *   <li>合法 access + 有 tenant → 放行并填充 SecurityContext（正路径）</li>
 * </ol>
 *
 * <p>验收：{@code mvn -pl services/sysman/impl/sysman-impl -am test -Dtest='JwtDenyTest'}</p>
 */
class JwtDenyTest {

    private JwtTokenProvider jwtTokenProvider;
    private com.chinacreator.gzcm.sysman.dao.AuthLookupDao authLookupDao;
    private JwtAuthenticationFilter filter;
    private FilterChain chain;
    private boolean chainInvoked;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setUp(boolean chainPassThrough) {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        authLookupDao = mock(com.chinacreator.gzcm.sysman.dao.AuthLookupDao.class);
        filter = new JwtAuthenticationFilter(jwtTokenProvider, authLookupDao);
        chain = new FilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req,
                                 jakarta.servlet.ServletResponse res) {
                if (chainPassThrough) {
                    // 计数由调用方通过 responseBody 侧观察；这里仅标记
                    chainInvoked = true;
                }
            }
        };
        chainInvoked = false;
    }

    private MockHttpServletRequest bearerRequest(String token, String path) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(path);
        req.setMethod("GET");
        req.addHeader("Authorization", "Bearer " + token);
        return req;
    }

    @Test
    @DisplayName("1. 签名/过期无效 → 401 ECOS-AUTH-002")
    void invalidTokenReturns401_002() throws Exception {
        setUp(false);
        when(jwtTokenProvider.validateToken(anyString()))
                .thenThrow(new JwtException("bad signature"));

        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(bearerRequest("tok", "/api/v1/data"), resp, chain);

        assertEquals(401, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("Token无效或已过期"));
    }

    @Test
    @DisplayName("2. token 类型非 access → 401 ECOS-AUTH-003")
    void nonAccessTypeReturns401_003() throws Exception {
        setUp(false);
        Claims claims = mock(Claims.class);
        when(claims.get("type", String.class)).thenReturn("refresh");
        when(jwtTokenProvider.validateToken(anyString())).thenReturn(claims);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(bearerRequest("tok", "/api/v1/data"), resp, chain);

        assertEquals(401, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("Token类型无效"));
    }

    @Test
    @DisplayName("3. jti 命中黑名单 → 401 ECOS-AUTH-004")
    void blacklistedJtiReturns401_004() throws Exception {
        setUp(false);
        Claims claims = mock(Claims.class);
        when(claims.get("type", String.class)).thenReturn("access");
        when(claims.getId()).thenReturn("jti-1");
        when(claims.getSubject()).thenReturn("u1");
        when(jwtTokenProvider.validateToken(anyString())).thenReturn(claims);
        // 黑名单命中（键为完整 token 串，与生产 fail-closed 路径一致）
        when(authLookupDao.isTokenBlacklisted("tok")).thenReturn(true);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(bearerRequest("tok", "/api/v1/data"), resp, chain);

        assertEquals(401, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("强制踢出"));
    }

    @Test
    @DisplayName("4. 黑名单查询抛 DataAccessException → 503 ECOS-AUTH-005（fail-closed）")
    void blacklistDbErrorReturns503_005() throws Exception {
        setUp(false);
        Claims claims = mock(Claims.class);
        when(claims.get("type", String.class)).thenReturn("access");
        when(claims.getId()).thenReturn("jti-1");
        when(claims.getSubject()).thenReturn("u1");
        when(jwtTokenProvider.validateToken(anyString())).thenReturn(claims);
        doThrow(new QueryTimeoutException("pg down"))
                .when(authLookupDao).isTokenBlacklisted("tok");

        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(bearerRequest("tok", "/api/v1/data"), resp, chain);

        assertEquals(503, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("依赖不可用"));
    }

    @Test
    @DisplayName("5. 缺 tenant_id 且 DB 回查为空 → 403 ECOS-AUTH-006（无 tenant-a 兜底）")
    void missingTenantReturns403_006() throws Exception {
        setUp(false);
        Claims claims = mock(Claims.class);
        when(claims.get("type", String.class)).thenReturn("access");
        when(claims.getId()).thenReturn(null); // 无 jti，跳过黑名单
        when(claims.getSubject()).thenReturn("u1");
        when(claims.get("tenant_id", String.class)).thenReturn(null);
        when(jwtTokenProvider.validateToken(anyString())).thenReturn(claims);
        // Mockito 默认对 List 返回值返 emptyList()：loadUserPermissions 与 tenant 回查均返回空
        // → tenantId 未解析且回查为空 → 403 ECOS-AUTH-006
        when(authLookupDao.loadUserPermissions("u1")).thenReturn(Collections.<String>emptyList());
        when(authLookupDao.findTenantById("u1")).thenReturn(Collections.<Map<String, Object>>emptyList());
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(bearerRequest("tok", "/api/v1/data"), resp, chain);

        assertEquals(403, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("用户未绑定租户"));
    }

    @Test
    @DisplayName("6. 合法 access + 有 tenant → 放行并填充 SecurityContext")
    void validAccessPassesThroughAndSetsContext() throws Exception {
        setUp(true);
        Claims claims = mock(Claims.class);
        when(claims.get("type", String.class)).thenReturn("access");
        when(claims.getId()).thenReturn(null);
        when(claims.getSubject()).thenReturn("u1");
        when(claims.get("tenant_id", String.class)).thenReturn("t1");
        when(claims.get("roles")).thenReturn(List.of("ROLE_ADMIN"));
        when(jwtTokenProvider.validateToken(anyString())).thenReturn(claims);
        when(authLookupDao.isTokenBlacklisted(any())).thenReturn(false);
        when(authLookupDao.loadUserPermissions("u1")).thenReturn(Collections.<String>emptyList());

        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(bearerRequest("tok", "/api/v1/data"), resp, chain);

        assertEquals(200, resp.getStatus());
        assertTrue(chainInvoked, "合法 token 应放行到后续链");
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("u1", SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
