package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentReadinessDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentReadinessGapDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentReadinessItemDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentQuestionDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentQuestionService;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentOrchestrationAdvice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** §5.2 #12/#13 readiness：只读实装 + 无评估 404（禁合成默认级）+ 补齐未接线如实 501。 */
class WAgentReadinessControllerTest {

    private WAgentQuestionService questionService;
    private WAgentReadinessDao readinessDao;
    private WAgentReadinessItemDao itemDao;
    private WAgentReadinessGapDao gapDao;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        questionService = mock(WAgentQuestionService.class);
        readinessDao = mock(WAgentReadinessDao.class);
        itemDao = mock(WAgentReadinessItemDao.class);
        gapDao = mock(WAgentReadinessGapDao.class);
        WAgentIdentityResolver identityResolver = mock(WAgentIdentityResolver.class);
        when(identityResolver.require()).thenReturn(new Identity("t1", "u1", "o1"));
        when(questionService.get(any(), eq("t1"))).thenReturn(new WAgentQuestionDO());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new WAgentReadinessController(
                        questionService, readinessDao, itemDao, gapDao, identityResolver))
                .setControllerAdvice(new WAgentOrchestrationAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    @DisplayName("#12 当前评估在位 ⇒ 200，出等级/缺项/缺口三面（快照原样）")
    void readinessReturnsSnapshot() throws Exception {
        WAgentReadinessDO a = new WAgentReadinessDO();
        a.setId("ra1");
        a.setQuestionId("q1");
        a.setTenantId("t1");
        a.setGrade("B");
        a.setLayerD("pass");
        when(readinessDao.findCurrentByQuestionId("q1")).thenReturn(Optional.of(a));
        when(itemDao.findByAssessmentId("ra1")).thenReturn(List.of());
        when(gapDao.findByAssessmentId("ra1")).thenReturn(List.of());

        MvcResult r = mockMvc.perform(get("/api/v1/wagent/readiness/q1")).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        JsonNode json = new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("B", json.path("data").path("grade").asText());
        assertEquals(true, json.path("data").has("items"));
        assertEquals(true, json.path("data").has("gaps"));
    }

    @Test
    @DisplayName("#12 无评估行 ⇒ 404 E-NOT-FOUND（禁伪造默认等级）")
    void missingAssessmentIs404() throws Exception {
        when(readinessDao.findCurrentByQuestionId("q2")).thenReturn(Optional.empty());
        MvcResult r = mockMvc.perform(get("/api/v1/wagent/readiness/q2")).andReturn();
        assertEquals(404, r.getResponse().getStatus());
        JsonNode json = new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("E-NOT-FOUND", json.path("errorCode").asText());
    }

    @Test
    @DisplayName("#12 跨租户评估行掩 404（防枚举，§5.3）")
    void crossTenantMaskedAs404() throws Exception {
        WAgentReadinessDO a = new WAgentReadinessDO();
        a.setId("ra9");
        a.setTenantId("other");
        when(readinessDao.findCurrentByQuestionId("q3")).thenReturn(Optional.of(a));
        assertEquals(404, mockMvc.perform(get("/api/v1/wagent/readiness/q3"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("#13 补齐循环未接线 ⇒ 存在性校验先行（无评估 404）")
    void refillFailsFastBefore501() throws Exception {
        when(readinessDao.findCurrentByQuestionId("q4")).thenReturn(Optional.empty());
        assertEquals(404, mockMvc.perform(post("/api/v1/wagent/readiness/q4/refill"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("#13 评估在位 ⇒ 如实 501 NOT_IMPLEMENTED（禁伪称已补齐）")
    void refillHonest501() throws Exception {
        WAgentReadinessDO a = new WAgentReadinessDO();
        a.setId("ra5");
        a.setTenantId("t1");
        when(readinessDao.findCurrentByQuestionId("q5")).thenReturn(Optional.of(a));
        MvcResult r = mockMvc.perform(post("/api/v1/wagent/readiness/q5/refill")).andReturn();
        assertEquals(501, r.getResponse().getStatus());
        JsonNode json = new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("NOT_IMPLEMENTED", json.path("errorCode").asText());
    }

    @Test
    @DisplayName("循环上限口径冻结（§5.2 表：循环 ≤3）")
    void refillRoundCapFrozen() {
        assertEquals(3, WAgentReadinessController.MAX_REFILL_ROUNDS);
        // notFound 语义锚：与 403 文案不可区分（防枚举）
        assertEquals("E-NOT-FOUND",
                WAgentOrchestrationException.notFound().errorCode().wireCode());
    }
}
