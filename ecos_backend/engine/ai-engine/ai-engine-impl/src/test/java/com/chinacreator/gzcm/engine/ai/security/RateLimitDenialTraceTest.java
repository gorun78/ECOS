package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.engine.ai.dao.GuardrailDecisionLedgerDao;
import com.chinacreator.gzcm.engine.ai.dao.ToolDao;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolDecision;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolObligations;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * 限流腿<b>拒绝态留痕</b>的验收（#142/B4-3e-1，差距 <b>D-B4-33</b> 的收口证明）。
 *
 * <p><b>D-B4-33 的原形态</b>：{@code rateLimitEnforce} 的三条拒绝分支只产出分类后的
 * {@code ToolResult} 与日志，事件与 V204 台账一条都不落 ⇒ 台账面上这次调用只剩闸侧的 ALLOW，
 * PRD-06 §1.5-1「无裁决记录的工具调用次数 = 0」的对账把被拒的调用读成"放行且执行成功"。
 * 因此本类每个用例都从 {@code ToolExecutorService.execute()} 入口驱动（不是直接调发布器）——
 * 只测 {@code auditExecutionLegDenial} 自己，等于把缺口改写成"有个咽喉不会调用的方法"。</p>
 *
 * <p><b>钉的三件事</b>（各自都能变红）：</p>
 * <ol>
 *   <li><b>两态不折叠</b>：配额用尽落 {@code DENY}、形态不合契约／消费方未装配落
 *       {@code FAIL_CLOSED}，且留痕失败（事件通道抛异常、发布器未装配）<b>不得</b>把
 *       {@code DENIED} 改成 {@code FAIL_CLOSED}（§4.7.2「不可用 ≠ 无权限」）；</li>
 *   <li><b>PASS 无痕</b>：放行与"未签发义务"都不补事件、不补台账行（闸侧已记 ALLOW，
 *       再补会把"每次裁决一条"的对账口径翻倍）；</li>
 *   <li><b>键面不外传</b>：限流键原文（{@code ai.tool.<subjectId>}／漂移键）不得出现在
 *       事件 JSON 或台账 {@code obligations} 里，只记码段与 {@code keyUsable}。</li>
 * </ol>
 *
 * <p><b>与闸侧的区分</b>：同一 {@code GUARDRAIL_EVAL} 事件类型下用
 * {@code action = guardrail:eval:rate_limit_enforce.<tool>} 的 stage 段区分两行，
 * 不新增事件类型词面（义务词面单源扩面属 D-B4-31，须先裁属主）。</p>
 *
 * <p>装置：真咽喉 + 真 {@link GuardrailAuditPublisher} + 真 {@link AiToolRateLimitGate}
 * （可控时钟），只 mock 事件通道、台账 DAO、裁决闸与注册表 —— 留痕形态本身是被测对象，
 * 用 mock 发布器就什么都没验。</p>
 */
class RateLimitDenialTraceTest {

    private static final String TOOL = "query_db";
    private static final String ROW_RESULT_JSON = "{\"rows\":[{\"id\":1}],\"count\":1}";
    private static final String RATE_KEY = GuardrailDecisionContract.RATE_LIMIT_KEY_PREFIX + "u-pj";
    private static final String DRIFTED_KEY = "tool.u-pj";

    private static final ToolAdjudicationRequest.Subject SUBJECT =
            new ToolAdjudicationRequest.Subject("u-pj", List.of("project_owner"), "d01", "t-demo");

    /** V204 DDL 单源路径（跨制品核对用；路径不入库到本机绝对路径，按仓根相对解析）。 */
    private static final String LEDGER_DDL_PATH =
            "docs/50-db_script/02-flyway-migration/gateway/V204__ai_guardrail_decision_ledger.sql";

    private static final Pattern CK_DECISION = Pattern.compile(
            "ck_agdl_decision\\s+CHECK\\s*\\(\\s*decision\\s+IN\\s*\\(([^)]*)\\)\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED = Pattern.compile("'([^']+)'");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EventBusService eventBus;
    private GuardrailDecisionLedgerDao ledgerDao;
    private GuardrailAuditPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        eventBus = mock(EventBusService.class);
        ledgerDao = mock(GuardrailDecisionLedgerDao.class);
        ObjectProvider<EventBusService> busProvider = mock(ObjectProvider.class);
        when(busProvider.getIfAvailable()).thenReturn(eventBus);
        publisher = new GuardrailAuditPublisher(busProvider, ledgerDao);
    }

    // ── 验收 1：拒绝态双写 ──────────────────────────────────────────────────

    @Test
    @DisplayName("D-B4-33 配额用尽 → 一条 GUARDRAIL_EVAL 事件 + 一行 V204 台账，词为 DENY")
    void deniedLegEmitsOneEventAndOneLedgerRow() throws Exception {
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        AiToolRateLimitGate rateGate = new AiToolRateLimitGate(now::get);
        Leg leg = new Leg(rate(RATE_KEY), rateGate, true);
        for (int i = 0; i < AiToolRateLimitGate.MAX_CALLS_PER_WINDOW; i++) {
            assertTrue(leg.call().isSuccess(), "第 " + (i + 1) + " 次应在额度内");
        }
        verify(eventBus, never()).publish(anyString(), any());

        ToolExecutorService.ToolResult denied = leg.call();

        assertEquals("GUARDRAIL_DENIED", denied.getErrorClass(), String.valueOf(denied.getError()));
        Map<String, Object> event = captureEvent(1);
        assertEquals(EventTypes.Guardrail.GUARDRAIL_EVAL, event.get("eventType"));
        assertEquals(ToolDecision.Verdict.DENY.name(), event.get("result"),
                "台账/事件词必须是 V204 允许词（直写本腿的 DENIED 会撞检查约束、留痕当场失效）");
        assertEquals("guardrail:eval:" + AiToolRateLimitGate.AUDIT_STAGE + ":" + TOOL, event.get("action"),
                "两行同类型的事件靠 action 的 stage 段区分，段缺失即无法与闸侧 ALLOW 行分离");
        Map<?, ?> detail = (Map<?, ?>) event.get("detail");
        assertEquals(Set.of("tool", "stage", "decision", "reasonCode", "policyId", "latencyMs", "obligations"),
                detail.keySet(), "本腿 detail 面发生偏移: " + detail.keySet());
        assertEquals(AiToolRateLimitGate.AUDIT_STAGE, detail.get("stage"));
        assertEquals(ToolDecision.Verdict.DENY.name(), detail.get("decision"));
        assertEquals(AiToolRateLimitGate.REASON_QUOTA_EXCEEDED, detail.get("reasonCode"));

        LedgerRow row = captureLedgerRow(1);
        assertEquals(ToolDecision.Verdict.DENY.name(), row.decision());
        assertEquals(TOOL, row.toolKey());
        assertEquals("p-rate", row.policyId(), "policyId 取裁决时点缓存，禁留空");
        assertEquals("u-pj", row.createBy());
        assertTrue(row.subjectSnapshot().contains("project_owner"),
                "主体快照缺 roles ⇒ 两行之间失去连接键: " + row.subjectSnapshot());
        assertCacheStage(row.obligations(), AiToolRateLimitGate.REASON_QUOTA_EXCEEDED);
        assertNoRawPayload(event, row.obligations(), RATE_KEY);
    }

    @Test
    @DisplayName("D-B4-33 键面漂移 → FAIL_CLOSED 行，且事件/台账内无键原文（只记码段与 keyUsable）")
    void offContractKeyTracesFailClosedWithoutEchoingTheKey() throws Exception {
        Leg leg = new Leg(rate(DRIFTED_KEY), new AiToolRateLimitGate(), true);

        ToolExecutorService.ToolResult rejected = leg.call();

        assertEquals("GUARDRAIL_FAIL_CLOSED", rejected.getErrorClass(), String.valueOf(rejected.getError()));
        Map<String, Object> event = captureEvent(1);
        assertEquals(ToolDecision.Verdict.FAIL_CLOSED.name(), event.get("result"));
        assertEquals(AiToolRateLimitGate.REASON_KEY_OFF_CONTRACT,
                ((Map<?, ?>) event.get("detail")).get("reasonCode"));
        LedgerRow row = captureLedgerRow(1);
        assertEquals(ToolDecision.Verdict.FAIL_CLOSED.name(), row.decision());
        assertCacheStage(row.obligations(), AiToolRateLimitGate.REASON_KEY_OFF_CONTRACT);
        // 码段可以记，键原文不行：明细段是 "码段: ai.tool.x"，整串入台账＝把主体标识搬进对账面
        assertNoRawPayload(event, row.obligations(), DRIFTED_KEY);
        assertNoRawPayload(event, row.obligations(),
                GuardrailDecisionContract.RATE_LIMIT_KEY_PREFIX);
    }

    @Test
    @DisplayName("D-B4-33 消费方未装配 → 同样落 FAIL_CLOSED 行（禁留痕缺席回到 D-B4-21 原形态）")
    void unwiredConsumerTracesFailClosedRow() throws Exception {
        Leg leg = new Leg(rate(RATE_KEY), null, true);

        ToolExecutorService.ToolResult rejected = leg.call();

        assertEquals("GUARDRAIL_FAIL_CLOSED", rejected.getErrorClass(), String.valueOf(rejected.getError()));
        assertEquals(AiToolRateLimitGate.REASON_GATE_UNWIRED,
                ((Map<?, ?>) captureEvent(1).get("detail")).get("reasonCode"));
        LedgerRow row = captureLedgerRow(1);
        assertEquals(ToolDecision.Verdict.FAIL_CLOSED.name(), row.decision());
        assertCacheStage(row.obligations(), AiToolRateLimitGate.REASON_GATE_UNWIRED);
    }

    // ── 验收 2：PASS 无痕（禁把对账口径从一条改成两条） ──────────────────────

    @Test
    @DisplayName("放行与「未签发义务」都不补事件、不补台账行（闸侧的 ALLOW 行已是对账口径）")
    void passingLegsLeaveNoTrace() {
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        AiToolRateLimitGate rateGate = new AiToolRateLimitGate(now::get);
        Leg withinQuota = new Leg(rate(RATE_KEY), rateGate, true);
        assertTrue(withinQuota.call().isSuccess());

        Leg noObligation = new Leg(ToolDecision.allow("p-none", ToolObligations.none(), 1L),
                new AiToolRateLimitGate(now::get), true);
        assertTrue(noObligation.call().isSuccess());

        verify(eventBus, never()).publish(anyString(), any());
        verify(ledgerDao, never()).insert(any(), any(), any(), any(), any(), any(), any(),
                anyInt(), any(), any());
    }

    // ── 验收 3：留痕失败不得改写拒绝态（与闸侧 auditOrThrow 的有意不对称） ────

    @Test
    @DisplayName("事件发布抛异常 → 拒绝分类保持 GUARDRAIL_DENIED，且台账同批不写（宁缺勿半条）")
    void publishFailureDoesNotFoldDeniedIntoFailClosed() {
        doThrow(new IllegalStateException("kafka broker down"))
                .when(eventBus).publish(anyString(), any());
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        AiToolRateLimitGate rateGate = new AiToolRateLimitGate(now::get);
        Leg leg = new Leg(rate(RATE_KEY), rateGate, true);
        for (int i = 0; i <= AiToolRateLimitGate.MAX_CALLS_PER_WINDOW; i++) {
            leg.call();
        }

        ToolExecutorService.ToolResult denied = leg.call();

        assertEquals("GUARDRAIL_DENIED", denied.getErrorClass(),
                "留痕失败把 DENIED 翻成 FAIL_CLOSED ＝ §4.7.2 两态在审计故障时折叠");
        assertEquals(AiErrorClass.GUARDRAIL_DENIED, AiErrorClass.valueOf(denied.getErrorClass()));
        verify(ledgerDao, never()).insert(any(), any(), any(), any(), any(), any(), any(),
                anyInt(), any(), any());
        verify(leg.registry, times(AiToolRateLimitGate.MAX_CALLS_PER_WINDOW)).execute(eq(TOOL), any());
    }

    @Test
    @DisplayName("审计出口未装配 → 拒绝结论与分类保持不变（缺行由 TRACE_FAILURE_MARKER 可观测）")
    void unwiredPublisherKeepsTheRejectionVerdict() {
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        AiToolRateLimitGate rateGate = new AiToolRateLimitGate(now::get);
        Leg leg = new Leg(rate(RATE_KEY), rateGate, false);
        for (int i = 0; i <= AiToolRateLimitGate.MAX_CALLS_PER_WINDOW; i++) {
            leg.call();
        }

        ToolExecutorService.ToolResult denied = leg.call();

        assertEquals("GUARDRAIL_DENIED", denied.getErrorClass(), String.valueOf(denied.getError()));
        verify(eventBus, never()).publish(anyString(), any());
        verify(ledgerDao, never()).insert(any(), any(), any(), any(), any(), any(), any(),
                anyInt(), any(), any());
    }

    @Test
    @DisplayName("台账写入失败 → 事件留痕已成立、拒绝分类不变（索引面缺行不翻转判定）")
    void ledgerWriteFailureKeepsEventAndVerdict() {
        doThrow(new RuntimeException("V204 table missing")).when(ledgerDao).insert(
                any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any());
        Leg leg = new Leg(rate(DRIFTED_KEY), new AiToolRateLimitGate(), true);

        ToolExecutorService.ToolResult rejected = leg.call();

        assertEquals("GUARDRAIL_FAIL_CLOSED", rejected.getErrorClass(), String.valueOf(rejected.getError()));
        assertEquals(ToolDecision.Verdict.FAIL_CLOSED.name(), captureEvent(1).get("result"));
    }

    // ── 验收 4：跨制品核对（词映射的正确性锚在 DDL 上，不锚在注释上） ──────────

    @Test
    @DisplayName("Verdict.ledgerDecision() 的取值必须落在 V204 ck_agdl_decision 允许词面内")
    void ledgerWordsStayInsideTheV204CheckConstraint() {
        String ddl = new String(readBytes(repoFile(LEDGER_DDL_PATH)), StandardCharsets.UTF_8);
        Matcher constraint = CK_DECISION.matcher(ddl);
        assertTrue(constraint.find(),
                "未解析到 V204 的 decision 检查约束 ⇒ 本腿不得默认通过（解析失败≠合规）");
        Set<String> allowed = new LinkedHashSet<>();
        Matcher quoted = QUOTED.matcher(constraint.group(1));
        while (quoted.find()) allowed.add(quoted.group(1));
        assertEquals(Set.of("ALLOW", "DENY", "FAIL_CLOSED"), allowed,
                "检查约束词面与门禁前提不符（改 DDL 须同批改映射）: " + allowed);
        assertFalse(allowed.contains("DENIED"),
                "约束里已含 DENIED ⇒ 本批折算的必要性前提失效，须重新裁决而不是继续沿用映射");

        for (AiToolRateLimitGate.Verdict verdict : AiToolRateLimitGate.Verdict.values()) {
            String word = verdict.ledgerDecision();
            if (verdict == AiToolRateLimitGate.Verdict.PASS) {
                assertNull(word, "PASS 必须返回 null（本腿不留痕），否则闸侧 ALLOW 行会被翻倍");
                continue;
            }
            assertNotNull(word, verdict + " 必须产出台账词，否则该拒绝态无痕（D-B4-33 未收口）");
            assertTrue(allowed.contains(word),
                    verdict + " 的台账词 " + word + " 不在 V204 允许词面内 ⇒ INSERT 当场撞检查约束、留痕归零");
        }
    }

    // ── 装置 ────────────────────────────────────────────────────────────────

    /** 恒定 ALLOW 且签发限流义务的裁决（义务键由入参决定，含漂移键场景）。 */
    private static ToolDecision rate(String rateLimitKey) {
        ToolObligations obligations = ToolObligations.fromEvaluateData(Map.of(
                GuardrailDecisionContract.KEY_OBLIGATIONS,
                List.of(Map.of(GuardrailDecisionContract.KEY_OBLIGATION,
                        GuardrailDecisionContract.OBLIGATION_RATE_LIMIT,
                        GuardrailDecisionContract.KEY_RATE_LIMIT_KEY, rateLimitKey))));
        return ToolDecision.allow("p-rate", obligations, 1L);
    }

    /** 真咽喉 + 真发布器 + 假裁决闸（恒 ALLOW）；dispatch 由假注册表承接。 */
    private final class Leg {
        final ToolExecutorService service = new ToolExecutorService();
        final ToolRegistry registry = mock(ToolRegistry.class);

        Leg(ToolDecision decision, AiToolRateLimitGate rateGate, boolean withPublisher) {
            ToolSubjectResolver resolver = mock(ToolSubjectResolver.class);
            when(resolver.resolve()).thenReturn(SUBJECT);
            AgentToolPolicyGate policyGate = mock(AgentToolPolicyGate.class);
            when(policyGate.review(any(), any(), any())).thenReturn(decision);
            when(registry.has(TOOL)).thenReturn(true);
            when(registry.execute(eq(TOOL), any())).thenReturn(
                    ToolExecutorService.ToolResult.ok("call-1", TOOL, ROW_RESULT_JSON, 1L));

            AiSecurityEngineClient securityStub = mock(AiSecurityEngineClient.class);
            service.setToolDao(mock(ToolDao.class));
            service.setPolicyGate(policyGate);
            service.setSubjectResolver(resolver);
            service.setObligationApplier(new ToolObligationApplier(
                    securityStub, new ObjectMapper(), new AiToolColumnSurfaceGate(securityStub)));
            service.setRateLimitGate(rateGate);
            if (withPublisher) service.setGuardrailAuditPublisher(publisher);
            ReflectionTestUtils.setField(service, "toolRegistry", registry);
        }

        ToolExecutorService.ToolResult call() {
            return service.execute(TOOL, Map.of("scenarioId", "s-1"));
        }
    }

    /** 台账 {@code obligations} 缓存列：必须读得出"哪条腿、哪个码"（V204 没有 stage/reason 列）。 */
    @SuppressWarnings("unchecked")
    private void assertCacheStage(String obligationsJson, String expectReasonCode) throws Exception {
        assertNotNull(obligationsJson, "obligations 缓存列为空 ⇒ 两行无法区分");
        Map<String, Object> cache = objectMapper.readValue(obligationsJson, Map.class);
        assertEquals(AiToolRateLimitGate.AUDIT_STAGE, cache.get("stage"));
        assertEquals(expectReasonCode, cache.get("reasonCode"));
        List<?> items = (List<?>) cache.get("obligations");
        assertEquals(1, items.size(), "义务摘要应恰好一条 rateLimit: " + cache);
        Map<?, ?> item = (Map<?, ?>) items.get(0);
        assertEquals("RATE_LIMIT", item.get("type"));
        assertTrue(Boolean.TRUE.equals(item.get("keyUsable")),
                "摘要须记「键可用」布尔而不是键原文（记键＝把主体标识搬进对账面）: " + item);
        assertFalse(item.toString().contains(GuardrailDecisionContract.RATE_LIMIT_KEY_PREFIX),
                "义务摘要出现限流键原文: " + item);
    }

    /** 留痕面零原文：事件 JSON 与台账缓存列都不许出现键原文（F06-03 要点 2 在本腿的延伸）。 */
    private void assertNoRawPayload(Map<String, Object> event, String obligationsJson, String forbidden)
            throws Exception {
        String json = (objectMapper.writeValueAsString(event) + "|" + obligationsJson)
                .toLowerCase(Locale.ROOT);
        assertFalse(json.contains(forbidden.toLowerCase(Locale.ROOT)),
                "限流键原文进了留痕面（键里带主体 ID，审计库不得成为新的敏感面）: " + forbidden);
    }

    private Map<String, Object> captureEvent(int expected) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventBus, times(expected)).publish(eq(KafkaTopics.AUDIT), captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) captor.getAllValues().get(expected - 1);
        return event;
    }

    private LedgerRow captureLedgerRow(int expected) {
        ArgumentCaptor<String> traceId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sessionId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> toolKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> decision = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> policyId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> obligations = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> latencyMs = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> createBy = ArgumentCaptor.forClass(String.class);
        verify(ledgerDao, times(expected)).insert(anyString(), traceId.capture(), sessionId.capture(),
                toolKey.capture(), decision.capture(), policyId.capture(), obligations.capture(),
                latencyMs.capture(), subject.capture(), createBy.capture());
        int last = expected - 1;
        assertNotNull(traceId.getAllValues().get(last), "trace_id 不得为空（对账按 traceId 聚合）");
        assertTrue(latencyMs.getAllValues().get(last) >= 0, "latency_ms 不得为负");
        return new LedgerRow(traceId.getAllValues().get(last), sessionId.getAllValues().get(last),
                toolKey.getAllValues().get(last), decision.getAllValues().get(last),
                policyId.getAllValues().get(last), obligations.getAllValues().get(last),
                latencyMs.getAllValues().get(last), subject.getAllValues().get(last),
                createBy.getAllValues().get(last));
    }

    private record LedgerRow(String traceId, String sessionId, String toolKey, String decision,
                             String policyId, String obligations, Integer latencyMs,
                             String subjectSnapshot, String createBy) {
    }

    private static Path repoRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 12 && start != null; i++) {
            if (Files.isDirectory(start.resolve("ecos_backend"))
                    && Files.isDirectory(start.resolve("docs"))) {
                return start;
            }
            start = start.getParent();
        }
        throw new AssertionError("未能从 user.dir 定位仓根（需同时含 ecos_backend 与 docs），本验收不得默认通过");
    }

    private static Path repoFile(String relative) {
        Path p = repoRoot().resolve(relative);
        if (!Files.isRegularFile(p)) {
            throw new AssertionError("验收依赖的文件不存在，不得当作\"无违例\": " + relative);
        }
        return p;
    }

    private static byte[] readBytes(Path p) {
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new AssertionError("读取文件失败: " + p, e);
        }
    }
}
