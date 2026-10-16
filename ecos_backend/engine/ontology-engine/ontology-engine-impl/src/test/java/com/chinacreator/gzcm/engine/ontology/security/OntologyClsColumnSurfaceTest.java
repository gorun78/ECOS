package com.chinacreator.gzcm.engine.ontology.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * #142/B4-3d-1（D-B4-25）—— 本体通道 CLS 列面消费具名用例。
 *
 * <p><b>缺陷本体</b>：{@code SecurityEngineClient#filterColumns} 只读
 * {@code data.allowedColumns}，而 CLS 生产者写的是 {@code visibleColumns} —— 本体侧 27 个
 * Ontology*Controller 的列裁剪因此结构性恒空集（恒 DENY），且既有单测把 client 整体 mock 掉，
 * 键名漂移在测试面完全不可见。桩下沉到 {@link RestTemplate} 后，
 * {@link #producerShapedResponseNowReturnsReadableColumns()} 就是"能变红"的那一刀：
 * 读数位退回 {@code allowedColumns} 立刻失败。</p>
 *
 * <p><b>钉现状（非认可）</b>：{@link #transportFailureFailsClosed()} 断言本体侧把传输异常折叠成
 * 空列集＝fail-closed。这与 workspace 侧「不可用→503 上抛」不同态（详设-01 §4.5.4 要求分态），
 * 差异已按 D-B4-25 附注登记在差距文档，本批不改本体侧对外异常契约（API 只增不改），
 * 断言只做"绝不放行"的方向锁死。</p>
 */
@DisplayName("本体通道 CLS：按生产者形态取回可见列 + 漂移/拒绝 fail-closed")
class OntologyClsColumnSurfaceTest {

    private static final String ENTITY = "OntologyObjectType";
    private static final List<String> ALL_COLUMNS =
            Arrays.asList("id", "code", "name", "salary", "id_card");

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static SecurityEngineClient clientWithData(Object dataSegment) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", 200);
        if (dataSegment != null) envelope.put("data", dataSegment);
        RestTemplate stub = mock(RestTemplate.class);
        when(stub.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(envelope));
        return newClient(stub);
    }

    private static SecurityEngineClient throwingClient(RuntimeException failure) {
        RestTemplate stub = mock(RestTemplate.class);
        when(stub.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(failure);
        return newClient(stub);
    }

    private static SecurityEngineClient newClient(RestTemplate stub) {
        SecurityEngineClient client = new SecurityEngineClient("http://127.0.0.1:18081", 200);
        try {
            Field f = SecurityEngineClient.class.getDeclaredField("serviceRestTemplate");
            f.setAccessible(true);
            f.set(client, stub);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("桩 RestTemplate 注不进去（字段名漂移即本类判据失效）", e);
        }
        return client;
    }

    /** 生产者形态：data.visibleColumns（+ blockedColumns）。 */
    private static Map<String, Object> producerData(List<String> visible, List<String> blocked) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tableName", ENTITY);
        data.put("visibleColumns", visible);
        data.put("blockedColumns", blocked);
        return data;
    }

    @Test
    @DisplayName("生产者应答（visibleColumns）→ 返回可见列（旧实现在此返空集＝本体通道恒 DENY）")
    void producerShapedResponseNowReturnsReadableColumns() {
        SecurityEngineClient client = clientWithData(producerData(List.of("id", "name"), List.of("salary")));

        List<String> visible = client.filterColumns(ENTITY, ALL_COLUMNS);

        assertEquals(List.of("id", "name"), visible, "D-B4-25 判据：读侧必须认生产者的 visibleColumns");
    }

    @Test
    @DisplayName("visibleColumns 为空表＝策略拒绝 → 空集（调用方按 DENY 处理）")
    void emptyVisibleSurfaceMeansPolicyDeny() {
        SecurityEngineClient client =
                clientWithData(producerData(List.of(), Arrays.asList("salary", "id_card")));

        assertTrue(client.filterColumns(ENTITY, ALL_COLUMNS).isEmpty());
    }

    @Test
    @DisplayName("应答两键皆不可读＝形态漂移 → 空集（改回读 allowedColumns 也在此变红）")
    void driftedColumnSurfaceFailsClosed() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tableName", ENTITY);
        data.put("renamedColumnSurface", List.of("id"));

        assertTrue(clientWithData(data).filterColumns(ENTITY, ALL_COLUMNS).isEmpty());
    }

    @Test
    @DisplayName("无 data 段 / data 非 Map → 空集（外层形态漂移 fail-closed）")
    void malformedResponseFailsClosed() {
        assertTrue(clientWithData(null).filterColumns(ENTITY, ALL_COLUMNS).isEmpty());
        assertTrue(clientWithData("visibleColumns").filterColumns(ENTITY, ALL_COLUMNS).isEmpty());
    }

    @Test
    @DisplayName("仅 legacy 别名在位 → 仍可读；权威键与别名同在时权威键胜出")
    void legacyAliasIsFallbackOnly() {
        assertEquals(List.of("id", "code"),
                clientWithData(Map.of("allowedColumns", List.of("id", "code")))
                        .filterColumns(ENTITY, ALL_COLUMNS));

        Map<String, Object> both = new LinkedHashMap<>();
        both.put("visibleColumns", List.of("id"));
        both.put("allowedColumns", ALL_COLUMNS);
        assertEquals(List.of("id"), clientWithData(both).filterColumns(ENTITY, ALL_COLUMNS),
                "别名不得覆盖权威键");
    }

    @Test
    @DisplayName("传输异常 → 空集（钉现状：本体侧不抛 503，但绝不放行全列）")
    void transportFailureFailsClosed() {
        SecurityEngineClient client = throwingClient(new ResourceAccessException("connect refused"));

        assertTrue(client.filterColumns(ENTITY, ALL_COLUMNS).isEmpty());
    }

    @Test
    @DisplayName("入参全列集为空/null → 空集且不发请求")
    void emptyRequestColumnSurfaceShortCircuits() {
        RestTemplate stub = mock(RestTemplate.class);
        SecurityEngineClient client = newClient(stub);

        assertTrue(client.filterColumns(ENTITY, List.of()).isEmpty());
        assertTrue(client.filterColumns(ENTITY, null).isEmpty());
        verifyNoInteractions(stub);
    }
}
