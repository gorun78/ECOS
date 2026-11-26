package com.chinacreator.gzcm.workspace.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import com.chinacreator.gzcm.workspace.scenario.DcchengClient;

/** 算法执行分派门禁（V367 批次 A）：JAVA / PYTHON / 旧面 / 未启用。 */
class AlgorithmExecutionServiceTest {

    private static WorkspaceDataDao daoWith(Map<String, Object> row) {
        WorkspaceDataDao dao = mock(WorkspaceDataDao.class);
        when(dao.findAlgorithmById(anyString())).thenReturn(List.of(row));
        return dao;
    }

    private static Map<String, Object> row(String kind, String ref, String status) {
        return Map.of("id", "alg-x", "name", "测试算法", "category", "BELIEF",
                "executor_kind", kind, "implementation_ref", ref, "status", status);
    }

    @Test
    @DisplayName("JAVA：转调 cognitive 并原样回传 metrics/status")
    void dispatchesJava() {
        WorkspaceDataDao dao = daoWith(row("JAVA", "java:bayes-update", "ACTIVE"));
        DcchengClient cognitive = mock(DcchengClient.class);
        when(cognitive.executeAlgorithm(eq("bayes-update"), any()))
                .thenReturn(Map.of("status", "SUCCEEDED", "degraded", false,
                        "metrics", Map.of("posterior", List.of(0.8, 0.2)),
                        "assumptions", List.of("x"), "elapsedMs", 5));
        PythonAlgorithmExecutor python = mock(PythonAlgorithmExecutor.class);

        Map<String, Object> out = new AlgorithmExecutionService(dao, cognitive, python)
                .execute("alg-x", Map.of("prior", List.of(0.5, 0.5)));

        assertEquals("SUCCEEDED", out.get("status"));
        assertEquals("bayes-update", out.get("code"));
        assertEquals("alg-x", out.get("algorithmId"));
    }

    @Test
    @DisplayName("PYTHON：沙盒未装配 ⇒ UNAVAILABLE（禁假执行）")
    void pythonWithoutSandboxIsUnavailable() {
        WorkspaceDataDao dao = daoWith(row("PYTHON_SANDBOX", "python:arima", "ACTIVE"));
        PythonAlgorithmExecutor python = mock(PythonAlgorithmExecutor.class);
        when(python.available()).thenReturn(false);

        Map<String, Object> out = new AlgorithmExecutionService(dao, mock(DcchengClient.class), python)
                .execute("alg-x", Map.of());

        assertEquals("UNAVAILABLE", out.get("status"));
        assertTrue(String.valueOf(out.get("message")).contains("沙盒未装配"));
    }

    @Test
    @DisplayName("旧 L2 面（DETERMINISTIC_SPI）⇒ UNAVAILABLE，不冒充新执行器")
    void legacyIsUnavailable() {
        WorkspaceDataDao dao = daoWith(row("DETERMINISTIC_SPI", "/cognitive/diagnose", "ACTIVE"));
        Map<String, Object> out = new AlgorithmExecutionService(dao, mock(DcchengClient.class),
                mock(PythonAlgorithmExecutor.class)).execute("alg-x", Map.of());
        assertEquals("UNAVAILABLE", out.get("status"));
    }

    @Test
    @DisplayName("未启用算法 ⇒ 400（不执行）")
    void inactiveRejected() {
        WorkspaceDataDao dao = daoWith(row("JAVA", "java:bayes-update", "DISABLED"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> new AlgorithmExecutionService(dao, mock(DcchengClient.class),
                        mock(PythonAlgorithmExecutor.class)).execute("alg-x", Map.of()));
        assertTrue(String.valueOf(ex.getMessage()).contains("算法未启用"));
    }
}
