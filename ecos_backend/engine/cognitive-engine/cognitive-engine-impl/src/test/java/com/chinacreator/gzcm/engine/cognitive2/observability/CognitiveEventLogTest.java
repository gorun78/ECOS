package com.chinacreator.gzcm.engine.cognitive2.observability;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveContractSchema;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.observability.CognitiveEventLog;
import com.chinacreator.gzcm.engine.cognitive.api.observability.CognitiveEventLog.Op;
import com.chinacreator.gzcm.engine.cognitive.api.observability.CognitiveEventLog.Result;
import com.chinacreator.gzcm.engine.cognitive2.controller.scenario.ScenarioCognitionController;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveObsFixtures;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.ScenarioCognitiveQueryService;
import com.chinacreator.gzcm.runtime.core.monitor.metrics.IMetricRecorder;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 详细设计-05 C.7 可观测性验收（C.7-2 事件日志四分 + C.7-4 三枚指标经 runtime-monitor 门面）。
 */
class CognitiveEventLogTest {

    // ── C.7-2 冻结行格式 ─────────────────────────────────────────────

    @Test
    @DisplayName("C.7-2：EVT 行格式冻结 —— op/agent/scenario/mind/cost/result 六字段有序")
    void eventLineFormatIsFrozen() {
        assertEquals("EVT=CognitiveAx op=detect agent=ag-1 scenario=sc-1 mind=md-1 cost=12ms result=ok",
                CognitiveEventLog.format(Op.DETECT, "ag-1", "sc-1", "md-1", 12L, Result.OK));
        assertEquals("EVT=CognitiveAx op=eval agent=- scenario=sc-1 mind=md-1 cost=0ms result=empty",
                CognitiveEventLog.format(Op.EVAL, null, "sc-1", "md-1", 0L, Result.EMPTY));
        assertEquals(List.of("detect", "eval", "hyp", "belief"),
                List.of(Op.DETECT.token(), Op.EVAL.token(), Op.HYP.token(), Op.BELIEF.token()),
                "op 四分必须与文档 token 逐字一致");
        assertEquals(List.of("ok", "empty", "deny", "stub"),
                List.of(Result.OK.token(), Result.EMPTY.token(), Result.DENY.token(), Result.STUB.token()));
    }

    @Test
    @DisplayName("C.7-2：agent 无数据源即 - 禁伪造；空集判 EMPTY（与 5xx 严格可分，DG-6/B-4）")
    void absentAgentDegradesToPlaceholderAndEmptySizeClassifiesEmpty() {
        assertEquals("-", CognitiveEventLog.ABSENT);
        assertTrue(CognitiveEventLog.format(Op.BELIEF, "  ", "s", null, 3L, Result.OK).contains("agent=-"));
        assertEquals(Result.EMPTY, CognitiveEventLog.ofSize(0));
        assertEquals(Result.OK, CognitiveEventLog.ofSize(1));
    }

    @Test
    @DisplayName("C.7-2：错误码→结局四分 —— 503 stub / 4xx deny / 故障码不记本行（禁混入降级率）")
    void errorCodeMapsToQuartResultOnlyForDataPlaneOutcomes() {
        assertEquals(Result.STUB, CognitiveEventLog.forErrorCode(CognitiveErrorCode.E503_NOT_IMPLEMENTED));
        assertEquals(Result.DENY, CognitiveEventLog.forErrorCode(CognitiveErrorCode.E400_MIND_NOT_ON_SCENARIO));
        assertEquals(Result.DENY, CognitiveEventLog.forErrorCode(CognitiveErrorCode.E403_CLEARANCE));
        assertEquals(Result.DENY, CognitiveEventLog.forErrorCode(CognitiveErrorCode.E404_SCENARIO));
        assertNull(CognitiveEventLog.forErrorCode(CognitiveErrorCode.E500_ENGINE_DISABLED), "5xx 故障进错误日志");
        assertNull(CognitiveEventLog.forErrorCode(CognitiveErrorCode.E501_SCHEMA_NOT_READY));
        assertNull(CognitiveEventLog.forErrorCode(CognitiveErrorCode.E502_BALANCE_NOT_OK));
        assertNull(CognitiveEventLog.forErrorCode(CognitiveErrorCode.E504_INTERNAL));
        assertNull(CognitiveEventLog.forErrorCode(null));
    }

    // ── C.7-4 指标经底座门面 ─────────────────────────────────────────

    @Test
    @DisplayName("C.7-4：计数经 runtime-monitor IMetricRecorder 门面，引擎内禁自建注册表")
    void counterGoesThroughRuntimeMonitorFacade() {
        IMetricRecorder recorder = mock(IMetricRecorder.class);
        CognitiveObservability obs = observability(recorder);

        obs.counter(CognitiveEventLog.METRIC_BALANCE_FAIL, 2);
        verify(recorder, times(2)).increment(CognitiveEventLog.METRIC_BALANCE_FAIL);

        obs.counter(CognitiveEventLog.METRIC_EVAL_TRUNCATED, 0);
        verify(recorder, never()).increment(CognitiveEventLog.METRIC_EVAL_TRUNCATED);
    }

    @Test
    @DisplayName("C.7-4：宿主未装配门面（无 actuator）→ 降级 DEBUG，禁抛错也禁静默丢弃计数语义")
    void counterDegradesWithoutRecorderAndNeverThrows() {
        CognitiveObservability obs = observability(null);
        obs.counter(CognitiveEventLog.METRIC_CAPABILITY_UNMAPPED, 3);
        obs.event(Op.DETECT, null, "s", null, obs.start(), Result.OK);
        obs.event(Op.DETECT, null, "s", null, obs.start(), null);
        obs.eventGuard(Op.EVAL, null, "s", "m", obs.start(),
                new CognitiveGuardException(CognitiveErrorCode.E504_INTERNAL, CognitiveErrorCode.MSG_INTERNAL));
    }

    @Test
    @DisplayName("C.7-2：E1~E4 每次请求出一条 EVT（含 guard 拒答/ stub），故障码不重复记")
    void controllerEmitsEventPerOutcomeWithoutSwallowingGuards() {
        ScenarioCognitiveQueryService service = mock(ScenarioCognitiveQueryService.class);
        when(service.detect("s-1")).thenReturn(List.of());
        CognitiveObservability obs = mock(CognitiveObservability.class);
        when(obs.start()).thenReturn(1L);
        ScenarioCognitionController controller = new ScenarioCognitionController(service, obs);

        ApiResponse<?> resp = controller.detect("s-1");
        assertEquals(0, resp.getCode(), "DG-6 空场景 = 200 data:[]");
        verify(obs).event(eq(Op.DETECT), isNull(), eq("s-1"), isNull(), eq(1L), eq(Result.EMPTY));

        CognitiveGuardException deny = new CognitiveGuardException(
                CognitiveErrorCode.E400_MIND_NOT_ON_SCENARIO, CognitiveErrorCode.MSG_MIND_NOT_ON_SCENARIO);
        when(service.beliefs(any(), any())).thenThrow(deny);
        assertThrows(CognitiveGuardException.class, () -> controller.beliefs("s-1", "m-9"));
        verify(obs).eventGuard(eq(Op.BELIEF), eq(null), eq("s-1"), eq("m-9"), eq(1L), eq(deny));
    }

    @Test
    @DisplayName("C.7-4：E2 截断计数 + E4 未映射能力行数（DG-7 不丢行，计数上报）")
    void truncatedEvalAndUnmappedBeliefsIncrementCounters() {
        ScenarioCognitiveQueryService service = mock(ScenarioCognitiveQueryService.class);
        CognitiveContractSchema.OperationEvalData evalData = new CognitiveContractSchema.OperationEvalData();
        evalData.setMetrics(List.of(new CognitiveContractSchema.OperationEvalMetric(
                "mt-1", "回款率", "CASH_FLOW", 0.8, "FLAT")));
        evalData.setTruncated(true);
        when(service.operationEval("s-1", "m-1", null)).thenReturn(evalData);
        when(service.beliefs("s-1", null)).thenReturn(List.of(
                new CognitiveContractSchema.BeliefVO("b-1", "m-1", "回款率", "finance", 0.7, 0.6, null, "2026-10-01"),
                new CognitiveContractSchema.BeliefVO("b-2", "m-1", "利润率", "finance", 0.5, 0.4, "PROFIT", "2026-10-02")));

        IMetricRecorder recorder = mock(IMetricRecorder.class);
        CognitiveObservability obs = observability(recorder);
        ScenarioCognitionController controller = new ScenarioCognitionController(service, obs);

        controller.operationEval("s-1", "m-1", null);
        verify(recorder).increment(CognitiveEventLog.METRIC_EVAL_TRUNCATED);

        controller.beliefs("s-1", null);
        verify(recorder, times(1)).increment(CognitiveEventLog.METRIC_CAPABILITY_UNMAPPED);
    }

    // ── C.7-3 审计 fail-closed 三段式 ────────────────────────────────

    @Test
    @DisplayName("C.7-3：诊断/模拟审计经 runtime-event 主通道发 ecos.audit（第一段）")
    void auditPublishesEcosAuditEventThroughEventBus() {
        EventBusService bus = mock(EventBusService.class);
        CognitiveObsFixtures.withEventBus(bus)
                .audit("cognitiveForecast.diagnose", "forecast_run", "forecastRunId=run-1 chains=2");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(bus).publish(eq(KafkaTopics.AUDIT), captor.capture());
        Map<String, Object> event = captor.getValue();
        assertEquals("cognitiveForecast.diagnose", event.get("action"));
        assertEquals("forecast_run", event.get("resource"));
        assertEquals("success", event.get("result"));
        assertNotNull(event.get("timestamp"), "审计事件必须带时间戳");
    }

    @Test
    @DisplayName("C.7-3：主通道未装配 → 本地兜底 JSONL 落盘（第二段，禁静默丢弃）")
    void auditFallsBackToLocalJsonlWhenBusMissing() throws IOException {
        CognitiveObsFixtures.unassembled()
                .audit("cognitiveForecast.simulateFallbackProbe", "forecast_scenario_run", "unit=ok");

        Path file = Path.of(CognitiveObsFixtures.TEST_FALLBACK_FILE);
        assertTrue(Files.exists(file), "兜底文件必须落盘");
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.get(lines.size() - 1).contains("cognitiveForecast.simulateFallbackProbe"),
                "兜底行必须含本次 action");
    }

    @Test
    @DisplayName("C.7-3：主通道与兜底双失败 → fail-closed 拒绝业务动作（第三段，禁假审计）")
    void auditRejectsActionWhenBothChannelsFail() {
        BusinessException e = assertThrows(BusinessException.class, () ->
                CognitiveObsFixtures.auditChannelBroken()
                        .audit("cognitiveForecast.simulate", "forecast_scenario_run", "d"));
        assertTrue(e.getMessage().contains(CognitiveErrorCode.E504_INTERNAL),
                "拒绝原因必须是 ECOS-COG-504: " + e.getMessage());
    }

    private static CognitiveObservability observability(IMetricRecorder recorder) {
        return CognitiveObsFixtures.withRecorder(recorder);
    }
}
