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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #142/B4-3e-81（差距 D-B4-23 余项）— {@code /decision} 入口侧「主体密级真值解析」的接线门。
 *
 * <p>与 {@code DecisionControllerSubjectDepartmentWiringTest}（机构腿）同族、但各用独立夹具：
 * 两腿一旦合用一条用例，失败就分不清断的是机构隶属还是密级档案链——那正是 D-B4-85／D-B4-90
 * 分态族禁掉的形态。本门盯四件事：</p>
 * <ol>
 *   <li><b>真值进得了上下文</b>：{@link DecisionContext#clearanceLevel()} 收的是属主五档链的读数；
 *       组件写了没接线＝台账照样记「已实现」，D-B4-28 的列面消费腿即前案。</li>
 *   <li><b>机构真值先于密级取数</b>：ORG 档要比对机构号，所以密级腿必须吃<b>已解析</b>的 orgId，
 *       而不是 null 或调用方自报机构。</li>
 *   <li><b>自报密级无读数通道</b>：请求体里塞 {@code clearanceLevel} 一律无效（恢复它等于
 *       把 W40 关掉的自报洞再开一次）。</li>
 *   <li><b>三态互斥</b>：未解析⇒照常 200 交策略裁决；断腿／门面缺席⇒独立一条 503 腿，
 *       理由句必须与机构腿、声明域腿、通用兜底腿都能分辨。</li>
 * </ol>
 */
@Timeout(60)
class DecisionControllerSubjectClearanceWiringTest {

    private static DecisionBundle stubBundle() {
        return new DecisionBundle(new LinkedHashMap<>(), new LinkedHashMap<>(),
                List.of(), new LinkedHashMap<>(), "d-1", "t-1");
    }

    private static Map<String, Object> body(Map<String, Object> subjectExtra) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assetRef", Map.of("schema", "public", "table", "t_demo", "columns", List.of("c1")));
        m.put(DecisionChannelContract.KEY_PURPOSE, DecisionChannelContract.PURPOSE_PAGE);
        Map<String, Object> subject = new LinkedHashMap<>();
        subject.put(DecisionChannelContract.KEY_SUBJECT_ID, "u-9");
        if (subjectExtra != null) subject.putAll(subjectExtra);
        m.put(DecisionChannelContract.KEY_SUBJECT, subject);
        return m;
    }

    private static SecurityDecisionService serviceWithStub() {
        SecurityDecisionService service = mock(SecurityDecisionService.class);
        when(service.decide(any(), any(), any())).thenReturn(stubBundle());
        return service;
    }

    /** 机构腿与声明域腿在本门只回显真值机构，保证密级腿的取数键可断言。 */
    private static SubjectDepartmentResolutionService orgResolver(String orgId) {
        SubjectDepartmentResolutionService resolver = mock(SubjectDepartmentResolutionService.class);
        when(resolver.resolvePrimaryOrgId(anyString())).thenReturn(orgId);
        return resolver;
    }

    private static DeclaredScopeResolutionService echoDeclaredResolver() {
        DeclaredScopeResolutionService resolver = mock(DeclaredScopeResolutionService.class);
        when(resolver.resolveForAiTool(any())).thenAnswer(inv -> inv.getArgument(0));
        return resolver;
    }

    private static DecisionContext captured(SecurityDecisionService service) {
        ArgumentCaptor<DecisionContext> captor = ArgumentCaptor.forClass(DecisionContext.class);
        verify(service, times(1)).decide(any(AssetRef.class),
                any(DecisionBundle.Purpose.class), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("真值：属主读数写进裁决上下文，且取数键是「主体 + 已解析机构」")
    void resolvedClearanceReachesTheDecisionContext() {
        SecurityDecisionService service = serviceWithStub();
        SubjectClearanceResolutionService clearanceResolver = mock(SubjectClearanceResolutionService.class);
        when(clearanceResolver.resolveClearanceLevel(anyString(), any(), any())).thenReturn(4);

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = new DecisionController(
                service, echoDeclaredResolver(), orgResolver("ORG-REAL"), clearanceResolver)
                .decide(body(null));

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getBody().getCode(), String.valueOf(resp));
        assertEquals(Integer.valueOf(4), captured(service).clearanceLevel(),
                "主体密级真值必须进裁决上下文，否则 CONTEXT_CLEARANCE_LEVEL 绑定腿恒按未解析拒绝");
        verify(clearanceResolver).resolveClearanceLevel(eq("u-9"), any(), eq("ORG-REAL"));
    }

    @Test
    @DisplayName("调用方自报密级没有任何读数通道：塞什么档位都不改上下文")
    void callerSuppliedClearanceHasNoReadChannel() {
        SecurityDecisionService service = serviceWithStub();
        SubjectClearanceResolutionService clearanceResolver = mock(SubjectClearanceResolutionService.class);
        when(clearanceResolver.resolveClearanceLevel(anyString(), any(), any())).thenReturn(1);

        new DecisionController(service, echoDeclaredResolver(), orgResolver("ORG-REAL"), clearanceResolver)
                .decide(body(Map.of("clearanceLevel", 9, "requiredLevel", 9)));

        assertEquals(Integer.valueOf(1), captured(service).clearanceLevel(),
                "自报档位若被采信，等于恢复 W40 关掉的「主体属性由调用方声明」洞");
        verify(clearanceResolver, times(1)).resolveClearanceLevel(anyString(), any(), any());
    }

    @Test
    @DisplayName("未解析（五档无档案）⇒ 上下文仍 null 且照常 200 交策略裁决，禁兜底成任何档位")
    void unresolvedStaysAnOrdinaryAnswer() {
        SecurityDecisionService service = serviceWithStub();
        SubjectClearanceResolutionService clearanceResolver = mock(SubjectClearanceResolutionService.class);
        when(clearanceResolver.resolveClearanceLevel(anyString(), any(), any())).thenReturn(null);

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = new DecisionController(
                service, echoDeclaredResolver(), orgResolver("ORG-REAL"), clearanceResolver)
                .decide(body(null));

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getBody().getCode(), String.valueOf(resp));
        assertNull(captured(service).clearanceLevel(),
                "「没配档案」就是没配档案——照抄准入面的兜底 L1 会把它谎报成内部级真值");
    }

    @Test
    @DisplayName("真值源不可用（含五档链断腿）⇒ 独立 503 腿，理由与机构腿／声明域腿／通用兜底可分辨")
    void unavailableClearanceSourceFailsClosedAs503() {
        SecurityDecisionService service = serviceWithStub();
        SubjectClearanceResolutionService clearanceResolver = mock(SubjectClearanceResolutionService.class);
        when(clearanceResolver.resolveClearanceLevel(anyString(), any(), any())).thenThrow(
                new SubjectClearanceResolutionService.SubjectClearanceSourceUnavailableException(
                        SubjectClearanceResolutionService.REASON_SOURCE_UNAVAILABLE
                                + ": 密级链含断腿，读数不可信: degradedTiers=[USER]"));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = new DecisionController(
                service, echoDeclaredResolver(), orgResolver("ORG-REAL"), clearanceResolver)
                .decide(body(null));

        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, resp.getStatusCode().value(),
                "不可用 ≠ 无权限 ≠ 未解析（§4.7.2）：真值源故障必须落 503，禁裸回 200 或折成 403");
        ApiResponse<Map<String, Object>> envelope = resp.getBody();
        assertNotNull(envelope);
        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, envelope.getCode(), "状态行与信封同号");
        String message = String.valueOf(envelope.getMessage());
        assertTrue(message.contains(RlsExecutionContract.SEC_UNAVAILABLE_CODE), "机读码必须在场：" + message);
        assertTrue(message.contains("密级"), "理由必须点明断的是密级腿：" + message);
        assertTrue(!message.contains("主体域真值源") && !message.contains("声明域")
                        && !message.contains("内部裁决失败"),
                "禁与机构腿／声明域腿／通用兜底共用同一句理由（取证要能分腿）：" + message);
        assertNull(envelope.getData(), "不可用态不得带着裁决结果返回");
        verify(service, never()).decide(any(), any(), any());
    }

    @Test
    @DisplayName("无主体上下文（403 拒绝）时密级腿一次都不触发")
    void noSubjectMeansNoClearanceFetch() {
        SecurityDecisionService service = serviceWithStub();
        SubjectClearanceResolutionService clearanceResolver = mock(SubjectClearanceResolutionService.class);
        Map<String, Object> m = body(null);
        m.put(DecisionChannelContract.KEY_SUBJECT, Map.of(DecisionChannelContract.KEY_SUBJECT_ID, "  "));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = new DecisionController(
                service, echoDeclaredResolver(), orgResolver("ORG-REAL"), clearanceResolver).decide(m);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(), String.valueOf(resp));
        verify(clearanceResolver, never()).resolveClearanceLevel(anyString(), any(), any());
    }
}
