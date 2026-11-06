package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.workspace.blueprint.BlueprintQueryInvalidException;
import com.chinacreator.gzcm.workspace.blueprint.CapabilityReadService;
import com.chinacreator.gzcm.workspace.blueprint.ScenarioBlueprint;
import com.chinacreator.gzcm.workspace.blueprint.ScenarioBlueprintDao;
import com.chinacreator.gzcm.workspace.blueprint.ScenarioBlueprintService;
import com.chinacreator.gzcm.workspace.blueprint.ScenarioBlueprintStateMachine;
import com.chinacreator.gzcm.workspace.blueprint.ScenarioBlueprintValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

/** 蓝图缺口严重级查询（遗留 P1-4）：服务端分类，未知 severity 400，阻断只看未关闭 BLOCKER。 */
@ExtendWith(MockitoExtension.class)
class ScenarioBlueprintGapsQueryTest {

    private static final String BP_ID = "bp-gaps-001";

    @Mock
    private ScenarioBlueprintDao dao;
    @Mock
    private CapabilityReadService capabilityReadService;
    @Mock
    private ObjectProvider<EventBusService> eventBusProvider;

    private ScenarioBlueprintService service;

    @BeforeEach
    void setUp() {
        service = new ScenarioBlueprintService(dao,
                new ScenarioBlueprintStateMachine(),
                new ScenarioBlueprintValidator(capabilityReadService, new ObjectMapper()),
                new ObjectMapper(), eventBusProvider);

        ScenarioBlueprint bp = new ScenarioBlueprint();
        bp.setId(BP_ID);
        bp.setScenarioId("sc-1");
        bp.setVersion(3);
        bp.setStatus("GAP_ANALYSIS");
        bp.setGaps(List.of(
                Map.of("gapId", "g1", "severity", "blocker", "status", "OPEN", "description", "缺收入口径"),
                Map.of("gapId", "g2", "severity", "BLOCKER", "status", "RESOLVED", "description", "已关闭"),
                Map.of("gapId", "g3", "severity", "WARNING", "status", "OPEN", "description", "警告项"),
                Map.of("gapId", "g4", "severity", "SUGGESTION", "status", "OPEN", "description", "建议项"),
                Map.of("gapId", "g5", "description", "无严重级")));
        lenient().when(dao.findById(BP_ID)).thenReturn(List.of(bp));
    }

    @Test
    void classifiesBySeverityAndOnlyOpenBlockersBlock() {
        Map<String, Object> result = service.gaps(BP_ID, null);

        @SuppressWarnings("unchecked")
        Map<String, Integer> counts = (Map<String, Integer>) result.get("counts");
        assertEquals(2, counts.get("BLOCKER"));
        assertEquals(1, counts.get("WARNING"));
        assertEquals(1, counts.get("SUGGESTION"));
        assertEquals(1, counts.get("UNKNOWN"));
        assertEquals(1, result.get("openBlockers"));
        assertEquals(true, result.get("blocking"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("gaps");
        assertEquals(5, items.size());
        Map<String, Object> closed = items.stream()
                .filter(g -> "g2".equals(g.get("gapId"))).findFirst().orElseThrow();
        assertEquals(false, closed.get("blocking"));
        Map<String, Object> open = items.stream()
                .filter(g -> "g1".equals(g.get("gapId"))).findFirst().orElseThrow();
        assertEquals("BLOCKER", open.get("severity"));
        assertEquals(true, open.get("blocking"));
    }

    @Test
    void severityFilterNarrowsItemsButKeepsGlobalCounts() {
        Map<String, Object> result = service.gaps(BP_ID, "warning");

        assertEquals("WARNING", result.get("severityFilter"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("gaps");
        assertEquals(1, items.size());
        assertEquals("g3", items.get(0).get("gapId"));
        assertEquals(1, result.get("openBlockers"));
    }

    @Test
    void unknownSeverityIsRejectedInsteadOfSilentlyIgnored() {
        BlueprintQueryInvalidException ex =
                assertThrows(BlueprintQueryInvalidException.class, () -> service.gaps(BP_ID, "CRITICAL"));
        assertTrue(ex.getMessage().contains("severity"));
        assertFalse(ex.getMessage().isBlank());
    }
}
