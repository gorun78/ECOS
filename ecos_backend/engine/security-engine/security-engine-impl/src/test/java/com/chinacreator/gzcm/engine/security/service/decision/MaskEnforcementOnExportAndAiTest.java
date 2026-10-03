package com.chinacreator.gzcm.engine.security.service.decision;

import com.chinacreator.gzcm.engine.security.service.ColumnLevelSecurityServiceImpl;
import com.chinacreator.gzcm.engine.security.service.OpaPolicyService;
import com.chinacreator.gzcm.engine.security.service.RowLevelSecurityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * MaskEnforcementOnExportAndAiTest — C.2.4 出口 mask 强制点：导出/AI 为强制点，
 * OPA 不可用时 fail-closed；读通道（page）OPA 不可用则强制全列 mask。
 *
 * <p>mock：RLS 放行、CLS 放行（含 3 列）、OPA {@code evaluate} 抛 {@code RuntimeException("opa down")}。</p>
 */
@ExtendWith(MockitoExtension.class)
class MaskEnforcementOnExportAndAiTest {

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

        when(rlsService.apply(anyString(), anyString())).thenReturn(Map.of(
                "denyAll", false,
                "predicateTemplate", "1=1",
                "bindings", List.of(),
                "denyIfEmpty", true));
        when(clsService.getColumns(anyString(), anyString(), anyList())).thenReturn(Map.of(
                "visibleColumns", List.of("col1", "col2", "col3"),
                "mode", "allow"));
        when(opaService.evaluate(eq("data_access"), anyMap()))
                .thenThrow(new RuntimeException("opa unavailable"));
    }

    @Test
    @DisplayName("OPA 不可用 + Purpose.export → 抛 ECOS-SEC-502（导出口 force fail-closed）")
    void opaDownExportDenied() {
        SecurityDecisionService.SecurityUnavailableException ex = assertThrows(
                SecurityDecisionService.SecurityUnavailableException.class,
                () -> svc.decide(new AssetRef("public", TABLE, List.of("col1", "col2", "col3")),
                        Purpose.export, new DecisionSubject("user1")));

        assertEquals("ECOS-SEC-502", ex.errorCode);
        assertEquals(503, ex.httpStatus);
    }

    @Test
    @DisplayName("OPA 不可用 + Purpose.ai → 抛 ECOS-SEC-502（AI 通道 fail-closed 护栏）")
    void opaDownAiDenied() {
        SecurityDecisionService.SecurityUnavailableException ex = assertThrows(
                SecurityDecisionService.SecurityUnavailableException.class,
                () -> svc.decide(new AssetRef("public", TABLE, List.of("col1", "col2", "col3")),
                        Purpose.ai, new DecisionSubject("user1")));

        assertEquals("ECOS-SEC-502", ex.errorCode);
        assertEquals(503, ex.httpStatus);
    }

    @Test
    @DisplayName("OPA 不可用 + Purpose.page → 返回 bundle 且 masks 非空（强制全列 mask，L0/L1 语义）")
    void opaDownPageForcedMasks() {
        DecisionBundle bundle = svc.decide(
                new AssetRef("public", TABLE, List.of("col1", "col2", "col3")),
                Purpose.page, new DecisionSubject("user1"));

        assertTrue(bundle.masks != null && !bundle.masks.isEmpty(),
                "读通道 OPA down 必须强制 mask（漏洞面不裸奔）");

        boolean allFreeText = bundle.masks.stream()
                .allMatch(m -> "FREE_TEXT".equals(String.valueOf(m.get("strategy"))));
        assertTrue(allFreeText, "强制 mask 策略应为 FREE_TEXT");
    }
}
