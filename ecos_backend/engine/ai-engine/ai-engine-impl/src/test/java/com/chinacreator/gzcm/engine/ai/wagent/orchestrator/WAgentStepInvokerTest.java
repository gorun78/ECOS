package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.StepType;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.tool.EngineEndpointInvoker;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link StepInvokerPort} 装配实现的分派与保守降级（详细设计-10 F10-14/§4.2；差距分析 P0-5）。
 *
 * <p>守的三件事：tool 委派咽喉通道；llm 只走 llm-gateway 门面且失败/缺席一律保守失败
 * （禁 fail-open 造结论、禁把缺用量冒充实测）；aggregate/sub_run 属未交付能力 ⇒
 * 如实失败并点名缺口（禁自造口径、禁伪称成功）。</p>
 */
@SuppressWarnings("unchecked")
class WAgentStepInvokerTest {

    private final EngineEndpointInvoker endpointInvoker = mock(EngineEndpointInvoker.class);
    private final LLMGateway gateway = mock(LLMGateway.class);
    private final ObjectProvider<LLMGateway> gatewayProvider = mock(ObjectProvider.class);
    private final WAgentStepInvoker invoker =
            new WAgentStepInvoker(endpointInvoker, gatewayProvider);

    private static StepInvokerPort.Request req(String type, Map<String, Object> input) {
        return new StepInvokerPort.Request("t1", "run-9", "s3_step", type, "some_tool", "1", input, 0);
    }

    // ── 分派面 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("tool 步骤委派 EngineEndpointInvoker（不自行触达网关）")
    void toolStepDelegatesToEndpointInvoker() {
        StepInvokerPort.Request request = req(StepType.TOOL.value(), Map.of("k", "v"));
        when(endpointInvoker.invoke(request))
                .thenReturn(StepInvokerPort.Outcome.ok(Map.of("profit", 1)));

        StepInvokerPort.Outcome out = invoker.invoke(request);

        assertTrue(out.success());
        verify(endpointInvoker).invoke(request);
        verifyNoInteractions(gatewayProvider);
    }

    @Test
    @DisplayName("human_gate/wait_task/未知 step_type ⇒ E-VALIDATION（StepType 单源，不经本接缝）")
    void nonInvokedStepTypesRejected() {
        for (String type : List.of(StepType.HUMAN_GATE.value(), StepType.WAIT_TASK.value(), "mystery")) {
            StepInvokerPort.Outcome out = invoker.invoke(req(type, Map.of()));
            assertEquals(WAgentErrorCode.E_VALIDATION.wireCode(), out.errorCode(), type);
        }
        assertEquals(WAgentErrorCode.E_VALIDATION.wireCode(),
                invoker.invoke(null).errorCode(), "空请求不得静默成功");
        verifyNoInteractions(endpointInvoker, gatewayProvider);
    }

    @Test
    @DisplayName("aggregate/sub_run 未交付 ⇒ E-NOT-FOUND 且点名缺口，禁落承诺重试的 E-WA-DEP、禁自造聚合口径")
    void unimplementedCapabilitiesFailHonestly() {
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);

        for (String type : List.of(StepType.AGGREGATE.value(), StepType.SUB_RUN.value())) {
            StepInvokerPort.Outcome out = invoker.invoke(req(type, Map.of("k", "v")));
            assertFalse(out.success(), type + " 不得伪称成功");
            assertEquals(WAgentErrorCode.E_NOT_FOUND.wireCode(), out.errorCode(), type);
            assertFalse(WAgentErrorCode.E_NOT_FOUND.clientRetryable(), "缺口类失败禁标可重试");
            assertTrue(out.errorText().contains("#13"), "缺口须可定位所属批次: " + out.errorText());
            assertEquals(type, out.metrics().get(WAgentStepInvoker.METRIC_GAP));
        }
        verifyNoInteractions(endpointInvoker, gateway);
    }

    // ── llm 通道 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("llm 成功：走网关门面一次；model 留空由网关路由、callerId/sessionId 单源、无明文密钥")
    void llmStepGoesThroughGatewayFacadeOnly() {
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        ChatResponse response = ChatResponse.ok("本期利润 12.5 万元", 100, 20, "deepseek-chat");
        response.setUsageReported(true);
        when(gateway.call(any(ChatRequest.class))).thenReturn(response);

        StepInvokerPort.Outcome out = invoker.invoke(req(StepType.LLM.value(),
                Map.of("system_prompt", "只做叙述，不得改数字", "prompt", "总结结论")));

        assertTrue(out.success(), out.errorText());
        assertEquals("本期利润 12.5 万元", out.output().get(WAgentStepInvoker.OUT_CONTENT));
        assertEquals("deepseek-chat", out.output().get(WAgentStepInvoker.OUT_MODEL));
        assertEquals(100, out.output().get(WAgentStepInvoker.OUT_TOKENS_INPUT));
        assertEquals("reported", out.metrics().get(WAgentStepInvoker.METRIC_TOKEN_USAGE));

        ArgumentCaptor<ChatRequest> cap = ArgumentCaptor.forClass(ChatRequest.class);
        verify(gateway).call(cap.capture());
        ChatRequest chat = cap.getValue();
        assertEquals(WAgentStepInvoker.CALLER_ID, chat.getCallerId());
        assertEquals("run-9", chat.getSessionId(), "计量作用域 = run（F06-07 单源）");
        assertEquals(Boolean.FALSE, chat.getStream());
        assertEquals(0.0, chat.getTemperature().doubleValue(), 1e-9, "缺省走确定性档");
        org.junit.jupiter.api.Assertions.assertNull(chat.getModel(),
                "禁在引擎侧写模型名（网关默认路由）");
        org.junit.jupiter.api.Assertions.assertNull(chat.getApiKeyRef());
        assertEquals(2, chat.getMessages().size());
        assertEquals("system", chat.getMessages().get(0).getRole());
    }

    @Test
    @DisplayName("llm 缺 prompt ⇒ E-VALIDATION 且文案指出字段名，不发网关调用")
    void llmMissingPromptRejectsBeforeCall() {
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);

        StepInvokerPort.Outcome out = invoker.invoke(req(StepType.LLM.value(), Map.of("foo", "bar")));

        assertEquals(WAgentErrorCode.E_VALIDATION.wireCode(), out.errorCode());
        assertTrue(out.errorText().contains("prompt"), out.errorText());
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("网关未装配 / 空响应 / 调用异常 ⇒ 一律 E-WA-DEP 保守失败（禁 fail-open 造结论）")
    void llmDependencyAbsenceFailsClosed() {
        ObjectProvider<LLMGateway> empty = mock(ObjectProvider.class);
        when(empty.getIfAvailable()).thenReturn(null);
        Map<String, Object> input = Map.of("prompt", "总结结论");

        assertEquals(WAgentErrorCode.E_WA_DEP.wireCode(),
                new WAgentStepInvoker(endpointInvoker, empty)
                        .invoke(req(StepType.LLM.value(), input)).errorCode(), "网关缺席");

        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(gateway.call(any(ChatRequest.class))).thenReturn(null);
        assertEquals(WAgentErrorCode.E_WA_DEP.wireCode(),
                invoker.invoke(req(StepType.LLM.value(), input)).errorCode(), "空响应");

        when(gateway.call(any(ChatRequest.class))).thenThrow(new IllegalStateException("连不上"));
        assertEquals(WAgentErrorCode.E_WA_DEP.wireCode(),
                invoker.invoke(req(StepType.LLM.value(), input)).errorCode(), "调用异常");
    }

    @Test
    @DisplayName("网关失败按 errorClass 映射 W 域码（LLM_RATE_LIMITED⇒E-RATE、GUARDRAIL_DENIED⇒E-POLICY、未分类⇒E-WA-DEP）")
    void llmFailureMappingUsesSameSingleSourceTable() {
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);

        when(gateway.call(any(ChatRequest.class)))
                .thenReturn(ChatResponse.fail(AiErrorClass.LLM_RATE_LIMITED, "provider 429"));
        assertEquals(WAgentErrorCode.E_RATE.wireCode(),
                invoker.invoke(req(StepType.LLM.value(), Map.of("prompt", "x"))).errorCode());

        when(gateway.call(any(ChatRequest.class)))
                .thenReturn(ChatResponse.fail(AiErrorClass.GUARDRAIL_DENIED, "ABAC DENY"));
        StepInvokerPort.Outcome denied = invoker.invoke(req(StepType.LLM.value(), Map.of("prompt", "x")));
        assertEquals(WAgentErrorCode.E_POLICY.wireCode(), denied.errorCode());
        assertEquals(AiErrorClass.GUARDRAIL_DENIED.name(),
                denied.metrics().get("llm_error_class"), "分类须留痕供对账");
        assertFalse(denied.errorText().contains("ABAC DENY"), "网关原因摘要禁进对外文案（X-49）");

        when(gateway.call(any(ChatRequest.class))).thenReturn(ChatResponse.fail("无分类"));
        assertEquals(WAgentErrorCode.E_WA_DEP.wireCode(),
                invoker.invoke(req(StepType.LLM.value(), Map.of("prompt", "x"))).errorCode());
    }

    @Test
    @DisplayName("用量缺失禁冒充实测：hasUsage=false 时不写 token 字段，metrics 记 token_usage=missing")
    void missingUsageIsNotPassedOffAsMeasured() {
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(gateway.call(any(ChatRequest.class)))
                .thenReturn(ChatResponse.ok("结论", 0, 0, "deepseek-chat"));

        StepInvokerPort.Outcome out = invoker.invoke(req(StepType.LLM.value(), Map.of("prompt", "x")));

        assertTrue(out.success());
        assertFalse(out.output().containsKey(WAgentStepInvoker.OUT_TOKENS_INPUT),
                "未上报也未估算 ⇒ 禁把默认 0 写进输出");
        assertEquals("missing", out.metrics().get(WAgentStepInvoker.METRIC_TOKEN_USAGE));
    }

    @Test
    @DisplayName("成功但正文空白 ⇒ E-INTERNAL 如实失败（禁把空叙述当结论发布）")
    void blankNarrativeIsNotSuccess() {
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(gateway.call(any(ChatRequest.class))).thenReturn(ChatResponse.ok("   ", 1, 1, "m"));

        StepInvokerPort.Outcome out = invoker.invoke(req(StepType.LLM.value(), Map.of("prompt", "x")));

        assertEquals(WAgentErrorCode.E_INTERNAL.wireCode(), out.errorCode());
        assertFalse(out.success());
    }

    @Test
    @DisplayName("temperature 越界/脏值回确定性档，max_tokens 只在正整数时透传")
    void scalarInputsAreSanitized() {
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(gateway.call(any(ChatRequest.class))).thenReturn(ChatResponse.ok("ok", 1, 1, "m"));

        invoker.invoke(req(StepType.LLM.value(), Map.of("prompt", "x", "temperature", 9.5)));
        invoker.invoke(req(StepType.LLM.value(),
                Map.of("prompt", "x", "temperature", 0.3, "max_tokens", -1)));

        ArgumentCaptor<ChatRequest> cap = ArgumentCaptor.forClass(ChatRequest.class);
        verify(gateway, org.mockito.Mockito.times(2)).call(cap.capture());
        List<ChatRequest> calls = cap.getAllValues();
        assertEquals(0.0, calls.get(0).getTemperature().doubleValue(), 1e-9);
        org.junit.jupiter.api.Assertions.assertNull(calls.get(0).getMaxTokens());
        assertEquals(0.3, calls.get(1).getTemperature().doubleValue(), 1e-9);
        org.junit.jupiter.api.Assertions.assertNull(calls.get(1).getMaxTokens(),
                "非正数 max_tokens 禁透传");
    }

    @Test
    @DisplayName("装配面：本实现即 StepInvokerPort 的唯一 Bean，StepExecutor 的「接缝未装配」分支不再触发")
    void implementsThePortItself() {
        assertTrue(StepInvokerPort.class.isAssignableFrom(WAgentStepInvoker.class));
        assertEquals(StepInvokerPort.class, WAgentStepInvoker.class.getInterfaces()[0]);
    }
}
