package com.chinacreator.gzcm.workspace.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException;

/**
 * 页面通道 CLS 列面消费用例（#142/B4-3d-1，差距 D-B4-25）。
 *
 * <p><b>缺陷本体</b>：{@code WorkspaceSecurityEngineClient#allowedColumns} 只读
 * {@code data.allowedColumns}，而 security-engine 生产者写的一直是 {@code visibleColumns}
 * （仓根实证：生产侧零处写 {@code allowedColumns}）。于是页面通道任何一次列裁剪都拿到空集 ⇒
 * {@code AbacQueryFilter#filterColumns} 把行集整条置空 —— 列级安全在功能上是死面，
 * 而且从测试里看不见：既有 workspace 用例全部 mock 的是 client 本身，桩的就是
 * {@code allowedColumns(...)} 的返回值，键名漂移在单测层被完全遮蔽。</p>
 *
 * <p>本类把桩下沉到 {@link RestTemplate}，用<b>生产者的真实应答形态</b>驱动客户端，
 * 因此 {@link #producerShapedResponseNowReturnsReadableColumns()} 是这套缺陷的"能变红"用例：
 * 把读数位改回只认 {@code allowedColumns}，它立刻失败。</p>
 *
 * <p>同时钉住三条不变口径：空可读集＝策略拒绝（返空表）、键面漂移＝拒绝（返空表但分态记日志）、
 * security 进程不可用＝503 上抛（§4.5.4「不可用 ≠ 无权限」，禁折叠成空集冒充裁决）。</p>
 */
@DisplayName("页面通道 CLS：按生产者形态取回可读列 + 漂移/拒绝/不可用三态分立")
class WorkspaceClsColumnSurfaceTest {

    private static final String ENTITY = "Contract";
    private static final List<String> ALL_COLUMNS =
            Arrays.asList("id", "customer", "amount", "salary", "id_card");

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static WorkspaceSecurityEngineClient clientReturning(Map<String, Object> envelope) {
        RestTemplate stub = mock(RestTemplate.class);
        when(stub.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(envelope));
        return new WorkspaceSecurityEngineClient(stub, mock(ObjectProvider.class));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static WorkspaceSecurityEngineClient clientThrowing(RuntimeException failure) {
        RestTemplate stub = mock(RestTemplate.class);
        when(stub.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(failure);
        return new WorkspaceSecurityEngineClient(stub, mock(ObjectProvider.class));
    }

    /** 生产者形态：data.visibleColumns（+ blockedColumns）。 */
    private static Map<String, Object> producerEnvelope(List<String> visible, List<String> blocked) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tableName", ENTITY);
        data.put("visibleColumns", visible);
        data.put("blockedColumns", blocked);
        data.put("policies", List.of());
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", 200);
        envelope.put("data", data);
        return envelope;
    }

    @SuppressWarnings("unchecked")
    private static WorkspaceSecurityEngineClient clientWithData(Object dataSegment) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", 200);
        if (dataSegment != null) envelope.put("data", dataSegment);
        return clientReturning(envelope);
    }

    // ═══════════════ 1. 修复本体：真实可读集现在真的能返回 ═══════════════

    @Test
    @DisplayName("生产者应答（visibleColumns）→ 返回可读列（旧实现在此返空集＝整表恒 DENY）")
    void producerShapedResponseNowReturnsReadableColumns() {
        WorkspaceSecurityEngineClient client =
                clientReturning(producerEnvelope(List.of("id", "amount"), List.of("salary")));

        List<String> allowed = client.allowedColumns(ENTITY, ALL_COLUMNS);

        assertEquals(List.of("id", "amount"), allowed,
                "D-B4-25 判据：读侧必须认生产者的 visibleColumns");
        assertTrue(allowed.stream().noneMatch("salary"::equals), "被隐藏列不得混进可读集");
    }

    @Test
    @DisplayName("请求体三字段签名不变（tableName/userId/allColumns）——只增语义不改契约")
    void requestBodySignatureIsUnchanged() {
        @SuppressWarnings({"unchecked", "rawtypes"})
        RestTemplate stub = mock(RestTemplate.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(stub.postForEntity(anyString(), captor.capture(), eq(Map.class)))
                .thenAnswer(inv -> ResponseEntity.ok(
                        producerEnvelope(List.of("id"), List.of())));
        WorkspaceSecurityEngineClient client =
                new WorkspaceSecurityEngineClient(stub, mock(ObjectProvider.class));

        client.allowedColumns(ENTITY, ALL_COLUMNS);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
        assertEquals(ENTITY, body.get("tableName"));
        assertTrue(body.containsKey("userId"), "userId 必须仍随请求体送出（身份单源）");
        assertEquals(ALL_COLUMNS, body.get("allColumns"));
    }

    // ═══════════════ 2. 拒绝态与漂移态（都返空表，但必须分立） ═══════════════

    @Test
    @DisplayName("visibleColumns 为空表＝策略拒绝全部列 → 空表（调用方整条置空行集）")
    void emptyVisibleSurfaceMeansPolicyDeny() {
        WorkspaceSecurityEngineClient client =
                clientReturning(producerEnvelope(List.of(), Arrays.asList("salary", "id_card")));

        assertTrue(client.allowedColumns(ENTITY, ALL_COLUMNS).isEmpty());
    }

    @Test
    @DisplayName("应答两键皆不可读＝形态漂移 → 空表且不复用「策略拒绝」的解释路径")
    void driftedColumnSurfaceFailsClosed() {
        // 生产者若改键名（本缺陷的复发形态），消费者必须拒绝而不是静默返回空集了事：
        // 空集在这里同样是 DENY，但漂移态在实现里走的是「列面漂移 → DENY」分支并带理由落日志。
        WorkspaceSecurityEngineClient client = clientWithData(
                Map.of("tableName", ENTITY, "someOtherKey", List.of("id")));

        assertTrue(client.allowedColumns(ENTITY, ALL_COLUMNS).isEmpty());
    }

    @Test
    @DisplayName("应答无 data 段 → 空表（外层漂移同样 fail-closed，禁抛裸异常打断读路径）")
    void missingDataSegmentFailsClosed() {
        assertTrue(clientWithData(null).allowedColumns(ENTITY, ALL_COLUMNS).isEmpty());
        assertTrue(clientWithData("visibleColumns").allowedColumns(ENTITY, ALL_COLUMNS).isEmpty());
    }

    @Test
    @DisplayName("data.visibleColumns 为 null 值 → 漂移态空表，禁 NPE 打断查询")
    void nullValuedVisibleSurfaceFailsClosed() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("visibleColumns", null);

        assertTrue(clientWithData(data).allowedColumns(ENTITY, ALL_COLUMNS).isEmpty());
    }

    // ═══════════════ 3. legacy 别名兼容（只兜底，不反客为主） ═══════════════

    @Test
    @DisplayName("仅 legacy 别名在位 → 仍可读（停写侧旧应答兼容），且权威键优先")
    void legacyAliasIsFallbackOnly() {
        WorkspaceSecurityEngineClient aliasOnly =
                clientWithData(Map.of("allowedColumns", List.of("id", "customer")));
        assertEquals(List.of("id", "customer"), aliasOnly.allowedColumns(ENTITY, ALL_COLUMNS));

        Map<String, Object> both = new LinkedHashMap<>();
        both.put("visibleColumns", List.of("id"));
        both.put("allowedColumns", ALL_COLUMNS);
        assertEquals(List.of("id"), clientWithData(both).allowedColumns(ENTITY, ALL_COLUMNS),
                "别名不得覆盖权威键");
    }

    // ═══════════════ 4. 可用性故障保持独立态（§4.5.4） ═══════════════

    @Test
    @DisplayName("连不上/超时 → 503 ECOS-SEC-501 上抛，禁折叠成空列集冒充「无可见列」")
    void unavailabilityStaysSeparateFromDeny() {
        WorkspaceSecurityEngineClient client =
                clientThrowing(new ResourceAccessException("connect refused"));

        SecurityEngineUnavailableException ex = assertThrows(SecurityEngineUnavailableException.class,
                () -> client.allowedColumns(ENTITY, ALL_COLUMNS));
        assertEquals(503, ex.getHttpStatus(),
                "不可用态必须是 503 独立态，禁渲染成 200 + 空列表（§4.5.4 分态）");
        assertEquals("ECOS-SEC-501", ex.getCode());
        assertTrue(ex.getTechnicalReason().contains("/api/v1/security/cls/columns"),
                "日志面须带端点定位，实际=" + ex.getTechnicalReason());
        // X-41 治理口径：下游原文（path/reason）只进日志面，不进对外 message
        assertFalse(ex.getMessage().contains("cls/columns"),
                "对外 message 不得泄漏下游原文，实际=" + ex.getMessage());
    }

    @Test
    @DisplayName("入参全列集为空 → 直接空表且不发请求（无列可裁＝无数据面）")
    void emptyRequestColumnSurfaceShortCircuits() {
        @SuppressWarnings({"unchecked", "rawtypes"})
        RestTemplate stub = mock(RestTemplate.class);
        WorkspaceSecurityEngineClient client =
                new WorkspaceSecurityEngineClient(stub, mock(ObjectProvider.class));

        assertTrue(client.allowedColumns(ENTITY, List.of()).isEmpty());
        org.mockito.Mockito.verifyNoInteractions(stub);
    }
}
