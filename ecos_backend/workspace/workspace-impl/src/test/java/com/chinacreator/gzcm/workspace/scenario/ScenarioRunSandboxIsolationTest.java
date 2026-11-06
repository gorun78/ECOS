package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 沙盘隔离（详细设计-场景工作台 §5；SW-05）：沙盘不写正式指标、产物带 lineage、正式拒绝沙盘基线。 */
class ScenarioRunSandboxIsolationTest {

    private final WorkspaceDataDao dao = mock(WorkspaceDataDao.class);
    private final DcchengClient dccheng = mock(DcchengClient.class);
    private final ScenarioService scenarioService = mock(ScenarioService.class);
    private final ScenarioRunService service =
            new ScenarioRunService(dao, new ObjectMapper(), dccheng, scenarioService, new SandboxWriteGuard());
    private final Map<String, Object> param = Map.of("runTypes", List.of("DIAGNOSE"), "metric", "利润");

    @Test
    void sandboxRunDoesNotWriteFormalMetricsAndCarriesLineage() {
        when(dccheng.diagnose(any())).thenReturn(Map.of("confidence", 0.82));
        when(dao.insertRun(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
                any(), any(), any(), any(), eq(false), anyString(), eq("SANDBOX"), isNull(), eq("SANDBOX")))
                .thenReturn(1);

        Map<String, Object> result = service.run("s1", param,
                new ScenarioRunRequestValidator.RunCommand("s1", RunMode.SANDBOX, null));

        verify(scenarioService, never()).updateMetrics(anyString(), any(), any());
        verify(dao).insertRun(anyString(), eq("s1"), eq("DIAGNOSE"), anyString(), eq("利润"), any(),
                any(), any(), any(), any(), eq(false), anyString(), eq("SANDBOX"), isNull(), eq("SANDBOX"));
        assertTrue("SANDBOX".equals(result.get("runMode")));
        assertTrue(result.get("metricsWritten") == null);
    }

    @Test
    void formalRunWritesMetricsAndKeepsFormalLineage() {
        when(dccheng.diagnose(any())).thenReturn(Map.of("confidence", 0.82));
        when(dao.insertRun(anyString(), anyString(), anyString(), anyString(), anyString(), any(),
                any(), any(), any(), any(), eq(false), anyString(), eq("FORMAL"), isNull(), eq("NONE")))
                .thenReturn(1);
        when(scenarioService.updateMetrics(anyString(), any(), any())).thenReturn(true);

        Map<String, Object> result = service.run("s1", param,
                new ScenarioRunRequestValidator.RunCommand("s1", RunMode.FORMAL, null));

        verify(scenarioService).updateMetrics(eq("s1"), any(), any());
        assertTrue("FORMAL".equals(result.get("runMode")));
    }

    @Test
    void formalRunReferencingSandboxBaselineIsRejected() {
        BaselineReferenceGuard guard = new BaselineReferenceGuard(null);
        assertThrows(SandboxReferenceForbiddenException.class,
                () -> guard.assertAllowed(RunMode.FORMAL, RunMode.SANDBOX, "run-sandbox-1"));
    }

    @Test
    void sandboxWriteGuardAllowsRunAndLayoutOnly() {
        SandboxWriteGuard guard = new SandboxWriteGuard();
        assertTrue(guard.isWhitelisted("ecos_scenario_run"));
        assertTrue(guard.isWhitelisted("ecos_scenario_sandbox_layout"));
        assertFalse(guard.isWhitelisted("ecos_business_scenario"));
        assertThrows(SandboxWriteForbiddenException.class,
                () -> guard.assertAllowed(RunMode.SANDBOX, "ecos_business_scenario"));
    }
}
