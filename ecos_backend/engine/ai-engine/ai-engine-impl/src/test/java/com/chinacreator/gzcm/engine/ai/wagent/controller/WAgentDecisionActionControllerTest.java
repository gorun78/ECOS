package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient;
import com.chinacreator.gzcm.engine.ai.wagent.approval.ApprovalTokenService;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentActionDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentActionDO;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * §5.2 #23/#24 decision + action。
 *
 * <p><b>2026-11-10 订正</b>：原用例断言「V241 表未落地 ⇒ 501」属过期口径 —— 表已由 V283 建好。
 * 现断言真实语义：草稿落库 201、提交走「凭证→裁决→CAS→属主派发」，派发失败保持 COMMITTING 且 502。</p>
 */
class WAgentDecisionActionControllerTest {

    private MockMvc mockMvc;
    private WAgentActionDao actionDao;
    private ApprovalTokenService tokenService;
    private AiSecurityEngineClient securityClient;

    @BeforeEach
    void setUp() {
        actionDao = mock(WAgentActionDao.class);
        tokenService = mock(ApprovalTokenService.class);
        securityClient = mock(AiSecurityEngineClient.class);
        WAgentIdentityResolver identityResolver = mock(WAgentIdentityResolver.class);
        when(identityResolver.require()).thenReturn(new Identity("t1", "u1", "o1"));
        // 批 A（2026-11-11）：提交面改调 requireWithRoles() 取真实角色集（授权墙双修之一）
        when(identityResolver.requireWithRoles()).thenReturn(
                new WAgentIdentityResolver.IdentityWithRoles(
                        "t1", "u1", "o1", java.util.List.of("admin")));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WAgentDecisionActionController(identityResolver, tokenService,
                        securityClient, actionDao, "http://127.0.0.1:1", 800L))
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
    @DisplayName("#23 合法入参仍 501：表已在(V283)，缺的是 pinned 评估校验闭环")
    void decisionHonest501() throws Exception {
        MvcResult r = postJson("/api/v1/wagent/decisions",
                "{\"questionId\":\"q1\",\"readinessId\":\"ra1\",\"rationaleText\":\"依据 A 级评估\"}");
        assertEquals(501, r.getResponse().getStatus());
        JsonNode json = new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("NOT_IMPLEMENTED", json.path("errorCode").asText());
    }

    @Test
    @DisplayName("#24a 草稿缺 owner ⇒ 400；合法 ⇒ 201 且真实落库 DRAFT")
    void actionDraft() throws Exception {
        assertEquals(400, postJson("/api/v1/wagent/actions",
                "{\"title\":\"追加投放\"}").getResponse().getStatus());
        assertEquals(400, postJson("/api/v1/wagent/actions",
                "{\"title\":\"追加投放\",\"ownerId\":\"u2\"}").getResponse().getStatus(),
                "缺 option_id 必须 400（V283 NOT NULL：行动须挂决策选项）");

        MvcResult ok = postJson("/api/v1/wagent/actions",
                "{\"title\":\"追加投放\",\"ownerId\":\"u2\",\"optionId\":\"opt-1\",\"publishTarget\":\"workspace\",\"targetObjectId\":\"s1\"}");
        assertEquals(201, ok.getResponse().getStatus());
        JsonNode data = new ObjectMapper().readTree(
                ok.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertEquals("DRAFT", data.path("status").asText());
        assertEquals("workspace", data.path("publish_target").asText());
        verify(actionDao).insert(any(WAgentActionDO.class));
    }

    @Test
    @DisplayName("#24b 提交缺凭证 ⇒ 400；凭证 + 行动不存在 ⇒ 404")
    void actionCommitGuards() throws Exception {
        assertEquals(400, postJson("/api/v1/wagent/actions/a1/commit", "{}")
                .getResponse().getStatus());
        when(actionDao.findById("a1")).thenReturn(Optional.empty());
        // 契约字段是 approval_token（snake_case）：必须被识别，不能因命名差异退化成 400
        assertEquals(404, postJson("/api/v1/wagent/actions/a1/commit",
                "{\"approval_token\":\"tok\"}").getResponse().getStatus());
    }

    @Test
    @DisplayName("#24b 裁决 DENY ⇒ 403 且不消费凭证、不推进状态（fail-closed 不烧审批）")
    void commitDeniedByAdjudication() throws Exception {
        when(actionDao.findById("a1")).thenReturn(Optional.of(draft("a1", "workspace")));
        when(securityClient.evaluate(any(), any(), any(), any(), any())).thenReturn(false);

        assertEquals(403, postJson("/api/v1/wagent/actions/a1/commit",
                "{\"approvalToken\":\"tok\"}").getResponse().getStatus());
        verify(tokenService, never()).verifyAndConsume(any(), any(), any(), any(), any());
        verify(actionDao, never()).transitionStatus(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("#24b 裁决通过但属主派发不可达 ⇒ 502，且状态停在 COMMITTING（绝不伪 COMMITTED）")
    void commitDispatchFailureKeepsCommitting() throws Exception {
        when(actionDao.findById("a1")).thenReturn(Optional.of(draft("a1", "workspace")));
        when(securityClient.evaluate(any(), any(), any(), any(), any())).thenReturn(true);
        when(actionDao.transitionStatus(eq("a1"), eq("DRAFT"), eq("COMMITTING"), any(), any()))
                .thenReturn(1);

        MvcResult r = postJson("/api/v1/wagent/actions/a1/commit",
                "{\"approvalToken\":\"tok\"}");
        assertEquals(502, r.getResponse().getStatus());
        verify(actionDao, never()).transitionStatus(any(), any(), eq("COMMITTED"), any(), any());
    }

    @Test
    @DisplayName("#24b publish_target 未登记通道 ⇒ 400（禁猜属主端点），且不消费凭证")
    void commitRejectsUnknownTarget() throws Exception {
        when(actionDao.findById("a1")).thenReturn(Optional.of(draft("a1", "unknown")));
        when(securityClient.evaluate(any(), any(), any(), any(), any())).thenReturn(true);

        assertEquals(400, postJson("/api/v1/wagent/actions/a1/commit",
                "{\"approvalToken\":\"tok\"}").getResponse().getStatus());
        verify(tokenService, never()).verifyAndConsume(any(), any(), any(), any(), any());
    }

    private static WAgentActionDO draft(String id, String publishTarget) {
        WAgentActionDO a = new WAgentActionDO();
        a.setId(id);
        a.setTenantId("t1");
        a.setStatus("DRAFT");
        a.setTitle("追加投放");
        a.setOwnerId("u2");
        a.setPublishTarget(publishTarget);
        a.setSourceObjectId("s1");
        return a;
    }
}
