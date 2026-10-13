package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.IdentityWithRoles;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentOrchestrationAdvice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §5.2 #11 kill-switch：admin 闸 fail-closed + 未接线面如实 501（无假真理）。 */
class WAgentKillSwitchControllerTest {

    private WAgentIdentityResolver identityResolver;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        identityResolver = mock(WAgentIdentityResolver.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WAgentKillSwitchController(identityResolver))
                .setControllerAdvice(new WAgentOrchestrationAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    private void identity(List<String> roles) {
        when(identityResolver.requireWithRoles())
                .thenReturn(new IdentityWithRoles("t1", "u1", "o1", roles));
    }

    private MvcResult call(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/wagent/flags/kill-switch")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    @Test
    @DisplayName("#11 非 admin 主体 ⇒ 403 E-POLICY（fail-closed，禁置旗）")
    void nonAdminRejected() throws Exception {
        identity(List.of("reader"));
        MvcResult r = call("{\"scope\":\"tenant\",\"enabled\":true,\"reason\":\"事故止血\"}");
        assertEquals(403, r.getResponse().getStatus());
        JsonNode json = new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("E-POLICY", json.path("errorCode").asText());
    }

    @Test
    @DisplayName("#11 admin + 合法入参 ⇒ 存储未接线如实 501 NOT_IMPLEMENTED（禁伪称已置位）")
    void adminGetsHonest501() throws Exception {
        identity(List.of("admin"));
        MvcResult r = call("{\"scope\":\"tenant\",\"enabled\":true,\"reason\":\"事故止血\"}");
        assertEquals(501, r.getResponse().getStatus());
        JsonNode json = new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("NOT_IMPLEMENTED", json.path("errorCode").asText());
        assertEquals(false, json.path("data").path("implemented").asBoolean());
    }

    @Test
    @DisplayName("#11 入参校验：scope 越界 / reason 缺失 ⇒ 400 E-VALIDATION")
    void validatesShape() throws Exception {
        identity(List.of("ROLE_ADMIN"));
        assertEquals(400, call("{\"scope\":\"global\",\"enabled\":true,\"reason\":\"x\"}")
                .getResponse().getStatus());
        assertEquals(400, call("{\"scope\":\"tenant\",\"enabled\":true}")
                .getResponse().getStatus());
    }
}
