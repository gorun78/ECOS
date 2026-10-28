package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolDecision;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolObligations;
import com.chinacreator.gzcm.engine.ai.security.AgentToolPolicyGate;
import com.chinacreator.gzcm.engine.ai.security.ToolSubjectResolver;
import com.chinacreator.gzcm.engine.ai.service.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * P1.3 场景工具干跑契约测试（附录 A 第 22 条 / 详设-06 §5.2 #10 / CLI {@code ecos ai tool dry-run}）。
 *
 * <p>准入三道的分态必须可区分：生产 profile ⇒ 403（先于一切解析）；
 * 注册缺位 ⇒ 404（真不存在，禁伪空裁决）；无主体/无 admin 角色 ⇒ 403（审批面）；
 * 裁决三态：DENY ⇒ 403、FAIL_CLOSED ⇒ 503、ALLOW ⇒ 200 且 {@code executed=false}
 * （干跑永不落副作用）。</p>
 */
class AgentToolControllerDryRunTest {

    private ToolRegistry toolRegistry;
    private AgentToolPolicyGate policyGate;
    private ToolSubjectResolver subjectResolver;
    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        toolRegistry = mock(ToolRegistry.class);
        policyGate = mock(AgentToolPolicyGate.class);
        subjectResolver = mock(ToolSubjectResolver.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AgentToolController(toolRegistry, policyGate,
                        subjectResolver, new MockEnvironment()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    private static ToolAdjudicationRequest.Subject subject(String userId, List<String> roles) {
        return new ToolAdjudicationRequest.Subject(userId, roles, null, "t1");
    }

    private MvcResult postDryRun(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/agent/tools/query_data/dry-run")
                .contentType("application/json")
                .content(body == null ? "" : body))
                .andReturn();
    }

    @Test
    void productionProfileIs403BeforeAnyParse() throws Exception {
        // 生产 profile：先于主体/裁决解析（禁在 prod 消耗裁决预算）
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("production");
        MockMvc prodMvc = MockMvcBuilders.standaloneSetup(new AgentToolController(
                        toolRegistry, policyGate, subjectResolver, prodEnv))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
        MvcResult r = prodMvc.perform(post("/api/v1/agent/tools/query_data/dry-run")
                .contentType("application/json").content("{}")).andReturn();
        assertEquals(403, r.getResponse().getStatus());
        assertEquals("ECOS-AI-403",
                mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .path("errorCode").asText());
        // 生产域 403 必须先于注册表与裁决面（禁在 prod 消耗裁决/留痕预算）
        verifyNoInteractions(policyGate);
    }

    @Test
    void unregisteredToolIs404() throws Exception {
        when(toolRegistry.get("query_data")).thenReturn(null);
        MvcResult r = postDryRun("{}");
        assertEquals(404, r.getResponse().getStatus());
        assertEquals("ECOS-AI-404",
                mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .path("errorCode").asText());
        verifyNoInteractions(policyGate);
    }

    @Test
    void noSubjectIs403FailClosedApproval() throws Exception {
        when(toolRegistry.get("query_data")).thenReturn(mock(com.chinacreator.gzcm.engine.ai.service.ToolSchema.class));
        when(subjectResolver.resolve()).thenReturn(null);
        MvcResult r = postDryRun("{}");
        assertEquals(403, r.getResponse().getStatus());
        assertEquals("ECOS-AI-403",
                mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .path("errorCode").asText());
        verifyNoInteractions(policyGate);
    }

    @Test
    void nonAdminRoleIs403() throws Exception {
        when(toolRegistry.get("query_data")).thenReturn(mock(com.chinacreator.gzcm.engine.ai.service.ToolSchema.class));
        when(subjectResolver.resolve()).thenReturn(subject("u1", List.of("operator")));
        MvcResult r = postDryRun("{}");
        assertEquals(403, r.getResponse().getStatus());
        verifyNoInteractions(policyGate);
    }

    @Test
    void denyVerdictIs403() throws Exception {
        when(toolRegistry.get("query_data")).thenReturn(mock(com.chinacreator.gzcm.engine.ai.service.ToolSchema.class));
        when(subjectResolver.resolve()).thenReturn(subject("u1", List.of("Admin")));
        when(policyGate.review(any(ToolAdjudicationRequest.class)))
                .thenReturn(ToolDecision.deny("策略拒绝", "pol-9", 5));
        MvcResult r = postDryRun("{}");
        assertEquals(403, r.getResponse().getStatus());
        assertEquals("ECOS-AI-403",
                mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .path("errorCode").asText());
    }

    @Test
    void failClosedVerdictIs503() throws Exception {
        when(toolRegistry.get("query_data")).thenReturn(mock(com.chinacreator.gzcm.engine.ai.service.ToolSchema.class));
        when(subjectResolver.resolve()).thenReturn(subject("u1", List.of("role_admin")));
        when(policyGate.review(any(ToolAdjudicationRequest.class)))
                .thenReturn(ToolDecision.failClosed("security engine unavailable", 5));
        MvcResult r = postDryRun("{}");
        assertEquals(503, r.getResponse().getStatus());
        assertEquals("ECOS-AI-503",
                mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .path("errorCode").asText());
    }

    @Test
    void allowVerdictIs200WithExecutedFalse() throws Exception {
        when(toolRegistry.get("query_data")).thenReturn(mock(com.chinacreator.gzcm.engine.ai.service.ToolSchema.class));
        when(subjectResolver.resolve()).thenReturn(subject("u1", List.of("ADMIN")));
        when(policyGate.review(any(ToolAdjudicationRequest.class)))
                .thenReturn(ToolDecision.allow("pol-1", ToolObligations.none(), 5));
        MvcResult r = postDryRun("{\"arguments\":{\"sql\":\"SELECT 1\"},\"asOfTime\":\"2026-10-27T00:00:00Z\"}");
        assertEquals(200, r.getResponse().getStatus());
        JsonNode data = mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertEquals(true, data.path("dryRun").asBoolean());
        assertEquals(false, data.path("executed").asBoolean(), "干跑永不执行副作用");
        assertEquals("ALLOW", data.path("adjudication").path("verdict").asText());
        assertEquals("pol-1", data.path("adjudication").path("policyId").asText());
        assertEquals("read", data.path("adjudication").path("operation").asText());
        assertEquals("SELECT 1", data.path("arguments").path("sql").asText());
        assertFalse(data.path("adjudication").has("highRisk"),
                "干跑不评估高危沙盒，禁放看似已评估的假值");
    }

    @Test
    void nonMapArgumentsIsNormalizedToEmpty() throws Exception {
        when(toolRegistry.get("query_data")).thenReturn(mock(com.chinacreator.gzcm.engine.ai.service.ToolSchema.class));
        when(subjectResolver.resolve()).thenReturn(subject("u1", List.of("admin")));
        when(policyGate.review(any(ToolAdjudicationRequest.class)))
                .thenReturn(ToolDecision.allow("pol-1", ToolObligations.none(), 5));
        MvcResult r = postDryRun("{\"arguments\":\"not-a-map\"}");
        assertEquals(200, r.getResponse().getStatus());
        JsonNode data = mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertTrue(data.path("arguments").isObject());
        assertEquals(0, data.path("arguments").size(), "非法形态归空集，禁静默当 {} 打进裁决载荷");
    }
}
