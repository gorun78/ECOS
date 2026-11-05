package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentGoalDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.SlotFillingService;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentGoalService;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentOrchestrationAdvice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Goal 面 wire 键面门（DI-2 #147CS）。
 *
 * <p>{@code view()} 原是 12 键 {@code LinkedHashMap}；改 record 后本题按**键集相等 + 键序**
 * 钉住，防漏注解导致 snake→camel 静默漂移。</p>
 */
class WAgentGoalWireShapeTest {

    private static final List<String> GOAL_KEYS = List.of(
            "goal_id", "name", "goal_type", "status", "version_no", "supersedes_goal_id",
            "caliber_version", "metric_id", "metric_version", "as_of_date", "inferred_count",
            "all_slots_inferred_warning");

    private WAgentGoalService goalService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        goalService = mock(WAgentGoalService.class);
        SlotFillingService slotFillingService = mock(SlotFillingService.class);
        WAgentIdentityResolver identityResolver = mock(WAgentIdentityResolver.class);
        when(identityResolver.require()).thenReturn(new Identity("t1", "u1", "o1"));
        when(slotFillingService.raisesAllInferredWarning(any())).thenReturn(true);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WAgentGoalController(goalService, slotFillingService, identityResolver))
                .setControllerAdvice(new WAgentOrchestrationAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    private static WAgentGoalDO goal() {
        WAgentGoalDO g = new WAgentGoalDO();
        g.setId("g1");
        g.setName("提升交付准时率");
        g.setGoalType("DELIVERY");
        g.setStatus("ACTIVE");
        g.setCaliberVersion("v1");
        g.setMetricId("m1");
        g.setMetricVersion("1");
        g.setAsOfDate(LocalDate.of(2026, 11, 5));
        g.setInferredCount(2);
        g.setSupersedesGoalId(null);
        return g;
    }

    private static List<String> keys(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private JsonNode dataOf(MvcResult r) throws Exception {
        return new ObjectMapper().readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
    }

    @Test
    @DisplayName("#1 创建：12 键且键序与旧 Map 一致，Location 指向修订面")
    void createShape() throws Exception {
        when(goalService.create(any())).thenReturn(goal());

        MvcResult r = mockMvc.perform(post("/api/v1/wagent/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"提升交付准时率\"}"))
                .andReturn();

        assertEquals(201, r.getResponse().getStatus());
        assertEquals(12, keys(dataOf(r)).size());
        assertEquals(GOAL_KEYS, keys(dataOf(r)));
        assertEquals("2026-11-05", dataOf(r).path("as_of_date").asText());
        assertTrue(dataOf(r).path("all_slots_inferred_warning").asBoolean());
        assertTrue(r.getResponse().getHeader("Location").endsWith("/goals/g1"));
    }

    @Test
    @DisplayName("#2 修订：口径字段走 revise，响应同 12 键")
    void reviseShape() throws Exception {
        when(goalService.revise(eq("g1"), any(), eq("u1"), eq("t1"))).thenReturn(goal());

        JsonNode data = dataOf(mockMvc.perform(patch("/api/v1/wagent/goals/g1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metricId\":\"m2\"}"))
                .andReturn());

        assertEquals(GOAL_KEYS, keys(data));
        assertEquals("g1", data.path("goal_id").asText());
        assertEquals(2, data.path("inferred_count").asInt());
    }
}
