package com.chinacreator.gzcm.workspace.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/** Python 算法执行门禁：模板注入、RESULT 解析、无结果/超时如实失败。 */
class PythonAlgorithmExecutorTest {

    @SuppressWarnings("unchecked")
    private static PythonAlgorithmExecutor executor(SandboxResearchExecutor sandbox) {
        ObjectProvider<SandboxResearchExecutor> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sandbox);
        return new PythonAlgorithmExecutor(provider);
    }

    @Test
    @DisplayName("模板注入：算法键与参数以字面量进入脚本，RESULT 解析为 metrics")
    void injectsTemplateAndParsesResult() {
        SandboxResearchExecutor sandbox = mock(SandboxResearchExecutor.class);
        when(sandbox.executeCode(eq("python"), anyString(), anyLong()))
                .thenReturn(Map.of("timedOut", false,
                        "measuredResult", Map.of("ate", 0.42, "n", 100)));
        Map<String, Object> result = executor(sandbox).execute("propensity-score",
                Map.of("X", List.of(List.of(1, 2)), "treatment", List.of(0, 1)));

        assertEquals("SUCCEEDED", result.get("status"));
        assertFalse((boolean) result.get("degraded"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
        assertEquals(0.42, metrics.get("ate"));

        ArgumentCaptor<String> script = ArgumentCaptor.forClass(String.class);
        verify(sandbox).executeCode(eq("python"), script.capture(), anyLong());
        assertTrue(script.getValue().contains("\"propensity-score\""), "算法键必须注入模板");
        assertTrue(script.getValue().contains("\"treatment\""), "参数 JSON 必须注入模板");
    }

    @Test
    @DisplayName("无 RESULT ⇒ FAILED（不把 stdout 猜成结论）")
    void missingResultFails() {
        SandboxResearchExecutor sandbox = mock(SandboxResearchExecutor.class);
        when(sandbox.executeCode(eq("python"), anyString(), anyLong()))
                .thenReturn(Map.of("timedOut", false, "stdout", "hello", "stderr", ""));
        Map<String, Object> result = executor(sandbox).execute("arima", Map.of());
        assertEquals("FAILED", result.get("status"));
        assertTrue((boolean) result.get("degraded"));
    }

    @Test
    @DisplayName("超时 ⇒ FAILED 且原因为超时")
    void timeoutFails() {
        SandboxResearchExecutor sandbox = mock(SandboxResearchExecutor.class);
        when(sandbox.executeCode(eq("python"), anyString(), anyLong()))
                .thenReturn(Map.of("timedOut", true, "measuredResult", Map.of()));
        Map<String, Object> result = executor(sandbox).execute("gbdt", Map.of());
        assertEquals("FAILED", result.get("status"));
        assertTrue(String.valueOf(result.get("message")).contains("超时"));
    }
}
