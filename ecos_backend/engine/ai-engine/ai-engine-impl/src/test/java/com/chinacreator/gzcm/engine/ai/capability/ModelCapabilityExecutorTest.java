package com.chinacreator.gzcm.engine.ai.capability;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SUP-MODEL-01 — 能力路由与统一结果契约冻结测试
 * （《ECOS 认知域与智能域补充详细设计 v1.0》§10.2 / §10.4 / §10.5，§19.5 测试登记）。
 *
 * <p>设计稿原名 {@code ModelCapabilityRouterTest}，但生产叠加层中不存在 "Router" 独立类，
 * 路由与分发落在 {@link ModelCapabilityExecutorImpl#execute(CapabilityRequest)} 的
 * capability switch（§10.4 execute 单入口），故按实际生产类名冻结为本类。</p>
 *
 * <p>红线断言：</p>
 * <ul>
 *   <li>SUP-MODEL-01：路由只按 {@link AiCapabilityId} 分派，调用方禁硬编码 provider/model；
 *       未接模型的能力一律按能力维度回 FAILED + 明确告警，<b>绝不伪造成功输出</b>
 *       （X-44 无伪数据：无 outputRef / 无 modelRef 落值）；</li>
 *   <li>§10.5 契约往返：CapabilityResult 必载 capabilityId/status/modelRef/modelVersion/
 *       latencyMs/traceId；真实失败必须 FAILED + 告警，禁吞成空成功（§12.2 真实错误传播）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ModelCapabilityExecutorTest {

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

    private ModelCapabilityExecutorImpl newExecutor() {
        return new ModelCapabilityExecutorImpl(ledgerDao, llmGatewayProvider, llmPropsProvider,
                taskManagementProvider, eventBusProvider);
    }

    private CapabilityRequest baseRequest(AiCapabilityId cap) {
        CapabilityRequest req = new CapabilityRequest();
        req.setCapabilityId(cap);
        req.setInputRef("ref-input-001");
        return req;
    }

    // ── SUP-MODEL-01: 能力维度路由，未接模型禁伪造 ─────────────────

    @Test
    @DisplayName("SUP-MODEL-01: 全目录按 AiCapabilityId 路由分发，一期未接模型能力一律 FAILED 且无伪造输出")
    void unwiredCapabilityFailsForWholeCatalogByCapabilityId() {
        ModelCapabilityExecutorImpl executor = newExecutor();
        for (AiCapabilityId cap : AiCapabilityId.values()) {
            // 已接 llm-gateway 的 LLM/Embedding 两族走真实模型通道，不在未接入断言范围
            if (cap == AiCapabilityId.LLM_TEXT_REASONING || cap == AiCapabilityId.TEXT_EMBEDDING) {
                continue;
            }
            CapabilityResult r = executor.execute(baseRequest(cap));
            // 生产实际状态 = FAILED：CapabilityState 七态词表即"不可用"的等价表达（§10.6 降级不满足门槛禁伪装 SUCCEEDED）
            assertEquals(CapabilityState.FAILED, r.getStatus(), "未接模型能力 " + cap + " 必须 FAILED");
            assertNull(r.getOutputRef(), "能力 " + cap + " 未接模型，禁伪造 outputRef (X-44 无伪数据)");
            assertNull(r.getModelRef(), "能力 " + cap + " 未接模型，不得落虚构 modelRef");
            assertFalse(r.isDegraded(), "未接入不是降级，必须 degraded=false");
            assertNotNull(r.getWarnings(), "能力 " + cap + " 失败必须带告警");
            assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains(cap.name())),
                    "告警必须指明具体能力 ID: " + cap);
            assertEquals(cap.costUnit(), r.getCostUnit(), "计量口径取 §10.2 目录冻结值");
        }
        // 台账对该能力维度仍逐行落留痕
        verify(ledgerDao, atLeast(3)).insertTask(any(CapabilityTaskDO.class));
    }

    @Test
    @DisplayName("SUP-MODEL-01: OCR/ASR/图像理解三典型未接能力，结果为 FAILED+告警且无任何虚构载荷")
    void ocrAsrImageUnderstandingReturnExplicitFailedNotFakeResult() {
        ModelCapabilityExecutorImpl executor = newExecutor();
        for (AiCapabilityId cap : java.util.List.of(
                AiCapabilityId.DOCUMENT_OCR, AiCapabilityId.SPEECH_TO_TEXT, AiCapabilityId.IMAGE_UNDERSTANDING)) {
            CapabilityResult r = executor.execute(baseRequest(cap));
            assertEquals(CapabilityState.FAILED, r.getStatus());
            assertNull(r.getOutputRef());
            assertNull(r.getModelRef());
            assertNull(r.getQuality(),
                    "未接入执行不产生质量分; 产出缺省 null, 台账侧才补 MODEL_ESTIMATE 留痕");
            assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("一期未接入模型 Provider")));
        }
    }

    @Test
    @DisplayName("SUP-MODEL-01: 调用方不传 modelRef 时由目录默认模型路由，禁调用方硬编 provider")
    void routingUsesCatalogDefaultModelWhenCallerDoesNotPinOne() {
        ModelCapabilityExecutorImpl executor = newExecutor();
        LLMGatewayProperties props = new LLMGatewayProperties();
        props.getEngine().setDefaultModel("catalog-model-x");
        lenient().when(llmGatewayProvider.getIfAvailable()).thenReturn(llmGateway);
        when(llmPropsProvider.getIfAvailable()).thenReturn(props);
        when(llmGateway.call(any(ChatRequest.class)))
                .thenReturn(ChatResponse.ok("ok", 3, 2, "catalog-model-x"));

        CapabilityResult r = executor.execute(baseRequest(AiCapabilityId.LLM_TEXT_REASONING));

        assertEquals("catalog-model-x", r.getModelRef(), "未指定模型时路由到目录默认模型");
        assertNotNull(r.getSelectedModelReason(), "路由决策必须留痕 selectedModelReason (§10.3)");
        assertTrue(r.getSelectedModelReason().contains("catalog-model-x"));
    }

    // ── §10.5: 请求/响应契约往返 ───────────────────────────────────

    @Test
    @DisplayName("SUP-MODEL-01/§10.5: 同步成功契约往返 capabilityId/status/modelRef/modelVersion/latencyMs/traceId 回合一致")
    void contractRoundTripOnSuccess() {
        ModelCapabilityExecutorImpl executor = newExecutor();
        when(llmGatewayProvider.getIfAvailable()).thenReturn(llmGateway);
        when(llmGateway.call(any(ChatRequest.class)))
                .thenReturn(ChatResponse.ok("hi", 10, 5, "m-1"));
        CapabilityRequest req = baseRequest(AiCapabilityId.LLM_TEXT_REASONING);
        req.setModelRef("m-1");
        req.setModelVersion("v2");
        req.setTraceId("trace-rt-1");

        CapabilityResult r = executor.execute(req);

        assertEquals("LLM_TEXT_REASONING", r.getCapabilityId());
        assertEquals(CapabilityState.SUCCEEDED, r.getStatus());
        assertEquals("m-1", r.getModelRef());
        assertEquals("v2", r.getModelVersion());
        assertEquals("trace-rt-1", r.getTraceId());
        assertTrue(r.getLatencyMs() >= 0, "latencyMs 必为实测非负");
        assertNotNull(r.getOutputRef(), "成功必产输出引用");
        assertEquals("TOKEN", r.getCostUnit());
        assertEquals(15L, r.getCostValue(), "costValue = tokensInput+tokensOutput 同真值");
        // 台账（V269）与响应同真值：tracing 字段往返一致（SUP-MODEL-02）
        ArgumentCaptor<CapabilityTaskDO> cap = ArgumentCaptor.forClass(CapabilityTaskDO.class);
        verify(ledgerDao).insertTask(cap.capture());
        assertEquals("SUCCEEDED", cap.getValue().getStatus());
        assertEquals("m-1", cap.getValue().getModelRef());
        assertEquals("v2", cap.getValue().getModelVersion());
        assertEquals("trace-rt-1", cap.getValue().getTraceId());
        assertEquals(r.getLatencyMs(), cap.getValue().getLatencyMs(), "台账延迟与响应同值");
    }

    @Test
    @DisplayName("SUP-MODEL-01/§12.2: 真实 LLM 失败不得吞成空成功，FAILED+告警原样传播且落台账 FAILED")
    void realLlmFailurePropagatesAsFailedNotEmptySuccess() {
        ModelCapabilityExecutorImpl executor = newExecutor();
        when(llmGatewayProvider.getIfAvailable()).thenReturn(llmGateway);
        when(llmGateway.call(any(ChatRequest.class))).thenReturn(ChatResponse.fail("boom"));
        CapabilityRequest req = baseRequest(AiCapabilityId.LLM_TEXT_REASONING);
        req.setModelRef("m-1");
        req.setTraceId("trace-fail-1");

        CapabilityResult r = executor.execute(req);

        assertEquals(CapabilityState.FAILED, r.getStatus(), "真实失败必须 FAILED, 禁包成空成功");
        assertNull(r.getOutputRef());
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("LLM 调用失败")),
                "失败类别必须进告警; 原错误码只进日志 X-49 禁进响应体");
        ArgumentCaptor<CapabilityTaskDO> cap = ArgumentCaptor.forClass(CapabilityTaskDO.class);
        verify(ledgerDao).insertTask(cap.capture());
        assertEquals("FAILED", cap.getValue().getStatus(), "台账同真值落 FAILED, 错误率可统计");
        assertNotNull(cap.getValue().getWarningsText());
    }

    @Test
    @DisplayName("SUP-MODEL-01/§10.4: 入参校验失败即抛 IAE，错误不吞且不落台账")
    void validationFailureThrowsAndSkipsLedger() {
        ModelCapabilityExecutorImpl executor = newExecutor();
        CapabilityRequest req = new CapabilityRequest();
        req.setCapabilityId(AiCapabilityId.DOCUMENT_OCR);
        // 空引用违反 §10.5 inputRef NOT NULL（输入只放引用 ID）
        req.setInputRef("  ");

        assertThrows(IllegalArgumentException.class, () -> executor.execute(req));
        verify(ledgerDao, never()).insertTask(any(CapabilityTaskDO.class));
    }
}
