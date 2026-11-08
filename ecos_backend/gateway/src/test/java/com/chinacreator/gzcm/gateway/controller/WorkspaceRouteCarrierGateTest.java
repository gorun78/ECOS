package com.chinacreator.gzcm.gateway.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.gateway.GatewayApplication;
import com.chinacreator.gzcm.gateway.filter.VersionPrefixRewriteFilter;

/**
 * P0-1 承流面门禁（2026-11-07 验证报告 A-1）。
 *
 * <p>钉住四件事，任一漂移即红：</p>
 * <ol>
 *   <li>转发控制器覆盖 {@code /api/v1/workspace/**} 正典与去 v1 兼容形态；</li>
 *   <li>gateway 仍整包排除 workspace.controller（转发存在的前提，防有人「顺手」扫进来复活 Bean 冲突）；</li>
 *   <li>版本前缀重写表不得出现 workspace 条目（否则正典路径会被改写走空）；</li>
 *   <li>下游失败必须 503 + 机读码，禁折叠成 200/404；成功时状态/体原样透传。</li>
 * </ol>
 */
class WorkspaceRouteCarrierGateTest {

    @Test
    void forwardMappingCarriesBothWorkspaceForms() throws Exception {
        Method forward = WorkspaceForwardController.class.getMethod(
                "forward", jakarta.servlet.http.HttpServletRequest.class,
                jakarta.servlet.http.HttpServletResponse.class);
        String[] mappings = forward.getAnnotation(RequestMapping.class).value();
        assertTrue(Arrays.asList(mappings).contains("/api/v1/workspace/**"),
                "正典承流缺失: " + Arrays.toString(mappings));
        assertTrue(Arrays.asList(mappings).contains("/api/workspace/**"),
                "兼容承流缺失: " + Arrays.toString(mappings));
    }

    @Test
    void gatewayStillExcludesWorkspaceControllers() throws Exception {
        org.springframework.context.annotation.ComponentScan scan =
                GatewayApplication.class.getAnnotation(org.springframework.context.annotation.ComponentScan.class);
        org.springframework.context.annotation.ComponentScan.Filter[] excludes = scan.excludeFilters();
        boolean hasController = false;
        boolean hasTwin = false;
        for (org.springframework.context.annotation.ComponentScan.Filter filter : excludes) {
            if (filter.pattern().length > 0 && filter.pattern()[0].contains("workspace")
                    && filter.pattern()[0].contains("controller")) {
                hasController = true;
            }
            if (filter.pattern().length > 0 && filter.pattern()[0].contains("workspace")
                    && filter.pattern()[0].contains("twin")) {
                hasTwin = true;
            }
        }
        assertTrue(hasController, "gateway 不得重扫 workspace.controller（会复活同名 Bean 冲突）");
        assertTrue(hasTwin, "gateway 不得重扫 workspace.twin");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rewriteFilterHasNoWorkspaceEntry() throws Exception {
        Field field = VersionPrefixRewriteFilter.class.getDeclaredField("V1_REWRITE_MAP");
        field.setAccessible(true);
        Map<String, String> map = (Map<String, String>) field.get(null);
        for (String key : map.keySet()) {
            assertFalse(key.startsWith("/api/v1/workspace/"),
                    "重写表出现 workspace 条目会绕开承流: " + key);
        }
    }

    @Test
    void defaultBaseUrlTargetsWorkspaceService() throws Exception {
        Constructor<?> constructor = WorkspaceForwardController.class.getConstructor(RestTemplate.class, String.class);
        Value value = constructor.getParameters()[1].getAnnotation(Value.class);
        assertTrue(value != null && value.value().contains("18090"),
                "默认下游必须是 workspace :18090，实得: " + (value == null ? null : value.value()));
    }

    @Test
    void upstreamSuccessPassesStatusAndBody() throws Exception {
        RestTemplate fake = new RestTemplate() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> ResponseEntity<T> exchange(URI url, HttpMethod method,
                                                  HttpEntity<?> requestEntity, Class<T> responseType) {
                HttpHeaders headers = new HttpHeaders();
                headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
                return (ResponseEntity<T>) ResponseEntity.ok().headers(headers)
                        .body("{\"data\":[{\"id\":\"sc_ent2_group_month\"}]}".getBytes());
            }
        };
        WorkspaceForwardController controller =
                new WorkspaceForwardController(fake, "http://127.0.0.1:18090");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/workspace/scenarios");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.forward(request, response);

        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("sc_ent2_group_month"));
    }

    @Test
    void forwardReinjectsAuthenticatedIdentityHeaders() throws Exception {
        java.util.List<HttpHeaders> captured = new java.util.ArrayList<>();
        RestTemplate fake = new RestTemplate() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> ResponseEntity<T> exchange(URI url, HttpMethod method,
                                                  HttpEntity<?> requestEntity, Class<T> responseType) {
                captured.add(requestEntity.getHeaders());
                return (ResponseEntity<T>) ResponseEntity.ok(new byte[0]);
            }
        };
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "u-admin", null,
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_SUPER_ADMIN"))));
        com.chinacreator.gzcm.common.context.TenantContextHolder.setTenantId("tenant-a");
        try {
            WorkspaceForwardController controller =
                    new WorkspaceForwardController(fake, "http://127.0.0.1:18090");
            controller.forward(new MockHttpServletRequest("GET", "/api/v1/workspace/scenarios"),
                    new MockHttpServletResponse());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            com.chinacreator.gzcm.common.context.TenantContextHolder.clear();
        }

        HttpHeaders headers = captured.get(0);
        assertEquals("u-admin", headers.getFirst("X-ECOS-USER"),
                "转发必须重注入已认证用户（EcosTrustHeaderFilter 已剥离客户端值）");
        assertEquals("tenant-a", headers.getFirst("X-ECOS-TENANT"));
        assertEquals("ROLE_SUPER_ADMIN", headers.getFirst("X-ECOS-ROLE"));
    }

    @Test
    void upstreamErrorStatusIsPassedThroughNotCollapsedTo503() throws Exception {
        RestTemplate fake = new RestTemplate() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> ResponseEntity<T> exchange(URI url, HttpMethod method,
                                                  HttpEntity<?> requestEntity, Class<T> responseType) {
                throw org.springframework.web.client.HttpClientErrorException.create(
                        org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed",
                        HttpHeaders.EMPTY, "{\"code\":405}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        java.nio.charset.StandardCharsets.UTF_8);
            }
        };
        WorkspaceForwardController controller =
                new WorkspaceForwardController(fake, "http://127.0.0.1:18090");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.forward(new MockHttpServletRequest("POST", "/api/v1/workspace/scenarios/x/pre-validate"),
                response);

        assertEquals(405, response.getStatus(), "上游 4xx 必须原样透传，禁折叠成 503");
        assertTrue(response.getContentAsString().contains("405"));
    }

    @Test
    void upstreamFailureIs503WithMachineCode() throws Exception {
        RestTemplate fake = new RestTemplate() {
            @Override
            public <T> ResponseEntity<T> exchange(URI url, HttpMethod method,
                                                  HttpEntity<?> requestEntity, Class<T> responseType) {
                throw new ResourceAccessException("connect timed out");
            }
        };
        WorkspaceForwardController controller =
                new WorkspaceForwardController(fake, "http://127.0.0.1:18090");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/workspace/scenarios");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.forward(request, response);

        assertEquals(503, response.getStatus(), "下游不可用必须 503，禁 404/200");
        assertTrue(response.getContentAsString().contains(
                WorkspaceForwardController.CODE_UPSTREAM_UNAVAILABLE));
    }
}
