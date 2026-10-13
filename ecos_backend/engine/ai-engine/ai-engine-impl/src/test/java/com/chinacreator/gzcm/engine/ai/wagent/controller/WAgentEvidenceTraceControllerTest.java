package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentClaimDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentClaimEvidenceDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentEvidenceDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentClaimDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentClaimEvidenceDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentEvidenceDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
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

/** §5.2 #21/#22 claims + evidence 追溯：引用型只读面（跨租户掩 404，无正文、无伪造）。 */
class WAgentEvidenceTraceControllerTest {

    private WAgentRunDao runDao;
    private WAgentClaimDao claimDao;
    private WAgentClaimEvidenceDao relDao;
    private WAgentEvidenceDao evidenceDao;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        runDao = mock(WAgentRunDao.class);
        claimDao = mock(WAgentClaimDao.class);
        relDao = mock(WAgentClaimEvidenceDao.class);
        evidenceDao = mock(WAgentEvidenceDao.class);
        WAgentIdentityResolver identityResolver = mock(WAgentIdentityResolver.class);
        when(identityResolver.require()).thenReturn(new Identity("t1", "u1", "o1"));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new WAgentEvidenceTraceController(
                        runDao, claimDao, relDao, evidenceDao, identityResolver))
                .setControllerAdvice(new WAgentOrchestrationAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    @DisplayName("#21 run 命中 ⇒ claims + 证据关联（引用型）")
    void listClaimsHappyPath() throws Exception {
        WAgentRunDO run = new WAgentRunDO();
        run.setId("r1");
        run.setTenantId("t1");
        when(runDao.findById("r1")).thenReturn(Optional.of(run));

        WAgentClaimDO claim = new WAgentClaimDO();
        claim.setId("cl1");
        claim.setRunId("r1");
        claim.setClaimText("营收环比 +3.2%");
        claim.setEvidenceGrade("A");
        when(claimDao.findByRunId("r1")).thenReturn(List.of(claim));

        WAgentClaimEvidenceDO rel = new WAgentClaimEvidenceDO();
        rel.setClaimId("cl1");
        rel.setEvidenceId("ev1");
        rel.setRelation("support");
        when(relDao.findByClaimId("cl1")).thenReturn(List.of(rel));

        WAgentEvidenceDO ev = new WAgentEvidenceDO();
        ev.setId("ev1");
        ev.setTenantId("t1");
        ev.setSourceEngine("data");
        when(evidenceDao.findById("ev1")).thenReturn(Optional.of(ev));

        MvcResult r = mockMvc.perform(get("/api/v1/wagent/claims/r1")).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        JsonNode data = new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertEquals(1, data.path("total").asInt());
        assertEquals("A", data.path("claims").get(0).path("evidence_grade").asText());
        assertEquals("ev1", data.path("claims").get(0).path("evidence_links").get(0)
                .path("evidence_id").asText());
    }

    @Test
    @DisplayName("#21 跨租户 run ⇒ 404 E-NOT-FOUND（防枚举）")
    void crossTenantRunMasked() throws Exception {
        WAgentRunDO run = new WAgentRunDO();
        run.setId("r2");
        run.setTenantId("other");
        when(runDao.findById("r2")).thenReturn(Optional.of(run));
        MvcResult r = mockMvc.perform(get("/api/v1/wagent/claims/r2")).andReturn();
        assertEquals(404, r.getResponse().getStatus());
        assertEquals("E-NOT-FOUND", new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("errorCode").asText());
    }

    @Test
    @DisplayName("#22 证据追溯：出引用四元组/哈希/反向 claim 索引 + 深追未接线的如实标注")
    void traceEvidence() throws Exception {
        WAgentEvidenceDO ev = new WAgentEvidenceDO();
        ev.setId("ev9");
        ev.setTenantId("t1");
        ev.setSourceEngine("ontology");
        ev.setSourceObjectId("obj-1");
        ev.setContentHash("hash-1");
        when(evidenceDao.findById("ev9")).thenReturn(Optional.of(ev));

        WAgentClaimEvidenceDO rel = new WAgentClaimEvidenceDO();
        rel.setClaimId("cl1");
        rel.setEvidenceId("ev9");
        rel.setRelation("support");
        when(relDao.findByEvidenceId("ev9")).thenReturn(List.of(rel));
        WAgentClaimDO claim = new WAgentClaimDO();
        claim.setId("cl1");
        claim.setRunId("r1");
        when(claimDao.findById("cl1")).thenReturn(Optional.of(claim));

        MvcResult r = mockMvc.perform(get("/api/v1/wagent/evidence/ev9")).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        JsonNode data = new ObjectMapper().readTree(
                r.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertEquals("hash-1", data.path("content_hash").asText());
        assertEquals("r1", data.path("used_by_claims").get(0).path("run_id").asText());
        assertEquals(true, data.path("engine_trace").asText().startsWith("pending#"));
    }

    @Test
    @DisplayName("#22 未知证据 ⇒ 404（禁空对象 200）")
    void unknownEvidence404() throws Exception {
        when(evidenceDao.findById("ghost")).thenReturn(Optional.empty());
        assertEquals(404, mockMvc.perform(get("/api/v1/wagent/evidence/ghost"))
                .andReturn().getResponse().getStatus());
    }
}
