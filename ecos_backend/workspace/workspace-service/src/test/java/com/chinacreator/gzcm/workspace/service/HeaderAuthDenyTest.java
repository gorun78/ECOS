package com.chinacreator.gzcm.workspace.service;

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
 * — service 侧信任头认证拦截器（workspace-service :18090 场景层，验收命令 {@code -Dtest='HeaderAuthDenyTest'}）。
 * <p>6 用例共享同逻辑（详见 sysman/datatnet/buszhi/aiming/dccheng 对应文件）。</p>
 */
class HeaderAuthDenyTest {

    private static final String SERVICE_ID = "workspace-service";
    private static final String SECRET = "test-secret";
    private static final String BIZ_PATH = "/api/v1/workspace/twin"; // 非匿名，须走 ①~③

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

    /** tc1 */
    @Test
    @DisplayName("tc1 无 X-ECOS-USER → 403 ECOS-AUTH-010")
    void missingUserHeadForbidden010() throws Exception {
        request.addHeader("X-ECOS-TENANT", "t-1");
        boolean ok = interceptor().preHandle(request, response, null);
        assertFalse(ok);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":403"));
    }

    /** tc2 */
    @Test
    @DisplayName("tc2 无 X-ECOS-TENANT → 403 ECOS-AUTH-010")
    void missingTenantHeadForbidden010() throws Exception {
        request.addHeader("X-ECOS-USER", "u-1");
        boolean ok = interceptor().preHandle(request, response, null);
        assertFalse(ok);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":403"));
    }

    /** tc3 */
    @Test
    @DisplayName("tc3 USER+TENANT 无 SERVICE → preHandle=true")
    void userTenantWithoutServicePasses() throws Exception {
        request.addHeader("X-ECOS-USER", "u-1");
        request.addHeader("X-ECOS-TENANT", "t-1");
        assertTrue(interceptor().preHandle(request, response, null));
        assertEquals(200, response.getStatus());
    }

    /** tc4 */
    @Test
    @DisplayName("tc4 假 sign SERVICE → 403 ECOS-AUTH-011")
    void forgedServiceCredentialForbidden011() throws Exception {
        request.addHeader("X-ECOS-USER", "u-1");
        request.addHeader("X-ECOS-TENANT", "t-1");
        request.addHeader("X-ECOS-SERVICE", "workspace-service.12345.deadbeefdeadbeef");
        boolean ok = interceptor().preHandle(request, response, null);
        assertFalse(ok);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":403"));
    }

    /** tc5 */
    @Test
    @DisplayName("tc5 合法 sign SERVICE → preHandle=true")
    void validServiceCredentialPasses() throws Exception {
        request.addHeader("X-ECOS-USER", "u-1");
        request.addHeader("X-ECOS-TENANT", "t-1");
        request.addHeader("X-ECOS-SERVICE", new EcosServiceCredentialSigner(SERVICE_ID, SECRET).sign());
        assertTrue(interceptor().preHandle(request, response, null));
        assertEquals(200, response.getStatus());
    }

    /** tc6 */
    @Test
    @DisplayName("tc6 匿名路径 /api/health → preHandle=true")
    void anonymousPathSkipsChecks() throws Exception {
        request.setRequestURI("/api/health");
        assertTrue(interceptor().preHandle(request, response, null));
        assertEquals(200, response.getStatus());
    }
}
