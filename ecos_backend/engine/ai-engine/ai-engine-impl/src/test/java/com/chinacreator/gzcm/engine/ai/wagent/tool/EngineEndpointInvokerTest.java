package com.chinacreator.gzcm.engine.ai.wagent.tool;

import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.engine.ai.service.ToolCall;
import com.chinacreator.gzcm.engine.ai.service.ToolExecutorService;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.StepType;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ToolContractStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentToolContractDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentToolContractDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.StepInvokerPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 工具通道执行器（详细设计-10 F10-14 §4.2 {@code invoke(tool)} + {@code output contract validate}；
 * 差距分析 P0-5 执行回路）。
 *
 * <p>三条纪律各自有用例守住：</p>
 * <ol>
 *   <li><b>咽喉唯一</b>：前置拒绝（契约缺失/非 active/入参地板）一律不触达
 *       {@code ToolExecutorService}（{@code verifyNoInteractions}），成功/失败路径必触达一次；</li>
 *   <li><b>出口码单源</b>：失败 Outcome 的 errorCode 只能是 §5.3 W 域码，
 *       底座 {@code ECOS-AI-*} 只入 metrics（映射表覆盖全部 {@link AiErrorClass} 成员，
 *       新增成员未登记映射即红）；</li>
 *   <li><b>禁把缺口洗成抖动</b>：{@code STUB_CAPABILITY} 落不可重试的 E-NOT-FOUND，
 *       不落承诺"稍后重试"的 E-WA-DEP。</li>
 * </ol>
 */
class EngineEndpointInvokerTest {

    private static final String TOOL = "query_project_profit";
    private static final String VER = "1";

    private final ToolExecutorService chokepoint = mock(ToolExecutorService.class);
    private final WAgentToolContractDao contractDao = mock(WAgentToolContractDao.class);
    private final EngineEndpointInvoker invoker =
            new EngineEndpointInvoker(chokepoint, contractDao);

    // ── fixtures ────────────────────────────────────────────────────────

    private static StepInvokerPort.Request req(Map<String, Object> input) {
        return new StepInvokerPort.Request("t1", "run-1", "s2_" + TOOL, StepType.TOOL.value(),
                TOOL, VER, input, 0);
    }

    private static WAgentToolContractDO contract(String status, String inputSchema,
                                                 String outputSchema) {
        WAgentToolContractDO c = new WAgentToolContractDO();
        c.setId("c-1");
        c.setName(TOOL);
        c.setVersion(VER);
        c.setStatus(status);
        c.setMinLevel(1);
        c.setInputSchemaJson(inputSchema);
        c.setOutputSchemaJson(outputSchema);
        return c;
    }

    private void activeContract(String inputSchema, String outputSchema) {
        when(contractDao.findByNameAndVersion(TOOL, VER)).thenReturn(
                Optional.of(contract(ToolContractStatus.ACTIVE.value(), inputSchema, outputSchema)));
    }

    private static ToolExecutorService.ToolResult ok(String content) {
        return ToolExecutorService.ToolResult.ok("wa:run-1:s2_query_project_profit:" + TOOL,
                TOOL, content, 42L);
    }

    private static ToolExecutorService.ToolResult fail(AiErrorClass cls) {
        ToolExecutorService.ToolResult r = ToolExecutorService.ToolResult.fail("call-1", TOOL,
                "底座原文：connection refused to 10.0.0.1", 7L);
        if (cls != null) {
            r.setErrorClass(cls.name());
            r.setErrorCode(cls.errorCode());
        }
        r.setTraceId("trace-xyz");
        return r;
    }

    // ── 1 契约目录纵深防御 ───────────────────────────────────────────────

    @Test
    @DisplayName("契约未注册 ⇒ E-NOT-FOUND 且不调咽喉（不产生未裁决执行，详设-06 F06-01 所引 PRD §1.5-1 口径）")
    void contractMissingRejectsWithoutCallingChokepoint() {
        when(contractDao.findByNameAndVersion(anyString(), anyString())).thenReturn(Optional.empty());

        StepInvokerPort.Outcome out = invoker.invoke(req(Map.of("project", "p1")));

        assertFalse(out.success());
        assertEquals(WAgentErrorCode.E_NOT_FOUND.wireCode(), out.errorCode());
        verifyNoInteractions(chokepoint);
    }

    @Test
    @DisplayName("缺 tool_version ⇒ E-VALIDATION（禁「取最新版」猜测）；目录与咽喉都不触达")
    void blankVersionFailsClosed() {
        StepInvokerPort.Outcome out = invoker.invoke(new StepInvokerPort.Request(
                "t1", "run-1", "s2", StepType.TOOL.value(), TOOL, "  ", Map.of(), 0));

        assertFalse(out.success());
        assertEquals(WAgentErrorCode.E_VALIDATION.wireCode(), out.errorCode());
        verifyNoInteractions(contractDao);
        verifyNoInteractions(chokepoint);
    }

    @Test
    @DisplayName("命中契约但非 active（F10-17 只认在用版）⇒ E-WA-STATE，不调咽喉")
    void nonActiveContractRejected() {
        when(contractDao.findByNameAndVersion(TOOL, VER)).thenReturn(
                Optional.of(contract(ToolContractStatus.DEPRECATED.value(),
                        "{\"required\":[]}", "{\"required\":[]}")));

        StepInvokerPort.Outcome out = invoker.invoke(req(Map.of()));

        assertFalse(out.success());
        assertEquals(WAgentErrorCode.E_WA_STATE.wireCode(), out.errorCode());
        assertEquals(ToolContractStatus.DEPRECATED.value(),
                out.metrics().get(EngineEndpointInvoker.METRIC_CONTRACT_STATUS));
        verifyNoInteractions(chokepoint);
    }

    // ── 2 入参地板 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("契约 input.required 缺键 ⇒ E-VALIDATION 且文案指出具体字段（§5.3），不发调用")
    void inputRequiredMissingRejectsWithFieldName() {
        activeContract("{\"required\":[\"project_id\",\"period\"]}", "{\"required\":[\"profit\"]}");

        StepInvokerPort.Outcome out = invoker.invoke(req(Map.of("project_id", "p1")));

        assertFalse(out.success());
        assertEquals(WAgentErrorCode.E_VALIDATION.wireCode(), out.errorCode());
        assertTrue(out.errorText().contains("period"), "文案须点出缺失字段名: " + out.errorText());
        assertEquals(EngineEndpointInvoker.CONTRACT_VIOLATED,
                out.metrics().get(EngineEndpointInvoker.METRIC_INPUT_CONTRACT));
        verifyNoInteractions(chokepoint);
    }

    @Test
    @DisplayName("契约 schema 不可解析 ⇒ 按「无从校验」留痕 no_required_declared，禁冒充实检通过")
    void unparseableSchemaIsNotFakePass() {
        activeContract("{not-json", "{\"required\":[\"profit\"]}");
        when(chokepoint.execute(any(ToolCall.class))).thenReturn(ok("{\"profit\":12}"));

        StepInvokerPort.Outcome out = invoker.invoke(req(Map.of("project_id", "p1")));

        assertTrue(out.success());
        assertEquals(EngineEndpointInvoker.CONTRACT_NO_REQUIRED,
                out.metrics().get(EngineEndpointInvoker.METRIC_INPUT_CONTRACT));
        assertEquals(EngineEndpointInvoker.CONTRACT_CHECKED_PASSED,
                out.metrics().get(EngineEndpointInvoker.METRIC_OUTPUT_CONTRACT));
    }

    // ── 3 成功面：咽喉出入参与 metrics 留痕 ──────────────────────────────

    @Test
    @DisplayName("成功路径：经咽喉 execute(ToolCall) 一次；JSON 对象返回体解析为结构化 output 并带契约/耗时留痕")
    void happyPathGoesThroughChokepoint() {
        activeContract("{\"required\":[\"project_id\"]}", "{\"required\":[\"profit\"]}");
        when(chokepoint.execute(any(ToolCall.class))).thenReturn(ok("{\"profit\":12.5,\"currency\":\"CNY\"}"));

        StepInvokerPort.Outcome out = invoker.invoke(req(Map.of("project_id", "p1")));

        assertTrue(out.success(), out.errorText());
        assertEquals(12.5, ((Number) out.output().get("profit")).doubleValue(), 1e-9);
        assertEquals("CNY", out.output().get("currency"));
        assertEquals(42L, out.metrics().get(EngineEndpointInvoker.METRIC_DURATION_MS));
        assertEquals(ToolContractStatus.ACTIVE.value(),
                out.metrics().get(EngineEndpointInvoker.METRIC_CONTRACT_STATUS));
        assertEquals(1, out.metrics().get(EngineEndpointInvoker.METRIC_CONTRACT_MIN_LEVEL));
        assertNotNull(out.metrics().get(EngineEndpointInvoker.METRIC_TOOL_CALL_ID));

        ArgumentCaptor<ToolCall> cap = ArgumentCaptor.forClass(ToolCall.class);
        verify(chokepoint).execute(cap.capture());
        assertEquals(TOOL, cap.getValue().getName());
        assertEquals("p1", cap.getValue().getArguments().get("project_id"));
        assertTrue(cap.getValue().getId().startsWith("wa:run-1:"),
                "调用标识须可追溯到 run+step（审计对账）: " + cap.getValue().getId());
    }

    // ── 4 失败面：错误码映射（§5.3 出口单源）─────────────────────────────

    @Test
    @DisplayName("咽喉 DENY ⇒ E-POLICY；底座原文只入日志，errorText 用 W 域模板且不含原文")
    void guardrailDeniedMapsToPolicy() {
        activeContract(null, null);
        when(chokepoint.execute(any(ToolCall.class))).thenReturn(fail(AiErrorClass.GUARDRAIL_DENIED));

        StepInvokerPort.Outcome out = invoker.invoke(req(Map.of()));

        assertFalse(out.success());
        assertEquals(WAgentErrorCode.E_POLICY.wireCode(), out.errorCode());
        assertFalse(out.errorText().contains("connection refused"),
                "X-49：底座原文禁进对外文案: " + out.errorText());
        assertTrue(out.errorText().contains("trace-xyz"), "诊断码须可追: " + out.errorText());
        assertEquals(AiErrorClass.GUARDRAIL_DENIED.name(),
                out.metrics().get(EngineEndpointInvoker.METRIC_TOOL_ERROR_CLASS));
        assertEquals(AiErrorClass.GUARDRAIL_DENIED.errorCode(),
                out.metrics().get(EngineEndpointInvoker.METRIC_TOOL_ERROR_CODE));
    }

    @Test
    @DisplayName("STUB_CAPABILITY（501 能力未上线）⇒ 落不可重试 E-NOT-FOUND，禁洗成「稍后重试」的 E-WA-DEP")
    void stubCapabilityIsNotWashedIntoRetryableDep() {
        activeContract(null, null);
        when(chokepoint.execute(any(ToolCall.class))).thenReturn(fail(AiErrorClass.STUB_CAPABILITY));

        StepInvokerPort.Outcome out = invoker.invoke(req(Map.of()));

        assertEquals(WAgentErrorCode.E_NOT_FOUND.wireCode(), out.errorCode());
        assertFalse(WAgentErrorCodes.fromWireCode(out.errorCode()).clientRetryable(),
                "缺口类失败不得标可重试（X-50 禁伪成功同族）");
        assertThrows(IllegalArgumentException.class,
                () -> WAgentErrorCodes.fromWireCode(AiErrorClass.STUB_CAPABILITY.errorCode()),
                "底座码不得出现在 W 面——本断言防映射表被改成透传");
    }

    @Test
    @DisplayName("未分类/缺失分类 ⇒ E-WA-DEP（失败发生在咽喉内侧，属后端态，禁冒充 500 引擎缺陷）")
    void unclassifiedFailureMapsToDep() {
        activeContract(null, null);
        when(chokepoint.execute(any(ToolCall.class))).thenReturn(fail(null));

        StepInvokerPort.Outcome out = invoker.invoke(req(Map.of()));

        assertEquals(WAgentErrorCode.E_WA_DEP.wireCode(), out.errorCode());
        assertEquals("UNCLASSIFIED", out.metrics().get(EngineEndpointInvoker.METRIC_TOOL_ERROR_CLASS));
    }

    @Test
    @DisplayName("映射表全类覆盖：每个 AiErrorClass 都映到 §5.3 已注册码（编排器 fromWireCode 不得抛）")
    void mappingCoversWholeErrorMatrixAndTargetsAreRegistered() {
        for (AiErrorClass cls : AiErrorClass.values()) {
            WAgentErrorCode mapped = EngineEndpointInvoker.mapToWAgentCode(cls.name());
            assertNotNull(mapped, cls.name() + " 未登记映射（新增错误类必须补映射并补用例）");
            assertEquals(mapped, WAgentErrorCodes.fromWireCode(mapped.wireCode()),
                    cls.name() + " 映射目标不是 §5.3 注册码");
        }
        assertEquals(WAgentErrorCode.E_WA_DEP, EngineEndpointInvoker.mapToWAgentCode(null));
        assertEquals(WAgentErrorCode.E_WA_DEP, EngineEndpointInvoker.mapToWAgentCode("NOT_A_CLASS"));
        assertEquals(AiErrorClass.values().length, 12, "AiErrorClass 矩阵成员数变化 ⇒ 映射表须同批改");
    }

    // ── 5 输出契约校验（§4.2 设计有、此前代码无的一环）───────────────────

    @Test
    @DisplayName("输出缺契约必需字段 ⇒ E-VALIDATION 且已拿到的返回体留证不丢")
    void outputContractViolationKeepsEvidence() {
        activeContract(null, "{\"required\":[\"profit\",\"snapshot_ref\"]}");
        when(chokepoint.execute(any(ToolCall.class))).thenReturn(ok("{\"profit\":12.5}"));

        StepInvokerPort.Outcome out = invoker.invoke(req(Map.of("project_id", "p1")));

        assertFalse(out.success());
        assertEquals(WAgentErrorCode.E_VALIDATION.wireCode(), out.errorCode());
        assertTrue(out.errorText().contains("snapshot_ref"), out.errorText());
        assertEquals(12.5, ((Number) out.output().get("profit")).doubleValue(), 1e-9);
        assertEquals(EngineEndpointInvoker.CONTRACT_VIOLATED,
                out.metrics().get(EngineEndpointInvoker.METRIC_OUTPUT_CONTRACT));
    }

    @Test
    @DisplayName("返回体非 JSON（数组/标量/文本）⇒ 原样透传 raw_content，禁编造结构化字段；声明 required 即判违约")
    void nonObjectContentPassesThroughRaw() {
        activeContract(null, null);
        when(chokepoint.execute(any(ToolCall.class))).thenReturn(ok("利润 12.5 万元（纯文本）"));

        StepInvokerPort.Outcome plain = invoker.invoke(req(Map.of()));
        assertTrue(plain.success());
        assertEquals("利润 12.5 万元（纯文本）",
                plain.output().get(EngineEndpointInvoker.OUTPUT_RAW_KEY));
        assertEquals("not_json", plain.metrics().get("output_shape"));
        assertEquals(EngineEndpointInvoker.CONTRACT_NO_REQUIRED,
                plain.metrics().get(EngineEndpointInvoker.METRIC_OUTPUT_CONTRACT));

        activeContract(null, "{\"required\":[\"profit\"]}");
        when(chokepoint.execute(any(ToolCall.class))).thenReturn(ok("[1,2,3]"));
        StepInvokerPort.Outcome violated = invoker.invoke(req(Map.of()));
        assertFalse(violated.success());
        assertEquals("non_object", violated.metrics().get("output_shape"));
        assertEquals(WAgentErrorCode.E_VALIDATION.wireCode(), violated.errorCode());
        assertTrue(violated.output().containsKey(EngineEndpointInvoker.OUTPUT_RAW_KEY));
    }

    @Test
    @DisplayName("成功但正文为空 ⇒ 契约无从满足时如实失败；无 required 声明则记 content_present=false")
    void emptyContentIsNotFabricatedAsOutput() {
        activeContract(null, "{\"required\":[\"profit\"]}");
        when(chokepoint.execute(any(ToolCall.class))).thenReturn(ok(""));

        StepInvokerPort.Outcome out = invoker.invoke(req(Map.of()));

        assertFalse(out.success());
        assertEquals(WAgentErrorCode.E_VALIDATION.wireCode(), out.errorCode());
        assertEquals(false, out.metrics().get("content_present"));
    }

    @Test
    @DisplayName("咽喉抛异常 ⇒ 捕后必分类为 E-INTERNAL，禁异常穿透编排器")
    void chokepointExceptionBecomesClassifiedFailure() {
        activeContract(null, null);
        when(chokepoint.execute(any(ToolCall.class))).thenThrow(new IllegalStateException("炸了"));

        StepInvokerPort.Outcome out = invoker.invoke(req(Map.of()));

        assertEquals(WAgentErrorCode.E_INTERNAL.wireCode(), out.errorCode());
        assertEquals("INVOKER_EXCEPTION",
                out.metrics().get(EngineEndpointInvoker.METRIC_TOOL_ERROR_CLASS));
    }

    @Test
    @DisplayName("咽喉引用的裁决/引用块透传进 output（证据链不断，供 F10-24 结论校验复用）")
    void decisionAndCitationAreCarried() {
        activeContract(null, null);
        ToolExecutorService.ToolResult r = ok("{\"profit\":1}");
        r.setDecision("ALLOW");
        r.setCitationBlock("依据：project_profit@v3");
        when(chokepoint.execute(any(ToolCall.class))).thenReturn(r);

        StepInvokerPort.Outcome out = invoker.invoke(req(Map.of()));

        assertEquals("ALLOW", out.output().get("policy_decision"));
        assertEquals("依据：project_profit@v3", out.output().get("citation_block"));
    }
}
