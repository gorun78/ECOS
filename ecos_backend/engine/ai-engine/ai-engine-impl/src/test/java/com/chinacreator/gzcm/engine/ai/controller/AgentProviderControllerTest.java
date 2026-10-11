package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 详细设计-06 F06-05 要点 1 验收：provider 目录端点委托 llm-gateway 取真值。
 *
 * <p>X-22 禁令：目录不可得时不得返回"恒空 200"伪实现，必须 501 + {@code stub=true}
 * （{@code ECOS-AI-501}，与分册 05 §0.2-⑦ 同裁定）。</p>
 */
@DisplayName("F06-05/X-22 AgentProviderController：真值来自网关，空目录必须 501 而非空 200")
class AgentProviderControllerTest {

    @Test
    @DisplayName("F06-05/X-22 空目录 → 501 + stub=true（禁 200 空 body 伪成功）")
    void emptyCatalogYields501Not200Empty() {
        LLMGateway gateway = mock(LLMGateway.class);
        when(gateway.getProviderStatus()).thenReturn(Map.of());

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                new AgentProviderController(gateway).listProviders();

        assertEquals(HttpStatus.NOT_IMPLEMENTED, resp.getStatusCode(),
                "无目录数据必须是 501（X-22 禁恒空 200）");
        Map<String, Object> data = resp.getBody().getData();
        assertNotNull(data, "501 也必须带结构化 body（诊断码 + stub 标记）");
        assertEquals(Boolean.TRUE, data.get("stub"), "data.stub 必须为 true");
        assertEquals("ECOS-AI-501", data.get("errorCode"));
        assertEquals(0, data.get("total"));
        assertNotNull(data.get("traceId"), "stub 响应必须带诊断码");
    }

    @Test
    @DisplayName("F06-05 llm-gateway 未装配（独立引擎态）同样 501 + stub=true")
    void missingGatewayYields501Not200Empty() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                new AgentProviderController(null).listProviders();

        assertEquals(HttpStatus.NOT_IMPLEMENTED, resp.getStatusCode());
        assertEquals(Boolean.TRUE, resp.getBody().getData().get("stub"));
    }

    @Test
    @DisplayName("F06-05 目录有数据 → 200 + 真值（provider 名/状态/baseUrl 来自网关探测）")
    void populatedCatalogYields200WithRealValues() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "available");
        // 夹具用假 host：F00-07（LlmSoleEgressTest）禁止 llm-gateway 之外任何 Java 源码
        // 出现供应商端点字面量；本用例校验的是网关探测值的透传，不是具体主机名。
        status.put("baseUrl", "https://provider-base-url.from-config/v1");
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("deepseek", status);
        catalog.put("openai", Map.of("status", "unreachable", "error", "connect refused"));

        LLMGateway gateway = mock(LLMGateway.class);
        when(gateway.getProviderStatus()).thenReturn(catalog);

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                new AgentProviderController(gateway).listProviders();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> data = resp.getBody().getData();
        assertEquals(Boolean.FALSE, data.get("stub"));
        assertEquals(2, data.get("total"));
        assertEquals(1, data.get("available"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> providers = (List<Map<String, Object>>) data.get("providers");
        assertEquals("deepseek", providers.get(0).get("name"));
        assertEquals("available", providers.get(0).get("status"));
        assertEquals(Boolean.TRUE, providers.get(0).get("available"));
        assertEquals(Boolean.FALSE, providers.get(1).get("available"));
        assertNotEquals("unknown", providers.get(0).get("status"),
                "真值必须来自网关探测结果，不是占位");
    }
}
