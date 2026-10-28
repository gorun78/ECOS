package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.capability.web.AiCapabilityApiPaths;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * #146x 附录 A 第 28 条 / 详设说明书 §9.3.1 #17-#19 契约面验收。
 *
 * <p>三裁定：</p>
 * <ol>
 *   <li>#17 真值腿的 modelKey/contextWindow 必须来自 {@link LLMGateway#modelCatalog()}，
 *       缺列以 {@code null + *Applicable=false +} 顶层原因单源自证（禁抄
 *       {@code AIPModelController}/{@code AgentConfigController#listModels} 的硬编码行）；</li>
 *   <li>读数不可得必须 501 + {@code stub=true}（X-22 禁恒空 200 伪实现）；</li>
 *   <li>#18/#19 未上线必须显式 501 + {@code missing[]} 取证自证 + 零写入，
 *       禁 200 伪成功、禁猜熔断态。</li>
 * </ol>
 */
@DisplayName("#146x 附录A#28 AiModelCatalogController：真值腿如实、缺载体显式 501")
class AiModelCatalogControllerTest {

    private static final String MODELS = AiCapabilityApiPaths.MODELS_COLLECTION;
    private static final String HEALTH = "/api/v1/ai/models/deepseek-chat/health";

    @Test
    @DisplayName("#17 真值腿：modelKey/contextWindow 取网关目录读数，缺列落 null + Applicable=false")
    void listModelsServesRealValuesWithHonestAbsentColumns() {
        LLMGateway gateway = mock(LLMGateway.class);
        when(gateway.modelCatalog()).thenReturn(List.of(
                new LLMGateway.ModelLimit("deepseek-chat", 64_000, true, true),
                new LLMGateway.ModelLimit("deepseek-reasoner", 64_000, true, true)));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                new AiModelCatalogController(gateway).listLlmModels(null, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> data = resp.getBody().getData();
        assertEquals(Boolean.FALSE, data.get("stub"));
        assertEquals(2, data.get("total"));
        assertEquals(2, data.get("contextWindowAvailable"));
        assertTrue(String.valueOf(data.get("catalog_source")).contains("modelCatalog"),
                "供数单源必须响应自证");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        Map<String, Object> first = items.get(0);
        assertEquals("deepseek-chat", first.get("modelKey"));
        assertEquals(64_000, first.get("contextWindow"), "上下文上限必须是网关目录真值");
        assertEquals(Boolean.TRUE, first.get("contextWindowApplicable"));

        // 无载体的四列：null + 逐项 Applicable=false（禁按模型名猜能力/成本档，禁反推版本）
        assertNull(first.get("capabilities"));
        assertNull(first.get("costClass"));
        assertNull(first.get("activeVersion"));
        assertNull(first.get("gitRef"));
        for (String column : List.of("capabilities", "costClass", "activeVersion", "gitRef")) {
            assertEquals(Boolean.FALSE, first.get(column + "Applicable"), column + " 必须显式声明不适用");
        }
        assertTrue(String.valueOf(data.get("columns_source")).contains("contextLimit"),
                "能力/成本列不可得原因必须单源自证");
        assertTrue(String.valueOf(data.get("version_source")).contains("ecos-git-repos"),
                "版本列不可得原因必须单源自证");
    }

    @Test
    @DisplayName("#17 目录登记但上限 <=0：contextWindow 落 null，禁把 0 当真实窗口")
    void unusableContextLimitYieldsNullNotZero() {
        LLMGateway gateway = mock(LLMGateway.class);
        when(gateway.modelCatalog()).thenReturn(List.of(
                new LLMGateway.ModelLimit("unknown-model", 0, true, false)));

        Map<String, Object> data = new AiModelCatalogController(gateway)
                .listLlmModels(null, null).getBody().getData();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        assertNull(items.get(0).get("contextWindow"));
        assertEquals(Boolean.FALSE, items.get(0).get("contextWindowApplicable"));
        assertEquals(0, data.get("contextWindowAvailable"));
        assertEquals(1, data.get("total"), "登记行仍如实计数，不因读数不可用而丢行");
    }

    @Test
    @DisplayName("#17 未装配 / 空目录 → 501 + stub=true（X-22 禁 200 空 items 伪实现）")
    void unreadableCatalogYields501() {
        ResponseEntity<ApiResponse<Map<String, Object>>> unassembled =
                new AiModelCatalogController(null).listLlmModels(null, null);
        assertEquals(HttpStatus.NOT_IMPLEMENTED, unassembled.getStatusCode());
        Map<String, Object> unassembledData = unassembled.getBody().getData();
        assertEquals(Boolean.TRUE, unassembledData.get("stub"));
        assertEquals("ECOS-AI-501", unassembledData.get("errorCode"));
        // 理由的承载位 = 信封 message（`AiResponseSupport.stubCapability` 唯一写入点）；
        // data.message 是 AiErrorClass 模板渲染值（通用文案），禁把两者混为一谈而误判「原因已自证」。
        assertTrue(String.valueOf(unassembled.getBody().getMessage()).contains("llm-gateway"),
                "原因必须区分「未装配」，禁与「目录为空」混为一谈");

        LLMGateway emptyGateway = mock(LLMGateway.class);
        when(emptyGateway.modelCatalog()).thenReturn(List.of());
        ResponseEntity<ApiResponse<Map<String, Object>>> empty =
                new AiModelCatalogController(emptyGateway).listLlmModels(null, null);
        assertEquals(HttpStatus.NOT_IMPLEMENTED, empty.getStatusCode());
        assertEquals(Boolean.TRUE, empty.getBody().getData().get("stub"));
        assertTrue(String.valueOf(empty.getBody().getMessage())
                .contains("llm.models.catalog"), "空目录原因必须点名配置面");
    }

    @Test
    @DisplayName("#17 capability/dataClassification 过滤无载体 → 显式 400，禁静默忽略后假装已筛选")
    void unsupportedFilterRejectedWith400() throws Exception {
        LLMGateway gateway = mock(LLMGateway.class);
        MvcResult r = MockMvcBuilders.standaloneSetup(new AiModelCatalogController(gateway))
                .setControllerAdvice(new AiErrorAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build()
                .perform(get(MODELS).param("capability", "vision"))
                .andReturn();

        assertEquals(400, r.getResponse().getStatus(),
                "对不存在的列做筛选必须被拒，不得返回未筛选的全量伪装成筛选结果");
        JsonNode d = json(r).path("data");
        assertEquals("ECOS-AI-400", d.path("errorCode").asText());
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("#18 注册/升版 → 501 + missing[] 取证 + written=false，字段值不回显（X-49）")
    void registerFaceIsExplicit501WithZeroWrite() {
        LLMGateway gateway = mock(LLMGateway.class);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("modelKey", "deepseek-chat");
        body.put("endpoint", "https://api.invalid.example/v1");
        body.put("apiKeyRef", "SECRET-REF-MUST-NOT-ECHO");
        body.put("capabilities", List.of("chat"));
        body.put("costClass", "T2");

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                new AiModelCatalogController(gateway).registerLlmModel(body);

        assertEquals(HttpStatus.NOT_IMPLEMENTED, resp.getStatusCode());
        Map<String, Object> data = resp.getBody().getData();
        assertEquals(Boolean.TRUE, data.get("stub"));
        assertEquals(Boolean.FALSE, data.get("written"), "501 必须同时声明零写入");
        assertNull(data.get("modelId"));
        assertEquals(List.of("apiKeyRef", "capabilities", "costClass", "endpoint", "modelKey"),
                data.get("received_field_names"), "只回显字段名（字典序），禁回显字段值");
        assertFalse(String.valueOf(jsonSerialize(data)).contains("SECRET-REF-MUST-NOT-ECHO"),
                "apiKeyRef 值禁外泄（X-49）");

        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) data.get("missing");
        assertEquals(4, missing.size(), "四条欠账必须逐条在册：写通道/角色/OPA/Git 归档");
        assertTrue(missing.stream().anyMatch(x -> x.contains("AI_ADMIN")));
        assertTrue(missing.stream().anyMatch(x -> x.contains("rbac.rego")));
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("#19 健康/熔断面 → 501 + 四列 null + healthApplicable=false，禁恒 healthy 假值")
    void healthFaceIsExplicit501() {
        LLMGateway gateway = mock(LLMGateway.class);
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                new AiModelCatalogController(gateway).checkModelHealth("deepseek-chat");

        assertEquals(HttpStatus.NOT_IMPLEMENTED, resp.getStatusCode());
        Map<String, Object> data = resp.getBody().getData();
        assertEquals("deepseek-chat", data.get("modelKey"), "路径参数如实回显");
        for (String column : List.of("state", "errorRate", "windowCount", "lastProbeTime")) {
            assertNull(data.get(column), column + " 无载体时必须落 null，禁沿用 AIPModelController 恒 healthy 形态");
        }
        assertEquals(Boolean.FALSE, data.get("healthApplicable"));
        assertEquals(Boolean.TRUE, data.get("stub"));
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) data.get("missing");
        assertEquals(2, missing.size(), "两条欠账在册：modelKey 维度熔断器 / 探测读数门面");
        assertTrue(missing.stream().anyMatch(x -> x.contains("AgentCircuitBreaker")));
        assertTrue(missing.stream().anyMatch(x -> x.contains("IMetricRecorder")));
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("契约路径真挂载：GET /api/v1/ai/models=200、health=501、POST=501")
    void contractPathsAreRouted() throws Exception {
        LLMGateway gateway = mock(LLMGateway.class);
        when(gateway.modelCatalog()).thenReturn(List.of(
                new LLMGateway.ModelLimit("deepseek-chat", 64_000, true, true)));
        var mvc = MockMvcBuilders.standaloneSetup(new AiModelCatalogController(gateway))
                .setControllerAdvice(new AiErrorAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();

        MvcResult list = mvc.perform(get(MODELS)).andReturn();
        assertEquals(200, list.getResponse().getStatus());
        JsonNode listData = json(list).path("data");
        assertEquals("deepseek-chat", listData.path("items").get(0).path("modelKey").asText());

        assertEquals(501, mvc.perform(get(HEALTH)).andReturn().getResponse().getStatus());
        assertEquals(501, mvc.perform(post(MODELS).contentType(MediaType.APPLICATION_JSON)
                .content("{\"modelKey\":\"x\"}")).andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("禁造契约外别名：本类只挂 /api/v1/ai 基址，/api/v1/agent/models 不得存在")
    void noOutOfContractAliasPrefix() throws Exception {
        LLMGateway gateway = mock(LLMGateway.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AiModelCatalogController(gateway))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();

        // 类级基址单源 = /api/v1/ai（AiCapabilityApiPaths.BASE_PREFIX）。若多挂 /api/v1/agent
        // 前缀，standalone 下该路径会 200/501 而非 404 —— 即造出契约册外的第二套别名面。
        assertEquals(404, mvc.perform(get("/api/v1/agent/models")).andReturn().getResponse().getStatus(),
                "禁为迁就前端调用新造 /api/v1/agent/models 别名端点");
    }

    @Test
    @DisplayName("未实现信封单源：#18/#19 与 #16 provider 面同码同形（禁两套 501 分叉）")
    void stubEnvelopeShapeMatchesProviderFace() {
        LLMGateway providerGateway = mock(LLMGateway.class);
        when(providerGateway.getProviderStatus()).thenReturn(Map.of());
        Map<String, Object> providerData =
                new AgentProviderController(providerGateway).listProviders().getBody().getData();

        Map<String, Object> healthData = new AiModelCatalogController(null)
                .checkModelHealth("m").getBody().getData();

        assertEquals(providerData.get("errorCode"), healthData.get("errorCode"));
        for (String key : List.of("errorCode", "errorClass", "message", "traceId", "retryable", "stub")) {
            assertTrue(providerData.containsKey(key), "provider 面信封缺键 " + key);
            assertTrue(healthData.containsKey(key), "模型面信封缺键 " + key);
        }
        assertEquals("STUB_CAPABILITY", healthData.get("errorClass"));
    }

    @Test
    @DisplayName("501 原因承载位置跨类互证：#19 与 #16 同走 AiResponseSupport#stubCapability")
    void stubReasonLegIsSharedWithProviderFace() {
        // 「信封同形」只比键面与 errorCode ⇒ 本面若自带第二套 501 拼装也可能同形通过。
        // 本例把原因的<b>承载位置</b>钉成跨类断言：ApiResponse.message 的前缀「该能力尚未上线（」
        // 由单源腿内插产出（判据，非变异实跑记录）——本类若自拼信封，前缀即与 #16 不等。
        LLMGateway providerGateway = mock(LLMGateway.class);
        when(providerGateway.getProviderStatus()).thenReturn(null);
        String providerMessage =
                new AgentProviderController(providerGateway).listProviders().getBody().getMessage();

        ResponseEntity<ApiResponse<Map<String, Object>>> health =
                new AiModelCatalogController(null).checkModelHealth("m");

        assertEquals(501, HttpStatus.valueOf(health.getStatusCode().value()).value());
        String prefix = "该能力尚未上线（";
        assertEquals(providerMessage.substring(0, prefix.length()),
                health.getBody().getMessage().substring(0, prefix.length()),
                "501 原因内插前缀须与 #16 provider 面同源，禁本类再拼第二套信封");
        assertTrue(health.getBody().getMessage().contains("模型维度熔断态与探测读数无载体"),
                "机读原因落在 ApiResponse.message（data.message 是 AiErrorClass 模板渲染）");
    }

    private static JsonNode json(MvcResult r) throws Exception {
        return new ObjectMapper().readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static String jsonSerialize(Map<String, Object> data) {
        try {
            return new ObjectMapper().writeValueAsString(data);
        } catch (Exception e) {
            throw new AssertionError("夹具序列化失败: " + e.getMessage(), e);
        }
    }
}
