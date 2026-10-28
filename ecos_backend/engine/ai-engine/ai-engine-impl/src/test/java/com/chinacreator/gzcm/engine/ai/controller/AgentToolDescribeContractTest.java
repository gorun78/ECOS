package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.engine.ai.security.AgentToolPolicyGate;
import com.chinacreator.gzcm.engine.ai.security.ToolSubjectResolver;
import com.chinacreator.gzcm.engine.ai.service.ToolRegistry;
import com.chinacreator.gzcm.engine.ai.service.ToolSchema;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentToolContractDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentToolContractDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 附录 A 第 21 条 {@code GET /api/v1/agent/tools/{name}}（describeAgentTool / 详设-06 §5.2 #9）。
 *
 * <p>两个真值面各自单源：执行面 ← {@code ToolRegistry}，契约面 ← {@code ecos_wagent_tool_contract}
 * （17 要素经 {@code WAgentToolContractController#contractView} 单源投影）。任一面在册即 200
 * 且缺席面如实留空；两面皆无才 404。禁由 ToolSchema 反推 17 要素（超时/幂等/回滚/密级无载体）。</p>
 */
@DisplayName("附录A#21 describeAgentTool：执行面/契约面双源不互相顶替，两面皆无才 404")
class AgentToolDescribeContractTest {

    private static final String DESCRIBE_PATH = "/api/v1/agent/tools/{name}";

    private ToolRegistry toolRegistry;
    private WAgentToolContractDao toolContractDao;
    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        toolRegistry = mock(ToolRegistry.class);
        toolContractDao = mock(WAgentToolContractDao.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AgentToolController(toolRegistry, mock(AgentToolPolicyGate.class),
                        mock(ToolSubjectResolver.class), toolContractDao, new MockEnvironment()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    private static WAgentToolContractDO contract(String version) {
        WAgentToolContractDO c = new WAgentToolContractDO();
        c.setId("tc-1");
        c.setName("query_data");
        c.setVersion(version);
        c.setEngine("data");
        c.setCategory("query");
        c.setSideEffect("read");
        c.setMinLevel(1);
        c.setTimeoutMs(30000);
        c.setIsIdempotent(1);
        c.setIsEvidenceOutput(1);
        c.setDataClassificationText("internal");
        c.setRollbackPlanText("read-only");
        c.setStatus("active");
        c.setSummary("查询数据");
        c.setToolset("core");
        c.setDisclosure("always_loaded");
        c.setCostClass("low");
        return c;
    }

    private static ToolSchema schema() {
        return new ToolSchema("query_data", "查询数据")
                .addParam("sql", new ToolSchema.ParamDef("string", true, "SQL 语句"));
    }

    private JsonNode data(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    @Test
    @DisplayName("仅契约面在册 → 200 + registered=false + 17 要素契约（executionSchema 留空）")
    void contractOnlyYields200WithContractView() throws Exception {
        when(toolRegistry.get("query_data")).thenReturn(null);
        when(toolContractDao.findLatestActiveByName("query_data"))
                .thenReturn(Optional.of(contract("1.2.0")));

        MvcResult r = mockMvc.perform(get(DESCRIBE_PATH, "query_data")).andReturn();

        assertEquals(200, r.getResponse().getStatus());
        JsonNode d = data(r);
        assertEquals("query_data", d.path("name").asText());
        assertFalse(d.path("registered").asBoolean(), "执行面未注册必须如实 false");
        assertTrue(d.path("executionSchema").isNull() || d.path("executionSchema").isMissingNode(),
                "缺席面禁合成空壳");
        assertEquals("1.2.0", d.path("version").asText());
        JsonNode c = d.path("contract");
        assertEquals("tc-1", c.path("tool_id").asText());
        assertEquals("1.2.0", c.path("version").asText());
        assertEquals(30000, c.path("timeout_ms").asInt());
        assertEquals(1, c.path("is_idempotent").asInt());
        assertEquals("internal", c.path("data_classification_text").asText());
        assertEquals("read-only", c.path("rollback_plan_text").asText());
        assertEquals("ecos_ai.ecos_wagent_tool_contract#V236", d.path("sources").path("contract").asText());
    }

    @Test
    @DisplayName("仅执行面在册 → 200 + registered=true + executionSchema（contract 留空，禁反推）")
    void executionOnlyYields200WithRegistryView() throws Exception {
        when(toolRegistry.get("query_data")).thenReturn(schema());
        when(toolContractDao.findLatestActiveByName("query_data")).thenReturn(Optional.empty());

        MvcResult r = mockMvc.perform(get(DESCRIBE_PATH, "query_data")).andReturn();

        assertEquals(200, r.getResponse().getStatus());
        JsonNode d = data(r);
        assertTrue(d.path("registered").asBoolean());
        assertTrue(d.path("contract").isNull() || d.path("contract").isMissingNode(),
                "契约面缺席必须留空，禁由 ToolSchema 反推 17 要素");
        assertTrue(d.path("version").isNull() || d.path("version").isMissingNode());
        JsonNode exec = d.path("executionSchema");
        assertEquals("query_data", exec.path("name").asText());
        JsonNode params = exec.path("parameters");
        assertTrue(params.isArray());
        assertEquals(1, params.size());
        assertEquals("sql", params.get(0).path("name").asText());
        assertTrue(params.get(0).path("required").asBoolean());
        assertTrue(exec.path("functionCallSchema").isObject(), "执行面附 function-calling schema");
    }

    @Test
    @DisplayName("两面皆无 → 404 + ECOS-AI-404（真不存在，禁伪空契约 200）")
    void neitherFaceYields404() throws Exception {
        when(toolRegistry.get("query_data")).thenReturn(null);
        when(toolContractDao.findLatestActiveByName("query_data")).thenReturn(Optional.empty());

        MvcResult r = mockMvc.perform(get(DESCRIBE_PATH, "query_data")).andReturn();

        assertEquals(404, r.getResponse().getStatus());
        JsonNode body = mapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("ECOS-AI-404", body.path("errorCode").asText());
        assertEquals(404, body.path("code").asInt());
    }

    @Test
    @DisplayName("?version= 按 uniq(name,version) 精确闭合，不走最新活跃探取")
    void explicitVersionPinsExactContract() throws Exception {
        when(toolRegistry.get("query_data")).thenReturn(null);
        when(toolContractDao.findByNameAndVersion("query_data", "1.1.0"))
                .thenReturn(Optional.of(contract("1.1.0")));

        MvcResult r = mockMvc.perform(get(DESCRIBE_PATH, "query_data").param("version", "1.1.0"))
                .andReturn();

        assertEquals(200, r.getResponse().getStatus());
        assertEquals("1.1.0", data(r).path("version").asText());
        verify(toolContractDao).findByNameAndVersion("query_data", "1.1.0");
        verify(toolContractDao, never()).findLatestActiveByName(anyString());
    }
}
