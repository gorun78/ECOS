package com.chinacreator.gzcm.sysman.security;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W06（docs/30-设计/详细设计-00-平台入口与横切底座-2026-09-28.md §F F00-02 / C.2.4，M0）
 * — JWT 认证过滤器失败语义收紧：默认 DENY 唯一实现口径。
 *
 * <p>7 用例（设计文档 F00-02 验收命令 {@code -Dtest='JwtDenyTest'}）：
 * <ol>
 *   <li>无 Authorization 头 → 放行给 Security 默认 DENY（chain 继续执行）</li>
 *   <li>{@link ExpiredJwtException} → 401 ECOS-AUTH-002</li>
 *   <li>token type != access → 401 ECOS-AUTH-003</li>
 *   <li>jti 命中黑名单 → 401 ECOS-AUTH-004</li>
 *   <li>黑名单查询抛 {@link DataAccessException} → <b>503 ECOS-AUTH-005 fail-closed</b>（关键用例）</li>
 *   <li>JWT 缺 tenant_id 且回查返 null → 403 ECOS-AUTH-006，SecurityContext 被清空</li>
 *   <li>JWT 缺 tenant_id 但 {@code users.tenant_id}（V245）回查命中 → 放行且租户上下文落位
 *       （2026-10-10 ECOS-AUTH-006 断链回归位：原 SQL 查 td_user."TENANT_ID"，该列不存在，
 *       凡走回查分支的请求恒 403）</li>
 * </ol>
 *
 * <p>注入方式：mock {@link JwtTokenProvider#validateToken}（不真造 RS256 token）；
 * 黑名单/租户/权限/用户名回查走 {@link JdbcTemplate} mock，按 SQL 字面路由。
 * 断言读 {@code MockHttpServletResponse.getContentAsString()}。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtDenyTest {

    private static final String PATH = "/api/v1/system/users";

    private JdbcTemplate jdbc;
    private MockHttpServletRequest request;
    private AtomicBoolean chainInvoked;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        UserContext.clear();
        TenantContextHolder.clear();
    }

    /** 按 SQL 字面路由 mock（sql → 返回值或 Throwable；null = 空结果/空列表） */
    private void stubJdbc(Function<String, Object> router) {
        jdbc = mock(JdbcTemplate.class);

        // 单值 Integer（黑名单 COUNT / clearance 等），双 Object 签名
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.<Class<Integer>>eq(Integer.class),
                        any(Object[].class)))
                .thenAnswer(inv -> invokeInteger(router, inv.getArgument(0)));
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.<Class<Integer>>eq(Integer.class),
                        org.mockito.ArgumentMatchers.<Object>any()))
                .thenAnswer(inv -> invokeInteger(router, inv.getArgument(0)));

        // 列表 String（权限联查 / 用户名回查）
        when(jdbc.queryForList(anyString(), org.mockito.ArgumentMatchers.<Class<String>>eq(String.class),
                        any(Object[].class)))
                .thenAnswer(inv -> invokeListString(router, inv.getArgument(0)));

        // 列表 Map（租户回查等）
        when(jdbc.queryForList(anyString(), org.mockito.ArgumentMatchers.<Object>any()))
                .thenAnswer(inv -> invokeListObject(router, inv.getArgument(0)));
    }

    private static Integer invokeInteger(Function<String, Object> router, String sql) {
        Object v = router.apply(sql);
        if (v instanceof Throwable t) {
            throw (t) instanceof RuntimeException re ? re : new RuntimeException(t);
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private static java.util.List<String> invokeListString(Function<String, Object> router, String sql) {
        Object v = router.apply(sql);
        if (v instanceof Throwable t) {
            throw (t) instanceof RuntimeException re ? re : new RuntimeException(t);
        }
        return v instanceof java.util.List ? new java.util.ArrayList<>(java.util.Collections.emptyList()) : java.util.Collections.emptyList();
    }

    private static java.util.List<java.util.Map<String, Object>> invokeListObject(Function<String, Object> router, String sql) {
        Object v = router.apply(sql);
        if (v instanceof Throwable t) {
            throw (t) instanceof RuntimeException re ? re : new RuntimeException(t);
        }
        if (v instanceof java.util.List<?> rows) {
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> cast =
                    (java.util.List<java.util.Map<String, Object>>) rows;
            return cast;
        }
        return java.util.Collections.emptyList();
    }

    /**
     * 租户回查 SQL 判据（V245 起 = {@code SELECT tenant_id FROM users WHERE id = ?}）。
     * 不用大写 {@code TENANT_ID}/{@code TD_USER} 字面，否则会误命中权限联查（TD_USER_ROLE）。
     */
    private static boolean tenantLookupSql(String sql) {
        return sql.contains("tenant_id") && sql.contains("FROM users");
    }

    private MockHttpServletRequest newRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        req.setRequestURI(PATH);
        return req;
    }

    /** 真实 Claims 假对象（HashMap 承载；避免 mock Map.get 触发 Mockito 未完成 stubbing） */
    private static final class TestClaims extends java.util.HashMap<String, Object>
            implements Claims {
        @Override public String getIssuer() {
            return asString(get(Claims.ISSUER));
        }
        @Override public String getSubject() {
            return asString(get(Claims.SUBJECT));
        }
        @Override public Set<String> getAudience() {
            Object a = get(Claims.AUDIENCE);
            if (a instanceof Set<?> s) {
                @SuppressWarnings("unchecked")
                Set<String> cast = (Set<String>) s;
                return cast;
            }
            return java.util.Collections.emptySet();
        }
        @Override public Date getExpiration() {
            Date d = (Date) get(Claims.EXPIRATION);
            return d;
        }
        @Override public Date getNotBefore() {
            return (Date) get(Claims.NOT_BEFORE);
        }
        @Override public Date getIssuedAt() {
            return (Date) get(Claims.ISSUED_AT);
        }
        @Override public String getId() {
            return asString(get(Claims.ID));
        }
        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String name, Class<T> claimedType) {
            Object v = get(name);
            return v == null ? null : (T) v;
        }
        private static String asString(Object o) {
            return o == null ? null : String.valueOf(o);
        }
    }

    private static Claims accessClaims(String subject, String jti, String type, String tenantId,
                                       String username) {
        TestClaims claims = new TestClaims();
        claims.put(Claims.SUBJECT, subject);
        claims.put(Claims.ID, jti);
        claims.put("type", type);
        if (tenantId != null) {
            claims.put("tenant_id", tenantId);
        }
        if (username != null) {
            claims.put("username", username);
        }
        claims.put("roles", java.util.Arrays.<Object>asList("ROLE_USER"));
        return claims;
    }

    private final class Outcome {
        final MockHttpServletResponse response;
        final AtomicBoolean chainInvoked = new AtomicBoolean();
        Outcome(MockHttpServletResponse response) { this.response = response; }
    }

    private Outcome run(JwtAuthenticationFilter filter, MockHttpServletRequest req)
            throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        Outcome out = new Outcome(resp);
        filter.doFilter(req, resp, (r, s) -> out.chainInvoked.set(true));
        return out;
    }

    // ────────────────────────────────────────────────────────────

    /** tc1：缺 Authorization → 直接链放行（交给 Spring Security anyRequest().authenticated() DENY） */
    @Test
    @DisplayName("tc1 无 Authorization → chain.doFilter 放行（Security 默认 DENY 接管）")
    void noAuthorizationPutsRequestThroughChain() throws Exception {
        stubJdbc(sql -> null);
        JwtTokenProvider tp = mock(JwtTokenProvider.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tp, jdbc);
        request = newRequest();
        Outcome out = run(filter, request);
        assertTrue(out.chainInvoked.get(), "无 Authorization 应直接 chain.doFilter（Security 默认 DENY 接管）");
        assertEquals(200, out.response.getStatus(), "放行时不写 errorCode 包络");
        verify(jdbc, never()).queryForObject(anyString(),
                org.mockito.ArgumentMatchers.<Class<Integer>>any(), any(Object[].class));
    }

    /** tc2：ExpiredJwtException → 401 ECOS-AUTH-002，链不执行 */
    @Test
    @DisplayName("tc2 ExpiredJwtException → 401，body 含 ECOS-AUTH-002")
    void expiredTokenReturns401Auth002() throws Exception {
        stubJdbc(sql -> null);
        JwtTokenProvider tp = mock(JwtTokenProvider.class);
        when(tp.validateToken(anyString())).thenThrow(new ExpiredJwtException(
                mock(io.jsonwebtoken.Header.class), mock(Claims.class), "expired"));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tp, jdbc);
        request = newRequest();
        request.addHeader("Authorization", "Bearer stale.token");
        Outcome out = run(filter, request);
        assertEquals(401, out.response.getStatus());
        // C.1.3 包络：当前 ApiResponse 序列化不含 errorCode 字段（无 getter），
        // 以 W06 专属 message + status 组合锁定 ECOS-AUTH-002 语义（过期）；
        // 待主代码补 errorCode getter 后可收紧为 contains("ECOS-AUTH-002")。
        assertTrue(out.response.getContentAsString().contains("Token已过期")
                        || out.response.getContentAsString().contains("Token无效或已过期"),
                "包络必须为过期语义 ECOS-AUTH-002：实际=" + out.response.getContentAsString());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    /** tc3：type=refresh → 401 ECOS-AUTH-003 */
    @Test
    @DisplayName("tc3 type != access → 401，body 含 ECOS-AUTH-003")
    void wrongTokenTypeReturns401Auth003() throws Exception {
        stubJdbc(sql -> null);
        JwtTokenProvider tp = mock(JwtTokenProvider.class);
        Claims claims = accessClaims("u-1", "jti-xx", "refresh", "t-1", null);
        when(tp.validateToken(anyString())).thenReturn(claims);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tp, jdbc);
        request = newRequest();
        request.addHeader("Authorization", "Bearer wrong-type.token");
        Outcome out = run(filter, request);
        assertEquals(401, out.response.getStatus());
        assertTrue(out.response.getContentAsString().contains("Token类型无效"),
                "包络必须为类型错语义 ECOS-AUTH-003：实际=" + out.response.getContentAsString());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    /** tc4：jti 命中黑名单（COUNT=1）→ 401 ECOS-AUTH-004 */
    @Test
    @DisplayName("tc4 jti 命中黑名单 → 401，body 含 ECOS-AUTH-004")
    void blacklistedJtiReturns401Auth004() throws Exception {
        stubJdbc(sql -> sql.contains("ecos_token_blacklist") ? 1 : null);
        JwtTokenProvider tp = mock(JwtTokenProvider.class);
        when(tp.validateToken(anyString()))
                .thenReturn(accessClaims("u-rot", "jti-rot", "access", "t-1", null));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tp, jdbc);
        request = newRequest();
        request.addHeader("Authorization", "Bearer jti.rot.token");
        Outcome out = run(filter, request);
        assertEquals(401, out.response.getStatus());
        assertTrue(out.response.getContentAsString().contains("Token已被强制踢出"),
                "包络必须为黑名单语义 ECOS-AUTH-004：实际=" + out.response.getContentAsString());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    /** tc5：黑名单查询抛 DataAccessException → 503 ECOS-AUTH-005（W06 fail-closed 核心用例） */
    @Test
    @DisplayName("tc5 黑名单查询抛 DataAccessException → 503，body 含 ECOS-AUTH-005 (fail-closed)")
    void blacklistDbFaultReturns503Auth005() throws Exception {
        stubJdbc(sql -> sql.contains("ecos_token_blacklist")
                ? new DataAccessException("pg down: blacklist lookup failed") { }
                : null);
        JwtTokenProvider tp = mock(JwtTokenProvider.class);
        when(tp.validateToken(anyString()))
                .thenReturn(accessClaims("u-503", "jti-live", "access", "t-1", null));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tp, jdbc);
        request = newRequest();
        request.addHeader("Authorization", "Bearer jti.live.token");
        Outcome out = run(filter, request);
        assertEquals(503, out.response.getStatus(),
                "黑名单依赖不可用必须 fail-closed 503（W06 原 fail-open 收口）");
        assertTrue(out.response.getContentAsString().contains("认证依赖不可用"),
                "包络必须为 fail-closed 语义 ECOS-AUTH-005：实际="
                        + out.response.getContentAsString());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    /** tc6：JWT 缺 tenant_id + loadTenantId 回查空 → 403 ECOS-AUTH-006，SecurityContext 空 */
    @Test
    @DisplayName("tc6 JWT 缺 tenant_id + 回查空 → 403，body 含 ECOS-AUTH-006，SecurityContext 空")
    void missingTenantReturns403Auth006() throws Exception {
        stubJdbc(sql -> {
            // 租户回查（users.tenant_id，V245）→ 空 list；其余 → null(→empty)
            return tenantLookupSql(sql) ? List.<Object>of() : null;
        });
        JwtTokenProvider tp = mock(JwtTokenProvider.class);
        when(tp.validateToken(anyString()))
                .thenReturn(accessClaims("u-notenant", "jti-nt", "access", null, null));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tp, jdbc);
        request = newRequest();
        request.addHeader("Authorization", "Bearer no.tenant.token");
        Outcome out = run(filter, request);
        assertEquals(403, out.response.getStatus());
        assertTrue(out.response.getContentAsString().contains("用户未绑定租户"),
                "包络必须为缺租户口径 ECOS-AUTH-006（删 tenant-a 兜底后显式拒绝）：实际="
                        + out.response.getContentAsString());
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "403 前 Filter 已 clearContext，不得残留 Authentication");
        assertNull(TenantContextHolder.getTenantId(), "403 不得落租户上下文");
    }

    /**
     * tc7：JWT 缺 tenant_id 但 {@code users.tenant_id}（V245）回查命中 → 放行且租户上下文落位。
     * <p>ECOS-AUTH-006 断链回归位（2026-10-10）：原 SQL 查 td_user 的 {@code "TENANT_ID"} 列，
     * 该表无此列 ⇒ 回查恒空 ⇒ 凡 token 不带 tenant_id claim 的请求一律 403。
     */
    @Test
    @DisplayName("tc7 JWT 缺 tenant_id + users.tenant_id 回查命中 → chain 放行，租户上下文落位")
    void tenantResolvedFromUsersTablePassesThrough() throws Exception {
        stubJdbc(sql -> tenantLookupSql(sql)
                ? List.<Object>of(java.util.Map.<String, Object>of("tenant_id", "tenant-a"))
                : null);
        JwtTokenProvider tp = mock(JwtTokenProvider.class);
        when(tp.validateToken(anyString()))
                .thenReturn(accessClaims("u-7", "jti-7", "access", null, null));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tp, jdbc);
        request = newRequest();
        request.addHeader("Authorization", "Bearer users.lookup.hit.token");
        Outcome out = run(filter, request);
        assertTrue(out.chainInvoked.get(),
                "users.tenant_id 回查命中必须放行（403 断链已修）：body="
                        + out.response.getContentAsString());
        assertEquals(200, out.response.getStatus());
        assertEquals("tenant-a", TenantContextHolder.getTenantId(),
                "回查到的租户必须落 TenantContextHolder");
        assertEquals("tenant-a", UserContext.getCurrentTenantId(),
                "回查到的租户必须落 UserContext");
    }
}
