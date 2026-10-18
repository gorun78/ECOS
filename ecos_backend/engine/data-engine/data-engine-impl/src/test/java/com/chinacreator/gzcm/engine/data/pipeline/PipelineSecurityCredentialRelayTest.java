package com.chinacreator.gzcm.engine.data.pipeline;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PipelineSecurityCredentialRelayTest — policy-engine 出站调用的 ADR-7 信任链凭证中继。
 *
 * <p>锁住两件事（缺一即回归）：
 * <ol>
 *   <li><b>plumbing 缺口已修</b>：入口请求带凭证 ⇒ 出站逐字中继，不再让已登录用户被
 *       gateway 自家 Spring Security 判 403 后折叠成 {@code SECURITY_ENGINE_UNAVAILABLE}
 *       （卷07 F07-17 同型判例，2026-10-16 live 实证）。</li>
 *   <li><b>fail-closed 语义没被改松</b>：无入口请求上下文 / 空白头 ⇒ 不伪造凭证，
 *       被调方不可用时仍默认 DENY。</li>
 * </ol>
 *
 * <p>RestTemplate mock + {@link Proxy} 假 {@code HttpServletRequest}（本模块测试面没有
 * spring-test，见 {@link PipelineSecurityIntegrationTest} 同源约束），不触网、不触 PG。
 */
@ExtendWith(MockitoExtension.class)
class PipelineSecurityCredentialRelayTest {

    private static final String EVALUATE_URL =
            "http://test:8080/api/v1/security/policy-engine/evaluate";

    @Mock
    private RestTemplate restTemplate;

    private PipelineSecurityService security;

    @BeforeEach
    void setUp() throws Exception {
        security = new PipelineSecurityService();
        setField(security, "securityBaseUrl", "http://test:8080");
        setField(security, "restTemplate", restTemplate);
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("入口带 Authorization + 信任头 — 出站逐字中继且不升权")
    void relaysCallerCredentialsFromRequestContext() {
        Map<String, String> incoming = new LinkedHashMap<>();
        incoming.put("Authorization", "Bearer caller-jwt");
        incoming.put("X-ECOS-USER", "u-1");
        incoming.put("X-ECOS-ROLE", "ROLE_A,ROLE_B");
        incoming.put("X-ECOS-TENANT", "t-1");
        bindRequest(incoming);
        stubAllow();

        PipelineSecurityService.AllowedResult r = security.evaluateExecute("p-relay");

        assertTrue(r.allowed());
        HttpHeaders out = capturedHeaders();
        assertEquals("Bearer caller-jwt", out.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("u-1", out.getFirst("X-ECOS-USER"));
        assertEquals("ROLE_A,ROLE_B", out.getFirst("X-ECOS-ROLE"));
        assertEquals("t-1", out.getFirst("X-ECOS-TENANT"));
        assertNull(out.getFirst("X-ECOS-DOMAIN"), "入口未带的信任头禁凭空补");
        assertEquals(MediaType.APPLICATION_JSON, out.getContentType());
        assertEquals(java.util.List.of(MediaType.APPLICATION_JSON), out.getAccept(),
                "Accept 必须钉死 JSON：默认值含 application/xml，而 jackson-dataformat-xml 在族内 ⇒ "
                        + "内容协商返回 XML 会被解析成异常并误报 SECURITY_ENGINE_UNAVAILABLE");
    }

    @Test
    @DisplayName("无入口请求上下文（异步/定时线程）— 不伪造凭证，security 不可用仍默认 DENY")
    void noRequestContextMeansNoFabricatedCredentials() {
        RequestContextHolder.resetRequestAttributes();
        doThrow(new ResourceAccessException("refused"))
                .when(restTemplate).postForEntity(anyString(), org.mockito.ArgumentMatchers.any(),
                        eq(String.class));

        PipelineSecurityService.AllowedResult r = security.evaluateExecute("p-async");

        assertFalse(r.allowed());
        assertEquals("SECURITY_ENGINE_UNAVAILABLE", r.reason());
        HttpHeaders out = capturedHeaders();
        assertNull(out.getFirst(HttpHeaders.AUTHORIZATION), "无上下文禁伪造凭证（fail-closed 不变）");
        assertNull(out.getFirst("X-ECOS-USER"));
    }

    @Test
    @DisplayName("空白 Authorization 头 — 视同缺失，不中继空串")
    void blankAuthorizationIsNotRelayed() {
        Map<String, String> incoming = new LinkedHashMap<>();
        incoming.put("Authorization", "   ");
        incoming.put("X-ECOS-USER", "u-2");
        bindRequest(incoming);
        stubAllow();

        security.evaluateDataGovernanceApply("c-1");

        HttpHeaders out = capturedHeaders();
        assertNull(out.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("u-2", out.getFirst("X-ECOS-USER"));
    }

    @Test
    @DisplayName("通用裁决入口 evaluate(action, resource, role) — 同一中继咽喉")
    void genericEvaluateAlsoRelays() {
        Map<String, String> incoming = new LinkedHashMap<>();
        incoming.put("Authorization", "Bearer caller-jwt");
        bindRequest(incoming);
        stubAllow();

        security.evaluate("data.period.close", "ecos_data_period_status:2026-01", "ROLE_FIN");

        assertEquals("Bearer caller-jwt", capturedHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    // ==================== helpers ====================

    private void stubAllow() {
        when(restTemplate.postForEntity(anyString(), org.mockito.ArgumentMatchers.any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"data\":{\"allow\":true}}", HttpStatus.OK));
    }

    @SuppressWarnings("rawtypes")
    private HttpHeaders capturedHeaders() {
        ArgumentCaptor<HttpEntity> cap = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq(EVALUATE_URL), cap.capture(), eq(String.class));
        return cap.getValue().getHeaders();
    }

    /** 绑定一个只实现 {@code getHeader} 的假请求；头名按 Servlet 规范大小写不敏感。 */
    private static void bindRequest(Map<String, String> headers) {
        Object request = Proxy.newProxyInstance(
                PipelineSecurityCredentialRelayTest.class.getClassLoader(),
                new Class<?>[]{jakarta.servlet.http.HttpServletRequest.class},
                (proxy, method, args) -> {
                    if ("getHeader".equals(method.getName())) {
                        String name = ((String) args[0]).toLowerCase(Locale.ROOT);
                        return headers.entrySet().stream()
                                .filter(e -> e.getKey().toLowerCase(Locale.ROOT).equals(name))
                                .map(Map.Entry::getValue)
                                .findFirst()
                                .orElse(null);
                    }
                    return switch (method.getName()) {
                        case "toString" -> "fake-request";
                        case "hashCode" -> 0;
                        case "equals" -> proxy == args[0];
                        default -> null;
                    };
                });
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes((jakarta.servlet.http.HttpServletRequest) request));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
