package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.security.decision.SubjectClearanceResolutionService;
import com.chinacreator.gzcm.engine.security.decision.SubjectDepartmentResolutionService;
import com.chinacreator.gzcm.engine.security.rls.RlsSubjectContext;
import com.chinacreator.gzcm.engine.security.service.RowLevelSecurityServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
 * #142/B4-3e-81（差距 D-B4-23 主体域密级腿）— {@code /rls/apply} 侧「主体密级真值解析」的接线门。
 *
 * <p>与 {@link RlsControllerSubjectDepartmentWiringTest} 同族、但分册：机构腿与密级腿各一套夹具，
 * 失败时一眼分得出断的是哪条腿（D-B4-85／D-B4-90 的分态教训——把两条腿塞进同一组用例，
 * 回归时只能靠读栈区分，取证成本比判据成本高得多）。</p>
 *
 * <p>本门盯四件事：</p>
 * <ol>
 *   <li><b>真值进上下文</b>：{@code CONTEXT_CLEARANCE_LEVEL} 谓词读的是
 *       {@link RlsSubjectContext#clearanceLevel()}；解析值没写进去就还是收口前那枚恒 {@code null}
 *       （组件写了没接线＝D-B4-28 前案，解析器单测判绿不代表裁决链上真被走到）。</li>
 *   <li><b>未解析仍落未解析</b>：五档查尽无档案 ⇒ 上下文 {@code null} 且照常 200 交策略拒绝
 *       （{@code RLS_CONTEXT_UNRESOLVED}）。<b>禁</b>在这一层兜底成任何档位——兜底一个数
 *       等于凭空给主体发了一张通行证，也正是收口前 {@code return 1} 冒充 L1 的行范围版本。</li>
 *   <li><b>不可用不被折叠</b>：密级腿故障必须抛 503 承载（裸回 {@code ApiResponse} 无状态行能力，
 *       D-B4-59 族修法），且理由片段必须与机构腿<b>分句</b>——同码同状态可以，同一句话不行。</li>
 *   <li><b>取数次数</b>：一次裁决恰好一次解析；机构腿未解析（{@code orgId=null}）时密级腿照旧发起，
 *       因为 USER/ROLE/TENANT/GLOBAL 四档不依赖机构。</li>
 * </ol>
 */
@Timeout(60)
class RlsControllerSubjectClearanceWiringTest {

    private static Map<String, Object> body(String userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tableName", "ecos_dw.ecos_biz_project_attribution");
        m.put("userId", userId);
        return m;
    }

    private static RowLevelSecurityServiceImpl serviceWithStub() {
        RowLevelSecurityServiceImpl rlsService = mock(RowLevelSecurityServiceImpl.class);
        when(rlsService.apply(anyString(), any(RlsSubjectContext.class))).thenReturn(new LinkedHashMap<>());
        return rlsService;
    }

    /** 机构腿在本门只做回显——密级腿判据不依赖机构真值，混在一起会让失败归因分不清腿。 */
    private static SubjectDepartmentResolutionService echoOrgResolver() {
        SubjectDepartmentResolutionService resolver = mock(SubjectDepartmentResolutionService.class);
        when(resolver.resolvePrimaryOrgId(anyString())).thenReturn("ORG-ECHO");
        return resolver;
    }

    private static RlsSubjectContext captured(RowLevelSecurityServiceImpl rlsService) {
        ArgumentCaptor<RlsSubjectContext> captor = ArgumentCaptor.forClass(RlsSubjectContext.class);
        verify(rlsService, org.mockito.Mockito.atLeastOnce())
                .apply(anyString(), captor.capture());
        var all = captor.getAllValues();
        return all.get(all.size() - 1);
    }

    @Test
    @DisplayName("密级真值写进 RLS 主体上下文，且取自信任链 userId + 已解析机构真值")
    void resolvedClearanceReachesTheRlsSubjectContext() {
        RowLevelSecurityServiceImpl rlsService = serviceWithStub();
        SubjectClearanceResolutionService clearanceResolver = mock(SubjectClearanceResolutionService.class);
        when(clearanceResolver.resolveClearanceLevel(anyString(), any(), any())).thenReturn(4);

        ApiResponse<Map<String, Object>> resp =
                new RlsController(rlsService, echoOrgResolver(), clearanceResolver).apply(body("u-9"));

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode(), String.valueOf(resp));
        assertEquals(Integer.valueOf(4), captured(rlsService).clearanceLevel(),
                "主体密级真值必须进 RLS 上下文，否则 CONTEXT_CLEARANCE_LEVEL 谓词恒按未解析拒绝");
        verify(clearanceResolver).resolveClearanceLevel(eq("u-9"), any(), eq("ORG-ECHO"));
    }

    @Test
    @DisplayName("自报密级无读数通道：请求体塞 clearanceLevel/requiredLevel 都不影响上下文")
    void callerSuppliedClearanceHasNoReadChannel() {
        RowLevelSecurityServiceImpl rlsService = serviceWithStub();
        SubjectClearanceResolutionService clearanceResolver = mock(SubjectClearanceResolutionService.class);
        when(clearanceResolver.resolveClearanceLevel(anyString(), any(), any())).thenReturn(1);
        Map<String, Object> m = body("u-9");
        m.put("clearanceLevel", 9);
        m.put("requiredLevel", 9);

        new RlsController(rlsService, echoOrgResolver(), clearanceResolver).apply(m);

        assertEquals(Integer.valueOf(1), captured(rlsService).clearanceLevel(),
                "上下文里的档位只能来自属主，调用方自报的数一律不读");
        verify(clearanceResolver).resolveClearanceLevel(eq("u-9"), any(), any());
    }

    @Test
    @DisplayName("五档无档案 ⇒ 上下文 clearanceLevel 仍 null 且照常 200 交策略拒绝，禁兜底成任何档位")
    void unresolvedIsNotAnInventedTier() {
        RowLevelSecurityServiceImpl rlsService = serviceWithStub();
        SubjectClearanceResolutionService clearanceResolver = mock(SubjectClearanceResolutionService.class);
        when(clearanceResolver.resolveClearanceLevel(anyString(), any(), any())).thenReturn(null);

        ApiResponse<Map<String, Object>> resp =
                new RlsController(rlsService, echoOrgResolver(), clearanceResolver).apply(body("u-9"));

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode(), "「没配档案」是事实不完备，不是服务故障");
        assertNull(captured(rlsService).clearanceLevel(),
                "兜底成任何档位＝替主体发通行证（收口前末端 return 1 冒充 L1 的行范围形态）");
    }

    @Test
    @DisplayName("密级真值源不可用 ⇒ 抛 503 承载且理由点名密级腿，禁与机构腿／策略失败共用一句理由")
    void unavailableClearanceSourceIsCarriedNotRefolded() {
        RowLevelSecurityServiceImpl rlsService = serviceWithStub();
        SubjectClearanceResolutionService clearanceResolver = mock(SubjectClearanceResolutionService.class);
        when(clearanceResolver.resolveClearanceLevel(anyString(), any(), any())).thenThrow(
                new SubjectClearanceResolutionService.SubjectClearanceSourceUnavailableException(
                        SubjectClearanceResolutionService.REASON_SOURCE_UNAVAILABLE + ": 密级链含断腿"));

        DataBridgeException carried = assertThrows(DataBridgeException.class,
                () -> new RlsController(rlsService, echoOrgResolver(), clearanceResolver).apply(body("u-9")));

        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, carried.getHttpStatus(),
                "不可用 ≠ 无权限（§4.7.2）：裸回端点只能靠抛型写上 503 状态行（D-B4-59 族）");
        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, carried.getErrorCode(), "码面与行面同号");
        String message = String.valueOf(carried.getMessage());
        assertTrue(message.contains(RlsExecutionContract.SEC_UNAVAILABLE_CODE), "机读码必须在场：" + message);
        assertTrue(message.contains("密级"), "理由必须点明断的是密级腿：" + message);
        assertTrue(!message.contains("主体域真值源") && !message.contains("策略应用失败"),
                "禁与机构腿／通用兜底共用同一句理由（取证要能分腿）：" + message);
        assertNotNull(carried.getCause(), "属主原始异常必须可取证，禁只留一句改写后的话");
        verify(rlsService, never()).apply(anyString(), any(RlsSubjectContext.class));
    }

    @Test
    @DisplayName("两条腿理由分句：机构腿断⇒「主体域」、密级腿断⇒「密级」，同码不同句才可取证")
    void theTwoLegsCarveDistinctReasons() {
        SubjectDepartmentResolutionService brokenOrg = mock(SubjectDepartmentResolutionService.class);
        when(brokenOrg.resolvePrimaryOrgId(anyString())).thenThrow(
                new SubjectDepartmentResolutionService.SubjectSourceUnavailableException(
                        SubjectDepartmentResolutionService.REASON_SOURCE_UNAVAILABLE + ": 属主取数失败"));
        String orgLegMessage = String.valueOf(assertThrows(DataBridgeException.class,
                () -> new RlsController(serviceWithStub(), brokenOrg,
                        mock(SubjectClearanceResolutionService.class)).apply(body("u-9"))).getMessage());

        SubjectClearanceResolutionService brokenClearance = mock(SubjectClearanceResolutionService.class);
        when(brokenClearance.resolveClearanceLevel(anyString(), any(), any())).thenThrow(
                new SubjectClearanceResolutionService.SubjectClearanceSourceUnavailableException(
                        SubjectClearanceResolutionService.REASON_SOURCE_UNAVAILABLE + ": 属主取数失败"));
        String clearanceLegMessage = String.valueOf(assertThrows(DataBridgeException.class,
                () -> new RlsController(serviceWithStub(), echoOrgResolver(), brokenClearance)
                        .apply(body("u-9"))).getMessage());

        assertTrue(orgLegMessage.contains("主体域") && !orgLegMessage.contains("密级"), orgLegMessage);
        assertTrue(clearanceLegMessage.contains("密级") && !clearanceLegMessage.contains("主体域"),
                clearanceLegMessage);
        assertTrue(!orgLegMessage.equals(clearanceLegMessage),
                "两条腿共用一句理由＝日志里分不出断的是隶属还是档案链：" + orgLegMessage);
    }

    @Test
    @DisplayName("机构未解析不阻断密级取数：USER/ROLE/TENANT/GLOBAL 四档与机构无关")
    void missingOrganizationStillFetchesClearance() {
        RowLevelSecurityServiceImpl rlsService = serviceWithStub();
        SubjectDepartmentResolutionService orgResolver = mock(SubjectDepartmentResolutionService.class);
        when(orgResolver.resolvePrimaryOrgId(anyString())).thenReturn(null);
        SubjectClearanceResolutionService clearanceResolver = mock(SubjectClearanceResolutionService.class);
        AtomicInteger order = new AtomicInteger();
        when(orgResolver.resolvePrimaryOrgId(anyString())).thenAnswer(inv -> {
            order.set(1);
            return null;
        });
        when(clearanceResolver.resolveClearanceLevel(anyString(), any(), any())).thenAnswer(inv -> {
            order.set(2);
            return 2;
        });

        ApiResponse<Map<String, Object>> resp =
                new RlsController(rlsService, orgResolver, clearanceResolver).apply(body("u-9"));

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode(), String.valueOf(resp));
        assertEquals(2, order.get(), "机构腿先解析、密级腿后取数（顺序反了会把 null 机构送进已发起的级联）");
        assertEquals(Integer.valueOf(2), captured(rlsService).clearanceLevel());
        assertNull(captured(rlsService).orgId());
    }
}
