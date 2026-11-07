package com.chinacreator.gzcm.workspace.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.workspace.scenario.ScenarioMindService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioStatusTransitionService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DI-2 强类型请求体线形保真（D-1；2026-11-07）。
 *
 * <p>钉住两个旧 Map 端点的 JSON 键面与语义：{@code {"status":"..."}} 与
 * {@code {"active":false}}；缺省/空体沿用旧行为，不因强类型化漂移。</p>
 */
class ScenarioRequestShapeTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void statusRequestBindsOldKeyAndReachesService() throws Exception {
        ScenarioStatusTransitionService service = mock(ScenarioStatusTransitionService.class);
        ScenarioStatusController controller = new ScenarioStatusController(service);

        ScenarioStatusController.ScenarioStatusRequest body =
                json.readValue("{\"status\":\"ACTIVE\",\"userId\":\"ignored\"}",
                        ScenarioStatusController.ScenarioStatusRequest.class);
        controller.transition("sc_1", body);

        verify(service).transition(eq("sc_1"), eq("ACTIVE"));
    }

    @Test
    void mindActiveRequestKeepsExplicitFalseSemantics() throws Exception {
        ScenarioMindService service = mock(ScenarioMindService.class);
        when(service.setActiveByRef(eq("sc_1"), eq("mind_1"), eq(false))).thenReturn(1);
        when(service.setActiveByRef(eq("sc_1"), eq("mind_1"), eq(true))).thenReturn(1);
        ScenarioMindsController controller = new ScenarioMindsController(service);

        ScenarioMindsController.MindActiveRequest active =
                json.readValue("{}", ScenarioMindsController.MindActiveRequest.class);
        ScenarioMindsController.MindActiveRequest inactive =
                json.readValue("{\"active\":false}", ScenarioMindsController.MindActiveRequest.class);

        assertNull(active.active());
        assertEquals(Boolean.FALSE, inactive.active());
        // 语义：空体 = 激活；显式 false = 失活（与旧 Map 版本一致）
        controller.updateByRef("sc_1", "mind_1", active);
        controller.updateByRef("sc_1", "mind_1", inactive);
        verify(service).setActiveByRef("sc_1", "mind_1", true);
        verify(service).setActiveByRef("sc_1", "mind_1", false);
    }

    @Test
    void statusRecordIgnoresUnknownKeysInsteadOfFailing() throws Exception {
        ScenarioStatusController.ScenarioStatusRequest body =
                json.readValue("{\"other\":1}", ScenarioStatusController.ScenarioStatusRequest.class);
        assertNull(body.status());
    }
}
