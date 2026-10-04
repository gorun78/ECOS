package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * F06-05 要点 1 验收（设计-06 §324 {@code AgentProviderControllerTest#emptyCatalogYields501Not200Empty}）。
 *
 * <p>X-22 收口：{@code GET /api/v1/agent/providers} 委托 llm-gateway 取真值；
 * 目录不可用/空 → <b>501 + stub</b>（禁"恒空 200"），有目录 → 200 真值。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentProviderControllerTest {

    @Mock
    private LLMGateway llmGateway;

    @Test
    @DisplayName("emptyCatalogYields501Not200Empty: llm-gateway 目录空 → 501 + stub=true，非 200 空列表")
    void emptyCatalogYields501Not200Empty() {
        AgentProviderController ctrl = new AgentProviderController(llmGateway);
        when(llmGateway.getProviderStatus()).thenReturn(Map.of());

        ApiResponse<Map<String, Object>> resp = ctrl.listProviders();

        assertEquals(501, resp.getCode(), "空目录必须 501（非 X-22 的恒空 200）");
        assertNotNull(resp.getMessage());
        assertTrue(resp.getMessage().contains("stub=true"),
                "501 响应须携带 stub 语义指纹（禁静默空 200），got=" + resp.getMessage());
    }

    @Test
    @DisplayName("gatewayAbsentYields501Not200Empty: llm-gateway 未装配 → 501 + stub=true")
    void gatewayAbsentYields501Not200Empty() {
        AgentProviderController ctrl = new AgentProviderController(null);

        ApiResponse<Map<String, Object>> resp = ctrl.listProviders();

        assertEquals(501, resp.getCode(), "网关缺失必须 501 stub");
        assertTrue(resp.getMessage().contains("stub=true"));
    }

    @Test
    @DisplayName("realCatalogYieldsRealValues: 有 provider 目录 → 200 真值（非 stub）")
    void realCatalogYieldsRealValues() {
        AgentProviderController ctrl = new AgentProviderController(llmGateway);
        Map<String, Object> status = new LinkedHashMap<>();
        Map<String, Object> deepseek = new LinkedHashMap<>();
        deepseek.put("status", "available");
        deepseek.put("baseUrl", "https://api.deepseek.com/v1");
        status.put("deepseek", deepseek);
        status.put("openai", Map.of("status", "unknown", "baseUrl", "https://api.openai.com/v1"));
        when(llmGateway.getProviderStatus()).thenReturn(status);

        ApiResponse<Map<String, Object>> resp = ctrl.listProviders();

        assertEquals(0, resp.getCode(), "有目录真值 → 200/code=0");
        assertNotNull(resp.getData());
        assertEquals(false, resp.getData().get("stub"), "真值路径 stub=false");
        assertEquals(2, resp.getData().get("total"));
    }
}
