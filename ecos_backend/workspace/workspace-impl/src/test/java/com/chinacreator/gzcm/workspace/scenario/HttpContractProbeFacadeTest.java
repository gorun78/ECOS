package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * D-W8-1 收口验证：必需边契约探测的生产实现。
 *
 * <p>钉住四件事：① 探测源真的被消费（MAPPING 走 ontology api 门面、EXTRACTION 走 kb api 门面，
 * 零直读 schema）；② 计数与最近校验时间来自应答真值，不是硬编码；
 * ③ COGNITION 边无载体时必须抛错而非返回 0（否则"数据模型缺列"会被冒充成"演示数据缺行"）；
 * ④ 出站凭证中继（缺它则引擎侧默认 DENY，把 plumbing 缺口读成契约不存在 = D-W8-3 同源教训）。</p>
 */
class HttpContractProbeFacadeTest {

    private static final String MAPPING_URL = "http://svc.test/api/v1/ontology/entity-mappings";
    private static final String EXTRACTION_URL = "http://svc.test/api/v1/knowledge/sync/status";

    private final RestTemplate ontologyRest = template();
    private final RestTemplate knowledgeRest = template();
    private final HttpContractProbeFacade facade =
            new HttpContractProbeFacade(ontologyRest, knowledgeRest);

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static RestTemplate template() {
        RestTemplate rest = new RestTemplate();
        rest.setUriTemplateHandler(new DefaultUriBuilderFactory("http://svc.test/api/v1"));
        return rest;
    }

    private static RequiredEdge edge(EdgeType type, BindingType src, BindingType dst) {
        return new RequiredEdge(type, src, dst);
    }

    // ═══════════════ MAPPING ═══════════════

    @Test
    void mappingCountsRowsAndTakesLatestUpdatedAt() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(ontologyRest).build();
        server.expect(requestTo(MAPPING_URL)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess("""
                {"code":0,"message":"ok","data":[
                  {"id":"m1","objectId":"ent_project","datasetId":"ds1",
                   "createdAt":"2026-10-17 02:44:40.631153","updatedAt":"2026-10-17 02:44:40.631153"},
                  {"id":"m2","objectId":"ent_cost","datasetId":"ds2",
                   "createdAt":"2026-10-16 01:00:00.000000","updatedAt":"2026-10-17 09:30:00.000000"}
                ]}""", MediaType.APPLICATION_JSON));

        ContractSnapshot snapshot = facade.fetch(edge(EdgeType.MAPPING, BindingType.DATASET, BindingType.OBJECT_TYPE));

        server.verify();
        assertEquals(2L, snapshot.getContractCount(), "映射契约数 = 门面应答 data 数组长度");
        assertEquals(localInstant("2026-10-17T09:30:00"), snapshot.getLastVerifiedAt(),
                "最近校验时间 = max(updatedAt)，非首行时间");
        assertTrue(snapshot.isVerified());
    }

    @Test
    void mappingWithZeroRowsIsNotVerified() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(ontologyRest).build();
        server.expect(requestTo(MAPPING_URL)).andRespond(withSuccess(
                "{\"code\":0,\"message\":\"ok\",\"data\":[]}", MediaType.APPLICATION_JSON));

        ContractSnapshot snapshot = facade.fetch(edge(EdgeType.MAPPING, BindingType.DATASET, BindingType.OBJECT_TYPE));

        server.verify();
        assertEquals(0L, snapshot.getContractCount());
        assertNull(snapshot.getLastVerifiedAt());
        assertFalse(snapshot.isVerified(), "零映射不得判 present（诚实偏低）");
    }

    // ═══════════════ EXTRACTION ═══════════════

    @Test
    void extractionSumsSyncedCountAcrossObjectTypes() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(knowledgeRest).build();
        server.expect(requestTo(EXTRACTION_URL)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess("""
                {"code":0,"message":"ok","data":{"objectTypes":[
                  {"type":"Table","syncedCount":12,"totalCount":12,"lastSyncTime":"2026-10-17T01:00:00Z"},
                  {"type":"Column","syncedCount":30,"totalCount":40,"lastSyncTime":"2026-10-17T05:00:00Z"},
                  {"type":"Indicator","syncedCount":0,"totalCount":0,"lastSyncTime":null}
                ],"overallStatus":"success"}}""", MediaType.APPLICATION_JSON));

        ContractSnapshot snapshot =
                facade.fetch(edge(EdgeType.EXTRACTION, BindingType.OBJECT_TYPE, BindingType.KNOWLEDGE_BASE));

        server.verify();
        assertEquals(42L, snapshot.getContractCount(), "导入契约数 = 各 objectType syncedCount 之和");
        assertEquals(Instant.parse("2026-10-17T05:00:00Z"), snapshot.getLastVerifiedAt(),
                "最近导入时间 = max(lastSyncTime)");
        assertTrue(snapshot.isVerified());
    }

    @Test
    void extractionNeverRunIsNotVerified() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(knowledgeRest).build();
        server.expect(requestTo(EXTRACTION_URL)).andRespond(withSuccess("""
                {"code":0,"message":"ok","data":{"objectTypes":[
                  {"type":"Table","syncedCount":0,"totalCount":0,"lastSyncTime":null}
                ],"overallStatus":"idle"}}""", MediaType.APPLICATION_JSON));

        ContractSnapshot snapshot =
                facade.fetch(edge(EdgeType.EXTRACTION, BindingType.OBJECT_TYPE, BindingType.KNOWLEDGE_BASE));

        server.verify();
        assertEquals(0L, snapshot.getContractCount());
        assertFalse(snapshot.isVerified(), "从未执行过导入 ⇒ missing，禁按定义面存在判 present");
    }

    // ═══════════════ COGNITION：无载体必须暴露，不得静默 0 ═══════════════

    @Test
    void cognitionHasNoContractCarrierAndThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                facade.fetch(edge(EdgeType.COGNITION, BindingType.KNOWLEDGE_BASE, BindingType.AI_AGENT)));
        assertTrue(ex.getMessage().contains("无契约载体"),
                "原因必须是「载体缺失」而非「探测故障」: " + ex.getMessage());
    }

    @Test
    void optionalEdgesAreNotProbed() {
        assertThrows(IllegalStateException.class, () ->
                facade.fetch(edge(EdgeType.GOVERN, BindingType.SECURITY_POLICY, BindingType.OBJECT_TYPE)));
        assertThrows(IllegalStateException.class, () ->
                facade.fetch(edge(EdgeType.EXPOSE, BindingType.OBJECT_TYPE, BindingType.INTERFACE)));
    }

    // ═══════════════ 失败语义：全部上抛交给 EdgeContractProbe 降级 ═══════════════

    @Test
    void businessErrorEnvelopeThrows() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(ontologyRest).build();
        server.expect(requestTo(MAPPING_URL)).andRespond(withSuccess(
                "{\"code\":500,\"errorCode\":\"ECOS-ONT-001\",\"message\":\"boom\",\"data\":null}",
                MediaType.APPLICATION_JSON));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                facade.fetch(edge(EdgeType.MAPPING, BindingType.DATASET, BindingType.OBJECT_TYPE)));
        server.verify();
        assertTrue(ex.getMessage().contains("code=500"), ex.getMessage());
    }

    @Test
    void missingDataSegmentThrowsAsContractDrift() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(knowledgeRest).build();
        server.expect(requestTo(EXTRACTION_URL)).andRespond(withSuccess(
                "{\"code\":0,\"message\":\"ok\"}", MediaType.APPLICATION_JSON));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                facade.fetch(edge(EdgeType.EXTRACTION, BindingType.OBJECT_TYPE, BindingType.KNOWLEDGE_BASE)));
        server.verify();
        assertTrue(ex.getMessage().contains("data"), ex.getMessage());
    }

    @Test
    void serverErrorThrowsNotSilentZero() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(ontologyRest).build();
        server.expect(requestTo(MAPPING_URL)).andRespond(withServerError());

        assertThrows(org.springframework.web.client.HttpServerErrorException.class, () ->
                facade.fetch(edge(EdgeType.MAPPING, BindingType.DATASET, BindingType.OBJECT_TYPE)));
        server.verify();
    }

    // ═══════════════ 凭证中继（不升权、无上下文时不伪造） ═══════════════

    @Test
    void relaysCallerCredentialsFromInboundRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/v1/workspace/scenarios/sc1/completeness");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer jwt-abc");
        request.addHeader("X-ECOS-USER", "u-admin");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        MockRestServiceServer server = MockRestServiceServer.bindTo(ontologyRest).build();
        server.expect(requestTo(MAPPING_URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer jwt-abc"))
                .andExpect(header("X-ECOS-USER", "u-admin"))
                .andRespond(withSuccess("{\"code\":0,\"data\":[]}", MediaType.APPLICATION_JSON));

        facade.fetch(edge(EdgeType.MAPPING, BindingType.DATASET, BindingType.OBJECT_TYPE));
        server.verify();
    }

    @Test
    void noRequestContextMeansNoForgedCredentials() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(ontologyRest).build();
        server.expect(requestTo(MAPPING_URL)).andExpect(request -> {
            assertTrue(request.getHeaders().getOrEmpty(HttpHeaders.AUTHORIZATION).isEmpty(),
                    "无入口请求上下文时不得伪造 Authorization（保持 fail-closed）");
        }).andRespond(withSuccess("{\"code\":0,\"data\":[]}", MediaType.APPLICATION_JSON));

        facade.fetch(edge(EdgeType.MAPPING, BindingType.DATASET, BindingType.OBJECT_TYPE));
        server.verify();
    }

    // ═══════════════ 时间解析：两态形态兼容 ═══════════════

    @Test
    void parseTimestampHandlesEngineForms() {
        // PG timestamp 的微秒段必须保留（实现 javadoc 明写该形态；截断＝丢精度的静默改写）
        assertEquals(localInstant("2026-10-17T02:44:40.631153"),
                HttpContractProbeFacade.parseTimestamp("2026-10-17 02:44:40.631153"));
        assertEquals(Instant.parse("2026-10-17T02:44:40Z"),
                HttpContractProbeFacade.parseTimestamp("2026-10-17T02:44:40Z"));
        assertEquals(Instant.parse("2026-10-17T01:44:40Z"),
                HttpContractProbeFacade.parseTimestamp("2026-10-17T02:44:40+01:00"));
        assertEquals(Instant.ofEpochMilli(1792219707127L),
                HttpContractProbeFacade.parseTimestamp(1792219707127L));
        assertNull(HttpContractProbeFacade.parseTimestamp(null));
        assertNull(HttpContractProbeFacade.parseTimestamp(""));
        assertNull(HttpContractProbeFacade.parseTimestamp("not-a-time"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void probeDegradesToMissingWithErrorWhenFacadeThrows() {
        ObjectProvider<ContractProbeFacade> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(facade);
        ProbeResult result = new EdgeContractProbe(provider).probe(
                edge(EdgeType.COGNITION, BindingType.KNOWLEDGE_BASE, BindingType.AI_AGENT),
                true, true, true);
        assertFalse(result.isPresent());
        assertTrue(result.getProbeError() != null && result.getProbeError().contains("无契约载体"),
                "missing 必须带原因，前端才能区分「契约未通过」与「载体缺失」");
    }

    private static Instant localInstant(String isoLocal) {
        return java.time.LocalDateTime.parse(isoLocal, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault()).toInstant();
    }
}
