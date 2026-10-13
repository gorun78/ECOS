package com.chinacreator.gzcm.engine.ai.wagent.controller;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** §5.2 #23/#24 decision + action：入参 fail-fast 在位，V241 存储未落地 ⇒ 如实 501（禁内存台账伪记录）。 */
class WAgentDecisionActionControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        WAgentIdentityResolver identityResolver = mock(WAgentIdentityResolver.class);
        when(identityResolver.require()).thenReturn(new Identity("t1", "u1", "o1"));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WAgentDecisionActionController(identityResolver))
                .setControllerAdvice(new WAgentOrchestrationAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    private MvcResult postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    @Test
    @DisplayName("#23 readiness_id/rationale 缺失 ⇒ 400（决策必须引用 pinned 评估并留痕）")
    void decisionShapeValidated() throws Exception {
        assertEquals(400, postJson("/api/v1/wagent/decisions",
                "{\"questionId\":\"q1\"}").getResponse().getStatus());
        assertEquals(400, postJson("/api/v1/wagent/decisions",
                "{\"questionId\":\"q1\",\"readinessId\":\"ra1\"}").getResponse().getStatus());
    }

    @Test
    @DisplayName("#23 合法入参 ⇒ 501 NOT_IMPLEMENTED（V241 决策表未落地）")
    void decisionHonest501() throws Exception {
        MvcResult r = postJson("/api/v1/wagent/decisions",
                "{\"questionId\":\"q1\",\"readinessId\":\"ra1\",\"rationaleText\":\"依据 A 级评估\"}");
        assertEquals(501, r.getResponse().getStatus());
        JsonNode json = new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("NOT_IMPLEMENTED", json.path("errorCode").asText());
    }

    @Test
    @DisplayName("#24a 草稿缺 owner ⇒ 400；合法 ⇒ 501（V241 action 表未落地）")
    void actionDraft() throws Exception {
        assertEquals(400, postJson("/api/v1/wagent/actions",
                "{\"title\":\"追加投放\"}").getResponse().getStatus());
        assertEquals(501, postJson("/api/v1/wagent/actions",
                "{\"title\":\"追加投放\",\"ownerId\":\"u2\"}").getResponse().getStatus());
    }

    @Test
    @DisplayName("#24b 提交缺凭证 ⇒ 400；带凭证 ⇒ 501（属主 Engine 写通道未接，禁伪提交）")
    void actionCommit() throws Exception {
        assertEquals(400, postJson("/api/v1/wagent/actions/a1/commit", "{}")
                .getResponse().getStatus());
        assertEquals(501, postJson("/api/v1/wagent/actions/a1/commit",
                "{\"approvalToken\":\"tok\"}").getResponse().getStatus());
    }
}
