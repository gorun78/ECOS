package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolDecision;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolObligations;
import com.chinacreator.gzcm.engine.ai.security.AgentToolPolicyGate;
import com.chinacreator.gzcm.engine.ai.security.ToolSubjectResolver;
import com.chinacreator.gzcm.engine.ai.service.ToolRegistry;
import com.chinacreator.gzcm.engine.ai.service.ToolSchema;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentToolContractDao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Agent 工具面 wire 键面门（DI-2 #147CX）。
 *
 * <p>执行面 4 键、参数条目 4/6 键（{@code default}/{@code enum} 条件出现）、描述 6 键、
 * 校验 3/5 键、干跑 7 键 + 裁决摘要 6 键——全部与旧 {@code Map} 时代逐键同名同序。</p>
 */
class AgentToolWireShapeTest {

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
        WAgentToolContractDao contractDao = mock(WAgentToolContractDao.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AgentToolController(toolRegistry, policyGate, subjectResolver,
                        contractDao, new MockEnvironment()))
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter())
                .build();
    }

    private static ToolSchema schema() {
        ToolSchema schema = new ToolSchema();
        schema.setName("query_data");
        schema.setDescription("查询");
        Map<String, ToolSchema.ParamDef> params = new LinkedHashMap<>();
        ToolSchema.ParamDef full = new ToolSchema.ParamDef();
        full.setType("string");
        full.setRequired(true);
        full.setDescription("SQL");
        full.setDefaultValue("SELECT 1");
        full.setEnumValues(List.of("SELECT 1", "SELECT 2"));
        params.put("sql", full);
        ToolSchema.ParamDef bare = new ToolSchema.ParamDef();
        bare.setType("number");
        bare.setRequired(false);
        bare.setDescription("上限");
        params.put("limit", bare);
        schema.setParameters(params);
        return schema;
    }

    private static List<String> keys(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private JsonNode dataOf(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    @Test
    @DisplayName("列表：执行面 4 键；参数全声明 6 键、缺省不落键")
    void listShape() throws Exception {
        when(toolRegistry.listAll()).thenReturn(List.of(schema()));

        JsonNode data = dataOf(mockMvc.perform(get("/api/v1/agent/tools")).andReturn());

        assertEquals(List.of("name", "description", "parameters", "functionCallSchema"), keys(data.get(0)));
        JsonNode params = data.get(0).path("parameters");
        assertEquals(List.of("name", "type", "required", "description", "default", "enum"),
                keys(params.get(0)));
        assertEquals(List.of("name", "type", "required", "description"), keys(params.get(1)));
    }

    @Test
    @DisplayName("描述：6 键；未登记契约面如实 null 不合成空对象")
    void describeShape() throws Exception {
        when(toolRegistry.get("query_data")).thenReturn(schema());

        JsonNode data = dataOf(mockMvc.perform(get("/api/v1/agent/tools/query_data")).andReturn());

        assertEquals(List.of("name", "registered", "executionSchema", "contract", "version", "sources"),
                keys(data));
        assertEquals(true, data.path("registered").asBoolean());
        assertEquals(true, data.path("contract").isNull());
        assertEquals(List.of("execution", "contract"), keys(data.path("sources")));
    }

    @Test
    @DisplayName("校验：缺必填 ⇒ 5 键；满足 ⇒ 3 键（条件键不凭空出现）")
    void validateShape() throws Exception {
        when(toolRegistry.get("query_data")).thenReturn(schema());

        JsonNode missing = dataOf(mockMvc.perform(
                get("/api/v1/agent/tools/query_data/validate").param("args", "{}")).andReturn());
        assertEquals(List.of("valid", "toolName", "missingRequired", "message"), keys(missing));
        assertEquals("sql", missing.path("missingRequired").get(0).asText());

        JsonNode warning = dataOf(mockMvc.perform(
                get("/api/v1/agent/tools/query_data/validate")
                        .param("args", "{\"sql\":\"SELECT 1\",\"limit\":\"abc\"}")).andReturn());
        assertEquals(List.of("valid", "toolName", "typeWarnings", "message"), keys(warning));
        assertEquals(List.of("param", "expected", "actual", "message"),
                keys(warning.path("typeWarnings").get(0)));

        JsonNode clean = dataOf(mockMvc.perform(
                get("/api/v1/agent/tools/query_data/validate").param("args", "{\"sql\":\"SELECT 1\"}"))
                .andReturn());
        assertEquals(List.of("valid", "toolName", "message"), keys(clean));
        assertEquals(true, clean.path("valid").asBoolean());
    }

    @Test
    @DisplayName("干跑 ALLOW：7 键 + 裁决摘要 6 键，obligations 键名取契约常量")
    void dryRunShape() throws Exception {
        when(toolRegistry.get("query_data")).thenReturn(schema());
        when(subjectResolver.resolve()).thenReturn(
                new ToolAdjudicationRequest.Subject("u1", List.of("admin"), null, "t1"));
        when(policyGate.review(any(ToolAdjudicationRequest.class)))
                .thenReturn(ToolDecision.allow("pol-1", ToolObligations.none(), 5));

        JsonNode data = dataOf(mockMvc.perform(post("/api/v1/agent/tools/query_data/dry-run")
                        .contentType("application/json")
                        .content("{\"arguments\":{\"sql\":\"SELECT 1\"},\"asOfTime\":\"2026-11-05T00:00:00Z\"}"))
                .andReturn());

        assertEquals(List.of("name", "arguments", "asOfTime", "dryRun", "executed",
                "adjudication", "obligations"), keys(data));
        assertEquals(List.of("verdict", "policyId", "reason", "operation", "subject", "source"),
                keys(data.path("adjudication")));
        assertFalse(data.path("adjudication").has("highRisk"));
        assertEquals("SELECT 1", data.path("arguments").path("sql").asText());
        assertEquals(false, data.path("executed").asBoolean());
    }
}
