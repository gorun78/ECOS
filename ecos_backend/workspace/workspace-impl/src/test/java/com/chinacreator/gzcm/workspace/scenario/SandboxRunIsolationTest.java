package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F07-08 沙盘演练隔离（run_mode 正交列 + SANDBOX 写隔离）— 离线 Mockito 单测。
 *
 * <p>锁 F07-08/C154 的服务层语义（设计验收 {@code ScenarioRunServiceTest#runModeDefaultsToFormalAndRejectsUnknownValue}
 * 与 {@code SandboxRunIsolationTest#sandboxRunWritesOnlyRunAndLayoutTables} 的离线可证子集）：
 * <ul>
 *   <li>run_mode 归一化：缺省/空白 → FORMAL，未知值 → 400（R-25①，与 run_type 正交）；</li>
 *   <li><b>写隔离</b>：SANDBOX 演练即使诊断成功也<b>不</b>回写场景指标（保护 D 层事实面），
 *       FORMAL 才回写；这是"隔离"的核心可测断言（隔离逻辑在
 *       {@code ScenarioRunService.run} 的 {@code if (!sandboxRun)} 分叉）；</li>
 *   <li>SANDBOX 仍落运行记录行（ISOLATION ≠ 不记，演练结果要留痕）；</li>
 *   <li>runTypes 缺失/空/含非法值 → 400。</li>
 * </ul>
 *
 * <p>Row-级别"演练前后五事实表 count 不变"（设计验收原文）需真库对账 → 归 P-2 载体，本窗不虚构；
 * 本测试聚焦可在 Mockito 层证伪的写回分叉，避免对未实跑库造假。</p>
 */
class SandboxRunIsolationTest {

    private JdbcTemplate jdbc;
    private DcchengClient dcchengClient;
    private ScenarioService scenarioService;
    private BaselineReferenceGuard guard;
    private ScenarioRunService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        dcchengClient = mock(DcchengClient.class);
        scenarioService = mock(ScenarioService.class);
        guard = mock(BaselineReferenceGuard.class);
        service = new ScenarioRunService(jdbc, new ObjectMapper(), dcchengClient, scenarioService, guard);
        // 场景存在（get 不抛即可）
        when(scenarioService.get(anyString())).thenReturn(null);
    }

    private Map<String, Object> run(Map<String, Object> param) {
        return service.run("sc001", param);
    }

    @Test
    @DisplayName("R-25① 验收: runMode 缺省 → FORMAL，且响应带 runMode 水印字段")
    void runModeDefaultsToFormal() {
        // 桩：认知引擎不可用 → 降级（确定性；不涉及指标回写分支）
        when(dcchengClient.diagnose(any())).thenThrow(new IllegalStateException("engine down"));
        Map<String, Object> param = Map.of("runTypes", List.of("DIAGNOSE"), "metric", "安全指标");
        Map<String, Object> resp = run(param);

        assertEquals("FORMAL", resp.get("runMode"));
        // 诊断失败 → 降级，状态 SUCCEEDED_DEGRADED，无任何指标回写
        assertEquals("SUCCEEDED_DEGRADED", resp.get("status"));
        verify(scenarioService, never()).updateMetrics(anyString(), any(), any());
    }

    @Test
    @DisplayName("R-25① 验收: 非法 runMode（DRILL）→ 400，而非静默降级为 FORMAL")
    void rejectsUnknownRunModeWith400() {
        Map<String, Object> param = Map.of("runTypes", List.of("DIAGNOSE"), "runMode", "DRILL");
        assertThrows(BusinessException.class, () -> run(param));
    }

    @Test
    @DisplayName("R-25①: runTypes 缺失 → 400；空数组 → 400；非法 runType → 400")
    void runTypesValidationRejectsMissingEmptyAndUnknown() {
        // 缺失
        assertThrows(BusinessException.class, () -> run(Map.of()));
        // 空数组
        assertThrows(BusinessException.class, () -> run(Map.of("runTypes", List.of())));
        // 非法值
        assertThrows(BusinessException.class, () -> run(
                Map.of("runTypes", List.of("DIAGNOSE", "FROBULATE"))));
    }

    @Test
    @DisplayName("F07-08 写隔离核心: SANDBOX 演练即使诊断含置信度也【禁回写】场景指标（护 D 层事实面）")
    void sandboxRunSkipsScenarioMetricWriteBack() {
        // 诊断成功并带置信度 0.9 —— 若隔离失效，FORMAL 语义下本会回写
        when(dcchengClient.diagnose(any())).thenReturn(Map.of("confidence", 0.9));

        Map<String, Object> resp = run(
                Map.of("runTypes", List.of("DIAGNOSE"), "metric", "安全指标", "runMode", "SANDBOX"));

        assertEquals("SANDBOX", resp.get("runMode"));
        // 隔离红线：SANDBOX 绝不回写事实面
        verify(scenarioService, never()).updateMetrics(anyString(), any(), any());
        assertNull(resp.get("metricsWritten"), "SANDBOX 运行响应 metricsWritten 必须为 null（无副作用水印）");
    }

    @Test
    @DisplayName("对照: FORMAL 运行诊断含置信度时【回写】场景指标（隔离只拦 SANDBOX，不误伤正式）")
    void formalRunWritesScenarioMetricWhenConfidencePresent() {
        when(dcchengClient.diagnose(any())).thenReturn(Map.of("confidence", 0.9));

        Map<String, Object> resp = run(
                Map.of("runTypes", List.of("DIAGNOSE"), "metric", "安全指标", "runMode", "FORMAL"));

        assertEquals("FORMAL", resp.get("runMode"));
        verify(scenarioService).updateMetrics(
                org.mockito.ArgumentMatchers.eq("sc001"),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("0.9")),
                any(Map.class));
        assertFalse(resp.get("metricsWritten") == null, "FORMAL 有置信度时应回写并标记");
    }

    @Test
    @DisplayName("F07-09 联动: FORMAL 运行才触发基线引用守卫（SANDBOX 不触发，避免误拦）")
    void formalModeTriggersBaselineGuardSandboxDoesNot() {
        // FORMAL：guard 被调用
        service.run("sc001", Map.of("runTypes", List.of("DIAGNOSE"),
                "runMode", "FORMAL", "baselineRunId", "run_x"));
        verify(guard).assertFormalMayReference("run_x");

        // SANDBOX：guard 不被调用（演练无基线引用语义）
        service.run("sc002", Map.of("runTypes", List.of("DIAGNOSE"),
                "runMode", "SANDBOX", "baselineRunId", "run_y"));
        verify(guard, never()).assertFormalMayReference("run_y");
    }
}
