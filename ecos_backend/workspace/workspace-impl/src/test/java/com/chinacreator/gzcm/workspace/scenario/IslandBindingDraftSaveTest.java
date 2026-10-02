package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.scenario.CompletenessVO.Island;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * 详细设计-07 F07-04 / C150 §8.1 点名 {@code IslandBindingGuardTest#draftSavePersistsIslandFlag}
 *（本测试类承载同名偏好 —— gate 1「保存期」的隔离单测；激活侧 gate 2 见 {@code IslandBindingGuardTest}）。
 *
 * <p><b>R-26① 双闸的保存期半（gate 1）</b>：DRAFT 态<b>容忍</b>孤岛（先挂对象再挂映射是自然
 * 中间态），保存/加载<b>不得拒绝</b>，而是把岛标以"读侧现算的附加字段"下发（前端角标消费，
 * {@code ScenarioVO.islands}）；**读路径失败降级空清单** —— 岛标是提示，绝不拖垮详情主响应。
 *
 * <p>驱动<b>真实</b> {@code ScenarioService#get(id)}（非 mock service），只 mock 依赖
 * （{@code JdbcTemplate} / {@code ScenarioCompletenessService}），{@code bindings(id)} 用 spy
 * 桩成空图以聚焦岛标填充。<b>激活闸拒绝</b>（gate 2，409）由 {@code IslandBindingGuardTest}
 * 独立承载 —— 两个 gate 各测其段，共用同一 {@code ScenarioCompletenessService.islandsOf/
 * islands} 单源判定（边可达性，非节点类型，X-27）。</p>
 */
class IslandBindingDraftSaveTest {

    private static final String ID = "sc-draft-1";

    /** 构造走真实 {@code get(id)} 的 ScenarioService：jdk spy 掉 bindings(id) 空图，只 mock Jdbc 三读。 */
    private ScenarioService serviceWith(JdbcTemplate jdbc, ScenarioCompletenessService completeness) {
        ScenarioService svc = spy(new ScenarioService(jdbc, new ObjectMapper(), completeness));
        doReturn(ScenarioVO.emptyBindings()).when(svc).bindings(ID);
        return svc;
    }

    /** 让 {@code get(id)} 的既有场景行读命中：一行 DRAFT 场景。 */
    private void stubExistingDraftRow(JdbcTemplate jdbc) {
        BusinessScenario e = new BusinessScenario();
        e.setId(ID);
        e.setName("孤岛容忍场景");
        e.setStatus("DRAFT");
        when(jdbc.query(
            argThat(s -> s != null && s.contains("FROM ecos_business_scenario WHERE id = ?")),
            any(RowMapper.class), eq(ID))).thenReturn(List.of(e));
        // fillAggregates 内部的 metrics 读：返回空 → parseMetrics("{}") → {}
        when(jdbc.query(
            argThat(s -> s != null && s.contains("SELECT metrics")),
            any(RowMapper.class), eq(ID))).thenReturn(List.of());
    }

    @Test
    @DisplayName("saveToleratesIslandDraftAndSurfacesFlag: DRAFT 带孤岛时 get 正常返回（不 400/409）+ 岛标 is_island 可见")
    void draftSavePersistsIslandFlag() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubExistingDraftRow(jdbc);
        ScenarioCompletenessService completeness = mock(ScenarioCompletenessService.class);
        when(completeness.islandsOf(ID)).thenReturn(List.of(
                new Island("b1", "DATASET", "ref-d1", "no-incident-edge")));
        ScenarioService svc = serviceWith(jdbc, completeness);

        // R-26① gate 1 红线：DRAFT 容忍孤岛，读侧不得抛（激活闸单独拦截，见 IslandBindingGuardTest）
        ScenarioVO vo = assertDoesNotThrow(() -> svc.get(ID),
                "DRAFT 保存/加载遇孤岛必须容忍（自然中间态），不得拒绝");
        assertEquals("DRAFT", vo.getStatus());
        assertTrue(vo.getIslands() != null && vo.getIslands().size() == 1,
                "岛标（is_island 语义）必须以附加字段下发，供前端 DRAFT 角标消费");
        assertEquals("b1", vo.getIslands().get(0).getBindingId());
        assertNotNull(vo.getIslands().get(0).getReason());
    }

    @Test
    @DisplayName("saveDegradedIslandProbeStillReturnsDetail: islandsOf 读失败 → 岛标降级空清单，详情主响应不拖垮")
    void isleProbeFailureDegradesToEmptyListAndDoesNotBreakDetail() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubExistingDraftRow(jdbc);
        ScenarioCompletenessService completeness = mock(ScenarioCompletenessService.class);
        when(completeness.islandsOf(ID)).thenThrow(new IllegalStateException("pg 抖动"));
        ScenarioService svc = serviceWith(jdbc, completeness);

        // 岛判定降级空清单（岛标仅提示）—— 详情主响应不被 drag 下
        ScenarioVO vo = assertDoesNotThrow(() -> svc.get(ID));
        assertEquals("DRAFT", vo.getStatus());
        assertTrue(vo.getIslands() != null && vo.getIslands().isEmpty(),
                "islandsOf 读失败必须降级为空清单（降级不抛，激活闸 rejectIfIsolated 独立再算）");
    }
}
