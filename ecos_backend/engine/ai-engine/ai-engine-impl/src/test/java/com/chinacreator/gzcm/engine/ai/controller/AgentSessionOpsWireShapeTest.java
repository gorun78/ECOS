package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.engine.ai.dao.AgentSessionDao;
import com.chinacreator.gzcm.engine.ai.dao.GuardrailDecisionLedgerDao;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.security.ToolSubjectResolver;
import com.chinacreator.gzcm.engine.ai.service.AgentSessionService;
import com.chinacreator.gzcm.engine.ai.service.SessionCompressionService;
import com.chinacreator.gzcm.engine.ai.service.SessionCompressionService.Outcome;
import com.chinacreator.gzcm.engine.ai.service.SessionCompressionService.Status;
import com.chinacreator.gzcm.engine.ai.service.SessionTokenQuotaService;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import com.chinacreator.gzcm.runtime.llm.metrics.AgentMetrics;
import com.chinacreator.gzcm.runtime.llm.model.AgentCallLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 会话运维链 wire 键面门（DI-2 #147DB）。
 *
 * <p>上下文 13 键、压缩回执 13 键（keyRefs 两键恒 null 仍在）、用量 9 键 + 桶 5 键、
 * 裁决台账 4 键 + 条目 7 键（{@code policyRef} 键名取契约常量）+ 过滤回显 5 键。</p>
 */
class AgentSessionOpsWireShapeTest {

    private AgentSessionService sessionService;
    private AgentSessionDao sessionDao;
    private SessionCompressionService compressionService;
    private SessionTokenQuotaService quotaService;
    private LLMGateway llmGateway;
    private AgentMetrics agentMetrics;
    private GuardrailDecisionLedgerDao ledgerDao;
    private ToolSubjectResolver subjectResolver;
    private AgentSessionOpsController controller;

    @BeforeEach
    void setUp() {
        sessionService = mock(AgentSessionService.class);
        sessionDao = mock(AgentSessionDao.class);
        compressionService = mock(SessionCompressionService.class);
        quotaService = mock(SessionTokenQuotaService.class);
        llmGateway = mock(LLMGateway.class);
        agentMetrics = mock(AgentMetrics.class);
        ledgerDao = mock(GuardrailDecisionLedgerDao.class);
        subjectResolver = mock(ToolSubjectResolver.class);
        controller = new AgentSessionOpsController(sessionService, sessionDao, compressionService,
                quotaService, llmGateway, agentMetrics, ledgerDao, subjectResolver);

        AgentSessionService.AgentSession session = new AgentSessionService.AgentSession();
        session.setId("s1");
        session.setAgentId("m1");
        when(sessionService.getSession("s1")).thenReturn(session);
    }

    private static List<String> keys(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static JsonNode json(Object payload) {
        return new ObjectMapper().valueToTree(payload);
    }

    @Test
    @DisplayName("#12 上下文：13 键，估算位与配额三数按分态保留键位")
    void contextShape() {
        when(sessionDao.tokenStats("s1")).thenReturn(new Object[]{100, 50, "auto"});
        when(sessionDao.governanceColumnsReady()).thenReturn(true);
        when(sessionDao.countCompressedMessages("s1")).thenReturn(2);
        when(compressionService.settings())
                .thenReturn(new SessionCompressionService.Settings(true, true, 0.8, 3));
        when(llmGateway.modelContextLimit("m1"))
                .thenReturn(new LLMGateway.ModelLimit("m1", 1000, true, true));
        when(quotaService.check("s1"))
                .thenReturn(new SessionTokenQuotaService.QuotaState(true, false, 10, 100, null));

        JsonNode data = json(controller.getSessionContext("s1").getBody().getData());

        assertEquals(List.of("sessionId", "estimatedTokens", "estimated", "promptTokens",
                "completionTokens", "limit", "threshold", "compressedTurns", "compressionMode",
                "quotaEnforced", "quotaRemaining", "quotaLimit", "quotaUsed"), keys(data));
        assertEquals(150, data.path("estimatedTokens").asInt());
        assertEquals(800, data.path("threshold").asInt());
        assertEquals(90, data.path("quotaRemaining").asInt());
    }

    @Test
    @DisplayName("#13 压缩回执：13 键，keyRefs 两键以 null 保留")
    void compressShape() {
        when(compressionService.compress(anyString(), anyString(), any(), any()))
                .thenReturn(new Outcome(Status.COMPRESSED, false, "auto", 100, 20, 80,
                        3, 1, 2, "b1", "tr1", null, 1000));

        JsonNode data = json(controller.compressSession("s1",
                new AgentSessionOpsController.CompressRequest(null, null)).getBody().getData());

        assertEquals(List.of("sessionId", "model", "status", "compressSkipped", "mode",
                "compressedTurns", "estimatedTokensBefore", "estimatedTokensAfter", "threshold",
                "batchId", "reason", "keptKeyRefs", "droppedKeyRefs"), keys(data));
        assertTrue(data.has("keptKeyRefs"));
        assertTrue(data.path("keptKeyRefs").isNull());
        assertEquals("m1", data.path("model").asText());
    }

    @Test
    @DisplayName("#15 用量：9 键 + 桶 5 键（estimated 恒 false、costRatio 恒 null）")
    void usageShape() {
        AgentCallLog call = new AgentCallLog();
        call.setProfileName("m1");
        call.setTokensInput(10);
        call.setTokensOutput(5);
        call.setCreatedTime(java.time.LocalDateTime.of(2026, 11, 5, 9, 0, 0));
        when(agentMetrics.getRecentCalls("ai-engine", 500)).thenReturn(List.of(call));

        JsonNode data = json(controller.getAgentUsage("2026-11-05", null, null).getBody().getData());

        assertEquals(List.of("subsystem", "period", "agentId", "modelKey", "itemCount",
                "items", "totals", "windowCallCount", "windowLimit"), keys(data));
        assertEquals(List.of("period", "promptTokens", "completionTokens", "estimated", "costRatio"),
                keys(data.path("items").get(0)));
        assertEquals(false, data.path("items").get(0).path("estimated").asBoolean());
        assertTrue(data.path("items").get(0).path("costRatio").isNull());
        assertEquals(10, data.path("totals").path("promptTokens").asInt());
    }

    @Test
    @DisplayName("#14 裁决台账：4 键 + 条目 7 键（policyRef 键名取契约常量）+ 过滤 5 键")
    void guardrailShape() {
        when(subjectResolver.resolve()).thenReturn(
                new ToolAdjudicationRequest.Subject("u1", List.of("AI_GUARDIAN"), null, "t1"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("trace_id", "tr1");
        row.put("session_id", "s1");
        row.put("tool_key", "query_db");
        row.put("decision", "ALLOW");
        row.put("policy_id", "pol-1");
        row.put("latency_ms", 12);
        row.put("create_time", Timestamp.valueOf("2026-11-05 09:00:00"));
        when(ledgerDao.listFiltered(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(row));

        JsonNode data = json(controller.listGuardrailDecisions("tr1", null, null, null, null, 100)
                .getBody().getData());

        assertEquals(List.of("itemCount", "items", "limit", "filters"), keys(data));
        assertEquals(List.of("traceId", "sessionId", "toolKey", "decision", "policyRef",
                "latencyMs", "createTime"), keys(data.path("items").get(0)));
        assertEquals("pol-1", data.path("items").get(0).path("policyRef").asText());
        assertEquals(List.of("traceId", "toolKey", "decision", "from", "to"),
                keys(data.path("filters")));
    }
}
