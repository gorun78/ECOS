package com.chinacreator.gzcm.engine.security.decision;

import com.chinacreator.gzcm.sysman.security.clearance.model.SubjectClearanceFact;
import com.chinacreator.gzcm.sysman.security.clearance.service.ISubjectClearanceQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * #142/B4-3e-81（差距 D-B4-23 余项）— 主体密级三态归一的判据门（消费侧唯一裁决点）。
 *
 * <p>属主侧交付的是<b>事实</b>（{@link SubjectClearanceFact}），本类决定的是<b>形态</b>：
 * 真值／未解析／不可用。三种形态各有一条独立的判据腿，收口前翻车的正是把它们混为一谈：</p>
 * <ul>
 *   <li>未解析 ⇒ {@code null}（交策略面落 {@code RLS_CONTEXT_UNRESOLVED}），
 *       <b>禁</b>兜底成任何档位——照抄准入面的「无档案兜底 L1」就是把「没配档案」谎报成「L1 内部」；</li>
 *   <li>断腿／门面缺席／取数失败 ⇒ 抛不可用态（调用方转 503），
 *       <b>禁</b>折成未解析——那会把部署缺陷伪装成用户没权限（D-B4-83／D-B4-84 同族）；</li>
 *   <li>「命中了下探档但高档断过」不是真值：整链不可信，仍按不可用处置。</li>
 * </ul>
 */
@Timeout(60)
class SubjectClearanceResolutionServiceTest {

    private static final String USER = "u-1";
    private static final String TENANT = "T1";
    private static final String ORG = "ORG-1";

    private final ISubjectClearanceQueryService facade = mock(ISubjectClearanceQueryService.class);
    private final SubjectClearanceResolutionService service =
            new SubjectClearanceResolutionService(facade);

    @Test
    @DisplayName("真值：整链可信的命中档原值交付，并带上已解析的机构号")
    void resolvedFactYieldsTheOwnerReading() {
        when(facade.resolveSubjectClearance(USER, TENANT, ORG))
                .thenReturn(SubjectClearanceFact.resolved(SubjectClearanceFact.TIER_USER, 3, List.of()));

        assertEquals(3, service.resolveClearanceLevel(USER, TENANT, ORG).intValue());
        verify(facade).resolveSubjectClearance(eq(USER), eq(TENANT), eq(ORG));
    }

    @Test
    @DisplayName("未解析（五档无档案）⇒ null 且不抛：交策略面拒绝，禁替主体兜一个档位")
    void unresolvedIsNotNullifiedToATier() {
        when(facade.resolveSubjectClearance(USER, TENANT, ORG))
                .thenReturn(SubjectClearanceFact.unresolved(List.of()));

        assertNull(service.resolveClearanceLevel(USER, TENANT, ORG),
                "「没配档案」不能折算成任何档位——兜底属准入语义，不属策略裁决");
    }

    @Test
    @DisplayName("链含断腿即使命中下探档也不可信 ⇒ 不可用态，禁当低档真值用")
    void degradedChainIsUnavailableEvenWhenALowerTierHit() {
        when(facade.resolveSubjectClearance(USER, TENANT, ORG)).thenReturn(
                SubjectClearanceFact.resolved(SubjectClearanceFact.TIER_ROLE, 2,
                        List.of(SubjectClearanceFact.TIER_USER)));

        SubjectClearanceResolutionService.SubjectClearanceSourceUnavailableException thrown =
                assertThrows(SubjectClearanceResolutionService.SubjectClearanceSourceUnavailableException.class,
                        () -> service.resolveClearanceLevel(USER, TENANT, ORG));
        assertTrue(String.valueOf(thrown.getMessage()).contains(SubjectClearanceFact.TIER_USER),
                "理由必须点明断的是哪一档：" + thrown.getMessage());
    }

    @Test
    @DisplayName("未解析且链断过 ⇒ 仍是不可用态（形态优先级：不可用 > 未解析）")
    void degradedUnresolvedIsUnavailableNotPlainNull() {
        when(facade.resolveSubjectClearance(USER, TENANT, ORG))
                .thenReturn(SubjectClearanceFact.unresolved(List.of(SubjectClearanceFact.TIER_TENANT)));

        assertThrows(SubjectClearanceResolutionService.SubjectClearanceSourceUnavailableException.class,
                () -> service.resolveClearanceLevel(USER, TENANT, ORG));
    }

    @Test
    @DisplayName("属主门面未装配 ⇒ 不可用态，禁回落调用方自报档位")
    void absentFacadeIsUnavailableNotUnresolved() {
        SubjectClearanceResolutionService unassembled =
                new SubjectClearanceResolutionService(Optional.empty());

        SubjectClearanceResolutionService.SubjectClearanceSourceUnavailableException thrown = assertThrows(
                SubjectClearanceResolutionService.SubjectClearanceSourceUnavailableException.class,
                () -> unassembled.resolveClearanceLevel(USER, TENANT, ORG));
        assertTrue(String.valueOf(thrown.getMessage())
                        .contains(SubjectClearanceResolutionService.REASON_SOURCE_UNAVAILABLE),
                "机读理由必须在场：" + thrown.getMessage());
    }

    @Test
    @DisplayName("取数抛错 ⇒ 不可用态并保留原始异常可取证")
    void fetchFailureIsUnavailableWithCause() {
        when(facade.resolveSubjectClearance(anyString(), any(), any()))
                .thenThrow(new IllegalStateException("sysman datasource down"));

        SubjectClearanceResolutionService.SubjectClearanceSourceUnavailableException thrown = assertThrows(
                SubjectClearanceResolutionService.SubjectClearanceSourceUnavailableException.class,
                () -> service.resolveClearanceLevel(USER, TENANT, ORG));
        assertTrue(String.valueOf(thrown.getMessage()).contains("sysman datasource down"),
                "属主原文要能进取证面：" + thrown.getMessage());
    }

    @Test
    @DisplayName("门面返回 null 事实对象＝契约破坏，按不可用处置（禁当「五档都没行」）")
    void nullFactIsUnavailable() {
        when(facade.resolveSubjectClearance(USER, TENANT, ORG)).thenReturn(null);

        assertThrows(SubjectClearanceResolutionService.SubjectClearanceSourceUnavailableException.class,
                () -> service.resolveClearanceLevel(USER, TENANT, ORG));
    }

    @Test
    @DisplayName("没有主体就没有事实：不取数、不伪造，也不转不可用")
    void blankSubjectFetchesNothing() {
        assertNull(service.resolveClearanceLevel("  ", TENANT, ORG));
        assertNull(service.resolveClearanceLevel(null, TENANT, ORG));
        verify(facade, never()).resolveSubjectClearance(anyString(), any(), any());
    }
}
