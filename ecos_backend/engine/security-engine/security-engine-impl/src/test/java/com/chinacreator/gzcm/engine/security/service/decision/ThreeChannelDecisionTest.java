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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ThreeChannelDecisionTest — C.1[3] 三通道（页面/导出/AI）唯一裁决入口 {@code SecurityDecisionService.decide()}。
 *
 * <p>不启 Spring、不连 DB：三安全服务全部 Mockito mock。验证：
 * <ul>
 *   <li>三目的（page/export/ai）均走 RLS + CLS + OPA 三段裁决，bundle 各段完整</li>
 *   <li>RLS 命中 denyAll → allowed=false，rls.denyAll=true</li>
 * </ul></p>
 */
@ExtendWith(MockitoExtension.class)
class ThreeChannelDecisionTest {

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
        stubAllowAll();
    }

    private void stubAllowAll() {
        when(rlsService.apply(anyString(), anyString())).thenReturn(Map.of(
                "denyAll", false,
                "predicateTemplate", "1=1",
                "bindings", List.of(),
                "denyIfEmpty", true,
                "conditionDeprecated", true));
        when(clsService.getColumns(anyString(), anyString(), anyList())).thenReturn(Map.of(
                "visibleColumns", List.of("col1", "col2")));
        when(opaService.evaluate(eq("data_access"), org.mockito.ArgumentMatchers.anyMap())).thenReturn(Map.of(
                "allow", true,
                "obligations", List.of(),
                "policy", "data_access"));
    }

    @ParameterizedTest
    @EnumSource(Purpose.class)
    @DisplayName("三目的均命中放行：OPA 被调用、rls 非空、columns.mode=allow、purpose 打标")
    void allThreePurposesAllowed(Purpose purpose) {
        DecisionBundle bundle = svc.decide(
                new AssetRef("public", TABLE, List.of("col1")), purpose, new DecisionSubject("user1"));

        assertTrue(bundle.allowed, "purpose=" + purpose + " 应放行");
        assertNotNull(bundle.rls, "rls 段必非空");
        assertEquals("allow", String.valueOf(bundle.columns.get("mode")), "columns.mode 应为 allow");
        assertEquals(purpose.name(), bundle.purpose, "purpose 打标应与入参一致");

        verify(opaService).evaluate(eq("data_access"), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    @DisplayName("RLS denyAll → allowed=false 且 rls.denyAll=true")
    void rlsDenyAllForcesDeny() {
        when(rlsService.apply(anyString(), anyString())).thenReturn(Map.of(
                "denyAll", true,
                "predicateTemplate", "1=0",
                "bindings", List.of(),
                "denyIfEmpty", true,
                "conditionDeprecated", true));

        DecisionBundle bundle = svc.decide(
                new AssetRef("public", TABLE, List.of("col1")), Purpose.page, new DecisionSubject("user1"));

        assertFalse(bundle.allowed, "RLS denyAll 时应拒绝");
        assertEquals(Boolean.TRUE, bundle.rls.get("denyAll"), "rls.denyAll 应为 true");
    }

    @Test
    @DisplayName("三通道依次调用 RLS → CLS → OPA（顺序锚定）")
    void channelsInvokedInOrder() {
        svc.decide(new AssetRef("public", TABLE, List.of("col1")), Purpose.ai, new DecisionSubject("user1"));

        var order = inOrder(rlsService, clsService, opaService);
        order.verify(rlsService).apply(eq(TABLE), eq("user1"));
        order.verify(clsService).getColumns(eq(TABLE), eq("user1"), org.mockito.ArgumentMatchers.anyList());
        order.verify(opaService).evaluate(eq("data_access"), org.mockito.ArgumentMatchers.anyMap());
    }
}
