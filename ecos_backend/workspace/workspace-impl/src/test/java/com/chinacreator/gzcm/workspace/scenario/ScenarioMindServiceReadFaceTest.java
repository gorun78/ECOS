package com.chinacreator.gzcm.workspace.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * P0-2 心智读面门禁（2026-11-07 验证报告 A-2）。
 *
 * <p>钉住：读源是认知绑定（不是 legacy 空表）、字段映射不丢、通道故障不折叠成空、
 * base/active 选择有确定口径。任一漂移即红。</p>
 */
class ScenarioMindServiceReadFaceTest {

    private final DcchengClient client = mock(DcchengClient.class);
    private final ScenarioMindService service = new ScenarioMindService(
            mock(WorkspaceDataDao.class), new ObjectMapper(), client);

    private static Map<String, Object> row(String id, String key, String name,
                                           boolean active, List<String> mask) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mindId", id);
        m.put("mindKey", key);
        m.put("name", name);
        m.put("description", "d");
        m.put("capabilityMask", mask);
        m.put("closedLoopBounds", List.of());
        m.put("active", active);
        m.put("bindingRole", "PRIMARY");
        m.put("priority", 100);
        m.put("createTime", "2026-11-07 10:00:00");
        m.put("updateTime", "2026-11-07 10:00:00");
        return m;
    }

    @Test
    void listMindsReadsCognitiveBindingsAndKeepsFacts() {
        when(client.listScenarioMinds("sc_ent2_group_month")).thenReturn(List.of(
                row("mind_ent2_month", "mind_entmgmt_monthly", "集团月度经营认知心智",
                        true, List.of("DIAGNOSE", "FORECAST"))));

        List<ScenarioMindVO> list = service.listMinds("sc_ent2_group_month");

        assertEquals(1, list.size());
        ScenarioMindVO vo = list.get(0);
        assertEquals("mind_ent2_month", vo.getMindRef());
        assertEquals("集团月度经营认知心智", vo.getMindLabel());
        assertEquals(1, vo.getActiveMind());
        @SuppressWarnings("unchecked")
        Map<String, Object> endpoints = (Map<String, Object>) vo.getCognitiveEndpoints();
        assertEquals(List.of("DIAGNOSE", "FORECAST"), endpoints.get("capabilityMask"));
    }

    @Test
    void activeAndBaseHaveDeterministicSelection() {
        when(client.listScenarioMinds("s1")).thenReturn(List.of(
                row("m2", "mind_s1_adverse", "adverse", false, List.of()),
                row("m1", "mind_s1_base", "base", true, List.of())));

        assertEquals("m1", service.getActiveMind("s1").getMindRef());
        assertEquals("m1", service.getMindBase("s1").getMindRef(),
                "有 base 标签时必须选 base，不得退化为首个绑定");
    }

    @Test
    void baseFallsBackToFirstBindingWhenNoBaseLabel() {
        when(client.listScenarioMinds("s2")).thenReturn(List.of(
                row("m9", "mind_s2_main", "主营心智", true, List.of())));

        assertEquals("m9", service.getMindBase("s2").getMindRef());
    }

    @Test
    void emptyBindingsStayEmptyAndBaseIsNull() {
        when(client.listScenarioMinds("s3")).thenReturn(List.of());

        assertTrue(service.listMinds("s3").isEmpty());
        assertNull(service.getMindBase("s3"));
    }

    @Test
    void cognitiveUnavailablePropagatesInsteadOfEmptyList() {
        when(client.listScenarioMinds("s4"))
                .thenThrow(new CognitiveEngineUnavailableException("down"));

        assertThrows(CognitiveEngineUnavailableException.class, () -> service.listMinds("s4"),
                "通道故障不得折叠成空列表（不可用 ≠ 无绑定）");
    }

    @Test
    void blankScenarioIdIs400() {
        assertThrows(BusinessException.class, () -> service.listMinds(" "));
    }
}
