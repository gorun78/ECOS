package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ToolContractStatus;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentToolContractDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentToolContractDO;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.IdentityWithRoles;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentOrchestrationAdvice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * #14/#15 工具契约 wire 键面门（DI-2 #147CR）。
 *
 * <p>摘要 10 键 / 完整契约 28 键（摘要 10 + 受控列 18）/ #15 响应再追加
 * {@code permission_filter}。这三层键面既服务 {@code /wagent/tools}，也被
 * {@code /agent/tools/{name}} 的 {@code contract} 字段复用（同源投影），故本类同时锁住
 * 「映射容器换 record 后不漂键」。</p>
 */
class WAgentToolContractWireShapeTest {

    private static final List<String> SUMMARY_KEYS = List.of(
            "name", "version", "engine", "category", "side_effect", "disclosure", "toolset",
            "summary", "status", "cost_class");

    private static final List<String> CONTRACT_KEYS = contractKeys();

    private WAgentToolContractDao dao;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        dao = mock(WAgentToolContractDao.class);
        WAgentIdentityResolver identityResolver = mock(WAgentIdentityResolver.class);
        when(identityResolver.requireWithRoles())
                .thenReturn(new IdentityWithRoles("t1", "u1", "o1", List.of()));
        when(identityResolver.require()).thenReturn(new Identity("t1", "u1", "o1"));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WAgentToolContractController(dao, identityResolver))
                .setControllerAdvice(new WAgentOrchestrationAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    private static List<String> contractKeys() {
        List<String> keys = new ArrayList<>(SUMMARY_KEYS);
        keys.addAll(List.of("tool_id", "min_level", "required_permission", "input_schema_json",
                "output_schema_json", "timeout_ms", "retry_text", "is_idempotent",
                "is_evidence_output", "data_classification_text", "rollback_plan_text",
                "endpoint_kind", "endpoint_url", "endpoint_method", "owner", "sla_text",
                "deprecated_by", "capability_deps_text"));
        return keys;
    }

    private static List<String> keys(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static WAgentToolContractDO tool() {
        WAgentToolContractDO t = new WAgentToolContractDO();
        t.setId("tool-1");
        t.setName("public.read");
        t.setVersion("1.0.0");
        t.setEngine("ai");
        t.setCategory("query");
        t.setSideEffect("none");
        t.setDisclosure("public");
        t.setToolset("core");
        t.setSummary("公开读工具");
        t.setStatus(ToolContractStatus.ACTIVE.value());
        t.setCostClass("low");
        t.setMinLevel(1);
        t.setInputSchemaJson("{}");
        t.setOutputSchemaJson("{}");
        t.setTimeoutMs(5000);
        t.setRetryText("0");
        t.setIsIdempotent(1);
        t.setIsEvidenceOutput(0);
        return t;
    }

    private JsonNode dataOf(MvcResult r) throws Exception {
        return new ObjectMapper().readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
    }

    @Test
    @DisplayName("#14 列表：data 5 键，条目摘要 10 键且键序与旧 Map 一致")
    void searchShape() throws Exception {
        when(dao.findByStatus(ToolContractStatus.ACTIVE.value())).thenReturn(List.of(tool()));

        JsonNode data = dataOf(mockMvc.perform(get("/api/v1/wagent/tools")).andReturn());

        assertEquals(List.of("tools", "total", "filtered_by_permission", "permission_filter",
                "status_used"), keys(data));
        assertEquals(SUMMARY_KEYS, keys(data.path("tools").get(0)));
        assertEquals("none", data.path("tools").get(0).path("side_effect").asText());
    }

    @Test
    @DisplayName("#15 完整契约：29 键（28 + 尾部 permission_filter），受控列类型不放宽")
    void describeShape() throws Exception {
        when(dao.findByNameAndVersion("public.read", "1.0.0")).thenReturn(Optional.of(tool()));

        JsonNode data = dataOf(mockMvc.perform(get("/api/v1/wagent/tools/public.read?version=1.0.0"))
                .andReturn());

        List<String> expected = new ArrayList<>(CONTRACT_KEYS);
        expected.add("permission_filter");
        assertEquals(expected, keys(data));
        assertEquals(5000, data.path("timeout_ms").asInt());
        assertEquals(1, data.path("is_idempotent").asInt());
        assertEquals(WAgentToolContractController.PERMISSION_FILTER,
                data.path("permission_filter").asText());
    }
}
