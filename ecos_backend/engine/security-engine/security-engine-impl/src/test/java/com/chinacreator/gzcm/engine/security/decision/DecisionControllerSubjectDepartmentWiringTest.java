package com.chinacreator.gzcm.engine.security.decision;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.security.decision.DecisionChannelContract;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.security.controller.DecisionController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #142/B4-3e-80（差距 D-B4-23 主体域腿）— {@code /decision} 入口侧「主体域真值解析」的接线门。
 *
 * <p>解析器单测判绿不等于裁决链上真被走到（D-B4-28 的列面消费腿即前案：组件写了没接线，
 * 台账照样记「已实现」）。本门只验四件事：</p>
 * <ol>
 *   <li><b>真值进上下文</b>：写进 {@link DecisionContext#orgId()} 的必须是解析器给的值；</li>
 *   <li><b>自报机构无读数通道</b>：请求体 {@code subject.department}／{@code subject.orgId}
 *       任何形式的自报都不得影响上下文——恢复它等于恢复 W40 关掉的洞；</li>
 *   <li><b>未解析与不可用分态</b>：解析器返回 {@code null} 是「事实不完备」，上下文照旧 {@code null}
 *       且仍 200 交策略裁决；抛不可用态是「事实取不到」，必须 503 + {@code ECOS-SEC-501}，
 *       <b>禁</b>落进「security 内部裁决失败」的通用腿（取证分不出哪条腿断的）；</li>
 *   <li><b>取数次数</b>：一次裁决恰好一次解析；无主体上下文（403 拒绝）时一次都不触发。</li>
 * </ol>
 */
@Timeout(60)
class DecisionControllerSubjectDepartmentWiringTest {

    private static DecisionBundle stubBundle() {
        return new DecisionBundle(new LinkedHashMap<>(), new LinkedHashMap<>(),
                List.of(), new LinkedHashMap<>(), "d-1", "t-1");
    }

    /** 请求体：subject 段除 subjectId 外还塞两枚自报机构键，用于验「无读数通道」。 */
    private static Map<String, Object> body(String purpose, Map<String, Object> subjectExtra) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assetRef", Map.of("schema", "public", "table", "t_demo", "columns", List.of("c1")));
        if (purpose != null) m.put(DecisionChannelContract.KEY_PURPOSE, purpose);
        Map<String, Object> subject = new LinkedHashMap<>();
        subject.put(DecisionChannelContract.KEY_SUBJECT_ID, "u-9");
        if (subjectExtra != null) subject.putAll(subjectExtra);
        m.put(DecisionChannelContract.KEY_SUBJECT, subject);
        return m;
    }

    private static DecisionContext captured(SecurityDecisionService service) {
        ArgumentCaptor<DecisionContext> captor = ArgumentCaptor.forClass(DecisionContext.class);
        org.mockito.Mockito.verify(service, org.mockito.Mockito.atLeastOnce())
                .decide(any(AssetRef.class), any(DecisionBundle.Purpose.class), captor.capture());
        List<DecisionContext> all = captor.getAllValues();
        return all.get(all.size() - 1);
    }

    private static SecurityDecisionService serviceWithStub() {
        SecurityDecisionService service = mock(SecurityDecisionService.class);
        when(service.decide(any(), any(), any())).thenReturn(stubBundle());
        return service;
    }

    /** 声明域解析器在本门只做回显——主体域腿与它互不相干，混在一起会让失败归因分不清腿。 */
    private static DeclaredScopeResolutionService echoDeclaredResolver() {
        DeclaredScopeResolutionService resolver = mock(DeclaredScopeResolutionService.class);
        when(resolver.resolveForAiTool(any())).thenAnswer(inv -> inv.getArgument(0));
        return resolver;
    }

    @Test
    @DisplayName("页面通道：真值机构写进上下文，且取自信任链 userId（不是自报 subjectId 的改写形态）")
    void resolvedOrgReachesTheDecisionContext() {
        SecurityDecisionService service = serviceWithStub();
        SubjectDepartmentResolutionService subjectResolver = mock(SubjectDepartmentResolutionService.class);
        when(subjectResolver.resolvePrimaryOrgId(anyString())).thenReturn("ORG-REAL");

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = new DecisionController(
                service, echoDeclaredResolver(), subjectResolver)
                .decide(body(DecisionChannelContract.PURPOSE_PAGE, null));

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getBody().getCode(), String.valueOf(resp));
        assertEquals("ORG-REAL", captured(service).orgId(), "主体域真值必须进裁决上下文");
        ArgumentCaptor<String> userId = ArgumentCaptor.forClass(String.class);
        verify(subjectResolver).resolvePrimaryOrgId(userId.capture());
        assertEquals("u-9", userId.getValue(), "解析键只能是裁决主体本身");
    }

    @Test
    @DisplayName("自报机构无读数通道：subject.department / subject.orgId 塞什么都不改变上下文")
    void callerSuppliedOrganizationHasNoReadChannel() {
        SecurityDecisionService service = serviceWithStub();
        SubjectDepartmentResolutionService subjectResolver = mock(SubjectDepartmentResolutionService.class);
        when(subjectResolver.resolvePrimaryOrgId(anyString())).thenReturn("ORG-REAL");

        new DecisionController(service, echoDeclaredResolver(), subjectResolver).decide(
                body(DecisionChannelContract.PURPOSE_PAGE,
                        Map.of("department", "ORG-FORGED", "orgId", "ORG-FORGED-2")));

        DecisionContext ctx = captured(service);
        assertEquals("ORG-REAL", ctx.orgId());
        verify(subjectResolver).resolvePrimaryOrgId("u-9");
    }

    @Test
    @DisplayName("AI 通道同样走主体解析（§4.7.2 p1「域内 ALLOW」的比对主体一侧在此才补齐）")
    void aiChannelAlsoResolvesTheSubjectSide() {
        SecurityDecisionService service = serviceWithStub();
        SubjectDepartmentResolutionService subjectResolver = mock(SubjectDepartmentResolutionService.class);
        when(subjectResolver.resolvePrimaryOrgId(anyString())).thenReturn("ORG-AI");

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = new DecisionController(
                service, echoDeclaredResolver(), subjectResolver)
                .decide(body(DecisionChannelContract.PURPOSE_AI, null));

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getBody().getCode(), String.valueOf(resp));
        assertEquals("ORG-AI", captured(service).orgId(), String.valueOf(resp));
    }

    @Test
    @DisplayName("解析未果（无主机构/多枚歧义）⇒ 上下文仍 null 且照常 200 交策略裁决，禁伪造机构")
    void unresolvedIsNotUnavailable() {
        SecurityDecisionService service = serviceWithStub();
        SubjectDepartmentResolutionService subjectResolver = mock(SubjectDepartmentResolutionService.class);
        when(subjectResolver.resolvePrimaryOrgId(anyString())).thenReturn(null);

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = new DecisionController(
                service, echoDeclaredResolver(), subjectResolver)
                .decide(body(DecisionChannelContract.PURPOSE_PAGE, null));

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getBody().getCode(), String.valueOf(resp));
        assertNull(captured(service).orgId(), "事实不完备就是不完备，禁替主体挑一个机构");
    }

    @Test
    @DisplayName("真值源不可用 ⇒ 503 + ECOS-SEC-501 且理由点名主体域，禁折成内部裁决失败/越权/未解析")
    void unavailableSubjectSourceFailsClosedAs503() {
        SecurityDecisionService service = serviceWithStub();
        SubjectDepartmentResolutionService subjectResolver = mock(SubjectDepartmentResolutionService.class);
        when(subjectResolver.resolvePrimaryOrgId(anyString())).thenThrow(
                new SubjectDepartmentResolutionService.SubjectSourceUnavailableException(
                        SubjectDepartmentResolutionService.REASON_SOURCE_UNAVAILABLE + ": 属主取数失败"));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = new DecisionController(
                service, echoDeclaredResolver(), subjectResolver)
                .decide(body(DecisionChannelContract.PURPOSE_PAGE, null));

        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, resp.getStatusCode().value(),
                "不可用 ≠ 无权限（§4.7.2）：真值源故障必须落 503，禁折成 403 或裸回 200");
        ApiResponse<Map<String, Object>> envelope = resp.getBody();
        assertNotNull(envelope);
        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, envelope.getCode(), "状态行与信封同号");
        String message = String.valueOf(envelope.getMessage());
        assertTrue(message.contains(RlsExecutionContract.SEC_UNAVAILABLE_CODE),
                "机读码必须在场：" + message);
        assertTrue(message.contains("主体域"), "理由必须点明断的是哪条腿：" + message);
        assertTrue(!message.contains("声明域") && !message.contains("内部裁决失败"),
                "禁与声明域腿／通用兜底共用同一句理由（取证要能分腿）：" + message);
        assertNull(envelope.getData(), "不可用态不得带着裁决结果返回");
        verify(service, never()).decide(any(), any(), any());
    }

    @Test
    @DisplayName("无主体上下文（403 拒绝）时一次都不触发属主取数")
    void noSubjectMeansNoFetch() {
        SecurityDecisionService service = serviceWithStub();
        SubjectDepartmentResolutionService subjectResolver = mock(SubjectDepartmentResolutionService.class);
        Map<String, Object> m = body(DecisionChannelContract.PURPOSE_PAGE, null);
        m.put(DecisionChannelContract.KEY_SUBJECT, Map.of(DecisionChannelContract.KEY_SUBJECT_ID, "  "));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = new DecisionController(
                service, echoDeclaredResolver(), subjectResolver).decide(m);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(), String.valueOf(resp));
        verify(subjectResolver, never()).resolvePrimaryOrgId(anyString());
    }
}
