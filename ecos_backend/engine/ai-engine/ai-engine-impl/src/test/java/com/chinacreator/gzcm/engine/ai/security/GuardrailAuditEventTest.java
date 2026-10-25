package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.ai.dao.GuardrailDecisionLedgerDao;
import com.chinacreator.gzcm.engine.ai.dao.ToolDao;
import com.chinacreator.gzcm.engine.ai.dao.ToolPermissionBindingDao;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolDecision;
import com.chinacreator.gzcm.engine.ai.service.ToolExecutorService;
import com.chinacreator.gzcm.engine.ai.service.ToolRegistry;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 详细设计-06 F06-03 验收 —— {@code GUARDRAIL_EVAL} 审计事件接线
 * （铁律 §2.4-5：审计走 Kafka {@code ecos.audit}；§2.5-7：禁自建 Kafka 通道）。
 *
 * <p>doc 冻结的三个标识：</p>
 * <ul>
 *   <li>{@code GuardrailAuditEventTest#allowDenyAndFailClosedEachEmitOneEvent} ——
 *       三态各留痕恰一次（I-1"每次工具执行 ↔ 恰一条裁决"的对账面）；</li>
 *   <li>{@code GuardrailAuditEventTest#auditPublishFailureFailsClosed} ——
 *       桩 {@code EventBusService} 抛异常 ⇒ 裁决整体失败且<b>工具不执行</b>
 *       （不接受 X-20 的"尽力而为/warn 继续"）；</li>
 *   <li>{@code GuardrailAuditEventTest#eventCarriesNoRawPayload} —— detail 无 sql/content 键，
 *       事件 JSON 内不得出现 SQL 原文、入参原文、结果原文（X-58 教训：审计库不得变成新的敏感面）。</li>
 * </ul>
 *
 * <p>装置：真闸 + 真审计出口（{@link GuardrailAuditPublisher}），只 mock 事件通道与台账 DAO —
 * 事件形态本身就是被测对象。</p>
 */
class GuardrailAuditEventTest {

    private static final String TOOL = "query_db";
    private static final String RAW_SQL =
            "SELECT id, phone FROM ecos_dw.payroll WHERE ssn = 'FINANCE-SECRET-9527'";
    private static final String RAW_RESULT = "13800001111";

    /** 审计事件里禁止出现的载荷键（F06-03 要点 2）。 */
    private static final Set<String> FORBIDDEN_EVENT_KEYS = Set.of(
            "sql", "sqltext", "sql_text", "sqlraw", "arguments", "args", "params",
            "content", "body", "payload", "rows", "data", "resulttext", "result_text",
            "llmoutput", "llm_output", "prompt", "command", "apikey", "api_key",
            "token", "password", "secret", "rawreason");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AiSecurityEngineClient client;
    private AgentToolSqlWhitelist whitelist;
    private EventBusService eventBus;
    private GuardrailDecisionLedgerDao ledgerDao;
    private GuardrailAuditPublisher publisher;
    private AgentToolPolicyGate gate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        client = mock(AiSecurityEngineClient.class);
        whitelist = mock(AgentToolSqlWhitelist.class);
        eventBus = mock(EventBusService.class);
        ledgerDao = mock(GuardrailDecisionLedgerDao.class);

        ObjectProvider<EventBusService> busProvider = mock(ObjectProvider.class);
        when(busProvider.getIfAvailable()).thenReturn(eventBus);
        publisher = new GuardrailAuditPublisher(busProvider, ledgerDao);

        ObjectProvider<ToolPermissionBindingDao> bindings = mock(ObjectProvider.class);
        when(bindings.getIfAvailable()).thenReturn(null);
        gate = new AgentToolPolicyGate(whitelist, client, publisher, bindings);

        // #142/B4-3e-78（D-B4-30 收口）：主裁决腿已切 /decision，
        // 请求体构造走真静态 AiSecurityEngineClient.decisionBody，旧 evaluate 桩只保留备查意义、已删。
        // payroll 表放行（本地地板只验形态，裁决态由各用例的 decideData 桩决定）
        when(whitelist.allowedColumns("ecos_dw.payroll")).thenReturn(Set.of("*"));
    }

    // ── 验收 1：三态各恰一条事件 ────────────────────────────────────────────

    @Test
    @DisplayName("F06-03 ALLOW / DENY / FAIL_CLOSED 三态各发恰一条 GUARDRAIL_EVAL 事件")
    void allowDenyAndFailClosedEachEmitOneEvent() throws Exception {
        when(client.decideData(any())).thenAnswer(inv -> Map.of(
                "allow", true, "policyId", "p-allow",
                "obligations", List.of(Map.of("column", "phone", "rule", "phone"))));
        ToolDecision allow = gate.review(request(), RAW_SQL, List.of());
        assertEquals(ToolDecision.Verdict.ALLOW, allow.verdict());

        when(client.decideData(any())).thenAnswer(inv -> Map.of("allow", false, "policyId", "p-deny"));
        ToolDecision deny = gate.review(request(), RAW_SQL, List.of());
        assertEquals(ToolDecision.Verdict.DENY, deny.verdict());

        when(client.decideData(any())).thenThrow(new RuntimeException("security-engine 503"));
        ToolDecision failClosed = gate.review(request(), RAW_SQL, List.of());
        assertEquals(ToolDecision.Verdict.FAIL_CLOSED, failClosed.verdict());

        // 三态都留痕：3 次 decideData ↔ 3 条事件 ↔ 3 条台账（I-1 对账口径）
        verify(client, times(3)).decideData(any());
        List<Map<String, Object>> events = capturePublishes(3);
        assertEnvelope(events.get(0), ToolDecision.Verdict.ALLOW);
        assertEnvelope(events.get(1), ToolDecision.Verdict.DENY);
        assertEnvelope(events.get(2), ToolDecision.Verdict.FAIL_CLOSED);
        verify(ledgerDao, times(3)).insert(anyString(), anyString(), any(), anyString(),
                anyString(), any(), any(), anyInt(), anyString(), anyString());
    }

    // ── 验收 2：审计发布失败 → 裁决整体失败，工具不执行 ──────────────────────

    @Test
    @DisplayName("F06-03 事件通道抛异常 → 裁决转 FAIL_CLOSED 且工具不执行（红线，非尽力而为）")
    void auditPublishFailureFailsClosed() {
        doThrow(new IllegalStateException("kafka broker down"))
                .when(eventBus).publish(anyString(), any());

        ToolDecision decision = gate.review(request(), null, null);
        assertEquals(ToolDecision.Verdict.FAIL_CLOSED, decision.verdict(),
                "留痕失败必须翻转裁决，禁'拦了但没记录'");
        assertEquals(com.chinacreator.gzcm.common.error.AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                AgentToolPolicyGate.errorClassFor(decision));

        // 咽喉面：同一故障下工具一次都没执行（I-2）
        Fixture f = new Fixture();
        ToolExecutorService.ToolResult r = f.execute();
        assertFalse(r.isSuccess());
        assertEquals("GUARDRAIL_FAIL_CLOSED", r.getErrorClass(), r.getError());
        verify(f.registry, never()).execute(anyString(), any());
        verify(f.toolDao, never()).queryForList(anyString(), any());
    }

    @Test
    @DisplayName("F06-03/X-20 EventBusService Bean 缺失（通道未装配）同样 FAIL_CLOSED，不静默放行")
    @SuppressWarnings("unchecked")
    void missingAuditChannelIsFailClosed() throws Exception {
        ObjectProvider<EventBusService> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);
        AgentToolPolicyGate noBusGate = new AgentToolPolicyGate(whitelist, client,
                new GuardrailAuditPublisher(emptyProvider, ledgerDao), bindings());

        when(client.decideData(any())).thenAnswer(inv -> Map.of("allow", true, "policyId", "p-1"));

        ToolDecision decision = noBusGate.review(request(), null, null);
        assertEquals(ToolDecision.Verdict.FAIL_CLOSED, decision.verdict(),
                "无审计通道即无裁决记录，等价于'无裁决记录的工具调用'（§1.5-1）");
        verify(eventBus, never()).publish(anyString(), any());
    }

    // ── 验收 3：事件零原文 ──────────────────────────────────────────────────

    @Test
    @DisplayName("F06-03 事件只带摘要与 ID：无 sql/content/arguments 键，无 SQL/入参/结果原文")
    void eventCarriesNoRawPayload() throws Exception {
        when(client.decideData(any())).thenAnswer(inv -> Map.of(
                "allow", true, "policyId", "p-allow",
                "obligations", List.of(Map.of("column", "phone", "rule", "phone"))));
        gate.review(request(), RAW_SQL, List.of());
        Map<String, Object> event = captureSinglePublish();

        assertEquals(Set.of("eventId", "timestamp", "module", "userId", "tenantId",
                        "traceId", "eventType", "action", "resource", "result", "detail"),
                event.keySet(), "事件信封发生偏移: " + event.keySet());
        Map<?, ?> detail = (Map<?, ?>) event.get("detail");
        assertEquals(Set.of("tool", "decision", "policyId", "latencyMs", "obligations"),
                detail.keySet(), "detail 只准带摘要与 ID（F06-03 要点 2）: " + detail.keySet());

        Set<String> keys = new LinkedHashSet<>();
        collectKeys(event, keys);
        Set<String> leaked = new LinkedHashSet<>(keys);
        leaked.retainAll(FORBIDDEN_EVENT_KEYS);
        assertTrue(leaked.isEmpty(), "审计事件出现原文类键: " + leaked);

        String json = objectMapper.writeValueAsString(event).toLowerCase(java.util.Locale.ROOT);
        assertFalse(json.contains("select salary") || json.contains(RAW_SQL.toLowerCase(java.util.Locale.ROOT)),
                "SQL 原文进审计库（X-58：审计库不得成为新的敏感面）: " + json);
        assertFalse(json.contains("finance-secret-9527"), "字面量进审计库: " + json);
        assertFalse(json.contains(RAW_RESULT), "结果原文进审计库: " + json);
        assertFalse(json.contains("ignore previous"), "入参原文进审计库: " + json);
        assertFalse(json.contains("13800001111"), "待脱敏值进审计库: " + json);

        // 反向自证：obligations 摘要确实存在（否则"无原文"可能只是因为什么都没记）
        List<?> obligations = (List<?>) detail.get("obligations");
        assertEquals(1, obligations.size());
        Map<?, ?> item = (Map<?, ?>) obligations.get(0);
        assertEquals("MASK", item.get("type"));
        assertEquals("phone", item.get("column"));
        assertEquals("ALLOW", detail.get("decision"));
    }

    // ── 台账面的有意不对称（写失败不翻转裁决，但事件留痕已成立） ──────────────

    @Test
    @DisplayName("F06-03 台账写入失败不翻转 ALLOW（事件留痕是权威），但事件仍已发出")
    void ledgerWriteFailureDoesNotFlipDecision() throws Exception {
        doThrow(new RuntimeException("ledger table missing")).when(ledgerDao).insert(
                anyString(), anyString(), any(), anyString(), anyString(), any(), any(),
                anyInt(), anyString(), anyString());
        when(client.decideData(any())).thenAnswer(inv -> Map.of("allow", true, "policyId", "p-1"));

        ToolDecision decision = gate.review(request(), null, null);

        assertEquals(ToolDecision.Verdict.ALLOW, decision.verdict());
        verify(eventBus, times(1)).publish(eq(KafkaTopics.AUDIT), any());
    }

    // ── 小工具 ────────────────────────────────────────────────────────────

    private ToolAdjudicationRequest request() {
        return new ToolAdjudicationRequest(
                new ToolAdjudicationRequest.Subject("u-9", List.of("analyst"), "dept-a", "t-1"),
                new ToolAdjudicationRequest.Action(ToolAdjudicationRequest.ACTION_TYPE, TOOL, "read"),
                ToolAdjudicationRequest.projectResource(Map.of("scenarioId", "s-7")),
                new ToolAdjudicationRequest.Environment("trace-fixed",
                        ToolAdjudicationRequest.CHANNEL_AGENT, "2026-10-09T00:00:00Z"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureSinglePublish() {
        return capturePublishes(1).get(0);
    }

    /** 一次性捕获全部 AUDIT 发布（多次裁决共用同一个 bus mock，逐次 verify 会累计计数）。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> capturePublishes(int expected) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventBus, times(expected)).publish(eq(KafkaTopics.AUDIT), captor.capture());
        List<Map<String, Object>> events = new ArrayList<>();
        for (Object payload : captor.getAllValues()) {
            events.add((Map<String, Object>) payload);
        }
        return events;
    }

    private static void assertEnvelope(Map<String, Object> event, ToolDecision.Verdict verdict) {
        assertEquals(EventTypes.Guardrail.GUARDRAIL_EVAL, event.get("eventType"));
        assertEquals(verdict.name(), event.get("result"));
        assertEquals("ai-engine", event.get("module"));
        assertEquals("u-9", event.get("userId"));
        assertEquals("t-1", event.get("tenantId"));
        assertEquals("trace-fixed", event.get("traceId"));
        assertEquals("guardrail:eval:" + TOOL, event.get("action"));
        assertNotNull(event.get("eventId"));
        Map<?, ?> detail = (Map<?, ?>) event.get("detail");
        assertEquals(TOOL, detail.get("tool"));
        assertNotNull(detail.get("latencyMs"));
    }

    private static void collectKeys(Object node, Set<String> sink) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sink.add(String.valueOf(e.getKey()).toLowerCase(java.util.Locale.ROOT));
                collectKeys(e.getValue(), sink);
            }
        } else if (node instanceof List<?> list) {
            for (Object o : list) collectKeys(o, sink);
        }
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ToolPermissionBindingDao> bindings() {
        ObjectProvider<ToolPermissionBindingDao> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    /** 咽喉装置：真闸 + 故障通道，观测"审计失败时工具确实没执行"。 */
    private final class Fixture {
        final ToolRegistry registry = mock(ToolRegistry.class);
        final ToolDao toolDao = mock(ToolDao.class);
        final ToolExecutorService service = new ToolExecutorService();

        ToolExecutorService.ToolResult execute() {
            ToolSubjectResolver resolver = mock(ToolSubjectResolver.class);
            when(resolver.resolve()).thenReturn(new ToolAdjudicationRequest.Subject(
                    "u-9", List.of("analyst"), "dept-a", "t-1"));
            when(registry.has(TOOL)).thenReturn(true);
            when(registry.execute(eq(TOOL), any())).thenReturn(
                    ToolExecutorService.ToolResult.ok("c-1", TOOL, "{\"rows\":[]}", 1L));
            service.setToolDao(toolDao);
            service.setPolicyGate(gate);
            service.setSubjectResolver(resolver);
            ReflectionTestUtils.setField(service, "toolRegistry", registry);
            return service.execute(TOOL, Map.of("scenarioId", "s-7"));
        }
    }
}
