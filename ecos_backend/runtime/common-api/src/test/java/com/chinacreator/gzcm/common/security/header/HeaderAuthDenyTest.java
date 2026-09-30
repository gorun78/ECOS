package com.chinacreator.gzcm.common.security.header;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-00 F00-03 / W02 — service 侧 {@link HeaderAuthInterceptor} fail-closed 门禁。
 *
 * <p>断言（服务拆分 S1 起的内网契约，ADR-7 fail-closed）：
 * <ol>
 *   <li>缺 {@code X-ECOS-USER} 或 {@code X-ECOS-TENANT} → 403 ECOS-AUTH-010</li>
 *   <li>{@code X-ECOS-SERVICE} 格式非法 → 403 ECOS-AUTH-011</li>
 *   <li>验签失败 → 403 ECOS-AUTH-011</li>
 *   <li>合法头 → 通过并构造 {@link ServiceContext}，afterCompletion 显式 clear（防线程池串号）</li>
 *   <li>health 注解族匿名可达（不 403）</li>
 * </ol>
 *
 * <p>验收：{@code mvn -pl runtime/common-api -am test -Dtest='HeaderAuthDenyTest'}</p>
 */
class HeaderAuthDenyTest {

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    private MockHttpServletRequest req(String path, String... headerPairs) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRequestURI(path);
        r.setMethod("GET");
        for (int i = 0; i + 1 < headerPairs.length; i += 2) {
            r.addHeader(headerPairs[i], headerPairs[i + 1]);
        }
        return r;
    }

    @Test
    @DisplayName("1. 缺 X-ECOS-USER 或 X-ECOS-TENANT → 403 ECOS-AUTH-010")
    void missingTrustHeadersReturns403_010() throws Exception {
        HeaderAuthInterceptor interceptor = new HeaderAuthInterceptor();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        // 只有 tenant，缺 user
        boolean ok = interceptor.preHandle(req("/api/v1/data", "X-ECOS-TENANT", "t1"), resp, new Object());

        assertFalse(ok);
        assertEquals(403, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("\"success\":false"),
                "拒绝响应体应标记 success=false，实际: " + resp.getContentAsString());
        // 只 user 缺 tenant 同样 010
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(req("/api/v1/data", "X-ECOS-USER", "u1"), resp2, new Object()));
        assertEquals(403, resp2.getStatus());
        assertTrue(resp2.getContentAsString().contains("\"success\":false"));
    }

    @Test
    @DisplayName("2. X-ECOS-SERVICE 格式非法（无点/空段）→ 403 ECOS-AUTH-011")
    void malformedServiceCredentialReturns403_011() throws Exception {
        HeaderAuthInterceptor interceptor = new HeaderAuthInterceptor();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(
                req("/api/v1/data", "X-ECOS-USER", "u1", "X-ECOS-TENANT", "t1", "X-ECOS-SERVICE", "no-dot-here"),
                resp, new Object());

        assertFalse(ok);
        assertEquals(403, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("\"success\":false"),
                "拒实响应体应标记 success=false，实际: " + resp.getContentAsString());
    }

    @Test
    @DisplayName("3. 验签失败 → 403 ECOS-AUTH-011")
    void failedVerifyReturns403_011() throws Exception {
        // 生产验签器：secret 固定 "k"，签名不匹配即拒绝
        HeaderAuthInterceptor interceptor = new HeaderAuthInterceptor(
                HeaderAuthInterceptor.EcosServiceCredentialVerifier.fromAad("secret-k"));
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(
                req("/api/v1/data", "X-ECOS-USER", "u1", "X-ECOS-TENANT", "t1",
                        "X-ECOS-SERVICE", "datanet.badsig"),
                resp, new Object());

        assertFalse(ok);
        assertEquals(403, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("\"success\":false"),
                "拒实响应体应标记 success=false，实际: " + resp.getContentAsString());
    }

    @Test
    @DisplayName("4. 合法头 → 通过并构造 ServiceContext；afterCompletion clear")
    void validHeadersPassAndContextCleared() throws Exception {
        HeaderAuthInterceptor interceptor = new HeaderAuthInterceptor();
        MockHttpServletRequest r = req("/api/v1/data",
                "X-ECOS-USER", "u1", "X-ECOS-TENANT", "t1", "X-ECOS-ROLE", "ROLE_A,ROLE_B");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(r, resp, new Object()));
        assertNotNull(ServiceContext.current());
        assertEquals("u1", ServiceContext.current().getUserId());
        assertEquals("t1", ServiceContext.current().getTenantId());

        interceptor.afterCompletion(r, resp, new Object(), null);
        assertNull(ServiceContext.current(), "afterCompletion 必须 clear，防线程池串号");
    }

    @Test
    @DisplayName("5. health 注解族匿名可达（不 403，不要求信任头）")
    void healthEndpointsAreAnonymous() throws Exception {
        HeaderAuthInterceptor interceptor = new HeaderAuthInterceptor();
        for (String p : new String[]{"/health", "/actuator/health", "/api/v1/engine/health"}) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            assertTrue(interceptor.preHandle(req(p), resp, new Object()), "health 路径应放行: " + p);
            assertEquals(200, resp.getStatus());
        }
    }
}
