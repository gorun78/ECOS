package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.engine.kb.cognition.CognitionContractGuard;
import com.chinacreator.gzcm.engine.kb.cognition.CognitionReadService;
import com.chinacreator.gzcm.engine.kb.support.KbErrorAdvice;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * F04-08 认知供给侧只读端点（KnowledgeCognitionSupplyController）MockMvc 验收
 * （2026-10-12 复盘补齐；standalone 模式 + 模块既有 {@link KbErrorAdvice}，
 * 与 ai-engine {@code WAgentGoalControllerIT} 同款 standaloneSetup 先例；
 * 本模块无 hamcrest 依赖，响应体经 Jackson 断言替代 jsonPath）。
 *
 * <p>内容协商注记：standaloneSetup 的转换器序会把 XML（Jaxb）排在 Jackson 前
 * （{@code ApiResponse} 带 JAXB 注解时响应体为 {@code <ApiResponse>…}），
 * 生产链路（Boot 容器 + 前端 fetch）为 JSON。用例统一显式
 * {@code Accept: application/json} 钉住 JSON 语义，与前端消费契约一致。</p>
 *
 * <p>断言：
 * <ol>
 *   <li>路径存在 — GET /api/v1/knowledge/cognition/{hypotheses|beliefs|minds} 命中映射（非 404）；</li>
 *   <li>fail-loud — 后端不可用（{@code DataAccessException} → ECOS-KB-073 DomainException）时
 *       返回 503 + 错误码，而<b>非</b>伪装"无数据"的空 200；</li>
 *   <li>禁写代理 — POST 命中 {@link CognitionContractGuard#assertReadOnly()} → 405 ECOS-KB-070；</li>
 *   <li>合法空态与故障态可区分（空 200 ≠ 503）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeCognitionSupplyControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private CognitionReadService cognitionReadService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        KnowledgeCognitionSupplyController controller =
                new KnowledgeCognitionSupplyController(cognitionReadService, new CognitionContractGuard());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new KbErrorAdvice())
                .build();
    }

    private static List<Map<String, Object>> oneRow() {
        Map<String, Object> r = new HashMap<>();
        r.put("id", "h-1");
        r.put("status", "EVIDENCED");
        r.put("domain", "sc-001");
        return new ArrayList<>(List.of(r));
    }

    /** 显式钉住 JSON 内容协商（见类头注记；生产 Boot 容器默认即 JSON） */
    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req) {
        return req.header("Accept", MediaType.APPLICATION_JSON_VALUE);
    }

    /** 非 JSON 响应体时把原文带进失败信息（定位内容协商/错误页问题） */
    private static JsonNode JSON(MvcResult res) throws Exception {
        String content = res.getResponse().getContentAsString();
        try {
            return MAPPER.readTree(content);
        } catch (Exception e) {
            throw new AssertionError("非 JSON 响应体: " + content.substring(0, Math.min(400, content.length())), e);
        }
    }

    @Test
    @DisplayName("①路径存在：GET /hypotheses → 200 + 真实行")
    void hypothesesPathExists() throws Exception {
        when(cognitionReadService.hypothesesSupply(anyString(), anyInt())).thenReturn(oneRow());
        MvcResult res = mockMvc.perform(json(get("/api/v1/knowledge/cognition/hypotheses")
                        .param("domain", "sc-001"))).andReturn();
        assertEquals(200, res.getResponse().getStatus(), "路径未映射会是 404");
        JsonNode body = JSON(res);
        assertEquals(0, body.get("code").asInt());
        assertEquals("EVIDENCED", body.get("data").get(0).get("status").asText());
    }

    @Test
    @DisplayName("①路径存在：GET /beliefs → 200")
    void beliefsPathExists() throws Exception {
        when(cognitionReadService.beliefsSupply(eq("sc-002"), eq(50))).thenReturn(oneRow());
        MvcResult res = mockMvc.perform(json(get("/api/v1/knowledge/cognition/beliefs")
                .param("domain", "sc-002"))).andReturn();
        assertEquals(200, res.getResponse().getStatus());
        assertEquals(0, JSON(res).get("code").asInt());
    }

    @Test
    @DisplayName("①路径存在：GET /minds → 200，limit 透传")
    void mindsPathExists() throws Exception {
        when(cognitionReadService.mindsSupply(eq("sc-003"), eq(10))).thenReturn(oneRow());
        MvcResult res = mockMvc.perform(json(get("/api/v1/knowledge/cognition/minds")
                .param("domain", "sc-003").param("limit", "10"))).andReturn();
        assertEquals(200, res.getResponse().getStatus());
        assertEquals("h-1", JSON(res).get("data").get(0).get("id").asText());
    }

    @Test
    @DisplayName("②fail-loud：后端不可用 → 503 ECOS-KB-073，绝不空 200 伪装无数据")
    void backendUnavailableReturns503NotEmptyOk() throws Exception {
        doThrow(KbErrorCodes.COGNITION_SUPPLY_UNAVAILABLE.exception(
                        "认知供给侧后端不可用（表缺失/访问被拒）table=ecos_cognitive.ecos_cognitive_hypothesis"))
                .when(cognitionReadService).hypothesesSupply(anyString(), anyInt());

        MvcResult res = mockMvc.perform(json(get("/api/v1/knowledge/cognition/hypotheses")
                .param("domain", "sc-004"))).andReturn();
        assertEquals(503, res.getResponse().getStatus(), "不可用必须 503，不得伪装空 200");
        assertEquals("ECOS-KB-073", JSON(res).get("errorCode").asText());
    }

    @Test
    @DisplayName("②真实空结果集仍 200 + 空 data（合法空态，与 503 故障态可区分）")
    void genuineEmptyReturns200EmptyList() throws Exception {
        when(cognitionReadService.mindsSupply(anyString(), anyInt())).thenReturn(new ArrayList<>());
        MvcResult res = mockMvc.perform(json(get("/api/v1/knowledge/cognition/minds")
                .param("domain", "sc-005"))).andReturn();
        assertEquals(200, res.getResponse().getStatus());
        JsonNode body = JSON(res);
        assertEquals(0, body.get("code").asInt());
        assertTrue(body.get("data").isEmpty(), "合法空态 = 空列表，故障态 = 503，两态可区分");
    }

    @Test
    @DisplayName("③禁写代理：POST /hypotheses → 405 ECOS-KB-070（REQ-KB-04）")
    void writeProxyRejected405() throws Exception {
        MvcResult res = mockMvc.perform(json(post("/api/v1/knowledge/cognition/hypotheses"))).andReturn();
        assertEquals(405, res.getResponse().getStatus());
        assertEquals("ECOS-KB-070", JSON(res).get("errorCode").asText());
    }
}
