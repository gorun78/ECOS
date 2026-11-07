package com.chinacreator.gzcm.engine.cognitive2.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor;

/**
 * B38/P1-12 契约门：认知共享 RestTemplate 的出站凭据中继。
 *
 * <p>三腿：①当前请求带头 ⇒ 逐字中继；②无请求上下文 ⇒ 不发明主语；③调用方已设 Authorization 不覆盖；
 * 另加装配断言（共享 bean 必须挂该拦截器，防回潮）。</p>
 */
class CognitiveCallerCredentialRelayInterceptorTest {

    private final CognitiveCallerCredentialRelayInterceptor interceptor =
            new CognitiveCallerCredentialRelayInterceptor();

    @BeforeEach
    void setUp() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer tok-cog");
        req.addHeader(HeaderAuthInterceptor.H_USER, "u-admin");
        req.addHeader(HeaderAuthInterceptor.H_TENANT, "tenant-a");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private HttpHeaders execute(HttpRequest request) throws Exception {
        ClientHttpRequestExecution execution = (req, body) ->
                new org.springframework.mock.http.client.MockClientHttpResponse(
                        new byte[0], org.springframework.http.HttpStatus.OK);
        interceptor.intercept(request, new byte[0], execution);
        return request.getHeaders();
    }

    private static HttpRequest requestWith(Map<String, String> preset) {
        return new HttpRequest() {
            private final HttpHeaders headers = new HttpHeaders();
            {
                preset.forEach(headers::set);
            }

            @Override
            public HttpHeaders getHeaders() { return headers; }

            @Override
            public URI getURI() { return URI.create("http://localhost:18082/api/v1/engine/data/x"); }

            @Override
            public HttpMethod getMethod() { return HttpMethod.GET; }
        };
    }

    @Test
    @DisplayName("当前请求带头 ⇒ Authorization 与信任头逐字中继")
    void relaysCallerCredentials() throws Exception {
        HttpHeaders out = execute(requestWith(Map.of()));
        assertEquals("Bearer tok-cog", out.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("u-admin", out.getFirst(HeaderAuthInterceptor.H_USER));
        assertEquals("tenant-a", out.getFirst(HeaderAuthInterceptor.H_TENANT));
    }

    @Test
    @DisplayName("无请求上下文 ⇒ 不发明主语")
    void noContextNoInventedCredentials() throws Exception {
        RequestContextHolder.resetRequestAttributes();
        HttpHeaders out = execute(requestWith(Map.of()));
        assertNull(out.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    @DisplayName("调用方已设 Authorization ⇒ 不覆盖")
    void doesNotOverrideExplicitAuthorization() throws Exception {
        HttpHeaders out = execute(requestWith(Map.of(HttpHeaders.AUTHORIZATION, "Bearer explicit")));
        assertEquals("Bearer explicit", out.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    @DisplayName("共享 cognitiveRestTemplate 必须装配该拦截器（防回潮）")
    void sharedTemplateHasInterceptor() {
        RestTemplate tpl = new CognitiveRestClientConfig().cognitiveRestTemplate();
        assertEquals(1, tpl.getInterceptors().size());
        assertEquals(CognitiveCallerCredentialRelayInterceptor.class,
                tpl.getInterceptors().get(0).getClass());
    }
}
