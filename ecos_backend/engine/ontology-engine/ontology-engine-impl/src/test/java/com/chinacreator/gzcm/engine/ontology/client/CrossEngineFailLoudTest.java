package com.chinacreator.gzcm.engine.ontology.client;

import com.chinacreator.gzcm.common.exception.DataBridgeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F02-15 验收（详细设计-02 P0）— 跨引擎取数 fail-loud，禁静默空列表（D-14）。
 *
 * <p>断言 {@link DataNetResourceClient#listResourceFields(String)} 在
 * <b>对端不可达 / 对端 503 / 寻址失败</b> 时<b>抛异常</b>（503 + ECOS-DATA-041），
 * <b>绝不返回空集合</b>。纯单元测试，不依赖任何真实服务。
 */
class CrossEngineFailLoudTest {

    private static final String CODE_041 = "ECOS-DATA-041";

    /** 死端口（实测本机无监听） */
    private static final String DEAD_PORT_BASE = "http://localhost:19999";

    private static RestTemplate fastRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(300);
        factory.setReadTimeout(300);
        return new RestTemplate(factory);
    }

    @Test
    @DisplayName("对端不可达（死端口）→ 抛 DataBridgeException(503, ECOS-DATA-041) 而非返回空列表")
    void unreachableDatanetThrowsInsteadOfEmptyList() {
        DataNetResourceClient client =
                new DataNetResourceClient(fastRestTemplate(), () -> DEAD_PORT_BASE);
        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> client.listResourceFields("asset-1"));
        assertTrue(ex.getMessage().contains(CODE_041),
                "异常消息必须含 ECOS-DATA-041，实际: " + ex.getMessage());
        assertEquals(503, ex.getHttpStatus(), "fail-loud 必须携带 HTTP 503");
        assertTrue(ex.getMessage().contains("资产字段端点不可用"),
                "应标注失败端点: " + ex.getMessage());
    }

    @Test
    @DisplayName("base URL 寻址失败（resolver 无 datanet 条目）→ 寻址错误上抛，不回落任何硬编码默认值")
    void addressFailurePropagatesWithoutFallback() {
        DataNetResourceClient client = new DataNetResourceClient(fastRestTemplate(), () -> {
            throw new DataBridgeException(503, 503,
                    CODE_041 + ": datanet 服务寻址失败（route-manifest 缺 datanet 条目）");
        });
        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> client.listResourceFields("asset-1"));
        assertTrue(ex.getMessage().contains("寻址失败"), "应为寻址失败: " + ex.getMessage());
        assertTrue(ex.getMessage().contains(CODE_041));
    }

    @Test
    @DisplayName("对端 503（模拟 HttpServerErrorException）→ 包装为 ECOS-DATA-041 上抛，不返回空集合")
    void server503ThrowsInsteadOfEmptyList() {
        RestTemplate stub = mock(RestTemplate.class);
        when(stub.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new HttpServerErrorException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "Service Unavailable", null, null, null));
        DataNetResourceClient client = new DataNetResourceClient(stub, () -> "http://ignored");
        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> client.listResourceFields("asset-1"));
        assertTrue(ex.getMessage().contains(CODE_041), "503 收口必须带 ECOS-DATA-041: " + ex.getMessage());
    }

    @Test
    @DisplayName("响应非法（data=null）→ fail-loud 上抛，不隐式返回空列表")
    void invalidResponseThrowsInsteadOfEmptyList() {
        RestTemplate stub = mock(RestTemplate.class);
        when(stub.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Collections.singletonMap("data", null));
        DataNetResourceClient client = new DataNetResourceClient(stub, () -> "http://ignored");
        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> client.listResourcesByLayer("CURATED"));
        assertTrue(ex.getMessage().contains("响应非法"), "非法响应必须 fail-loud: " + ex.getMessage());
    }

    @Test
    @DisplayName("成功路径：分页响应 items → 返回字段列表（含 primaryKey 兼容键补齐）")
    void successPagedResponseMapsItems() {
        RestTemplate stub = mock(RestTemplate.class);
        Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("fieldName", "ID");
        item.put("fieldType", "VARCHAR");
        Map<String, Object> page = new java.util.LinkedHashMap<>();
        page.put("items", new java.util.ArrayList<>(java.util.List.of(item)));
        when(stub.getForObject(anyString(), eq(Map.class)))
                .thenReturn(java.util.Map.of("data", page));
        DataNetResourceClient client = new DataNetResourceClient(stub, () -> "http://ignored");
        java.util.List<Map<String, Object>> fields = client.listResourceFields("a1");
        assertEquals(1, fields.size());
        assertEquals("ID", fields.get(0).get("fieldName"));
        assertEquals(Boolean.FALSE, fields.get(0).get("primaryKey"), "items 无 primaryKey 时补 false");
    }
}
