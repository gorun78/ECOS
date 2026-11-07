package com.chinacreator.gzcm.engine.cognitive2.service.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.engine.cognitive.api.context.ContextBuildRequest;
import com.chinacreator.gzcm.engine.cognitive.api.context.ContextSourceRef;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;

/**
 * B39/P1-9 契约门：Context 主体验级授权裁剪器（不依赖 Hamcrest / MockRestServiceServer）。
 *
 * <p>六腿：①allow 无隐藏列 ⇒ allowAll；②allow 有 blockedColumns ⇒ allowWithHidden；
 * ③403 ⇒ DENY；④5xx ⇒ 抛异常（上层 fail-closed）；⑤无已认证主体 ⇒ DENY；
 * ⑥未登记来源类型 ⇒ DENY。</p>
 */
class SecurityEngineContextTrimmerTest {

    private static final String BASE = "http://svc.test";

    @SuppressWarnings("unchecked")
    private final RestTemplate rest = mock(RestTemplate.class);
    private final ServiceEndpointResolver resolver = mock(ServiceEndpointResolver.class);
    private SecurityEngineContextTrimmer trimmer;

    @BeforeEach
    void setUp() {
        when(resolver.resolve(anyString(), any())).thenReturn(Optional.of(BASE));
        trimmer = new SecurityEngineContextTrimmer(rest, resolver);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u-admin", "n/a",
                        AuthorityUtils.createAuthorityList("ROLE_SUPER_ADMIN")));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static ContextSourceRef asset() {
        ContextSourceRef ref = new ContextSourceRef();
        ref.setSourceType("DATA_ASSET");
        ref.setSourceId("asset_x");
        return ref;
    }

    @SuppressWarnings("unchecked")
    private void stubDecision(Object columns) {
        Map<String, Object> rls = new LinkedHashMap<>();
        rls.put("whereClause", "(1=1)");
        rls.put("noPolicyMatched", false);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rls", rls);
        data.put("columns", columns);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", 0);
        payload.put("data", data);
        when(rest.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(payload, HttpStatus.OK));
    }

    @Test
    @DisplayName("裁决 allow 且无隐藏列 ⇒ allowAll")
    void allowsWhenDecisionAllows() {
        stubDecision(Map.of("mode", "allow", "blockedColumns", List.of()));
        ContextSecurityTrimmer.TrimDecision d = trimmer.trim(new ContextBuildRequest(), asset(), null);
        assertTrue(d.isPermitted());
        assertTrue(d.isAllVisible());
    }

    @Test
    @DisplayName("RLS 非平凡谓词（不可下推）⇒ DENY")
    void deniesWhenRlsPredicateNotTrivial() {
        Map<String, Object> rls = new LinkedHashMap<>();
        rls.put("whereClause", "department_id = :userDeptId");
        rls.put("noPolicyMatched", false);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rls", rls);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", 0);
        payload.put("data", data);
        when(rest.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(payload, HttpStatus.OK));
        assertFalse(trimmer.trim(new ContextBuildRequest(), asset(), null).isPermitted());
    }

    @Test
    @DisplayName("裁决 allow 但存在隐藏列 ⇒ allowWithHidden")
    void allowsWithHiddenColumns() {
        stubDecision(Map.of("mode", "allow", "blockedColumns", List.of("business_desc")));
        ContextSecurityTrimmer.TrimDecision d = trimmer.trim(new ContextBuildRequest(), asset(), null);
        assertTrue(d.isPermitted());
        assertFalse(d.isAllVisible());
        assertEquals(List.of("business_desc"), d.getHiddenItems());
    }

    @Test
    @DisplayName("裁决 403 ⇒ DENY（fail-closed）")
    void deniesOnForbidden() {
        when(rest.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", null, null, null));
        assertFalse(trimmer.trim(new ContextBuildRequest(), asset(), null).isPermitted());
    }

    @Test
    @DisplayName("裁决 503 ⇒ 抛异常（上层按不可用 fail-closed 处置）")
    void throwsOnServerError() {
        when(rest.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "down", null, null, null));
        assertThrows(IllegalStateException.class,
                () -> trimmer.trim(new ContextBuildRequest(), asset(), null));
    }

    @Test
    @DisplayName("无已认证主体 ⇒ DENY（不发明主语）")
    void deniesWithoutAuthenticatedSubject() {
        SecurityContextHolder.clearContext();
        assertFalse(trimmer.trim(new ContextBuildRequest(), asset(), null).isPermitted());
    }

    @Test
    @DisplayName("未登记来源类型 ⇒ DENY（禁当无敏感数据放行）")
    void deniesUnknownSourceType() {
        ContextSourceRef ref = new ContextSourceRef();
        ref.setSourceType("CAPABILITY");
        ref.setSourceId("cap_x");
        assertFalse(trimmer.trim(new ContextBuildRequest(), ref, null).isPermitted());
    }
}
