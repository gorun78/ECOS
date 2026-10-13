package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.CandidateStatus;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentCandidateDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentCandidateReviewDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentCandidateDO;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentOrchestrationAdvice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** §5.2 #17~#20 candidate：租户隔离列表/详情 + #19 四眼评审实装 + #20 发布未接线的如实 501。 */
class WAgentCandidateControllerTest {

    private WAgentCandidateDao candidateDao;
    private WAgentCandidateReviewDao reviewDao;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        candidateDao = mock(WAgentCandidateDao.class);
        reviewDao = mock(WAgentCandidateReviewDao.class);
        WAgentIdentityResolver identityResolver = mock(WAgentIdentityResolver.class);
        when(identityResolver.require()).thenReturn(new Identity("t1", "u1", "o1"));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new WAgentCandidateController(candidateDao, reviewDao, identityResolver))
                .setControllerAdvice(new WAgentOrchestrationAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    private WAgentCandidateDO candidate(String id, String status, String createBy) {
        WAgentCandidateDO c = new WAgentCandidateDO();
        c.setId(id);
        c.setTenantId("t1");
        c.setCandidateType("ENTITY");
        c.setStatus(status);
        c.setCreateBy(createBy);
        when(candidateDao.findById(id)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    @DisplayName("#17 列表按租户 DAO 收口 + candidate_type/status 受控过滤")
    void listIsTenantScoped() throws Exception {
        WAgentCandidateDO mine = candidate("c1", CandidateStatus.GENERATED.value(), "gen");
        mine.setCandidateType("ENTITY");
        when(candidateDao.findByTenant("t1")).thenReturn(List.of(mine));
        MvcResult r = mockMvc.perform(get("/api/v1/wagent/candidates?candidateType=ENTITY&status=generated"))
                .andReturn();
        assertEquals(200, r.getResponse().getStatus());
        JsonNode data = new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertEquals(1, data.path("total").asInt());
    }

    @Test
    @DisplayName("#17 词表越界 ⇒ 400；#18 未知/跨租户 ⇒ 404（防枚举）")
    void shapeGates() throws Exception {
        assertEquals(400, mockMvc.perform(get("/api/v1/wagent/candidates?candidateType=GHOST"))
                .andReturn().getResponse().getStatus());
        when(candidateDao.findById("nope")).thenReturn(Optional.empty());
        assertEquals(404, mockMvc.perform(get("/api/v1/wagent/candidates/nope"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("#19 APPROVE 缺 approval_token ⇒ 400（凭证闸）")
    void approveRequiresToken() throws Exception {
        candidate("c2", CandidateStatus.REVIEW_PENDING.value(), "gen");
        MvcResult r = mockMvc.perform(post("/api/v1/wagent/candidates/c2/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andReturn();
        assertEquals(400, r.getResponse().getStatus());
        verify(candidateDao, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("#19 四眼：候选生成者自审 ⇒ 403 E-POLICY，零写入")
    void fourEyesBlocksSelfReview() throws Exception {
        candidate("c3", CandidateStatus.REVIEW_PENDING.value(), "u1"); // createBy == 当前操作者
        MvcResult r = mockMvc.perform(post("/api/v1/wagent/candidates/c3/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"approvalToken\":\"tok-1\"}"))
                .andReturn();
        assertEquals(403, r.getResponse().getStatus());
        assertEquals("E-POLICY", new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("errorCode").asText());
        verify(reviewDao, never()).insert(any());
    }

    @Test
    @DisplayName("#19 合法四眼评审 ⇒ 评审行落库 + 状态推进 approved")
    void approveHappyPath() throws Exception {
        candidate("c4", CandidateStatus.REVIEW_PENDING.value(), "gen");
        when(reviewDao.findByCandidateId("c4")).thenReturn(List.of());
        MvcResult r = mockMvc.perform(post("/api/v1/wagent/candidates/c4/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"approvalToken\":\"tok-1\","
                                + "\"commentText\":\"口径核对通过\"}"))
                .andReturn();
        assertEquals(200, r.getResponse().getStatus());
        verify(reviewDao).insert(any());
        verify(candidateDao).updateStatus(eq("c4"), eq(CandidateStatus.APPROVED.value()), eq("u1"));
    }

    @Test
    @DisplayName("#19 不可审状态（generated）⇒ 409 E-WA-STATE")
    void illegalStatusIs409() throws Exception {
        candidate("c5", CandidateStatus.GENERATED.value(), "gen");
        MvcResult r = mockMvc.perform(post("/api/v1/wagent/candidates/c5/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\"}"))
                .andReturn();
        assertEquals(409, r.getResponse().getStatus());
        assertEquals("E-WA-STATE", new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("errorCode").asText());
    }

    @Test
    @DisplayName("#20 approved + token ⇒ pre-commit 裁决/Git 归档未接线，如实 501（禁伪 published）")
    void publishHonest501() throws Exception {
        candidate("c6", CandidateStatus.APPROVED.value(), "gen");
        MvcResult r = mockMvc.perform(post("/api/v1/wagent/candidates/c6/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalToken\":\"tok-2\"}"))
                .andReturn();
        assertEquals(501, r.getResponse().getStatus());
        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        verify(candidateDao, never()).updateStatus(any(), status.capture(), any());
        verify(reviewDao, never()).insert(any());
    }

    @Test
    @DisplayName("#20 未达 approved ⇒ 409（发布状态机闸先于降级出口）")
    void publishBlockedByState() throws Exception {
        candidate("c7", CandidateStatus.REVIEW_PENDING.value(), "gen");
        assertEquals(409, mockMvc.perform(post("/api/v1/wagent/candidates/c7/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalToken\":\"tok-3\"}"))
                .andReturn().getResponse().getStatus());
    }
}
