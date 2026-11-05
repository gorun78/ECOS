package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.CandidateStatus;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentCandidateDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentCandidateReviewDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentCandidateDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentCandidateReviewDO;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * #17~#20 candidate wire 键面门（DI-2 #147CP）。
 *
 * <p>换载体前是 {@code LinkedHashMap} 拼装，键名/键序即契约；改成 record 后只要有人漏了
 * {@code @JsonProperty} 就会静默改名（camelCase 冒出）。本类按**键集相等 + 键序**判等，
 * 并与旧 Map 时代逐键对齐。</p>
 */
class WAgentCandidateWireShapeTest {

    private static final List<String> CANDIDATE_KEYS = List.of(
            "candidate_id", "candidate_type", "target_engine", "target_object_ref_json", "payload_hash",
            "generated_by_run", "generated_by_step", "confidence", "basis_json", "validation", "status",
            "batch_id", "engine_draft_ref_text", "published_ref_text", "published_git_ref", "expire_time",
            "expired", "version_no", "create_time");

    private static final List<String> DETAIL_KEYS = append(CANDIDATE_KEYS, "reviews");

    private static final List<String> VALIDATION_KEYS = List.of(
            "structure", "reference", "conflict", "impact", "regression");

    private static final List<String> REVIEW_KEYS = List.of(
            "review_id", "reviewer_id", "decision", "comment_text", "changes_json",
            "approval_token_present", "review_time");

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

    private static List<String> append(List<String> base, String extra) {
        List<String> list = new ArrayList<>(base);
        list.add(extra);
        return list;
    }

    private static List<String> keys(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private WAgentCandidateDO fullCandidate(String id, String status) {
        WAgentCandidateDO c = new WAgentCandidateDO();
        c.setId(id);
        c.setTenantId("t1");
        c.setCandidateType("ENTITY");
        c.setTargetEngine("ontology");
        c.setTargetObjectRefJson("{\"code\":\"E1\"}");
        c.setPayloadHash("h-1");
        c.setGeneratedByRun("run-1");
        c.setGeneratedByStep("step-2");
        c.setConfidence(new BigDecimal("0.87"));
        c.setBasisJson("{\"src\":\"x\"}");
        c.setValidationStructure("pass");
        c.setValidationReference("pass");
        c.setValidationConflict("warn");
        c.setValidationImpact("pass");
        c.setValidationRegression("pass");
        c.setStatus(status);
        c.setBatchId("b-1");
        c.setEngineDraftRefText("draft:1");
        c.setVersionNo("3");
        c.setCreateTime(LocalDateTime.of(2026, 11, 5, 9, 0, 0));
        when(candidateDao.findById(id)).thenReturn(Optional.of(c));
        return c;
    }

    private static WAgentCandidateReviewDO review(String id, String decision, boolean withToken) {
        WAgentCandidateReviewDO r = new WAgentCandidateReviewDO();
        r.setId(id);
        r.setReviewerId("u9");
        r.setDecision(decision);
        r.setCommentText("ok");
        r.setChangesJson(null);
        r.setApprovalToken(withToken ? "tok" : null);
        r.setReviewTime(LocalDateTime.of(2026, 11, 5, 9, 30, 0));
        return r;
    }

    private JsonNode dataOf(MvcResult r) throws Exception {
        return new ObjectMapper().readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
    }

    @Test
    @DisplayName("#17 列表：data 两键（candidates/total），条目 19 键且键序与旧 Map 一致")
    void listWireShape() throws Exception {
        WAgentCandidateDO c = fullCandidate("c1", CandidateStatus.GENERATED.value());
        when(candidateDao.findByTenant("t1")).thenReturn(List.of(c));

        JsonNode data = dataOf(mockMvc.perform(get("/api/v1/wagent/candidates")).andReturn());

        assertEquals(List.of("candidates", "total"), keys(data));
        JsonNode item = data.path("candidates").get(0);
        assertEquals(CANDIDATE_KEYS, keys(item));
        assertEquals(VALIDATION_KEYS, keys(item.path("validation")));
        assertEquals("2026-11-05T09:00", item.path("create_time").asText());
        assertEquals(false, item.path("expired").asBoolean());
    }

    @Test
    @DisplayName("#18 详情：候选 19 键平铺后追加 reviews（20 键）")
    void detailWireShape() throws Exception {
        fullCandidate("c2", CandidateStatus.REVIEW_PENDING.value());
        when(reviewDao.findByCandidateId("c2"))
                .thenReturn(List.of(review("r1", "APPROVE", true), review("r2", "REVISE", false)));

        JsonNode data = dataOf(mockMvc.perform(get("/api/v1/wagent/candidates/c2")).andReturn());

        assertEquals(DETAIL_KEYS, keys(data));
        assertEquals(VALIDATION_KEYS, keys(data.path("validation")));
        JsonNode reviews = data.path("reviews");
        assertEquals(2, reviews.size());
        assertEquals(REVIEW_KEYS, keys(reviews.get(0)));
        assertEquals(true, reviews.get(0).path("approval_token_present").asBoolean());
        assertEquals(false, reviews.get(1).path("approval_token_present").asBoolean());
        assertEquals("2026-11-05T09:30", reviews.get(0).path("review_time").asText());
    }

    @Test
    @DisplayName("#19 评审：响应仍是候选 19 键 + reviews，status 取裁决后状态")
    void reviewWireShape() throws Exception {
        fullCandidate("c3", CandidateStatus.REVIEW_PENDING.value());
        when(reviewDao.findByCandidateId("c3")).thenReturn(List.of());

        JsonNode data = dataOf(mockMvc.perform(post("/api/v1/wagent/candidates/c3/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"approve\",\"approvalToken\":\"tok-1\"}"))
                .andReturn());

        assertEquals(DETAIL_KEYS, keys(data));
        assertEquals(CandidateStatus.APPROVED.value(), data.path("status").asText());
    }

    @Test
    @DisplayName("#20 501 降级载荷两键（implemented/pending），键名不随 record 改名")
    void publishNotImplementedShape() throws Exception {
        fullCandidate("c4", CandidateStatus.APPROVED.value());

        MvcResult r = mockMvc.perform(post("/api/v1/wagent/candidates/c4/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalToken\":\"tok-2\"}"))
                .andReturn();

        assertEquals(501, r.getResponse().getStatus());
        JsonNode data = dataOf(r);
        assertEquals(List.of("implemented", "pending"), keys(data));
        assertEquals(false, data.path("implemented").asBoolean());
    }
}
