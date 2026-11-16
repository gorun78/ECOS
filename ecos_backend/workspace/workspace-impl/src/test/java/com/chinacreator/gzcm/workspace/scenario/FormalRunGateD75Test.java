package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * D75 钉桩：ACTIVE→COMPLETED 的「正式运行」门禁必须只认 {@code run_mode='FORMAL'}。
 *
 * <p>修复前 {@code hasFormalRun} 只判断「任一 run 存在」——跑过一次 SANDBOX 即可满足
 * 完成态门禁，与 F07-07 / V-WS-042 第③条「SANDBOX 运行不计 isFormalRunExists」相悖。
 * 本用例把 DAO 打桩成「只有 SANDBOX 行」，期望 COMPLETED 迁移被拒；再打桩成「含 FORMAL 行」，
 * 期望放行——两侧都断言，避免「恒拒绝」式假绿。</p>
 */
class FormalRunGateD75Test {

    private static Map<String, Object> run(String runMode) {
        return Map.of("id", "run-" + runMode, "run_mode", runMode);
    }

    private ScenarioStatusTransitionService serviceWithRuns(List<Map<String, Object>> runs) {
        ScenarioVO vo = new ScenarioVO();
        vo.setId("s-1");
        vo.setStatus(ScenarioStatus.ACTIVE.name());

        ScenarioService scenarioService = Mockito.mock(ScenarioService.class);
        Mockito.when(scenarioService.get("s-1")).thenReturn(vo);

        CompletenessDataLoader loader = Mockito.mock(CompletenessDataLoader.class);
        Mockito.when(loader.bindingsOf("s-1")).thenReturn(List.of());
        Mockito.when(loader.linksOf("s-1")).thenReturn(List.of());

        ScenarioCompletenessService completeness = Mockito.mock(ScenarioCompletenessService.class);
        CompletenessReport report = Mockito.mock(CompletenessReport.class);
        Mockito.when(report.getPresentEdges()).thenReturn(List.of());
        Mockito.when(report.getIslands()).thenReturn(List.of());
        Mockito.when(report.getVerdict()).thenReturn(CompletenessReport.Verdict.OK);
        Mockito.when(completeness.compute(Mockito.anyString(), Mockito.anyList(), Mockito.anyList()))
                .thenReturn(report);

        WorkspaceSecurityAudit audit = Mockito.mock(WorkspaceSecurityAudit.class);
        WorkspaceDataDao dao = Mockito.mock(WorkspaceDataDao.class);
        Mockito.when(dao.listRunHistory(Mockito.anyString(), Mockito.anyInt())).thenReturn(runs);

        return new ScenarioStatusTransitionService(scenarioService,
                new ScenarioStatusMachine((point, subject) -> true),
                loader, completeness, audit, dao);
    }

    @Test
    @DisplayName("D75：仅 SANDBOX 运行 ⇒ ACTIVE→COMPLETED 必须被拒（noFormalRun）")
    void sandboxOnlyRunMustNotSatisfyCompletionGate() {
        ScenarioStatusTransitionService service = serviceWithRuns(List.of(run("SANDBOX")));
        assertThrows(IllegalScenarioTransitionException.class,
                () -> service.transition("s-1", "COMPLETED"));
    }

    @Test
    @DisplayName("D75 正向对照：存在 FORMAL 运行 ⇒ ACTIVE→COMPLETED 放行")
    void formalRunSatisfiesCompletionGate() {
        ScenarioStatusTransitionService service =
                serviceWithRuns(List.of(run("SANDBOX"), run("FORMAL")));
        assertDoesNotThrow(() -> service.transition("s-1", "COMPLETED"));
    }
}
