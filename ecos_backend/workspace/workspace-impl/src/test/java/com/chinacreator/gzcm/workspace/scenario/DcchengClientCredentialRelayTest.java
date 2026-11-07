package com.chinacreator.gzcm.workspace.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor;

/**
 * B32/P1-12 契约门：{@link DcchengClient} 出站 POST 必须中继调用方凭证。
 *
 * <p>实证背景：原两条 post/postJson 腿裸调（无 Authorization/信任头）⇒ gateway 按匿名
 * 拒绝 403 ⇒ 场景运行 DIAGNOSE/FORECAST/SIMULATE/STRATEGY 全部降级
 * （run_e810c6a7-804 的 diagnosis=「认知引擎暂不可用」即此腿）。</p>
 *
 * <p>判据：mock RestTemplate 捕获 HttpEntity 头，断言当前请求的 Bearer 与四枚信任头
 * 逐字中继；无请求上下文时不发明主语（头缺席，交由属主 fail-closed）。</p>
 */
class DcchengClientCredentialRelayTest {

    private final RestTemplate rest = mock(RestTemplate.class);
    private DcchengClient client;

    @BeforeEach
    void setUp() {
        client = new DcchengClient(rest);
        // 模拟「当前请求带全量凭证」的线程上下文：RequestContextHolder 由 Spring MVC 填充，
        // 单测无容器 ⇒ 直接挂一个带头的 mock request attributes。
        org.springframework.mock.web.MockHttpServletRequest req =
                new org.springframework.mock.web.MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer tok-1");
        req.addHeader(HeaderAuthInterceptor.H_USER, "u-admin");
        req.addHeader(HeaderAuthInterceptor.H_ROLE, "ROLE_SUPER_ADMIN");
        req.addHeader(HeaderAuthInterceptor.H_TENANT, "tenant-a");
        req.addHeader(HeaderAuthInterceptor.H_DOMAIN, "entmgmt");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(req));
    }

    @AfterEach
    void tearDown() {
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private HttpEntity<?> capturedEntity() {
        ArgumentCaptor<HttpEntity> cap = ArgumentCaptor.forClass(HttpEntity.class);
        verify(rest).postForObject(anyString(), cap.capture(), any(Class.class));
        return cap.getValue();
    }

    @Test
    @DisplayName("post()：Authorization 与四枚信任头逐字中继")
    void postRelaysCallerCredentials() {
        when(rest.postForObject(anyString(), any(HttpEntity.class), any(Class.class)))
                .thenReturn(Map.of("code", 0, "data", Map.of("ok", true)));
        client.diagnose(Map.of("metric", "毛利率"));
        HttpEntity<?> entity = capturedEntity();
        HttpHeaders headers = entity.getHeaders();
        assertEquals("Bearer tok-1", headers.getFirst("Authorization"));
        assertEquals("u-admin", headers.getFirst(HeaderAuthInterceptor.H_USER));
        assertEquals("ROLE_SUPER_ADMIN", headers.getFirst(HeaderAuthInterceptor.H_ROLE));
        assertEquals("tenant-a", headers.getFirst(HeaderAuthInterceptor.H_TENANT));
        assertEquals("entmgmt", headers.getFirst(HeaderAuthInterceptor.H_DOMAIN));
    }

    @Test
    @DisplayName("postJson()：中继同款头（反事实通道）")
    void postJsonRelaysCallerCredentials() {
        when(rest.postForObject(anyString(), any(HttpEntity.class), any(Class.class)))
                .thenReturn("{\"code\":0,\"data\":{}}");
        client.counterfactual(Map.of("domain", "business"));
        HttpEntity<?> entity = capturedEntity();
        assertNotNull(entity.getHeaders().getFirst("Authorization"));
        assertEquals("u-admin", entity.getHeaders().getFirst(HeaderAuthInterceptor.H_USER));
    }

    @Test
    @DisplayName("无请求上下文时不发明主语（头缺席 = fail-closed 交属主）")
    void noContextMeansNoInventedCredentials() {
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        when(rest.postForObject(anyString(), any(HttpEntity.class), any(Class.class)))
                .thenReturn(Map.of("code", 0));
        client.diagnose(Map.of("metric", "x"));
        HttpEntity<?> entity = capturedEntity();
        assertNull(entity.getHeaders().getFirst("Authorization"));
        assertNull(entity.getHeaders().getFirst(HeaderAuthInterceptor.H_USER));
    }

    @Test
    @DisplayName("类内无第二套取头实现（防复制体回潮，D-B4-61 口径）")
    void noDuplicateCredentialImpl() throws Exception {
        String src = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                "src/main/java/com/chinacreator/gzcm/workspace/scenario/DcchengClient.java")));
        assertEquals(0, src.split("getHeader\\(", -1).length - 1,
                "凭证只允许经 CallerCredentialSource 单源中继，类内出现 getHeader( 即复制体回潮");
        assertEquals(6, src.split("CallerCredentialSource\\.applyTo", -1).length - 1,
                "六条出站腿各中继一次（post/postJson/detect GET/unbind DELETE/active PUT/scenario-minds GET），多一次即出现第二套真值");
    }
}
