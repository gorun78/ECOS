package com.chinacreator.gzcm.engine.security.decision;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.security.decision.DataScopeContract;
import com.chinacreator.gzcm.common.security.decision.DecisionChannelContract;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.security.controller.DecisionController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #142/B4-3e-79（差距 D-B4-36）— {@code /decision} 入口侧「声明域真值解析」的接线门。
 *
 * <p>解析器本身判绿不等于裁决链上真被走到：本门只验三件事，缺一件就是"新组件写了但没接线"
 * 这一 ECOS 台账反复登记的形态（D-B4-28 的列面消费腿即同型前案）：</p>
 * <ol>
 *   <li><b>AI 通道必取真值</b>：请求体里的 {@code resource.dataScope} 只作为<b>项目集声明</b>
 *       进入解析器，写进 {@link DecisionContext} 的部门必须是解析器给的真值（自报部门不得透传）；</li>
 *   <li><b>页面/导出通道零影响</b>：非 AI 通道一次都不触发解析器（否则每次页面裁决都多一次
 *       跨引擎同步调用，且把 D-B4-20 的"载荷逐字节不变"承诺打穿）；</li>
 *   <li><b>真值源不可用落 503</b>：解析器抛不可用态时，状态行与机读码都走既有 fail-closed 出口
 *       （{@code ECOS-SEC-501} = {@code RlsExecutionContract.SEC_UNAVAILABLE_CODE} 单源读数），
 *       <b>禁</b>折成 403「越权」或 200「裁决通过」。</li>
 * </ol>
 *
 * <p>构造参数里的 {@code SubjectDepartmentResolutionService} 一律 mock（mock 默认返回
 * {@code null}＝如实未解析），使本门只看声明域一条腿；主体域腿的接线判据另见
 * {@code DecisionControllerSubjectDepartmentWiringTest}（#142/B4-3e-80，D-B4-23）。</p>
 */
@Timeout(60)
class DecisionControllerDeclaredScopeWiringTest {

    private static DecisionBundle stubBundle() {
        return new DecisionBundle(new LinkedHashMap<>(), new LinkedHashMap<>(),
                List.of(), new LinkedHashMap<>(), "d-1", "t-1");
    }

    private static Map<String, Object> body(String purpose, String departmentId, List<String> projectIds) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assetRef", Map.of("schema", "public", "table", "t_demo", "columns", List.of("c1")));
        if (purpose != null) m.put(DecisionChannelContract.KEY_PURPOSE, purpose);
        m.put("subject", Map.of("subjectId", "u1"));
        if (departmentId != null || projectIds != null) {
            Map<String, Object> scope = new LinkedHashMap<>();
            if (departmentId != null) scope.put(DataScopeContract.KEY_DEPARTMENT_ID, departmentId);
            if (projectIds != null) scope.put(DataScopeContract.KEY_PROJECT_IDS, projectIds);
            m.put(DataScopeContract.KEY_RESOURCE, Map.of(DataScopeContract.KEY_DATA_SCOPE, scope));
        }
        return m;
    }

    /** 取最近一次进入 service.decide 的上下文（循环用例里按调用顺序取末次）。 */
    private static DecisionContext captured(SecurityDecisionService service) {
        ArgumentCaptor<DecisionContext> captor = ArgumentCaptor.forClass(DecisionContext.class);
        org.mockito.Mockito.verify(service, org.mockito.Mockito.atLeastOnce())
                .decide(any(AssetRef.class), any(DecisionBundle.Purpose.class), captor.capture());
        List<DecisionContext> all = captor.getAllValues();
        return all.get(all.size() - 1);
    }

    @Test
    @DisplayName("AI 通道：进解析器的只有项目集（自报部门丢弃），写进上下文的是真值部门")
    void aiChannelResolvesDepartmentFromTheServerSide() {
        SecurityDecisionService service = mock(SecurityDecisionService.class);
        when(service.decide(any(), any(), any())).thenReturn(stubBundle());
        AtomicInteger calls = new AtomicInteger();
        DeclaredScopeResolutionService resolver = new DeclaredScopeResolutionService(
                () -> "http://stub-never-used") {
            @Override
            public DataScopeContract.Declared resolveForAiTool(DataScopeContract.Declared declared) {
                calls.incrementAndGet();
                // 真值语义：只认项目集，部门由属主读数决定（这里直接给出唯一真值域）
                return new DataScopeContract.Declared("D-REAL", declared.projectIds());
            }
        };
        DecisionController controller = new DecisionController(service, resolver,
                mock(SubjectDepartmentResolutionService.class), mock(SubjectClearanceResolutionService.class));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controller.decide(body(DecisionChannelContract.PURPOSE_AI, "D-FAKE", List.of("P1")));

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getBody().getCode(), String.valueOf(resp));
        DecisionContext ctx = captured(service);
        assertEquals(1, calls.get(), "AI 通道必须恰好一次解析（多次＝裁决链上重复取数）");
        assertNotNull(ctx.dataScope(), "解析结果必须写进上下文，否则 safeOpa 仍发不出 resource 段");
        assertEquals("D-REAL", ctx.dataScope().departmentId(), "自报部门不得透传到策略面");
        assertEquals(List.of("P1"), ctx.dataScope().projectIds());
    }

    @Test
    @DisplayName("页面/导出通道：解析器一次都不触发，上下文声明域维持未声明（载荷逐字节不变）")
    void nonAiChannelsNeverTouchTheResolver() {
        SecurityDecisionService service = mock(SecurityDecisionService.class);
        when(service.decide(any(), any(), any())).thenReturn(stubBundle());
        AtomicInteger calls = new AtomicInteger();
        DeclaredScopeResolutionService resolver = new DeclaredScopeResolutionService(
                () -> "http://stub-never-used") {
            @Override
            public DataScopeContract.Declared resolveForAiTool(DataScopeContract.Declared declared) {
                calls.incrementAndGet();
                throw new AssertionError("非 AI 通道不得触发真值解析");
            }
        };
        DecisionController controller = new DecisionController(service, resolver,
                mock(SubjectDepartmentResolutionService.class), mock(SubjectClearanceResolutionService.class));

        for (String purpose : List.of(DecisionChannelContract.PURPOSE_PAGE,
                DecisionChannelContract.PURPOSE_EXPORT)) {
            ApiResponse<Map<String, Object>> envelope =
                    controller.decide(body(purpose, "D-FAKE", List.of("P1"))).getBody();

            assertEquals(ApiResponse.CODE_SUCCESS, envelope.getCode(), purpose);
            DecisionContext ctx = captured(service);
            // 非 AI 通道仍按既有语义承流请求体里的声明段（页面/导出没有 AI 列判据），
            // 但绝不经解析器——这条腿是"新组件不影响既有两通道"的凭据。
            assertEquals("D-FAKE", ctx.dataScope().departmentId(), purpose + " 不得被解析器改写");
            assertEquals(List.of("P1"), ctx.dataScope().projectIds(), purpose);
        }
        assertEquals(0, calls.get(), "页面/导出通道触发解析＝每次裁决多一次跨引擎同步调用");
    }

    @Test
    @DisplayName("AI 未声明项目集：不取数也不伪造，上下文按未声明写出（策略落 AI_SCOPE_NOT_DECLARED）")
    void aiWithoutDeclaredProjectsStaysUndeclared() {
        SecurityDecisionService service = mock(SecurityDecisionService.class);
        when(service.decide(any(), any(), any())).thenReturn(stubBundle());
        AtomicInteger calls = new AtomicInteger();
        DeclaredScopeResolutionService resolver = new DeclaredScopeResolutionService(
                () -> "http://stub-unused") {
            @Override
            public DataScopeContract.Declared resolveForAiTool(DataScopeContract.Declared declared) {
                calls.incrementAndGet();
                // 与生产实现同形的短路：空项目集＝不取数、部门丢弃
                return declared == null || !declared.hasProjectScope()
                        ? new DataScopeContract.Declared(null, List.of())
                        : new DataScopeContract.Declared("D-REAL", declared.projectIds());
            }
        };
        DecisionController controller = new DecisionController(service, resolver,
                mock(SubjectDepartmentResolutionService.class), mock(SubjectClearanceResolutionService.class));

        assertEquals(ApiResponse.CODE_SUCCESS,
                controller.decide(body(DecisionChannelContract.PURPOSE_AI, null, null))
                        .getBody().getCode());
        DecisionContext ctx = captured(service);
        assertEquals(1, calls.get(), "解析器仍被调用一次（由它判定未声明并丢弃部门），但内部不得取数");
        assertTrue(ctx.dataScope() == null || !ctx.dataScope().hasProjectScope(),
                "未声明项目集不得被伪造成任何范围：" + ctx.dataScope());
        assertTrue(ctx.dataScope() == null || !ctx.dataScope().hasDepartment(),
                "未声明时禁凭空造一个部门：" + ctx.dataScope());
    }

    @Test
    @DisplayName("真值源不可用：503 + ECOS-SEC-501（禁折成 403 越权，也禁放行）")
    void unavailableSourceFailsClosedAs503() {
        SecurityDecisionService service = mock(SecurityDecisionService.class);
        DeclaredScopeResolutionService resolver = new DeclaredScopeResolutionService(
                () -> "http://stub-unused") {
            @Override
            public DataScopeContract.Declared resolveForAiTool(DataScopeContract.Declared declared) {
                throw new DeclaredScopeResolutionService.ScopeSourceUnavailableException(
                        DeclaredScopeResolutionService.REASON_SOURCE_UNAVAILABLE + ": 桩不可达");
            }
        };
        DecisionController controller = new DecisionController(service, resolver,
                mock(SubjectDepartmentResolutionService.class), mock(SubjectClearanceResolutionService.class));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = controller.decide(
                body(DecisionChannelContract.PURPOSE_AI, null, List.of("P1")));

        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, resp.getStatusCode().value(),
                "不可用 ≠ 无权限（§4.7.2）：真值源故障必须落 503，禁折成 403");
        ApiResponse<Map<String, Object>> envelope = resp.getBody();
        assertNotNull(envelope);
        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, envelope.getCode(), "状态行与信封同号");
        assertTrue(String.valueOf(envelope.getMessage()).contains(RlsExecutionContract.SEC_UNAVAILABLE_CODE),
                "机读码必须在场：" + envelope.getMessage());
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    @DisplayName("解析器缺席≠放行：AI 腿异常向上只剩 fail-closed 出口，状态行不得是 200")
    void resolutionFailureIsNeverReadAsAllow() {
        SecurityDecisionService service = mock(SecurityDecisionService.class);
        // 故意不桩 service.decide：解析器先抛，任何走到 service 的调用都会以 null 信封立刻变红
        DeclaredScopeResolutionService resolver = new DeclaredScopeResolutionService(
                () -> "http://stub-unused") {
            @Override
            public DataScopeContract.Declared resolveForAiTool(DataScopeContract.Declared declared) {
                throw new IllegalStateException("未预期故障（非声明的不可用态）");
            }
        };
        DecisionController controller = new DecisionController(service, resolver,
                mock(SubjectDepartmentResolutionService.class), mock(SubjectClearanceResolutionService.class));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = controller.decide(
                body(DecisionChannelContract.PURPOSE_AI, null, List.of("P1")));

        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, resp.getStatusCode().value(),
                "兜底 catch 同样必须显式承载不可用态，禁裸回 200");
        assertNull(resp.getBody().getData(), "不可用态不得带着裁决结果返回");
        org.mockito.Mockito.verifyNoInteractions(service);
    }
}
