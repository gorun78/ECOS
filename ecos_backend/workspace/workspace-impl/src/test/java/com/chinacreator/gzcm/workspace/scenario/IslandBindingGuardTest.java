package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.exception.IslandBindingException;
import com.chinacreator.gzcm.workspace.exception.IslandBindingException.IslandItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F07-04 / C150 §8.1 点名 {@code IslandBindingGuardTest#activateWithIslandReturnsHttp409WithIslandList}
 * — R-26① 双闸的**激活闸**（gate 2）：进入 {@code ACTIVE} 时孤岛非空 → 409 {@code ISLAND_BINDING} +
 * 携带孤岛清单（前端角标 & 409 弹窗都消费 {@code getIslands()}）。
 *
 * <p>驱动<b>真实</b> {@code ScenarioStatusTransitionService#transition(id, "ACTIVE")}（非 mock
 * service），仅 stub 依赖（{@code ScenarioStatusMachine} 用真实现、{@code ScenarioCompletenessService}
 * / {@code JdbcTemplate} mock）—— 断言：(1) 抛 {@link IslandBindingException} 且 HTTP 409（禁 200 伪装）；
 * (2) 409 里携带与 stub 一致的孤岛清单（bindingId/name/bindingType/reason 一一对应）；
 * (3) **前置** UPDATE 未落（激活未成功绝不写库）。</p>
 *
 * <p>对照：无孤岛时（`noIslandPassesGateAndPersists`）同输入过闸落库 —— 证明"闸"只在有岛时开，
 * 排除"误伤式"永不放行的实现。</p>
 */
class IslandBindingGuardTest {

    private JdbcTemplate jdbc;
    private ScenarioCompletenessService completeness;
    private ScenarioStatusTransitionService svc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        completeness = mock(ScenarioCompletenessService.class);
        svc = new ScenarioStatusTransitionService(jdbc, new ScenarioStatusMachine(), completeness);
        // DRAFT 态：DRAFT→ACTIVE 合法；无 FORMAL 运行（DRAFT→ACTIVE 无需 passes）
        when(jdbc.queryForList(anyString(), same(String.class), eq("sc1"))).thenReturn(List.of("DRAFT"));
        when(jdbc.queryForObject(anyString(), same(Integer.class), eq("sc1"))).thenReturn(0);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("operator", "ignored", List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("激活（→ACTIVE）遇孤岛 → 409 ISLAND_BINDING + 携带孤岛清单，且不误落库")
    void activateWithIslandReturnsHttp409WithIslandList() {
        when(completeness.islandsOf("sc1")).thenReturn(List.of(
            new CompletenessVO.Island("b1", "孤立数据源 A", "DATASET", "indeg+outdeg=0"),
            new CompletenessVO.Island("b2", "孤立 Agent B", "AI_AGENT", "无合同边")));

        IslandBindingException ex = assertThrows(IslandBindingException.class,
                () -> svc.transition("sc1", "ACTIVE"));

        assertEquals(409, ex.getHttpStatus(), "R-26① 双闸红线：激活遇孤岛必须真 409（禁 200 伪装）");
        assertEquals(IslandBindingException.CODE, "ISLAND_BINDING");
        assertEquals(409, ex.getErrorCode(), "错误码走 409 状态码体系（非 200 + body.code）");

        assertNotNull(ex.getIslands(), "409 必须携带孤岛清单（前端角标 & 409 弹窗消费）");
        assertEquals(2, ex.getIslands().size(), "stub 2 个孤岛 → 409 里必须恰 2 个 IslandItem");
        IslandItem i0 = ex.getIslands().get(0);
        assertEquals("b1", i0.bindingId());
        assertEquals("孤立数据源 A", i0.name());
        assertEquals("DATASET", i0.bindingType());
        assertEquals("indeg+outdeg=0", i0.reason());
        IslandItem i1 = ex.getIslands().get(1);
        assertEquals("b2", i1.bindingId());
        assertEquals("AI_AGENT", i1.bindingType());

        // 关键反证：激活未成功 → 状态持久化 UPDATE 未触发（否则拖垮并发一致性）
        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("无孤岛时激活跨过 gate（对照 ScenarioTransitionSubjectSourceTest）：不误伤，落库 UPDATE 触发")
    void noIslandPassesGateAndPersists() {
        when(completeness.islandsOf("sc1")).thenReturn(List.of());
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(1);

        ScenarioStatusTransitionService.StatusVO vo = svc.transition("sc1", "ACTIVE");

        assertEquals("ACTIVE", vo.getStatus(), "无孤岛 → 闸放行 → 状态推进到 ACTIVE");
        verify(jdbc).update(anyString(), any(), any(), any());
        assertTrue(vo.getAllowedTransitions().contains("SUSPENDED")
                && vo.getAllowedTransitions().contains("COMPLETED"),
                "ACTIVE 允许的下一步 = SUSPENDED/COMPLETED（服务端下发，非前端硬编码）");
    }
}
