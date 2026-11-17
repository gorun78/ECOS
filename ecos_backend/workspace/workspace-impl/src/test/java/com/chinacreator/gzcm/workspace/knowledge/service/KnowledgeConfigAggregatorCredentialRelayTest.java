package com.chinacreator.gzcm.workspace.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import com.chinacreator.gzcm.workspace.knowledge.dto.KnowledgeEngineConfigVO;

/**
 * D150 契约门：{@link KnowledgeConfigAggregator} 读写两条腿必须中继调用方凭证。
 *
 * <p>实证背景（2026-11-16 live）：知识工作台「引擎配置」三 scope 全部降级
 * {@code {"_remote_failed":"Forbidden: 403 ...<path>/api/v1/knowledge/settings</path>"}}，
 * 因为读面用 {@code HttpEntity.EMPTY} 裸调、写面只设 Content-Type ⇒ gateway 按匿名 403。
 * 降级语义把 403 折叠成一条配置项，前端「只校验 200 + count 为数值」的用例因此假绿。</p>
 */
class KnowledgeConfigAggregatorCredentialRelayTest {

    private final RestTemplate kbRest = mock(RestTemplate.class);
    private final RestTemplate cognitiveRest = mock(RestTemplate.class);
    private final WorkspaceDataDao dao = mock(WorkspaceDataDao.class);
    private KnowledgeConfigAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new KnowledgeConfigAggregator(dao, kbRest, cognitiveRest);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer tok-150");
        req.addHeader(HeaderAuthInterceptor.H_USER, "u-admin");
        req.addHeader(HeaderAuthInterceptor.H_ROLE, "ROLE_SUPER_ADMIN");
        req.addHeader(HeaderAuthInterceptor.H_TENANT, "tenant-a");
        req.addHeader(HeaderAuthInterceptor.H_DOMAIN, "entmgmt");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private HttpEntity<?> capturedEntity(RestTemplate rest, HttpMethod method) {
        ArgumentCaptor<HttpEntity> cap = ArgumentCaptor.forClass(HttpEntity.class);
        verify(rest).exchange(anyString(), org.mockito.ArgumentMatchers.eq(method), cap.capture(),
                any(Class.class));
        return cap.getValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubOk(RestTemplate rest, Object data) {
        when(rest.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(Map.of("code", 0, "data", data),
                        org.springframework.http.HttpStatus.OK));
    }

    @Test
    @DisplayName("knowledge 读面：Authorization 与四枚信任头逐字中继（403 降级根因）")
    void readKnowledgeRelaysCallerCredentials() {
        stubOk(kbRest, List.of(Map.of("config_key", "extract.max_pages", "config_value", "200")));
        KnowledgeEngineConfigVO vo = aggregator.read("knowledge");

        HttpHeaders headers = capturedEntity(kbRest, HttpMethod.GET).getHeaders();
        assertEquals("Bearer tok-150", headers.getFirst(HttpHeaders.AUTHORIZATION),
                "读面丢 Authorization ⇒ 网关按匿名 403，聚合器再把它折叠成 _remote_failed（D150 复现）");
        assertEquals("u-admin", headers.getFirst(HeaderAuthInterceptor.H_USER));
        assertEquals("ROLE_SUPER_ADMIN", headers.getFirst(HeaderAuthInterceptor.H_ROLE));
        assertEquals("tenant-a", headers.getFirst(HeaderAuthInterceptor.H_TENANT));
        assertEquals("entmgmt", headers.getFirst(HeaderAuthInterceptor.H_DOMAIN));

        assertFalse(vo.getConfig().containsKey("_remote_failed"), "中继后不得再降级: " + vo.getConfig());
        assertEquals("200", vo.getConfig().get("extract.max_pages"));
    }

    @Test
    @DisplayName("cognitive 读面：同款头中继（aiming 侧 ECOS-AUTH-010 的另一条腿）")
    void readCognitiveRelaysCallerCredentials() {
        stubOk(cognitiveRest, List.of(Map.of("config_key", "ecos.cognitive.enabled", "config_value", "true")));
        aggregator.read("cognitive");

        HttpHeaders headers = capturedEntity(cognitiveRest, HttpMethod.GET).getHeaders();
        assertEquals("Bearer tok-150", headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("tenant-a", headers.getFirst(HeaderAuthInterceptor.H_TENANT));
        assertTrue(headers.getFirst(HeaderAuthInterceptor.H_USER) != null);
    }

    @Test
    @DisplayName("写面 PUT：同一份凭证中继（原 500「PUT failed」根因）")
    void updateRelaysCallerCredentials() {
        when(kbRest.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                .thenReturn(new org.springframework.http.ResponseEntity<>(
                        Map.of("code", 0, "data", Map.of("updated", 1)),
                        org.springframework.http.HttpStatus.OK));

        int updated = aggregator.update("knowledge", Map.of("extract.max_pages", "201"));

        HttpEntity<?> entity = capturedEntity(kbRest, HttpMethod.PUT);
        assertEquals("Bearer tok-150", entity.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("u-admin", entity.getHeaders().getFirst(HeaderAuthInterceptor.H_USER));
        assertEquals("tenant-a", entity.getHeaders().getFirst(HeaderAuthInterceptor.H_TENANT));
        assertEquals(1, updated);
    }

    @Test
    @DisplayName("无请求上下文时不发明主语（fail-closed 交属主，不是伪造 admin）")
    void noRequestContextMeansNoInventedCredentials() {
        RequestContextHolder.resetRequestAttributes();
        stubOk(kbRest, List.of(Map.of("config_key", "k", "config_value", "v")));
        aggregator.read("knowledge");

        HttpHeaders headers = capturedEntity(kbRest, HttpMethod.GET).getHeaders();
        assertNull(headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertNull(headers.getFirst(HeaderAuthInterceptor.H_USER));
        assertFalse(headers.containsKey(HeaderAuthInterceptor.H_TENANT));
    }

    @Test
    @DisplayName("类内无第二套取头实现（D-B4-61 口径：只经单源中继）")
    void noDuplicateCredentialImplementation() throws Exception {
        String src = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                "src/main/java/com/chinacreator/gzcm/workspace/knowledge/service/KnowledgeConfigAggregator.java")));
        assertEquals(0, src.split("getHeader\\(", -1).length - 1,
                "凭证只允许经单源中继，类内出现 getHeader( 即复制体回潮");
        assertEquals(2, src.split("CallerCredentialRelay\\.applyTo", -1).length - 1,
                "读写两条腿各中继一次（read 的 remoteGetConfigRows 与 update 的 headers），"
                        + "多一次即出现第二套真值");
    }
}
