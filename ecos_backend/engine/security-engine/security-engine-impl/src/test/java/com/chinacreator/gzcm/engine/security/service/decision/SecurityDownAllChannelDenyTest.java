package com.chinacreator.gzcm.engine.security.service.decision;

import com.chinacreator.gzcm.engine.security.service.ColumnLevelSecurityServiceImpl;
import com.chinacreator.gzcm.engine.security.service.OpaPolicyService;
import com.chinacreator.gzcm.engine.security.service.RowLevelSecurityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * SecurityDownAllChannelDenyTest — C.6 降级矩阵：安全链/OPA 宕机时的 fail-closed 行为。
 *
 * <ul>
 *   <li>security 存储链（RLS apply）不可用 → 三通道统一 503 {@code ECOS-SEC-501}</li>
 *   <li>OPA 不可用：写/导出/AI 通道 = FAIL_CLOSED 503 {@code ECOS-SEC-502}；
 *       读通道（page）= policySkipped=true + 强制全列 mask，不抛（读降级口径）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SecurityDownAllChannelDenyTest {

    private static final String TABLE = "test_table";

    @Mock
    private RowLevelSecurityServiceImpl rlsService;
    @Mock
    private ColumnLevelSecurityServiceImpl clsService;
    @Mock
    private OpaPolicyService opaService;

    private SecurityDecisionService svc;

    @BeforeEach
    void setUp() {
        this.svc = new SecurityDecisionService(rlsService, clsService, opaService);
    }

    // ── scenario 1: security 存储链（RLS DB）宕机 ──────────────────────

    @ParameterizedTest
    @EnumSource(Purpose.class)
    @DisplayName("RLS 链不可用 → 三通道统一抛 SecurityUnavailableException(503 ECOS-SEC-501)")
    void securityDbDownAllChannelsDeny(Purpose purpose) {
        when(rlsService.apply(anyString(), anyString()))
                .thenThrow(new RuntimeException("DB down"));

        SecurityDecisionService.SecurityUnavailableException ex = assertThrows(
                SecurityDecisionService.SecurityUnavailableException.class,
                () -> svc.decide(new AssetRef("public", TABLE, List.of("col1")),
                        purpose, new DecisionSubject("user1")));

        assertEquals("ECOS-SEC-501", ex.errorCode, "security down 错误码应为 ECOS-SEC-501");
        assertEquals(503, ex.httpStatus, "security down 应为 503");
    }

    // ── scenario 2: OPA 宕机 ─────────────────────────────────────────

    private void stubRlsAndClsOk() {
        when(rlsService.apply(anyString(), anyString())).thenReturn(Map.of(
                "denyAll", false,
                "predicateTemplate", "1=1",
                "bindings", List.of(),
                "denyIfEmpty", true));
        // 读通道强制全列 mask 需要可见列；一般放行场景给非空
        when(clsService.getColumns(anyString(), anyString(), anyList())).thenReturn(Map.of(
                "visibleColumns", List.of("col1", "col2")));
    }

    @Test
    @DisplayName("OPA down + Purpose.export → FAIL_CLOSED 抛 ECOS-SEC-502")
    void opaDownExportFailsClosed() {
        stubRlsAndClsOk();
        when(opaService.evaluate(eq("data_access"), anyMap()))
                .thenThrow(new RuntimeException("opa down"));

        SecurityDecisionService.SecurityUnavailableException ex = assertThrows(
                SecurityDecisionService.SecurityUnavailableException.class,
                () -> svc.decide(new AssetRef("public", TABLE, List.of("col1")),
                        Purpose.export, new DecisionSubject("user1")));

        assertEquals("ECOS-SEC-502", ex.errorCode, "OPA down 应 ECOS-SEC-502");
        assertEquals(503, ex.httpStatus, "OPA down 应 503");
    }

    @Test
    @DisplayName("OPA down + Purpose.ai → FAIL_CLOSED 抛 ECOS-SEC-502")
    void opaDownAiFailsClosed() {
        stubRlsAndClsOk();
        when(opaService.evaluate(eq("data_access"), anyMap()))
                .thenThrow(new RuntimeException("opa down"));

        SecurityDecisionService.SecurityUnavailableException ex = assertThrows(
                SecurityDecisionService.SecurityUnavailableException.class,
                () -> svc.decide(new AssetRef("public", TABLE, List.of("col1")),
                        Purpose.ai, new DecisionSubject("user1")));

        assertEquals("ECOS-SEC-502", ex.errorCode, "OPA down 应 ECOS-SEC-502");
        assertEquals(503, ex.httpStatus, "OPA down 应 503");
    }

    @Test
    @DisplayName("OPA down + Purpose.page → 不抛，policySkipped=true 且读通道 fail-closed (allowed=false)")
    void opaDownPagePolicySkippedButDenied() {
        stubRlsAndClsOk();
        // CLS allow 集为空 → columns.mode=deny → allowed=false（读通道漏洞面 fail-closed）
        when(clsService.getColumns(anyString(), anyString(), anyList())).thenReturn(Map.of(
                "visibleColumns", List.of()));
        when(opaService.evaluate(eq("data_access"), anyMap()))
                .thenThrow(new RuntimeException("opa down"));

        DecisionBundle bundle = svc.decide(
                new AssetRef("public", TABLE, List.of("col1", "col2")),
                Purpose.page, new DecisionSubject("user1"));

        assertTrue(Boolean.TRUE.equals(bundle.opa.get("policySkipped")), "opa.policySkipped 应为 true");
        assertFalse(bundle.allowed, "读通道 OPA down 应 fail-closed allowed=false");
        assertFalse(bundle.masks.isEmpty(), "读通道 OPA down 应强制全列 mask");
    }
}
