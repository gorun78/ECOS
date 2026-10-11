package com.chinacreator.gzcm.engine.ai.capability;

import com.chinacreator.gzcm.engine.ai.capability.CapabilityResult.Quality;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.runtime.llm.config.LLMGatewayProperties;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SUP-MODEL-02 — 能力调用台账（V269 {@code ecos_ai.ecos_ai_capability_task}）可追溯性测试
 * （《ECOS 认知域与智能域补充详细设计 v1.0》§10.4 / §12.2，§19.5 测试登记）。
 *
 * <p>断言维度：model_ref / model_version（版本可追溯）、latency_ms（延迟可追溯）、
 * trace_id（全链对账）逐列随 insertTask 落；cost 维度按 V269 实际列集——表内无独立
 * cost 数值列，计量口径落 cost_unit（§10.6），故断言 cost_unit 落 §10.2 目录值；
 * 失败路径台账<b>照写</b>且 status 保持 FAILED（错误率可统计，禁把失败吞成没发生，§12.2）；
 * 异步提交<b>唯一通道</b>为注入的 runtime-task 协作者（SUP-GOV-02：引擎禁自建调度器/裸线程）。</p>
 */
@ExtendWith(MockitoExtension.class)
class CapabilityTaskLedgerTest {

    @Mock
    private CapabilityLedgerDao ledgerDao;
    @Mock
    private ObjectProvider<LLMGateway> llmGatewayProvider;
    @Mock
    private ObjectProvider<LLMGatewayProperties> llmPropsProvider;
    @Mock
    private ObjectProvider<ITaskManagementService> taskManagementProvider;
    @Mock
    private ObjectProvider<EventBusService> eventBusProvider;
    @Mock
    private LLMGateway llmGateway;
    @Mock
    private ITaskManagementService taskManagementService;

    private ModelCapabilityExecutorImpl newExecutor() {
        return new ModelCapabilityExecutorImpl(ledgerDao, llmGatewayProvider, llmPropsProvider,
                taskManagementProvider, eventBusProvider);
    }

    private CapabilityRequest syncRequest() {
        CapabilityRequest req = new CapabilityRequest();
        req.setCapabilityId(AiCapabilityId.LLM_TEXT_REASONING);
        req.setInputRef("ref-001");
        req.setModelRef("m-9");
        req.setModelVersion("v7");
        req.setTraceId("trace-ledger-1");
        req.setRunId("run-001");
        req.setToolContractRef("tc-001");
        req.setTenantId("t-1");
        req.setSubmitter("tester");
        return req;
    }

    @Test
    @DisplayName("SUP-MODEL-02: 成功调用台账落 modelRef/modelVersion/latencyMs/costUnit/traceId 全维度可追溯")
    void ledgerPersistsTraceabilityColumnsOnSuccess() {
        ModelCapabilityExecutorImpl executor = newExecutor();
        when(llmGatewayProvider.getIfAvailable()).thenReturn(llmGateway);
        when(llmGateway.call(any(ChatRequest.class)))
                .thenReturn(ChatResponse.ok("ok", 4, 6, "m-9"));

        CapabilityResult r = executor.execute(syncRequest());

        assertEquals(CapabilityState.SUCCEEDED, r.getStatus());
        ArgumentCaptor<CapabilityTaskDO> cap = ArgumentCaptor.forClass(CapabilityTaskDO.class);
        verify(ledgerDao).insertTask(cap.capture());
        CapabilityTaskDO t = cap.getValue();
        assertEquals("m-9", t.getModelRef(), "model_ref 列落选中模型 (SUP-MODEL-02 版本可追溯)");
        assertEquals("v7", t.getModelVersion(), "model_version 列落版本 (SUP-MODEL-02 版本可追溯)");
        assertNotNull(t.getLatencyMs(), "latency_ms 列必落 (延迟可追溯)");
        assertTrue(t.getLatencyMs() >= 0);
        assertEquals("TOKEN", t.getCostUnit(), "cost_unit 计量口径落 10.2 目录值");
        assertEquals("trace-ledger-1", t.getTraceId(), "trace_id 与响应 traceId 同真值");
        assertEquals("SYNC", t.getTaskKind(), "task_kind 落 SYNC 单源字面量");
        assertEquals("run-001", t.getRunId(), "run_id 列同真值");
        assertEquals("tc-001", t.getToolContractRef(), "tool_contract_ref 列同真值");
        assertEquals("default", t.getDomain(), "domain 基线列 default");
        assertEquals("1", t.getVersionNo(), "version_no 基线列 1");
        assertNotNull(t.getId(), "主键 = 台账行 UUID, MC01 应用侧生成");
    }

    @Test
    @DisplayName("SUP-MODEL-02: 真实失败时台账照写 FAILED 留痕，告警保留失败原因（错误不吞成没发生）")
    void ledgerStillWrittenOnRealFailureWithFailedStatusAndWarning() {
        ModelCapabilityExecutorImpl executor = newExecutor();
        when(llmGatewayProvider.getIfAvailable()).thenReturn(llmGateway);
        when(llmGateway.call(any(ChatRequest.class))).thenReturn(ChatResponse.fail("upstream boom"));

        CapabilityResult r = executor.execute(syncRequest());

        assertEquals(CapabilityState.FAILED, r.getStatus());
        ArgumentCaptor<CapabilityTaskDO> cap = ArgumentCaptor.forClass(CapabilityTaskDO.class);
        verify(ledgerDao).insertTask(cap.capture());
        CapabilityTaskDO t = cap.getValue();
        assertEquals("FAILED", t.getStatus(), "失败必须原样落 FAILED, 禁包成成功或缺行 (错误率可统计)");
        assertNotNull(t.getWarningsText(), "失败原因必须以告警 JSON 原样留痕");
        assertTrue(t.getWarningsText().contains("LLM 调用失败"));
        // 未跑成功不虚构 model/score 落值; kind 缺省 MODEL_ESTIMATE 且 calibrated 恒 0
        assertEquals(Quality.KIND_MODEL_ESTIMATE, t.getQualityKind());
        assertEquals(Integer.valueOf(0), t.getCalibrated(), "失败路径 calibrated 不得置 1 (SUP-MODEL-03 同族纪律)");
    }

    @Test
    @DisplayName("SUP-MODEL-02/SUP-GOV-02: 异步提交唯一经注入的 runtime-task 协作者，禁引擎自建调度")
    void asyncSubmitOnlyGoesThroughInjectedRuntimeTask() throws Exception {
        ModelCapabilityExecutorImpl executor = newExecutor();
        when(taskManagementProvider.getIfAvailable()).thenReturn(taskManagementService);
        when(taskManagementService.submitTask(any(TaskDescription.class))).thenReturn("task-777");

        CapabilityRequest req = syncRequest();
        AsyncCapabilityHandle h = executor.submit(req);

        assertEquals("task-777", h.getTaskId(), "句柄 taskId = runtime-task 回显键");
        assertEquals("trace-ledger-1", h.getTraceId());
        assertEquals(CapabilityState.SUBMITTED, h.getInitialStatus(), "初态恒 SUBMITTED, 禁提前 SUCCEEDED");
        // runtime-task 是唯一调度通道 (SUP-GOV-02)：仅 verify 注入的 mock，任务类型/参数/租户全程对账
        ArgumentCaptor<TaskDescription> tdCap = ArgumentCaptor.forClass(TaskDescription.class);
        verify(taskManagementService).submitTask(tdCap.capture());
        assertEquals("AI_CAPABILITY", tdCap.getValue().getTaskType(), "任务类型词 = ASYNC_TASK_TYPE 单源字面量");
        assertEquals(Boolean.TRUE, tdCap.getValue().getAsync(), "async 标志恒 TRUE 走异步通道");
        assertEquals("LLM_TEXT_REASONING", tdCap.getValue().getParameters().get("capabilityId"),
                "capabilityId 入 runtime-task 参数以枚举名单源, 禁自由字符串");
        assertEquals("t-1", tdCap.getValue().getTenantId());
        // 台账单行记 ASYNC/PENDING, 主键 = runtime-task taskId (后续 getStatus 对账键)
        ArgumentCaptor<CapabilityTaskDO> cap = ArgumentCaptor.forClass(CapabilityTaskDO.class);
        verify(ledgerDao).insertTask(cap.capture());
        assertEquals("ASYNC", cap.getValue().getTaskKind());
        assertEquals("PENDING", cap.getValue().getStatus());
        assertEquals("task-777", cap.getValue().getId(), "台账主键 = runtime-task taskId, 对账键");
    }

    @Test
    @DisplayName("SUP-MODEL-02/SUP-GOV-02: runtime-task 未装配时 submit 显式失败，禁静默降级或自建线程兜底")
    void submitWithoutRuntimeTaskFailsExplicitly() {
        ModelCapabilityExecutorImpl executor = newExecutor();
        // taskManagementProvider.getIfAvailable() 默认返回 null
        assertThrows(IllegalStateException.class, () -> executor.submit(syncRequest()));
        verify(ledgerDao, never()).insertTask(any(CapabilityTaskDO.class));
    }

    @Test
    @DisplayName("SUP-MODEL-02: runtime-task 提交异常时向上抛真实原因，不落半截台账")
    void submitFailureFromRuntimeTaskPropagatesWithoutLedger() throws Exception {
        ModelCapabilityExecutorImpl executor = newExecutor();
        lenient().when(taskManagementProvider.getIfAvailable()).thenReturn(taskManagementService);
        when(taskManagementService.submitTask(any(TaskDescription.class)))
                .thenThrow(new ITaskManagementService.TaskManagementException("quota exhausted"));

        assertThrows(IllegalStateException.class, () -> executor.submit(syncRequest()));
        verify(ledgerDao, never()).insertTask(any(CapabilityTaskDO.class));
    }

    // ── getStatus 台账只读投影（台账读侧同样禁伪空成功） ─────────────

    @Test
    @DisplayName("SUP-MODEL-02: getStatus 空 taskId/查无行均返回 null（调用方映射 404，禁伪空成功）")
    void getStatusReturnsNullForBlankOrMissingTaskId() {
        ModelCapabilityExecutorImpl executor = newExecutor();
        assertNull(executor.getStatus(null));
        assertNull(executor.getStatus("  "));
        assertNull(executor.getStatus("no-such-task"));
    }
}
