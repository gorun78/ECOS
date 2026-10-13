package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ToolContractStatus;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentToolContractDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentToolContractDO;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.IdentityWithRoles;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** §5.2 #14~#16 工具契约：本地权限地板过滤 + 完整契约读取 + 五关验证未接线的如实 501。 */
class WAgentToolContractControllerTest {

    private WAgentToolContractDao dao;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        dao = mock(WAgentToolContractDao.class);
        WAgentIdentityResolver identityResolver = mock(WAgentIdentityResolver.class);
        when(identityResolver.requireWithRoles())
                .thenReturn(new IdentityWithRoles("t1", "u1", "o1", List.of()));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new WAgentToolContractController(dao, identityResolver))
                .setControllerAdvice(new WAgentOrchestrationAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    private static WAgentToolContractDO tool(String name, String permission) {
        WAgentToolContractDO t = new WAgentToolContractDO();
        t.setId(name + "-id");
        t.setName(name);
        t.setVersion("1.0.0");
        t.setStatus(ToolContractStatus.ACTIVE.value());
        t.setSummary(name + " 摘要");
        t.setRequiredPermission(permission);
        return t;
    }

    @Test
    @DisplayName("#14 缺省 status=active 列表面：required_permission 未命中角色的工具被剔除（fail-closed）")
    void searchFiltersByPermissionFloor() throws Exception {
        when(dao.findByStatus(ToolContractStatus.ACTIVE.value()))
                .thenReturn(List.of(tool("public.read", null), tool("secret.write", "tool:secret")));
        MvcResult r = mockMvc.perform(get("/api/v1/wagent/tools")).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        JsonNode data = new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertEquals(1, data.path("total").asInt());
        assertEquals("public.read", data.path("tools").get(0).path("name").asText());
        assertEquals(1, data.path("filtered_by_permission").asInt());
        assertEquals(WAgentToolContractController.PERMISSION_FILTER,
                data.path("permission_filter").asText());
    }

    @Test
    @DisplayName("#14 状态词表越界 ⇒ 400（ToolContractStatus 六值单源）")
    void searchRejectsUnknownStatus() throws Exception {
        assertEquals(400, mockMvc.perform(get("/api/v1/wagent/tools?status=ghost"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("#15 version 必填（name+version 闭合定位）⇒ 缺 version 400 / 命中 200 全契约")
    void describeTool() throws Exception {
        assertEquals(400, mockMvc.perform(get("/api/v1/wagent/tools/public.read"))
                .andReturn().getResponse().getStatus());

        when(dao.findByNameAndVersion("public.read", "1.0.0"))
                .thenReturn(Optional.of(tool("public.read", null)));
        MvcResult r = mockMvc.perform(get("/api/v1/wagent/tools/public.read?version=1.0.0"))
                .andReturn();
        assertEquals(200, r.getResponse().getStatus());
        JsonNode data = new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertEquals("public.read", data.path("name").asText());
        assertEquals(true, data.has("input_schema_json"));
    }

    @Test
    @DisplayName("#15 未知契约 ⇒ 404 E-NOT-FOUND（禁空对象 200）")
    void describeUnknownIs404() throws Exception {
        when(dao.findByNameAndVersion("ghost", "9")).thenReturn(Optional.empty());
        MvcResult r = mockMvc.perform(get("/api/v1/wagent/tools/ghost?version=9")).andReturn();
        assertEquals(404, r.getResponse().getStatus());
        assertEquals("E-NOT-FOUND", new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("errorCode").asText());
    }

    @Test
    @DisplayName("#16 契约存在但五关执行器未接线 ⇒ 501 NOT_IMPLEMENTED（禁伪称 validated）")
    void validateHonest501() throws Exception {
        when(dao.findByNameAndVersion("public.read", "1.0.0"))
                .thenReturn(Optional.of(tool("public.read", null)));
        MvcResult r = mockMvc.perform(post("/api/v1/wagent/tools/public.read/validate?version=1.0.0"))
                .andReturn();
        assertEquals(501, r.getResponse().getStatus());
        assertEquals("NOT_IMPLEMENTED", new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("errorCode").asText());
    }
}
