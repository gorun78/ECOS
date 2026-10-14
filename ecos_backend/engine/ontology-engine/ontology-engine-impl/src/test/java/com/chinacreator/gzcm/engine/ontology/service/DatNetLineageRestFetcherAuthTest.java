package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-3 第二处断点（服务间凭证）回归测试：V3 血缘列供给的出向调用必须透传入站 JWT，
 * 并且两端 JSON 契约必须逐字段对得上。
 *
 * <p>为什么这不是"锦上添花"：datanet 面不在 {@code AnonymousEndpointRegistry} 的匿名清单里，
 * 网关 {@code SecurityConfig} 对未登记路径默认 {@code authenticated()}。
 * 修复前 fetcher 用裸 {@code getForEntity}（不带任何头）调用 ⇒ 对端 401 ⇒ 返回 null ⇒
 * 提案 execute 恒 503 fail-closed，即使服务端端点已实现也照样不通。</p>
 *
 * <p>测试桩 tableRef 用 {@code ecos_data.*} 而非 {@code ecos_dw.*}：
 * {@code BusinessDomainWriteArchTest#noEcosDwOutsideDataEngine}（C.3.6）禁止非 data-engine
 * 文件出现 {@code ecos_dw} 限定名，本文件属 ontology 侧，业务域表名只应作为运行时数据流入。</p>
 */
@ExtendWith(MockitoExtension.class)
class DatNetLineageRestFetcherAuthTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ServiceEndpointResolver endpointResolver;

    private static Map<String, Object> columnsBody(String... cols) {
        return Map.of("code", 0, "message", "ok", "data", Map.of(
                "tableRef", "ecos_data.stage_fact",
                "resolvedTable", "ecos_data.stage_fact",
                "exists", true,
                "columns", List.of(cols)));
    }

    private DatNetLineageGuard.RestLineageColumnFetcher fetcher(String authorization) {
        when(endpointResolver.resolve("datanet", ServiceEndpointResolver.RouteMode.MONOLITH))
                .thenReturn(Optional.of("http://localhost:8080"));
        return new DatNetLineageGuard.RestLineageColumnFetcher(
                endpointResolver, restTemplate, () -> authorization);
    }

    @Test
    @DisplayName("入站凭证存在 → 出向请求带同一 Authorization 头（服务间调用不再裸奔）")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void forwardsInboundAuthorizationHeader() {
        fetcher("Bearer jwt-abc");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(columnsBody("fact_id", "amount")));

        List<String> cols = new DatNetLineageGuard.RestLineageColumnFetcher(
                endpointResolver, restTemplate, () -> "Bearer jwt-abc")
                .columns("ecos_data.stage_fact");

        assertEquals(List.of("fact_id", "amount"), cols, "两端 JSON 契约对账：$.data.columns 必须解析成功");

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("http://localhost:8080/api/v1/datanet/metadata/columns?tableRef=ecos_data.stage_fact"),
                eq(HttpMethod.GET), entityCaptor.capture(), eq(Map.class));
        assertEquals("Bearer jwt-abc",
                entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    @DisplayName("无入站凭证（异步/定时线程）→ 不伪造凭证，缺 Authorization 头")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void doesNotFabricateAuthorizationWhenAbsent() {
        when(endpointResolver.resolve("datanet", ServiceEndpointResolver.RouteMode.MONOLITH))
                .thenReturn(Optional.of("http://localhost:8080"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(columnsBody("fact_id")));

        List<String> cols = new DatNetLineageGuard.RestLineageColumnFetcher(
                endpointResolver, restTemplate, () -> null).columns("ecos_data.stage_fact");

        assertEquals(List.of("fact_id"), cols);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), entityCaptor.capture(), eq(Map.class));
        assertFalse(entityCaptor.getValue().getHeaders().containsKey(HttpHeaders.AUTHORIZATION),
                "无入站凭证时不得凭空带上认证头");
    }

    @Test
    @DisplayName("非 HTTP 线程取不到请求上下文 → currentInboundAuthorization() 返回 null（fail-closed 前提）")
    void noRequestContextYieldsNullAuthorization() {
        assertNull(DatNetLineageGuard.RestLineageColumnFetcher.currentInboundAuthorization());
    }

    @Test
    @DisplayName("寻址失败（resolver 无 datanet 条目）→ null，且不发 HTTP")
    void unresolvedEndpointYieldsNullWithoutHttp() {
        when(endpointResolver.resolve("datanet", ServiceEndpointResolver.RouteMode.MONOLITH))
                .thenReturn(Optional.empty());

        assertNull(new DatNetLineageGuard.RestLineageColumnFetcher(
                endpointResolver, restTemplate, () -> "Bearer jwt-abc").columns("ecos_data.stage_fact"));

        verify(restTemplate, never())
                .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("对端 401（未认证）→ null = 依赖不可用，由 guard 折成 503 fail-closed")
    void unauthorizedYieldsNull() {
        when(endpointResolver.resolve("datanet", ServiceEndpointResolver.RouteMode.MONOLITH))
                .thenReturn(Optional.of("http://localhost:8080"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(HttpClientErrorException.create(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "Unauthorized",
                        new HttpHeaders(), null, null));

        List<String> cols = new DatNetLineageGuard.RestLineageColumnFetcher(
                endpointResolver, restTemplate, () -> null).columns("ecos_data.stage_fact");

        assertTrue(cols == null);
    }

    @Test
    @DisplayName("响应缺 $.data.columns（对端契约漂移）→ null，不得当成「表无列」放行")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void malformedBodyYieldsNullNotEmptyColumns() {
        when(endpointResolver.resolve("datanet", ServiceEndpointResolver.RouteMode.MONOLITH))
                .thenReturn(Optional.of("http://localhost:8080"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(Map.of("code", 0, "data", Map.of("fields", List.of()))));

        assertNull(new DatNetLineageGuard.RestLineageColumnFetcher(
                endpointResolver, restTemplate, () -> null).columns("ecos_data.stage_fact"));
    }
}
