package com.chinacreator.gzcm.services.sysman;

import com.chinacreator.gzcm.common.security.credential.EcosServiceCredentialSigner;
import com.chinacreator.gzcm.common.security.header.EcosServiceContext;
import com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W02（docs/30-设计/详细设计-00-平台入口与横切底座-2026-09-28.md §F F00-03 / C.2.2，M0）
 * — service 侧信任头认证拦截器 {@link HeaderAuthInterceptor}（6 service 共享同一逻辑；
 * 本套 ×6 每 service 一份，此份对应 sysman :18081，验收命令 {@code -Dtest='HeaderAuthDenyTest'}）。
 *
 * <p>职责仅"还原上下文"，裁决归 01 册 security-engine：
 * <ol>
 *   <li>host 必填 X-ECOS-USER + X-ECOS-TENANT，缺一 → 403 ECOS-AUTH-010（fail-closed）</li>
 *   <li>携带 X-ECOS-SERVICE → 验签，失败 → 403 ECOS-AUTH-011</li>
 *   <li>仅 USER+TENANT（无 SERVICE）→ 通过 = 用户态经 gateway 直连的合法双跑态</li>
 *   <li>匿名清单路径（health 族）跳过 ①~③</li>
 * </ol>
 * 用 MockHttpServletRequest/MockHttpServletResponse 直接调 preHandle。</p>
 */
class HeaderAuthDenyTest {

    private static final String SERVICE_ID = "sysman";
    private static final String SECRET = "test-secret";
    private static final String BIZ_PATH = "/api/v1/system/users"; // 非匿名，须走 ①~③

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/");
        request.setRequestURI(BIZ_PATH);
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        EcosServiceContext.clear();
    }

    private HeaderAuthInterceptor interceptor() {
        return new HeaderAuthInterceptor(SERVICE_ID, SECRET);
    }

    /** tc1：缺 X-ECOS-USER → 403 ECOS-AUTH-010 */
    @Test
    @DisplayName("tc1 无 X-ECOS-USER → 403 ECOS-AUTH-010")
    void missingUserHeadForbidden010() throws Exception {
        request.addHeader("X-ECOS-TENANT", "t-1");
        boolean ok = interceptor().preHandle(request, response, null);
        assertFalse(ok);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":403"),
                "实际=" + response.getContentAsString());
    }

    /** tc2：缺 X-ECOS-TENANT → 403 ECOS-AUTH-010 */
    @Test
    @DisplayName("tc2 无 X-ECOS-TENANT → 403 ECOS-AUTH-010")
    void missingTenantHeadForbidden010() throws Exception {
        request.addHeader("X-ECOS-USER", "u-1");
        boolean ok = interceptor().preHandle(request, response, null);
        assertFalse(ok);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":403"));
    }

    /** tc3：有 USER+TENANT、无 SERVICE → 通过（用户态经 gateway 双跑合法） */
    @Test
    @DisplayName("tc3 USER+TENANT 无 SERVICE → preHandle=true（通过该步）")
    void userTenantWithoutServicePasses() throws Exception {
        request.addHeader("X-ECOS-USER", "u-1");
        request.addHeader("X-ECOS-TENANT", "t-1");
        assertTrue(interceptor().preHandle(request, response, null),
                "USER+TENANT 合法双跑态应通过");
        assertEquals(200, response.getStatus(), "通过时不写拒绝包络");
    }

    /** tc4：USER+TENANT + 假 sign 的 SERVICE → 403 ECOS-AUTH-011 */
    @Test
    @DisplayName("tc4 USER+TENANT + 假 sign SERVICE → 403 ECOS-AUTH-011")
    void forgedServiceCredentialForbidden011() throws Exception {
        request.addHeader("X-ECOS-USER", "u-1");
        request.addHeader("X-ECOS-TENANT", "t-1");
        request.addHeader("X-ECOS-SERVICE", "sysman.12345.deadbeefdeadbeef"); // 假 hmac
        boolean ok = interceptor().preHandle(request, response, null);
        assertFalse(ok);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":403"),
                "实际=" + response.getContentAsString());
    }

    /** tc5：USER+TENANT + 同 secret 合法 sign → 验签通过（preHandle=true） */
    @Test
    @DisplayName("tc5 USER+TENANT + 同 secret 合法 sign → preHandle=true")
    void validServiceCredentialPasses() throws Exception {
        request.addHeader("X-ECOS-USER", "u-1");
        request.addHeader("X-ECOS-TENANT", "t-1");
        String good = new EcosServiceCredentialSigner(SERVICE_ID, SECRET).sign();
        request.addHeader("X-ECOS-SERVICE", good);
        assertTrue(interceptor().preHandle(request, response, null),
                "同 secret 合法凭证应验签通过: " + good);
        assertEquals(200, response.getStatus());
    }

    /** tc6：匿名清单路径（/api/health）+ 无任何头 → preHandle=true（跳过 ①~③） */
    @Test
    @DisplayName("tc6 匿名路径 /api/health 无头 → preHandle=true（跳过 ①~③）")
    void anonymousPathSkipsChecks() throws Exception {
        request.setRequestURI("/api/health");
        // 无任何 X-ECOS-* 头
        assertTrue(interceptor().preHandle(request, response, null),
                "registry 匿名清单路径应跳过信任头校验");
        assertEquals(200, response.getStatus());
    }
}
