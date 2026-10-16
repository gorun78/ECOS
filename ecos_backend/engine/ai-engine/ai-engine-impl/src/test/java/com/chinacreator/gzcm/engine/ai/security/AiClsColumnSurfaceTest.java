package com.chinacreator.gzcm.engine.ai.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * #142/B4-3d-1（D-B4-25）—— AI 通道 CLS 列面消费具名用例。
 *
 * <p><b>缺陷本体</b>：{@code AiSecurityEngineClient#allowedColumns} 只读
 * {@code data.allowedColumns}，而 CLS 生产者写的是 {@code visibleColumns} ⇒
 * AI 通道（含 OAG {@code SecurityCheckerNode} 与工具 SQL 出口）的列裁剪结构性恒空集。
 * 与 workspace/ontology 同族：既有 AI 单测桩的是 client 方法返回值，看不见跨模块键名漂移。</p>
 *
 * <p>桩下沉到 {@link RestTemplate} 后，
 * {@link #producerShapedResponseNowReturnsReadableColumns()} 为"能变红"判据；
 * {@link #transportFailureFailsClosed()} 钉现状（AI 侧把传输异常折成空集＝fail-closed，
 * 与 §4.5.4 的分态要求有差，差异随 D-B4-25 附注登记，本批不改 AI 侧对外异常形态）。</p>
 */
@DisplayName("AI 通道 CLS：按生产者形态取回可见列 + 漂移/拒绝 fail-closed")
class AiClsColumnSurfaceTest {

    private static final String USER = "u-42";
    private static final String TENANT = "t-default";
    private static final String ENTITY = "sales_order";
    private static final List<String> ALL_COLUMNS =
            Arrays.asList("id", "customer", "amount", "salary", "id_card");

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static AiSecurityEngineClient clientWith(RestTemplate stub) {
        AiSecurityEngineClient client = new AiSecurityEngineClient(
                "http://127.0.0.1:18081", 200, mock(ObjectProvider.class));
        ReflectionTestUtils.setField(client, "restTemplate", stub);
        return client;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static AiSecurityEngineClient clientWithData(Object dataSegment) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", 200);
        if (dataSegment != null) envelope.put("data", dataSegment);
        RestTemplate stub = mock(RestTemplate.class);
        when(stub.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(envelope));
        return clientWith(stub);
    }

    private static AiSecurityEngineClient throwingClient(RuntimeException failure) {
        RestTemplate stub = mock(RestTemplate.class);
        when(stub.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(failure);
        return clientWith(stub);
    }

    private static Map<String, Object> producerData(List<String> visible, List<String> blocked) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tableName", ENTITY);
        data.put("visibleColumns", visible);
        data.put("blockedColumns", blocked);
        return data;
    }

    @Test
    @DisplayName("生产者应答（visibleColumns）→ 返回可见列（旧实现在此返空集＝AI 通道恒 DENY）")
    void producerShapedResponseNowReturnsReadableColumns() {
        AiSecurityEngineClient client =
                clientWithData(producerData(List.of("id", "amount"), List.of("salary")));

        List<String> visible = client.allowedColumns(USER, TENANT, ENTITY, ALL_COLUMNS);

        assertEquals(List.of("id", "amount"), visible,
                "D-B4-25 判据：读侧必须认生产者的 visibleColumns");
    }

    @Test
    @DisplayName("visibleColumns 为空表＝策略拒绝 → 空集")
    void emptyVisibleSurfaceMeansPolicyDeny() {
        AiSecurityEngineClient client =
                clientWithData(producerData(List.of(), Arrays.asList("salary", "id_card")));

        assertTrue(client.allowedColumns(USER, TENANT, ENTITY, ALL_COLUMNS).isEmpty());
    }

    @Test
    @DisplayName("应答两键皆不可读＝形态漂移 → 空集（读数位退回 allowedColumns 也在此变红）")
    void driftedColumnSurfaceFailsClosed() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tableName", ENTITY);
        data.put("columnWhitelist", List.of("id", "amount"));

        assertTrue(clientWithData(data).allowedColumns(USER, TENANT, ENTITY, ALL_COLUMNS).isEmpty());
    }

    @Test
    @DisplayName("无 data 段 / data 非 Map → 空集（外层形态漂移 fail-closed）")
    void malformedResponseFailsClosed() {
        assertTrue(clientWithData(null).allowedColumns(USER, TENANT, ENTITY, ALL_COLUMNS).isEmpty());
        assertTrue(clientWithData(Boolean.TRUE)
                .allowedColumns(USER, TENANT, ENTITY, ALL_COLUMNS).isEmpty());
    }

    @Test
    @DisplayName("仅 legacy 别名在位 → 仍可读；权威键与别名同在时权威键胜出")
    void legacyAliasIsFallbackOnly() {
        assertEquals(List.of("id", "customer"),
                clientWithData(Map.of("allowedColumns", List.of("id", "customer")))
                        .allowedColumns(USER, TENANT, ENTITY, ALL_COLUMNS));

        Map<String, Object> both = new LinkedHashMap<>();
        both.put("visibleColumns", List.of("id"));
        both.put("allowedColumns", ALL_COLUMNS);
        assertEquals(List.of("id"),
                clientWithData(both).allowedColumns(USER, TENANT, ENTITY, ALL_COLUMNS),
                "别名不得覆盖权威键");
    }

    @Test
    @DisplayName("传输异常 → 空集（钉现状：AI 侧不抛 503，但绝不放行全列）")
    void transportFailureFailsClosed() {
        AiSecurityEngineClient client = throwingClient(new ResourceAccessException("connect refused"));

        // 钉现状：AI 侧 allowedColumns 走 catch(Exception) → 空集；断言只锁"不放行"方向
        assertTrue(client.allowedColumns(USER, TENANT, ENTITY, ALL_COLUMNS).isEmpty());
    }

    @Test
    @DisplayName("入参全列集为空/null → 空集且不发请求")
    void emptyRequestColumnSurfaceShortCircuits() {
        RestTemplate stub = mock(RestTemplate.class);
        AiSecurityEngineClient client = clientWith(stub);

        assertTrue(client.allowedColumns(USER, TENANT, ENTITY, List.of()).isEmpty());
        assertTrue(client.allowedColumns(USER, TENANT, ENTITY, null).isEmpty());
        verifyNoInteractions(stub);
    }

    @Test
    @DisplayName("邻位收口边界：脱敏出口的传输异常仍原样上抛（本批只改列面读数位）")
    void maskingPathKeepsThrowingOnTransportFailure() {
        AiSecurityEngineClient client = throwingClient(new ResourceAccessException("connect refused"));

        // applyMasking 声明 throws Exception 且不吞传输异常：列面改造不得把它顺手折成空结果
        assertThrows(ResourceAccessException.class,
                () -> client.applyMasking(List.of("a"), List.of("MASK")));
    }
}
