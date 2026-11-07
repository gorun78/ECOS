package com.chinacreator.gzcm.engine.kb.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor;

/**
 * B33/P1-12 契约门：kb 共享 RestTemplate 的凭据中继拦截器。
 *
 * <p>判据三腿（与 CallerCredentialSource 单源纪律一致）：
 * ①当前请求带头 ⇒ 出站逐字中继；②无请求上下文 ⇒ 不发明主语；③调用方已显式设置
 * Authorization ⇒ 不覆盖。</p>
 */
class CallerCredentialRelayInterceptorTest {

    private final CallerCredentialRelayInterceptor interceptor = new CallerCredentialRelayInterceptor();

    @BeforeEach
    void setUp() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer tok-kb");
        req.addHeader(HeaderAuthInterceptor.H_USER, "u-admin");
        req.addHeader(HeaderAuthInterceptor.H_TENANT, "tenant-a");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private HttpHeaders execute(HttpRequest request) throws Exception {
        ClientHttpRequestExecution execution = (req, body) -> new org.springframework.mock.http.client.MockClientHttpResponse(
                new byte[0], org.springframework.http.HttpStatus.OK);
        interceptor.intercept(request, new byte[0], execution);
        return request.getHeaders();
    }

    private static HttpRequest requestWith(URI uri, Map<String, String> preset) {
        return new HttpRequest() {
            private final HttpHeaders headers = new HttpHeaders();
            {
                preset.forEach(headers::set);
            }

            @Override
            public HttpHeaders getHeaders() { return headers; }

            @Override
            public URI getURI() { return uri; }

            @Override
            public org.springframework.http.HttpMethod getMethod() {
                return org.springframework.http.HttpMethod.GET;
            }
        };
    }

    @Test
    @DisplayName("当前请求带头 ⇒ Authorization 与信任头逐字中继到出站请求")
    void relaysCallerCredentials() throws Exception {
        HttpHeaders out = execute(requestWith(URI.create("http://localhost:18082/api/v1/engine/data/layers/CURATED"), Map.of()));
        assertEquals("Bearer tok-kb", out.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("u-admin", out.getFirst(HeaderAuthInterceptor.H_USER));
        assertEquals("tenant-a", out.getFirst(HeaderAuthInterceptor.H_TENANT));
    }

    @Test
    @DisplayName("无请求上下文 ⇒ 不发明主语（不加任何头，属主 fail-closed）")
    void noContextNoInventedCredentials() throws Exception {
        RequestContextHolder.resetRequestAttributes();
        HttpHeaders out = execute(requestWith(URI.create("http://localhost/x"), Map.of()));
        assertNull(out.getFirst(HttpHeaders.AUTHORIZATION));
        assertNull(out.getFirst(HeaderAuthInterceptor.H_USER));
    }

    @Test
    @DisplayName("调用方已显式设置 Authorization ⇒ 不覆盖")
    void doesNotOverrideExplicitAuthorization() throws Exception {
        HttpHeaders out = execute(requestWith(URI.create("http://localhost/x"),
                Map.of(HttpHeaders.AUTHORIZATION, "Bearer explicit")));
        assertEquals("Bearer explicit", out.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    @DisplayName("共享模板必须装配拦截器（防回潮：bean 无拦截器即整族裸调回潮）")
    void restTemplateBeanHasInterceptor() {
        RestTemplate tpl = new KbEngineRestConfig().restTemplate();
        List<org.springframework.http.client.ClientHttpRequestInterceptor> interceptors = tpl.getInterceptors();
        assertTrue(interceptors.stream().anyMatch(CallerCredentialRelayInterceptor.class::isInstance),
                "KbEngineRestConfig#restTemplate 必须挂 CallerCredentialRelayInterceptor");
    }
}
