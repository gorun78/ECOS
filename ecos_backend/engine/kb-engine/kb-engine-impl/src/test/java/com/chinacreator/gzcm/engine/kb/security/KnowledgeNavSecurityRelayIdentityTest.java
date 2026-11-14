package com.chinacreator.gzcm.engine.kb.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor;
import com.chinacreator.gzcm.common.security.header.ServiceContext;

/**
 * D49（2026-11-13 验证批）—— kb 出站 ABAC 裁决请求必须携带调用方身份信任头。
 *
 * <p><b>缺陷形态（活体实测）</b>：经网关 {@code POST /api/v1/knowledge/retrieval/search}
 * 恒 503 {@code ECOS-KB-020}「security-engine 不可用」；security 侧日志回的却是
 * <b>403 {@code ECOS-AUTH-010}「缺少信任头」</b>。根因是
 * {@code EcosTrustHeaderFilter} 在链前无条件剥离 {@code X-ECOS-USER/ROLE/TENANT}（防伪造，正确），
 * 而 {@code CallerCredentialSource.current()} 读请求头，剥离后读不到 ⇒ 中继拦截器只发了
 * {@code Authorization} ⇒ security 入口按缺信任头拒绝 ⇒ kb 侧把 4xx 归一成 UNEVALUATED → 503。
 * 直连带齐三枚信任头调同一端点，裁决链路立刻通了，证明缺的是<b>身份传递</b>不是策略判定。</p>
 *
 * <p><b>本文件钉什么</b>：出站 {@code HttpEntity} 的头上必须出现与 {@link ServiceContext}
 * 一致的 {@code X-ECOS-USER/ROLE/TENANT}；且三条反向腿钉住「不伪造主语」——
 * 无上下文时不加信任头（交下游 fail-closed）、匿名主体不算身份、{@code anonymous/default/user}
 * 这些<b>裁决入参占位值</b>禁止写进信任头。</p>
 */
@DisplayName("D49 kb 出站裁决请求：身份信任头中继 + 不伪造主语")
class KnowledgeNavSecurityRelayIdentityTest {

    private static final String EVALUATE_PATH = "/api/v1/security/policy-engine/evaluate";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("有 ServiceContext 身份 ⇒ 出站带 X-ECOS-USER/ROLE/TENANT，且与裁决主体一致")
    void relaysIdentityFromServiceContext() {
        ServiceContext.set(ServiceContext.of("trace-1", "u-admin", "ROLE_SUPER_ADMIN,admin",
                "tenant-a", "control", "kb-engine"));

        HttpHeaders sent = captureOutboundHeaders();

        assertEquals("u-admin", sent.getFirst(HeaderAuthInterceptor.H_USER),
                "信任头必须带真实 userId（否则 security 入口 403 ECOS-AUTH-010）");
        assertEquals("ROLE_SUPER_ADMIN", sent.getFirst(HeaderAuthInterceptor.H_ROLE),
                "角色取首值，与裁决主体 input.subject.role 同源");
        assertEquals("tenant-a", sent.getFirst(HeaderAuthInterceptor.H_TENANT),
                "信任头必须带真实 tenantId");
        assertEquals("application/json", sent.getFirst(HttpHeaders.ACCEPT),
                "Accept 必须显式 JSON：XML 转换器会把应答序列化成 XML 导致误报引擎不可用");
    }

    @Test
    @DisplayName("无任何身份来源 ⇒ 不加信任头（禁把 anonymous/default/user 写进头）")
    void doesNotFabricateIdentity() {
        HttpHeaders sent = captureOutboundHeaders();

        assertNull(sent.getFirst(HeaderAuthInterceptor.H_USER),
                "无上下文时禁发明主语：裁决入参的回退值 anonymous 不得落成信任头");
        assertNull(sent.getFirst(HeaderAuthInterceptor.H_ROLE),
                "回退角色 user 不得落成信任头");
        assertNull(sent.getFirst(HeaderAuthInterceptor.H_TENANT),
                "回退租户 default 不得落成信任头");
    }

    @Test
    @DisplayName("只有匿名主体 ⇒ 仍不加信任头（匿名不是身份）")
    void anonymousAuthenticationIsNotIdentity() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        HttpHeaders sent = captureOutboundHeaders();

        assertNull(sent.getFirst(HeaderAuthInterceptor.H_USER),
                "AnonymousAuthenticationToken 不得被当成真实身份中继");
    }

    @Test
    @DisplayName("回退腿：无 ServiceContext 但有已认证主体 ⇒ 用 SecurityContext 身份（网关内嵌宿主形态）")
    void fallsBackToSecurityContextPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "u-admin", null, AuthorityUtils.createAuthorityList("ROLE_SUPER_ADMIN")));
        TenantContextHolder.setTenantId("tenant-a");

        HttpHeaders sent = captureOutboundHeaders();

        assertEquals("u-admin", sent.getFirst(HeaderAuthInterceptor.H_USER),
                "网关内嵌宿主里 ServiceContext 可能为空，必须能从 SecurityContext 取到身份");
        assertEquals("ROLE_SUPER_ADMIN", sent.getFirst(HeaderAuthInterceptor.H_ROLE));
        assertEquals("tenant-a", sent.getFirst(HeaderAuthInterceptor.H_TENANT));
    }

    // ── helpers ──

    /** 通过 mock RestTemplate 抓取出站 HttpEntity 的请求头。 */
    @SuppressWarnings("unchecked")
    private HttpHeaders captureOutboundHeaders() {
        RestTemplate rt = mock(RestTemplate.class);
        AtomicReference<HttpEntity<?>> captured = new AtomicReference<>();
        Map<String, Object> okBody = new LinkedHashMap<>();
        okBody.put("code", 0);
        okBody.put("data", Map.of("allow", true));
        when(rt.postForEntity(anyString(), any(), eq(Map.class))).thenAnswer(inv -> {
            captured.set(inv.getArgument(1, HttpEntity.class));
            return ResponseEntity.ok(okBody);
        });

        new KnowledgeNavSecurityEngineClient("http://localhost:18081", 100, rt, null)
                .evaluatePolicy("kb.retrieval.search", new LinkedHashMap<>());

        HttpEntity<?> entity = captured.get();
        assertTrue(entity != null, "预置：evaluatePolicy 必须真的发出一次 POST 到 " + EVALUATE_PATH);
        return entity.getHeaders();
    }
}
