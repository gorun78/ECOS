package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.CandidateStatus;
import com.chinacreator.gzcm.runtime.access.git.GitRepositoryService;
import com.chinacreator.gzcm.runtime.access.git.archive.ArchiveOutcome;
import com.chinacreator.gzcm.runtime.access.git.archive.DesignAssetArchiver;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private AiSecurityEngineClient securityClient;
    private GitRepositoryService gitRepositoryService;
    private DesignAssetArchiver designAssetArchiver;
    private com.chinacreator.gzcm.engine.ai.wagent.approval.ApprovalTokenService approvalTokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        candidateDao = mock(WAgentCandidateDao.class);
        reviewDao = mock(WAgentCandidateReviewDao.class);
        securityClient = mock(AiSecurityEngineClient.class);
        gitRepositoryService = mock(GitRepositoryService.class);
        designAssetArchiver = mock(DesignAssetArchiver.class);
        approvalTokenService = mock(com.chinacreator.gzcm.engine.ai.wagent.approval.ApprovalTokenService.class);
        WAgentIdentityResolver identityResolver = mock(WAgentIdentityResolver.class);
        when(identityResolver.require()).thenReturn(new Identity("t1", "u1", "o1"));
        // 批 A（2026-11-11）：发布面改调 requireWithRoles() 取真实角色集（授权墙双修之一）
        when(identityResolver.requireWithRoles()).thenReturn(
                new WAgentIdentityResolver.IdentityWithRoles(
                        "t1", "u1", "o1", java.util.List.of("admin")));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new WAgentCandidateController(candidateDao, reviewDao, identityResolver,
                        approvalTokenService, securityClient, gitRepositoryService, designAssetArchiver,
                        new com.chinacreator.gzcm.engine.ai.wagent.validate.WAgentCandidateValidationService()))
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
    @DisplayName("D-WC-1 validate：generated + 五段全过 ⇒ validated，五段列同次回写")
    void validateAdvancesToValidated() throws Exception {
        WAgentCandidateDO c = candidate("v1", CandidateStatus.GENERATED.value(), "gen");
        c.setPayloadJson("{\"name\":\"x\"}");
        c.setBasisJson("{\"evidence\":[\"e-1\"]}");
        c.setTargetEngine("kb");
        when(candidateDao.findByTenant("t1")).thenReturn(List.of(c));
        when(candidateDao.updateValidation(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        MvcResult r = mockMvc.perform(post("/api/v1/wagent/candidates/v1/validate")).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        JsonNode data = new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertEquals("validated", data.path("status").asText());
        assertEquals("pass", data.path("validation").path("structure").asText());
        assertEquals("pass", data.path("validation").path("reference").asText());
        assertEquals("pass", data.path("validation").path("impact").asText());
        assertEquals("skipped", data.path("validation").path("regression").asText());
        verify(candidateDao).updateValidation(eq("v1"), eq(CandidateStatus.GENERATED.value()),
                eq(CandidateStatus.VALIDATED.value()), eq("pass"), eq("pass"), any(), eq("pass"),
                eq("skipped"), eq("u1"));
    }

    @Test
    @DisplayName("D-WC-1 validate：任一段 fail ⇒ 停 validating（200 + 明细，非错误）")
    void validateFailureStaysValidating() throws Exception {
        WAgentCandidateDO c = candidate("v2", CandidateStatus.GENERATED.value(), "gen");
        c.setPayloadJson("not-json");           // ①结构 fail
        c.setBasisJson("{\"evidence\":[]}");
        c.setTargetEngine("kb");
        when(candidateDao.findByTenant("t1")).thenReturn(List.of(c));
        when(candidateDao.updateValidation(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        MvcResult r = mockMvc.perform(post("/api/v1/wagent/candidates/v2/validate")).andReturn();
        assertEquals(200, r.getResponse().getStatus(), "校验未过不是调用错误，须回明细");
        JsonNode data = new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertEquals("validating", data.path("status").asText());
        assertEquals("fail", data.path("validation").path("structure").asText());
        verify(candidateDao).updateValidation(eq("v2"), eq(CandidateStatus.GENERATED.value()),
                eq(CandidateStatus.VALIDATING.value()), eq("fail"), any(), any(), eq("pass"), any(), any());
    }

    @Test
    @DisplayName("D-WC-1 validate：未登记 target_engine ⇒ impact fail（属主不可解析）")
    void validateRejectsUnknownEngine() throws Exception {
        WAgentCandidateDO c = candidate("v3", CandidateStatus.GENERATED.value(), "gen");
        c.setPayloadJson("{}");
        c.setBasisJson("{}");
        c.setTargetEngine("ghost-engine");
        when(candidateDao.findByTenant("t1")).thenReturn(List.of(c));
        when(candidateDao.updateValidation(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        JsonNode data = new ObjectMapper().readTree(mockMvc.perform(
                        post("/api/v1/wagent/candidates/v3/validate")).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertEquals("fail", data.path("validation").path("impact").asText());
        assertEquals("validating", data.path("status").asText());
    }

    @Test
    @DisplayName("D-WC-1 validate：已进评审链（review_pending）⇒ 409，零写入")
    void validateBlockedByState() throws Exception {
        candidate("v4", CandidateStatus.REVIEW_PENDING.value(), "gen");
        MvcResult r = mockMvc.perform(post("/api/v1/wagent/candidates/v4/validate")).andReturn();
        assertEquals(409, r.getResponse().getStatus());
        assertEquals("E-WA-STATE", new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("errorCode").asText());
        verify(candidateDao, never()).updateValidation(any(), any(), any(), any(), any(), any(), any(),
                any(), any());
    }

    @Test
    @DisplayName("D-WC-1 validate：过期候选 ⇒ 409（TTL 口径与评审一致）")
    void validateBlockedByExpiry() throws Exception {
        WAgentCandidateDO c = candidate("v5", CandidateStatus.GENERATED.value(), "gen");
        c.setExpireTime(java.time.LocalDateTime.now().minusDays(1));
        MvcResult r = mockMvc.perform(post("/api/v1/wagent/candidates/v5/validate")).andReturn();
        assertEquals(409, r.getResponse().getStatus());
        verify(candidateDao, never()).updateValidation(any(), any(), any(), any(), any(), any(), any(),
                any(), any());
    }

    @Test
    @DisplayName("D-WC-1 validate：CAS 落库 0 行（并发已改状态）⇒ 409，绝不谎报已推进")
    void validateCasConflictIs409() throws Exception {
        WAgentCandidateDO c = candidate("v6", CandidateStatus.GENERATED.value(), "gen");
        c.setPayloadJson("{}");
        c.setBasisJson("{}");
        c.setTargetEngine("kb");
        when(candidateDao.findByTenant("t1")).thenReturn(List.of(c));
        when(candidateDao.updateValidation(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);

        MvcResult r = mockMvc.perform(post("/api/v1/wagent/candidates/v6/validate")).andReturn();
        assertEquals(409, r.getResponse().getStatus());
    }

    // ── target_object_ref_json：创建写入 + conflict 段真判 ──────────────

    @Test
    @DisplayName("建候选：targetObjectRefJson 按 key 字典序规范化后落库（键序不同 ⇒ 同串）")
    void createCandidateCanonicalizesTargetRef() throws Exception {
        ArgumentCaptor<WAgentCandidateDO> saved = ArgumentCaptor.forClass(WAgentCandidateDO.class);
        when(candidateDao.insert(any())).thenReturn(1);

        mockMvc.perform(post("/api/v1/wagent/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateType\":\"ENTITY\",\"payloadJson\":\"{}\"," +
                                "\"targetObjectRefJson\":\"{\\\"objectId\\\":\\\"o-1\\\",\\\"engine\\\":\\\"ontology\\\"}\"}"))
                .andReturn();

        verify(candidateDao).insert(saved.capture());
        assertEquals("{\"engine\":\"ontology\",\"objectId\":\"o-1\"}",
                saved.getValue().getTargetObjectRefJson(),
                "服务端必须按 key 字典序规范化，否则同语义引用会因序列化差异漏判冲突");
    }

    @Test
    @DisplayName("建候选：targetObjectRefJson 非法 JSON ⇒ 400（禁写入不可解析的比对键）")
    void createCandidateRejectsMalformedTargetRef() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/wagent/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateType\":\"ENTITY\",\"payloadJson\":\"{}\"," +
                                "\"targetObjectRefJson\":\"not-json\"}"))
                .andReturn();
        assertEquals(400, r.getResponse().getStatus());
        verify(candidateDao, never()).insert(any());
    }

    @Test
    @DisplayName("建候选：不传 targetObjectRefJson ⇒ 该列 null（conflict 段走 skipped，不伪造引用）")
    void createCandidateWithoutTargetRef() throws Exception {
        ArgumentCaptor<WAgentCandidateDO> saved = ArgumentCaptor.forClass(WAgentCandidateDO.class);
        when(candidateDao.insert(any())).thenReturn(1);
        mockMvc.perform(post("/api/v1/wagent/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateType\":\"ENTITY\",\"payloadJson\":\"{}\"}"))
                .andReturn();
        verify(candidateDao).insert(saved.capture());
        assertEquals(null, saved.getValue().getTargetObjectRefJson());
    }

    @Test
    @DisplayName("validate conflict 段真判：同类型同目标并存未终结候选 ⇒ fail（停 validating）")
    void validateDetectsConflictOnSameTarget() throws Exception {
        WAgentCandidateDO mine = candidate("c-new", CandidateStatus.GENERATED.value(), "gen");
        mine.setPayloadJson("{}");
        mine.setBasisJson("{}");
        mine.setTargetEngine("ontology");
        mine.setTargetObjectRefJson("{\"engine\":\"ontology\",\"objectId\":\"o-1\"}");

        WAgentCandidateDO peer = new WAgentCandidateDO();
        peer.setId("c-old");
        peer.setTenantId("t1");
        peer.setCandidateType("ENTITY");
        peer.setStatus(CandidateStatus.APPROVED.value());
        // 键序不同但语义同目标：规范化后必须被判成同一目标
        peer.setTargetObjectRefJson("{\"objectId\":\"o-1\",\"engine\":\"ontology\"}");

        when(candidateDao.findByTenant("t1")).thenReturn(List.of(mine, peer));
        when(candidateDao.updateValidation(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        JsonNode data = new ObjectMapper().readTree(mockMvc.perform(
                        post("/api/v1/wagent/candidates/c-new/validate")).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertEquals("fail", data.path("validation").path("conflict").asText());
        assertEquals("validating", data.path("status").asText());
        verify(candidateDao).updateValidation(eq("c-new"), any(), eq(CandidateStatus.VALIDATING.value()),
                any(), any(), eq("fail"), any(), any(), any());
    }

    @Test
    @DisplayName("validate conflict 段：并存候选已是终态（rejected）⇒ 不算冲突，放行 validated")
    void validateIgnoresClosedPeerCandidate() throws Exception {
        WAgentCandidateDO mine = candidate("c-new2", CandidateStatus.GENERATED.value(), "gen");
        mine.setPayloadJson("{}");
        mine.setBasisJson("{}");
        mine.setTargetEngine("ontology");
        mine.setTargetObjectRefJson("{\"objectId\":\"o-2\"}");

        WAgentCandidateDO closed = new WAgentCandidateDO();
        closed.setId("c-closed");
        closed.setTenantId("t1");
        closed.setCandidateType("ENTITY");
        closed.setStatus(CandidateStatus.REJECTED.value());
        closed.setTargetObjectRefJson("{\"objectId\":\"o-2\"}");

        when(candidateDao.findByTenant("t1")).thenReturn(List.of(mine, closed));
        when(candidateDao.updateValidation(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        JsonNode data = new ObjectMapper().readTree(mockMvc.perform(
                        post("/api/v1/wagent/candidates/c-new2/validate")).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertEquals("pass", data.path("validation").path("conflict").asText());
        assertEquals("validated", data.path("status").asText());
    }

    @Test
    @DisplayName("#20 approved + token：归档不可用 ⇒ 502 且绝不落 published（禁伪成功）")
    void publishHonest503WhenArchiveUnavailable() throws Exception {
        candidate("c6", CandidateStatus.APPROVED.value(), "gen");
        when(securityClient.evaluate(any(), any(), any(), any(), any())).thenReturn(true);
        when(gitRepositoryService.getDefaultRepository())
                .thenReturn(new com.chinacreator.gzcm.runtime.access.git.entity.GitRepository());
        when(designAssetArchiver.archive(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ArchiveOutcome.notAvailable("仓未注册"));
        MvcResult r = mockMvc.perform(post("/api/v1/wagent/candidates/c6/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalToken\":\"tok-2\"}"))
                .andReturn();
        assertEquals(502, r.getResponse().getStatus());
        verify(candidateDao, never()).updatePublished(any(), any(), any(), any(), any());
        verify(reviewDao, never()).insert(any());
    }

    @Test
    @DisplayName("#20 pre-commit 裁决 DENY ⇒ 403（fail-closed），不触发归档")
    void publishDeniedByAdjudication() throws Exception {
        candidate("c8", CandidateStatus.APPROVED.value(), "gen");
        when(securityClient.evaluate(any(), any(), any(), any(), any())).thenReturn(false);
        assertEquals(403, mockMvc.perform(post("/api/v1/wagent/candidates/c8/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalToken\":\"tok-4\"}"))
                .andReturn().getResponse().getStatus());
        verify(designAssetArchiver, never()).archive(any(), any(), any(), any(), any(), any(), any());
        verify(candidateDao, never()).updatePublished(any(), any(), any(), any(), any());
        // 关键不变量：裁决/仓库不可用时不得消耗凭证（否则「没发布却烧掉审批」）
        verify(approvalTokenService, never())
                .verifyAndConsume(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("#20 归档成功 ⇒ 200 + 落库 PUBLISHED + 写 gitRef")
    void publishHappyPath() throws Exception {
        candidate("c9", CandidateStatus.APPROVED.value(), "gen");
        when(securityClient.evaluate(any(), any(), any(), any(), any())).thenReturn(true);
        when(gitRepositoryService.getDefaultRepository())
                .thenReturn(new com.chinacreator.gzcm.runtime.access.git.entity.GitRepository());
        when(designAssetArchiver.archive(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ArchiveOutcome.archived("abc123sha", null));
        when(candidateDao.updatePublished(any(), any(), any(), any(), any())).thenReturn(1);

        MvcResult r = mockMvc.perform(post("/api/v1/wagent/candidates/c9/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalToken\":\"tok-5\"}"))
                .andReturn();
        assertEquals(200, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("abc123sha"));
        verify(candidateDao).updatePublished(eq("c9"), eq(CandidateStatus.PUBLISHED.value()),
                eq("abc123sha"), eq("abc123sha"), any());
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
