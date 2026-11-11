package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrator;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrator.RunTimelineVO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrator.TimelineEventVO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrator.TimelineStepVO;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * #5~#10 Run 面 wire 键面门（DI-2 #147CQ）。
 *
 * <p>换载体前 {@code runView}/{@code progressCapabilityBody}/{@code timeline} 都是
 * {@code LinkedHashMap} 拼装；改 record 后靠本类按**键集相等 + 键序**兜住改名/漏注解。
 * 特别注意两条通道的**条件键**：同步通道只有 9 键，异步通道才多出
 * {@code execution_dispatch}/{@code progress_channel}。</p>
 */
class WAgentRunWireShapeTest {

    private static final List<String> RUN_KEYS = List.of(
            "run_id", "question_id", "mission_id", "status", "plan_source", "automation_ceiling",
            "start_time", "end_time");

    private WAgentOrchestrator orchestrator;
    private com.chinacreator.gzcm.engine.ai.wagent.approval.ApprovalTokenService approvalTokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        orchestrator = mock(WAgentOrchestrator.class);
        WAgentIdentityResolver identityResolver = mock(WAgentIdentityResolver.class);
        when(identityResolver.require()).thenReturn(new Identity("t1", "u1", "o1"));
        approvalTokenService = mock(
                com.chinacreator.gzcm.engine.ai.wagent.approval.ApprovalTokenService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WAgentRunController(orchestrator, identityResolver,
                        approvalTokenService))
                .setControllerAdvice(new WAgentOrchestrationAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    private WAgentRunDO runFixture() {
        WAgentRunDO run = new WAgentRunDO();
        run.setId("r1");
        run.setTenantId("t1");
        run.setQuestionId("q1");
        run.setMissionId("m1");
        run.setStatus("executing");
        run.setPlanSource("PLAYBOOK");
        run.setAutomationCeiling(1);
        run.setStartTime(LocalDateTime.of(2026, 11, 5, 9, 0, 0));
        return run;
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
    @DisplayName("#5 同步通道：9 键（Run 8 + idempotent_replay），无异步两键")
    void startRunSyncShape() throws Exception {
        WAgentRunDO run = runFixture();
        when(orchestrator.startRun(any()))
                .thenReturn(new WAgentOrchestrator.RunStartOutcome(run, true));
        when(orchestrator.runPipeline(eq("r1"), eq("u1"))).thenReturn(run);

        JsonNode data = dataOf(mockMvc.perform(post("/api/v1/wagent/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"q1\"}"))
                .andReturn());

        assertEquals(append(RUN_KEYS, "idempotent_replay"), keys(data));
        assertTrue(data.path("idempotent_replay").asBoolean());
        assertEquals("2026-11-05T09:00", data.path("start_time").asText());
        assertFalse(data.has("execution_dispatch"));
        assertFalse(data.has("progress_channel"));
    }

    @Test
    @DisplayName("#5 异步通道：202 + 11 键，progress_channel 4 键")
    void startRunAsyncShape() throws Exception {
        when(orchestrator.startRun(any()))
                .thenReturn(new WAgentOrchestrator.RunStartOutcome(runFixture(), false));

        MvcResult r = mockMvc.perform(post("/api/v1/wagent/runs")
                        .header("Prefer", "respond-async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"q1\"}"))
                .andReturn();

        assertEquals(202, r.getResponse().getStatus());
        JsonNode data = dataOf(r);
        assertEquals(List.of("run_id", "question_id", "mission_id", "status", "plan_source",
                "automation_ceiling", "start_time", "end_time",
                "idempotent_replay", "execution_dispatch", "progress_channel"), keys(data));
        assertEquals("pending#wagent-run-dispatch(#130)", data.path("execution_dispatch").asText());
        assertEquals(List.of("channel", "degrade_code", "poll_path", "fallback"),
                keys(data.path("progress_channel")));
    }

    @Test
    @DisplayName("#6 详情：Run 8 键 + timeline（events 4 键 / steps 8 键）")
    void getRunShape() throws Exception {
        when(orchestrator.requireRunForTenant("r1", "t1")).thenReturn(runFixture());
        when(orchestrator.timeline("r1")).thenReturn(new RunTimelineVO("r1", "executing",
                List.of(new TimelineEventVO("step_started", "s1", "2026-11-05T09:01", "ref:1")),
                List.of(new TimelineStepVO("s1", 1, "TOOL", "succeeded", 1, "ih", "oh", null))));

        JsonNode data = dataOf(mockMvc.perform(get("/api/v1/wagent/runs/r1")).andReturn());

        assertEquals(append(RUN_KEYS, "timeline"), keys(data));
        JsonNode timeline = data.path("timeline");
        assertEquals(List.of("run_id", "status", "events", "steps"), keys(timeline));
        assertEquals(List.of("event_type", "step_id", "occur_time", "payload_ref"),
                keys(timeline.path("events").get(0)));
        assertEquals(List.of("step_key", "attempt", "step_type", "status", "automation_level",
                "input_hash", "output_hash", "error_code"), keys(timeline.path("steps").get(0)));
    }

    @Test
    @DisplayName("进度能力位：4 键（channel/degrade_code/poll_path/fallback）")
    void progressCapabilityShape() throws Exception {
        when(orchestrator.requireRunForTenant("r1", "t1")).thenReturn(runFixture());

        JsonNode data = dataOf(mockMvc.perform(get("/api/v1/wagent/runs/r1/progress-capability"))
                .andReturn());

        assertEquals(List.of("channel", "degrade_code", "poll_path", "fallback"), keys(data));
        assertEquals("polling", data.path("channel").asText());
        assertEquals("DG-M1", data.path("degrade_code").asText());
    }

    @Test
    @DisplayName("#9 审批：凭证校验失败 ⇒ 非 2xx 且绝不清闸（approveGate 零调用）")
    void approveGateIsNeverClearedWhenTokenRejected() throws Exception {
        doThrow(com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException
                .validation("凭证无效（越权/过期/自审批）"))
                .when(approvalTokenService)
                .verifyAndConsume(any(), any(), any(), any(), any());

        int status = mockMvc.perform(post("/api/v1/wagent/runs/r1/approval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalToken\":\"forged\"}"))
                .andReturn().getResponse().getStatus();

        assertTrue(status >= 400, "凭证被拒时不得返回 2xx，实得 " + status);
        verify(orchestrator, never()).approveGate(any(), any(), any());
        verify(orchestrator, never()).runPipeline(any(), any());
    }

    @Test
    @DisplayName("#9 审批：凭证通过后才清闸，且先校验后清闸（顺序不可倒）")
    void verificationHappensBeforeGateClearance() throws Exception {
        WAgentRunDO run = runFixture();
        when(orchestrator.approveGate(any(), any(), any())).thenReturn(run);

        int status = mockMvc.perform(post("/api/v1/wagent/runs/r1/approval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalToken\":\"valid\"}")
                        .header("Prefer", "respond-async"))
                .andReturn().getResponse().getStatus();

        org.mockito.InOrder order = inOrder(approvalTokenService, orchestrator);
        order.verify(approvalTokenService).verifyAndConsume(any(), any(), any(), any(), any());
        order.verify(orchestrator).approveGate(any(), any(), any());
        assertEquals(200, status);
    }

    @Test
    @DisplayName("#7 SSE 未就绪：502 + 能力位 4 键 + last_event_id_received")
    void sseFallbackShape() throws Exception {
        when(orchestrator.requireRunForTenant("r1", "t1")).thenReturn(runFixture());

        MvcResult r = mockMvc.perform(get("/api/v1/wagent/runs/r1/events")
                        .header("Last-Event-ID", "evt-7"))
                .andReturn();

        assertEquals(502, r.getResponse().getStatus());
        JsonNode data = dataOf(r);
        assertEquals(List.of("channel", "degrade_code", "poll_path", "fallback",
                "last_event_id_received"), keys(data));
        assertTrue(data.path("last_event_id_received").asBoolean());
    }

    @Test
    @DisplayName("#10 取消：Run 8 键（不追加任何条件键）")
    void cancelShape() throws Exception {
        when(orchestrator.requireRunForTenant("r1", "t1")).thenReturn(runFixture());
        when(orchestrator.cancel("r1", "u1")).thenReturn(runFixture());

        JsonNode data = dataOf(mockMvc.perform(post("/api/v1/wagent/runs/r1/cancel")).andReturn());

        assertEquals(RUN_KEYS, keys(data));
    }

    private static List<String> append(List<String> base, String extra) {
        List<String> list = new ArrayList<>(base);
        list.add(extra);
        return list;
    }
}
