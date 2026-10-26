package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.security.decision.SubjectDepartmentResolutionService;
import com.chinacreator.gzcm.engine.security.rls.RlsSubjectContext;
import com.chinacreator.gzcm.engine.security.service.RowLevelSecurityServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #142/B4-3e-80（差距 D-B4-23 主体域腿）— {@code /rls/apply} 侧主体域接线与不可用承载门。
 *
 * <p>本门盯三件在台账上反复翻车的事：</p>
 * <ol>
 *   <li><b>真值进得了上下文</b>：依赖 {@code CONTEXT_ORG} 的谓词读的是
 *       {@link RlsSubjectContext#orgId()}；解析结果没写进去，声明侧写得再真也只是
 *       {@code 1=0}（D-B4-17 的渲染腿即前案）。</li>
 *   <li><b>不可用承载不被折叠</b>：{@code apply} 裸回 {@code ApiResponse}、没有任何 HTTP 状态能力，
 *       属主不可达必须以 {@link DataBridgeException} 抛型上抛（D-B4-59 族修法）；
 *       同方法既有的 {@code catch (Exception)} 会把它再折回 {@code internalError}，
 *       因此放行腿必须与兜底腿同时在场——本门就是钉这一对的。</li>
 *   <li><b>未解析 ≠ 不可用</b>（§4.7.2）：解析器返回 {@code null} 时照常 200 交策略拒绝，
 *       抛不可用态时才转 503 承载；两者互换形态就是「把故障谎报成没权限」或反之。</li>
 * </ol>
 */
@Timeout(60)
class RlsControllerSubjectDepartmentWiringTest {

    private static Map<String, Object> body(String userId, Map<String, Object> extra) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tableName", "ecos_dw.ecos_biz_project_attribution");
        m.put("userId", userId);
        if (extra != null) m.putAll(extra);
        return m;
    }

    @Test
    @DisplayName("属主真值写进 RLS 主体上下文；请求体自报 orgId 没有任何读数通道")
    void resolvedOrgReachesTheRlsSubjectContext() {
        RowLevelSecurityServiceImpl rlsService = mock(RowLevelSecurityServiceImpl.class);
        when(rlsService.apply(anyString(), any(RlsSubjectContext.class))).thenReturn(new LinkedHashMap<>());
        SubjectDepartmentResolutionService resolver = mock(SubjectDepartmentResolutionService.class);
        when(resolver.resolvePrimaryOrgId(anyString())).thenReturn("ORG-REAL");

        ApiResponse<Map<String, Object>> resp = new RlsController(rlsService, resolver)
                .apply(body("u-9", Map.of("orgId", "ORG-FORGED", "department", "ORG-FORGED-2")));

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode(), String.valueOf(resp));
        ArgumentCaptor<RlsSubjectContext> captor = ArgumentCaptor.forClass(RlsSubjectContext.class);
        verify(rlsService).apply(eq("ecos_dw.ecos_biz_project_attribution"), captor.capture());
        RlsSubjectContext ctx = captor.getValue();
        assertEquals("ORG-REAL", ctx.orgId(), "主体域真值必须进 RLS 上下文，否则 CONTEXT_ORG 谓词恒拒");
        assertEquals("u-9", ctx.userId());
        assertNull(ctx.clearanceLevel(), "密级腿本批未接（台账余项），禁顺手伪造一个档位");
        verify(resolver).resolvePrimaryOrgId("u-9");
    }

    @Test
    @DisplayName("解析未果 ⇒ 上下文 orgId 仍 null 且照常应答（事实不完备交策略拒绝，不是服务故障）")
    void unresolvedStaysAnOrdinaryAnswer() {
        RowLevelSecurityServiceImpl rlsService = mock(RowLevelSecurityServiceImpl.class);
        when(rlsService.apply(anyString(), any(RlsSubjectContext.class))).thenReturn(new LinkedHashMap<>());
        SubjectDepartmentResolutionService resolver = mock(SubjectDepartmentResolutionService.class);
        when(resolver.resolvePrimaryOrgId(anyString())).thenReturn(null);

        ApiResponse<Map<String, Object>> resp = new RlsController(rlsService, resolver)
                .apply(body("u-9", null));

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode(), String.valueOf(resp));
        ArgumentCaptor<RlsSubjectContext> captor = ArgumentCaptor.forClass(RlsSubjectContext.class);
        verify(rlsService).apply(anyString(), captor.capture());
        assertNull(captor.getValue().orgId(), "禁替主体挑一个机构（PIPImpl 的 orgs.get(0) 形态）");
    }

    @Test
    @DisplayName("属主不可用 ⇒ 抛 503 承载并原样上抛，禁被同方法兜底 catch 折成 internalError")
    void unavailableSubjectSourceIsCarriedNotRefolded() {
        RowLevelSecurityServiceImpl rlsService = mock(RowLevelSecurityServiceImpl.class);
        SubjectDepartmentResolutionService resolver = mock(SubjectDepartmentResolutionService.class);
        when(resolver.resolvePrimaryOrgId(anyString())).thenThrow(
                new SubjectDepartmentResolutionService.SubjectSourceUnavailableException(
                        SubjectDepartmentResolutionService.REASON_SOURCE_UNAVAILABLE + ": IOrganizationService 未装配"));

        DataBridgeException carried = assertThrows(DataBridgeException.class,
                () -> new RlsController(rlsService, resolver).apply(body("u-9", null)));

        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, carried.getHttpStatus(),
                "裸回端点的不可用态只能靠抛型写上状态行（D-B4-59 族）");
        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, carried.getErrorCode(), "码面与行面同号");
        assertTrue(String.valueOf(carried.getMessage()).contains(RlsExecutionContract.SEC_UNAVAILABLE_CODE),
                "机读码必须在场：" + carried.getMessage());
        assertTrue(String.valueOf(carried.getMessage()).contains("主体域"),
                "理由必须点明断的是哪条腿：" + carried.getMessage());
        assertNotNull(carried.getCause(), "属主原始异常必须可取证，禁只留一句改写后的话");
        verify(rlsService, never()).apply(anyString(), any(RlsSubjectContext.class));
    }

    @Test
    @DisplayName("参数校验先于取数：tableName/userId 缺失时一次都不打属主")
    void validationPrecedesTheFetch() {
        RowLevelSecurityServiceImpl rlsService = mock(RowLevelSecurityServiceImpl.class);
        SubjectDepartmentResolutionService resolver = mock(SubjectDepartmentResolutionService.class);
        RlsController controller = new RlsController(rlsService, resolver);

        assertEquals(ApiResponse.CODE_BAD_REQUEST, controller.apply(body("  ", null)).getCode());
        assertEquals(ApiResponse.CODE_BAD_REQUEST, controller.apply(Map.of("userId", "u-9")).getCode());
        verify(resolver, never()).resolvePrimaryOrgId(anyString());
        verify(rlsService, never()).apply(anyString(), any(RlsSubjectContext.class));
    }
}
