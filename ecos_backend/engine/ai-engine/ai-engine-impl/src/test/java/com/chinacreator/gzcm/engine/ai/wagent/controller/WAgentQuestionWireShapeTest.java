package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentQuestionDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.IntentClassifyPort;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.SlotFillingService;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentQuestionService;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentQuestionService.QuestionOutcome;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Question 面 wire 键面门（DI-2 #147CT）。
 *
 * <p>三条形状须逐字保真：READY 9 键、CLARIFY 12 键、详情 7 键或 8 键（{@code latest_run}
 * 仅在问题上带 latest_run_id 时出现；行缺失时键仍在、值为 null）。</p>
 */
class WAgentQuestionWireShapeTest {

    private static final List<String> QUESTION_KEYS = List.of(
            "question_id", "mission_id", "intent", "intent_confidence", "status",
            "latest_run_id", "answer_summary_text");

    private WAgentQuestionService questionService;
    private SlotFillingService slotFillingService;
    private WAgentRunDao runDao;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        questionService = mock(WAgentQuestionService.class);
        slotFillingService = mock(SlotFillingService.class);
        runDao = mock(WAgentRunDao.class);
        WAgentIdentityResolver identityResolver = mock(WAgentIdentityResolver.class);
        when(identityResolver.require()).thenReturn(new Identity("t1", "u1", "o1"));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WAgentQuestionController(questionService, slotFillingService,
                        runDao, identityResolver))
                .setControllerAdvice(new WAgentOrchestrationAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    private static WAgentQuestionDO question(String latestRunId) {
        WAgentQuestionDO q = new WAgentQuestionDO();
        q.setId("q1");
        q.setMissionId("m1");
        q.setIntent("deviation_query");
        q.setIntentConfidence(new BigDecimal("0.82"));
        q.setStatus("READY");
        q.setLatestRunId(latestRunId);
        q.setAnswerSummaryText("摘要");
        return q;
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
    @DisplayName("#3 READY：9 键，无澄清三键")
    void createReadyShape() throws Exception {
        when(questionService.fromUtterance(any()))
                .thenReturn(new QuestionOutcome(question(null), null, false));

        JsonNode data = dataOf(mockMvc.perform(post("/api/v1/wagent/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rawText\":\"本月营收偏差\"}"))
                .andReturn());

        assertEquals(List.of("question_id", "mission_id", "intent", "intent_confidence", "status",
                "latest_run_id", "answer_summary_text", "outcome", "requires_clarify"), keys(data));
        assertEquals("READY", data.path("outcome").asText());
    }

    @Test
    @DisplayName("#3 CLARIFY：12 键（追加澄清项/分类器可用位/阈值）")
    void createClarifyShape() throws Exception {
        when(questionService.fromUtterance(any())).thenReturn(new QuestionOutcome(question(null),
                new IntentClassifyPort.Verdict(null, null, false), true));
        when(slotFillingService.nextClarificationItems(any()))
                .thenReturn(List.of("intent", "time_range"));

        JsonNode data = dataOf(mockMvc.perform(post("/api/v1/wagent/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rawText\":\"看看\"}"))
                .andReturn());

        assertEquals(List.of("question_id", "mission_id", "intent", "intent_confidence", "status",
                "latest_run_id", "answer_summary_text", "outcome", "requires_clarify",
                "clarification_items", "classifier_available", "confidence_threshold"), keys(data));
        assertEquals("CLARIFY", data.path("outcome").asText());
        assertEquals(2, data.path("clarification_items").size());
        assertEquals(false, data.path("classifier_available").asBoolean());
        assertEquals("0.70", data.path("confidence_threshold").asText());
    }

    @Test
    @DisplayName("#4 详情：无 latest_run_id ⇒ 7 键")
    void detailWithoutRunShape() throws Exception {
        when(questionService.get("q1", "t1")).thenReturn(question(null));

        JsonNode data = dataOf(mockMvc.perform(get("/api/v1/wagent/questions/q1")).andReturn());

        assertEquals(QUESTION_KEYS, keys(data));
    }

    @Test
    @DisplayName("#4 详情：有 latest_run_id 且行在 ⇒ 8 键，latest_run 4 键")
    void detailWithRunShape() throws Exception {
        when(questionService.get("q1", "t1")).thenReturn(question("r1"));
        WAgentRunDO run = new WAgentRunDO();
        run.setId("r1");
        run.setStatus("executing");
        run.setPlanSource("PLAYBOOK");
        run.setAutomationCeiling(1);
        when(runDao.findById("r1")).thenReturn(Optional.of(run));

        JsonNode data = dataOf(mockMvc.perform(get("/api/v1/wagent/questions/q1")).andReturn());

        List<String> expected = new ArrayList<>(QUESTION_KEYS);
        expected.add("latest_run");
        assertEquals(expected, keys(data));
        assertEquals(List.of("run_id", "status", "plan_source", "automation_ceiling"),
                keys(data.path("latest_run")));
    }

    @Test
    @DisplayName("#4 详情：有 latest_run_id 但行缺失 ⇒ 键仍在、值为 null（逐字保旧）")
    void detailWithMissingRunKeepsNullKey() throws Exception {
        when(questionService.get("q1", "t1")).thenReturn(question("r-gone"));
        when(runDao.findById("r-gone")).thenReturn(Optional.empty());

        JsonNode data = dataOf(mockMvc.perform(get("/api/v1/wagent/questions/q1")).andReturn());

        assertTrue(data.has("latest_run"));
        assertTrue(data.path("latest_run").isNull());
    }
}
