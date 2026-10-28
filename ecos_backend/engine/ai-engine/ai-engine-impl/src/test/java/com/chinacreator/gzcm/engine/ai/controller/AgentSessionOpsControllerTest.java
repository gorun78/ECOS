package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.engine.ai.dao.AgentSessionDao;
import com.chinacreator.gzcm.engine.ai.dao.GuardrailDecisionLedgerDao;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.security.ToolSubjectResolver;
import com.chinacreator.gzcm.engine.ai.service.AgentSessionService;
import com.chinacreator.gzcm.engine.ai.service.SessionCompressionService;
import com.chinacreator.gzcm.engine.ai.service.SessionCompressionService.Outcome;
import com.chinacreator.gzcm.engine.ai.service.SessionCompressionService.Settings;
import com.chinacreator.gzcm.engine.ai.service.SessionCompressionService.Status;
import com.chinacreator.gzcm.engine.ai.service.SessionTokenQuotaService;
import com.chinacreator.gzcm.engine.ai.service.SessionTokenQuotaService.QuotaState;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway.ModelLimit;
import com.chinacreator.gzcm.runtime.llm.metrics.AgentMetrics;
import com.chinacreator.gzcm.runtime.llm.model.AgentCallLog;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * P1.3 会话运维链契约测试（#12 context / #13 compress / #15 usage / #4 messages）。
 *
 * <p>#12/#13/#15 端点在 {@link AgentSessionOpsController}，#4 端点在
 * {@link AgentLoopController}（@Autowired 字段，用反射注入 sessionService 验证 404 分支）。</p>
 */
class AgentSessionOpsControllerTest {

    private AgentSessionService sessionService;
    private AgentSessionDao sessionDao;
    private SessionCompressionService compressionService;
    private SessionTokenQuotaService quotaService;
    private LLMGateway llmGateway;
    private AgentMetrics agentMetrics;
    private GuardrailDecisionLedgerDao guardrailLedgerDao;
    private ToolSubjectResolver toolSubjectResolver;
    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        sessionService       = mock(AgentSessionService.class);
        sessionDao           = mock(AgentSessionDao.class);
        compressionService   = mock(SessionCompressionService.class);
        quotaService         = mock(SessionTokenQuotaService.class);
        llmGateway           = mock(LLMGateway.class);
        agentMetrics         = mock(AgentMetrics.class);
        guardrailLedgerDao   = mock(GuardrailDecisionLedgerDao.class);
        toolSubjectResolver  = mock(ToolSubjectResolver.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AgentSessionOpsController(
                        sessionService, sessionDao, compressionService,
                        quotaService, llmGateway, agentMetrics,
                        guardrailLedgerDao, toolSubjectResolver))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  #12 GET /api/v1/agent/sessions/{sessionId}/context
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("#12 会话不存在 ⇒ 404 ECOS-AI-404（禁合成预算伪数据）")
    void contextMissingSessionIs404() throws Exception {
        when(sessionService.getSession("nope")).thenReturn(null);
        MvcResult r = mockMvc.perform(get("/api/v1/agent/sessions/nope/context")).andReturn();
        assertEquals(404, r.getResponse().getStatus());
        JsonNode json = mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("ECOS-AI-404", json.path("errorCode").asText());
    }

    @Test
    @DisplayName("#12 会话存在 ⇒ 200，limit/threshold 按模型目录×字典阈值计算，estimated 标志显式标注")
    void contextReturnsBudgetAndQuota() throws Exception {
        AgentSessionService.AgentSession session = new AgentSessionService.AgentSession();
        session.setId("s1");
        session.setAgentId("agent-x");
        when(sessionService.getSession("s1")).thenReturn(session);
        // V202 治理列 rows 全部 null → 走估算兜底分支
        when(sessionDao.tokenStats("s1")).thenReturn(new Object[]{null, null, null});
        when(sessionDao.governanceColumnsReady()).thenReturn(false);
        when(sessionService.getMessages("s1")).thenReturn(List.of());
        when(compressionService.estimateSessionTokens(anyList())).thenReturn(0);
        // 模型目录：agent-x 未登记 ⇒ fallback 到 "default" 已登记
        when(llmGateway.modelContextLimit("agent-x")).thenReturn(ModelLimit.unavailable("agent-x"));
        when(llmGateway.modelContextLimit("default"))
                .thenReturn(new ModelLimit("default", 32_000, true, true));
        when(compressionService.settings()).thenReturn(new Settings(true, true, 0.6, 6));
        when(quotaService.check("s1"))
                .thenReturn(new QuotaState(true, false, 1_000L, 10_000L, null));

        MvcResult r = mockMvc.perform(get("/api/v1/agent/sessions/s1/context")).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        JsonNode data = mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertEquals(32_000, data.path("limit").asInt());
        // threshold = floor(32_000 * 0.6) = 19_200
        assertEquals(19_200, data.path("threshold").asInt());
        assertEquals(true, data.path("estimated").asBoolean(), "estimated=false 需要真值计量读数");
        assertEquals(0, data.path("compressedTurns").asInt());
        assertTrue(data.has("quotaEnforced"));
    }

    // ═══════════════════════════════════════════════════════════════
    //  #13 POST /api/v1/agent/sessions/{sessionId}/compress
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("#13 会话不存在 ⇒ 404 ECOS-AI-404（不进入压缩服务）")
    void compressMissingSessionIs404() throws Exception {
        when(sessionService.getSession("nope")).thenReturn(null);
        MvcResult r = mockMvc.perform(post("/api/v1/agent/sessions/nope/compress")
                .contentType("application/json").content("{}")).andReturn();
        assertEquals(404, r.getResponse().getStatus());
        JsonNode json = mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("ECOS-AI-404", json.path("errorCode").asText());
    }

    @Test
    @DisplayName("#13 开关关闭 ⇒ 200 + compressSkipped=true + status=SWITCH_OFF（禁静默关闭）")
    void compressSwitchOffIs200WithSkip() throws Exception {
        AgentSessionService.AgentSession session = new AgentSessionService.AgentSession();
        session.setId("s2"); session.setAgentId("agent-x");
        when(sessionService.getSession("s2")).thenReturn(session);
        // Outcome.of(…) 是 package-private，用 public 规范构造器
        Outcome switchOff = new Outcome(Status.SWITCH_OFF, true, null,
                0, 0, 0, 0, 0, 0, null, null, "switch_off", 0);
        when(compressionService.compress(eq("s2"), anyString(), anyString(), isNull()))
                .thenReturn(switchOff);

        MvcResult r = mockMvc.perform(post("/api/v1/agent/sessions/s2/compress")
                .contentType("application/json").content("{}")).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        JsonNode data = mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertEquals("SWITCH_OFF", data.path("status").asText());
        assertEquals(true, data.path("compressSkipped").asBoolean());
        assertEquals("agent-x", data.path("model").asText());
    }

    @Test
    @DisplayName("#13 SCHEMA_UNAVAILABLE ⇒ 503 ECOS-AI-503（依赖不可得，禁 200 伪装已压缩）")
    void compressSchemaUnavailableIs503() throws Exception {
        AgentSessionService.AgentSession session = new AgentSessionService.AgentSession();
        session.setId("s3"); session.setAgentId("agent-x");
        when(sessionService.getSession("s3")).thenReturn(session);
        Outcome unavail = new Outcome(Status.SCHEMA_UNAVAILABLE, false, null,
                0, 0, 0, 0, 0, 0, null, null, "schema_not_ready", 0);
        when(compressionService.compress(eq("s3"), anyString(), anyString(), isNull()))
                .thenReturn(unavail);

        MvcResult r = mockMvc.perform(post("/api/v1/agent/sessions/s3/compress")
                .contentType("application/json").content("{}")).andReturn();
        assertEquals(503, r.getResponse().getStatus());
        JsonNode json = mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("ECOS-AI-503", json.path("errorCode").asText());
    }

    @Test
    @DisplayName("#13 MODEL_LIMIT_UNAVAILABLE ⇒ 503（不可按'无需压缩'静默放行）")
    void compressModelLimitUnavailableIs503() throws Exception {
        AgentSessionService.AgentSession session = new AgentSessionService.AgentSession();
        session.setId("s4"); session.setAgentId("agent-x");
        when(sessionService.getSession("s4")).thenReturn(session);
        Outcome unavail = new Outcome(Status.MODEL_LIMIT_UNAVAILABLE, false, null,
                0, 0, 0, 0, 0, 0, null, null, "model_not_in_catalog", 0);
        when(compressionService.compress(eq("s4"), anyString(), anyString(), isNull()))
                .thenReturn(unavail);

        MvcResult r = mockMvc.perform(post("/api/v1/agent/sessions/s4/compress")
                .contentType("application/json").content("{}")).andReturn();
        assertEquals(503, r.getResponse().getStatus());
    }

    // ═══════════════════════════════════════════════════════════════
    //  #15 GET /api/v1/agent/usage
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("#15 period 格式非法 ⇒ 400 ECOS-AI-400")
    void usageInvalidPeriodIs400() throws Exception {
        when(agentMetrics.getRecentCalls(eq("ai-engine"), anyInt())).thenReturn(List.of());
        MvcResult r = mockMvc.perform(get("/api/v1/agent/usage")
                .param("period", "bad-format")).andReturn();
        assertEquals(400, r.getResponse().getStatus());
        JsonNode json = mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("ECOS-AI-400", json.path("errorCode").asText());
    }

    @Test
    @DisplayName("#15 无 period 默认当天桶 ⇒ 200，estimated=false（llm-gateway 实测计量口径）")
    void usageReturnsAggregatedBuckets() throws Exception {
        LocalDateTime today = LocalDateTime.now();
        AgentCallLog call = AgentCallLog.builder()
                .id("cl1").subsystem("ai-engine").profileName("agent-x")
                .tokensInput(100).tokensOutput(50)
                .createdTime(today).build();
        when(agentMetrics.getRecentCalls(eq("ai-engine"), anyInt()))
                .thenReturn(List.of(call));

        MvcResult r = mockMvc.perform(get("/api/v1/agent/usage")).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        JsonNode data = mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertEquals("ai-engine", data.path("subsystem").asText());
        assertEquals(1, data.path("itemCount").asInt());
        assertEquals(100L, data.path("totals").path("promptTokens").asLong());
        assertEquals(50L, data.path("totals").path("completionTokens").asLong());
        JsonNode item = data.path("items").path(0);
        assertEquals(false, item.path("estimated").asBoolean());
    }

    // ═══════════════════════════════════════════════════════════════
    //  #14 GET /api/v1/agent/guardrail-decisions
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("#14 无 token 主体 ⇒ 403 ECOS-AI-403（禁伪造空台账放行）")
    void guardrailDecisionsNoSubjectIs403() throws Exception {
        when(toolSubjectResolver.resolve()).thenReturn(null);
        MvcResult r = mockMvc.perform(get("/api/v1/agent/guardrail-decisions")).andReturn();
        assertEquals(403, r.getResponse().getStatus());
        JsonNode json = mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("ECOS-AI-403", json.path("errorCode").asText());
    }

    @Test
    @DisplayName("#14 主体无 AI_GUARDIAN 角色 ⇒ 403（角色门禁先于台账读数，禁读面伪造）")
    void guardrailDecisionsNonGuardianRoleIs403() throws Exception {
        when(toolSubjectResolver.resolve())
                .thenReturn(new ToolAdjudicationRequest.Subject("u1", List.of("operator"), null, "t1"));
        MvcResult r = mockMvc.perform(get("/api/v1/agent/guardrail-decisions")).andReturn();
        assertEquals(403, r.getResponse().getStatus());
        assertEquals("ECOS-AI-403",
                mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .path("errorCode").asText());
    }

    @Test
    @DisplayName("#14 AI_GUARDIAN 主体 ⇒ 200，items 逐键映射且 items 不投影 obligations/subject 面")
    void guardrailDecisionsAsGuardianReturnsItems() throws Exception {
        when(toolSubjectResolver.resolve())
                .thenReturn(new ToolAdjudicationRequest.Subject("u1", List.of("AI_GUARDIAN"), null, "t1"));
        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("trace_id", "tr-1");
        row.put("session_id", "s9");
        row.put("tool_key", "query_data");
        row.put("decision", "DENY");
        row.put("policy_id", "pol-1");
        row.put("latency_ms", 42);
        row.put("create_time", java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 10, 27, 9, 0, 0)));
        when(guardrailLedgerDao.listFiltered(isNull(), isNull(), isNull(), isNull(), isNull(), org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(List.of(row));

        MvcResult r = mockMvc.perform(get("/api/v1/agent/guardrail-decisions")).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        JsonNode data = mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertEquals(1, data.path("itemCount").asInt());
        JsonNode item = data.path("items").path(0);
        assertEquals("tr-1", item.path("traceId").asText());
        assertEquals("query_data", item.path("toolKey").asText());
        assertEquals("DENY", item.path("decision").asText());
        assertEquals("pol-1", item.path("policyRef").asText());
        assertEquals(42, item.path("latencyMs").asInt());
        assertEquals("2026-10-27T09:00:00", item.path("createTime").asText());
    }

    // ═══════════════════════════════════════════════════════════════
    //  #4 GET /api/v1/agent-loop/sessions/{id}/messages
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("#4 会话不存在 ⇒ 信封 notFound（code=404，禁合成消息伪数据）")
    void messagesMissingSessionIs404() throws Exception {
        when(sessionService.getSession("nope")).thenReturn(null);
        when(sessionService.getMessages("nope")).thenReturn(List.of());
        AgentLoopController controller = new AgentLoopController();
        try {
            var field = AgentLoopController.class.getDeclaredField("sessionService");
            field.setAccessible(true);
            field.set(controller, sessionService);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("测试装配失败: " + e.getMessage(), e);
        }
        MockMvc loopMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
        MvcResult r = loopMvc.perform(
                get("/api/v1/agent-loop/sessions/nope/messages")).andReturn();
        // 该 Controller 的 notFound 口径 = 信封 code=404（HTTP 状态行 200，与该类其余端点一致）
        JsonNode json = mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(404, json.path("code").asInt(), "信封 code 必须为 404");
        assertTrue(json.path("message").asText().contains("不存在"), "消息须如实报会话不存在");
        assertFalse(json.path("data").isArray(), "不存在时 data 不得合成消息列表伪数据");
    }
}
