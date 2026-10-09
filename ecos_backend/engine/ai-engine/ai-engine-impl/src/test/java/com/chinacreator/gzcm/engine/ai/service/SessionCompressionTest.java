package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.ai.dao.AgentSessionDao;
import com.chinacreator.gzcm.engine.ai.dao.CompressionBatchDao;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 详细设计-06 F06-09 验收（文档冻结的可执行形态）：
 *
 * <ol>
 *   <li>{@code triggerIsTokenBasedNotCountBased} —— 触发只看 token，不看条数（X-29）；</li>
 *   <li>{@code summaryKeepsAllForecastRunIdsFromCompressedRange} —— 引用完整性硬校验放行面（要点 3）；</li>
 *   <li>{@code referencedToolCallResultsAreNotCompressed} —— 被引用的工具结果原文保留（要点 4 / X-33）；</li>
 *   <li>{@code compressDoesNotIssuePhysicalDelete} —— 归档只 UPDATE，无 DELETE（要点 6 / X-31）；</li>
 *   <li>{@code summaryFailureFallsBackToTruncateWithMarker} —— 摘要失败降级打标 + 事件上报（要点 5 / X-35）。</li>
 * </ol>
 *
 * <p>纯 mock 装配：不连 PG，不依赖 V202/V203 是否已对库执行（迁移面见 §14.4）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("F06-09 会话压缩按 PRD 语义重写")
class SessionCompressionTest {

    private static final String SESSION = "sess-0001";
    private static final String MODEL = "deepseek/deepseek-chat";
    private static final String TRACE = "TRACE-F0609";

    /** 目录上限 1000 × 阈值 0.6 ⇒ 触发线 600 token */
    private static final int CONTEXT_LIMIT = 1000;
    private static final int THRESHOLD_TOKENS = 600;

    @Mock
    private AgentSessionDao sessionDao;
    @Mock
    private CompressionBatchDao batchDao;
    @Mock
    private LLMGateway llmGateway;
    @Mock
    private SysConfigService sysConfig;
    @Mock
    private EventBusService eventBus;

    // ═══════════════════ 装配辅助 ═══════════════════

    private SessionCompressionService wiredService(int keepRecentRounds) {
        when(sysConfig.getBoolean(SessionCompressionService.KEY_ENABLED, true)).thenReturn(true);
        when(sysConfig.getString(SessionCompressionService.KEY_THRESHOLD, null)).thenReturn("0.6");
        when(sysConfig.getInt(eq(SessionCompressionService.KEY_KEEP_RECENT), anyInt()))
                .thenReturn(keepRecentRounds);
        when(llmGateway.modelContextLimit(MODEL))
                .thenReturn(new LLMGateway.ModelLimit(MODEL, CONTEXT_LIMIT, true, true));
        when(sessionDao.governanceColumnsReady()).thenReturn(true);
        when(batchDao.tableReady()).thenReturn(true);
        return new SessionCompressionService(sessionDao, batchDao, llmGateway,
                providerOf(sysConfig), providerOf(eventBus));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T instance) {
        ObjectProvider<T> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(instance);
        return provider;
    }

    /** 一条消息：id 自增，content 为 n 个中文字（估算 ≈ 1.5n token）。 */
    private static AgentSessionService.AgentMessage msg(long id, String role, int cnChars) {
        AgentSessionService.AgentMessage m = new AgentSessionService.AgentMessage();
        m.setId(id);
        m.setSessionId(SESSION);
        m.setRole(role);
        m.setThreadId("main");
        m.setContent("哈".repeat(cnChars));
        return m;
    }

    /** 8 轮 × 2 条、每条 150 中文字 ⇒ 总估算 ≈ 3664 token（远超 600），条数 16 也 > 旧阈值 20 的一半。 */
    private static List<AgentSessionService.AgentMessage> longHeavySession() {
        List<AgentSessionService.AgentMessage> list = new ArrayList<>();
        long id = 1;
        for (int round = 0; round < 8; round++) {
            list.add(msg(id++, "user", 150));
            list.add(msg(id++, "assistant", 150));
        }
        return list;
    }

    // ═══════════════════ 1. 触发口径 ═══════════════════

    @Test
    @DisplayName("要点1/X-29: 触发只看 token —— 4 条长文触发、30 条短文不触发")
    void triggerIsTokenBasedNotCountBased() {
        SessionCompressionService service = wiredService(6);

        // (a) 16 条但 token 超阈值 ⇒ 触发归档
        when(sessionDao.findActiveMessages(SESSION, "main")).thenReturn(longHeavySession());
        when(sessionDao.archiveMessagesByIds(eq(SESSION), anyList(), anyString()))
                .thenAnswer(inv -> ((List<?>) inv.getArgument(1)).size());
        when(llmGateway.call(any())).thenReturn(ChatResponse.ok(summaryJson(
                "{\"type\":\"FORECAST_RUN\",\"id\":\"FR-IGNORE01\"}"), 10, 5, MODEL));

        SessionCompressionService.Outcome heavy = service.compress(SESSION, MODEL, TRACE);
        assertEquals(SessionCompressionService.Status.COMPRESSED, heavy.status());
        assertTrue(heavy.estimatedTokensBefore() > THRESHOLD_TOKENS,
                "before 应为 token 估算值，实际=" + heavy.estimatedTokensBefore());
        assertEquals(THRESHOLD_TOKENS, heavy.thresholdTokens());
        verify(sessionDao).archiveMessagesByIds(eq(SESSION), anyList(), anyString());

        // (b) 30 条短文（旧"条数 >20"会误触发）⇒ token 未过线，必须不触发
        List<AgentSessionService.AgentMessage> many = new ArrayList<>();
        long id = 100;
        for (int i = 0; i < 30; i++) {
            many.add(msg(id++, i % 2 == 0 ? "user" : "assistant", 4));
        }
        when(sessionDao.findActiveMessages(SESSION, "main")).thenReturn(many);

        SessionCompressionService.Outcome light = service.compress(SESSION, MODEL, TRACE);
        assertEquals(SessionCompressionService.Status.BELOW_THRESHOLD, light.status());
        assertTrue(light.estimatedTokensBefore() <= THRESHOLD_TOKENS);
        assertFalse(light.compressSkipped());
        // 上一子案例已归档过 ⇒ 计数比对，本轮不得新增归档调用
        assertEquals(1, Mockito.mockingDetails(sessionDao).getInvocations().stream()
                        .filter(inv -> "archiveMessagesByIds".equals(inv.getMethod().getName()))
                        .count(),
                "X-29：条数多但 token 未过线时不得触发归档");

        // (c) 条数阈值载体必须已消灭（禁旧语义复活）
        for (Method m : AgentSessionDao.class.getDeclaredMethods()) {
            assertNotEquals("countMessages", m.getName(),
                    "X-29 的 SELECT COUNT(*) 触发载体必须从 DAO 移除");
            assertNotEquals("findOldestMessages", m.getName(),
                    "取前 N 条的旧压缩读取面必须移除");
        }
    }

    // ═══════════════════ 2. 引用完整性放行面 ═══════════════════

    @Test
    @DisplayName("要点3: 被压缩段的每个 forecastRunId 都必须进摘要 keyRefs")
    void summaryKeepsAllForecastRunIdsFromCompressedRange() {
        SessionCompressionService service = wiredService(6);
        List<AgentSessionService.AgentMessage> active = longHeavySession();
        // 前 2 轮（压缩段）各埋一枚引用；保留段不含引用
        active.get(1).setContent("口径已定 forecastRunId=FR-AAAA11 " + active.get(1).getContent());
        active.get(3).setContent("{\"forecastRunId\": \"FR-BBBB22\"} " + active.get(3).getContent());
        when(sessionDao.findActiveMessages(SESSION, "main")).thenReturn(active);
        when(sessionDao.archiveMessagesByIds(eq(SESSION), anyList(), anyString()))
                .thenAnswer(inv -> ((List<?>) inv.getArgument(1)).size());
        when(llmGateway.call(any())).thenReturn(ChatResponse.ok(
                summaryJson("{\"type\":\"FORECAST_RUN\",\"id\":\"FR-AAAA11\"}",
                        "{\"type\":\"FORECAST_RUN\",\"id\":\"FR-BBBB22\"}"), 20, 8, MODEL));

        SessionCompressionService.Outcome outcome = service.compress(SESSION, MODEL, TRACE);

        assertEquals(SessionCompressionService.Status.COMPRESSED, outcome.status());
        assertEquals(SessionCompressionService.MODE_LLM_SUMMARY, outcome.compressionMode());
        ArgumentCaptor<String> contentCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> summaryRefsCap = ArgumentCaptor.forClass(String.class);
        verify(sessionDao).insertSummaryMessage(eq(SESSION), contentCap.capture(),
                summaryRefsCap.capture(), anyString(), anyInt(), anyLong());
        assertTrue(summaryRefsCap.getValue().contains("FR-AAAA11"),
                "keyRefs=" + summaryRefsCap.getValue());
        assertTrue(summaryRefsCap.getValue().contains("FR-BBBB22"),
                "keyRefs=" + summaryRefsCap.getValue());
        assertTrue(contentCap.getValue().contains("FR-AAAA11")
                        && contentCap.getValue().contains("FR-BBBB22"),
                "摘要正文（TEXT 列）必须原文并入引用，实际=" + contentCap.getValue());
        ArgumentCaptor<String> batchRefsCap = ArgumentCaptor.forClass(String.class);
        verify(batchDao).insertBatch(anyString(), eq(SESSION),
                eq(SessionCompressionService.MODE_LLM_SUMMARY), any(), any(),
                any(), any(), batchRefsCap.capture(), isNull(), isNull(), eq(TRACE), anyString());
        assertTrue(batchRefsCap.getValue().contains("FR-AAAA11")
                        && batchRefsCap.getValue().contains("FR-BBBB22"),
                "批次台账 kept_key_refs 必须同源登记，实际=" + batchRefsCap.getValue());
    }

    // ═══════════════════ 3. 工具引用保护 ═══════════════════

    @Test
    @DisplayName("要点4/X-33: 被保留段引用的 tool_call 结果原文保留，不进归档")
    void referencedToolCallResultsAreNotCompressed() {
        SessionCompressionService service = wiredService(6);
        List<AgentSessionService.AgentMessage> active = longHeavySession();

        // id=2 的 assistant 行声明 tool_call_id=call_shared01（落在压缩段）
        AgentSessionService.AgentMessage declaring = active.get(1);
        declaring.setToolCalls(List.of(Map.<String, Object>of("id", "call_shared01",
                "name", "forecast.query")));
        // 保留段某行引用同一 tool_call_id
        active.get(14).setContent("沿用工具结果 tool_call_id=call_shared01 继续分析 "
                + active.get(14).getContent());

        when(sessionDao.findActiveMessages(SESSION, "main")).thenReturn(active);
        when(sessionDao.archiveMessagesByIds(eq(SESSION), anyList(), anyString()))
                .thenAnswer(inv -> ((List<?>) inv.getArgument(1)).size());
        when(llmGateway.call(any())).thenReturn(ChatResponse.ok(
                summaryJson("{\"type\":\"TOOL_CALL\",\"id\":\"call_shared01\"}"), 5, 5, MODEL));

        SessionCompressionService.Outcome outcome = service.compress(SESSION, MODEL, TRACE);

        assertEquals(SessionCompressionService.Status.COMPRESSED, outcome.status());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> idsCap = ArgumentCaptor.forClass(List.class);
        verify(sessionDao).archiveMessagesByIds(eq(SESSION), idsCap.capture(), anyString());
        assertFalse(idsCap.getValue().contains(2L),
                "被引用的工具结果行不得归档，实际归档 id=" + idsCap.getValue());
        assertTrue(outcome.protectedCount() >= 1, "引用保护必须可观测");
    }

    // ═══════════════════ 4. 禁物理删除 ═══════════════════

    @Test
    @DisplayName("要点6/X-31: 归档语句只有 UPDATE 置标记，SQL 语句集合内无 DELETE")
    void compressDoesNotIssuePhysicalDelete() throws Exception {
        // (a) DAO 不再声明任何 delete* 方法（旧 deleteMessagesUpTo 载体已移除）
        for (Method m : AgentSessionDao.class.getDeclaredMethods()) {
            assertFalse(m.getName().toLowerCase().startsWith("delete"),
                    "AgentSessionDao 不得保留物理删除方法：" + m.getName());
        }

        // (b) 归档实际下发的 SQL = UPDATE + compressed/is_deleted/compression_batch_id
        AgentSessionDao dao = new AgentSessionDao();
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        Field field = JdbcTemplateDaoSupport.class.getDeclaredField("jdbcTemplate");
        field.setAccessible(true);
        field.set(dao, jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(2);

        int archived = dao.archiveMessagesByIds(SESSION, List.of(11L, 12L), "batch-1");

        assertEquals(2, archived);
        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sqlCap.capture(), any(Object[].class));
        String sql = sqlCap.getValue().toUpperCase();
        // 词边界判定：列名 IS_DELETED 含 DELETE 字串，但不是删除语句
        assertFalse(java.util.regex.Pattern.compile("(?<!\\w)DELETE(?!\\w)").matcher(sql).find(),
                "归档 SQL 不得含 DELETE 语句，实际=" + sql);
        assertTrue(sql.startsWith("UPDATE SYS_AGENT_MESSAGE"), "实际=" + sql);
        assertTrue(sql.contains("COMPRESSED = 1") && sql.contains("IS_DELETED = 1")
                && sql.contains("COMPRESSION_BATCH_ID"), "必须置三枚标记，实际=" + sql);
    }

    // ═══════════════════ 5. 失败降级打标 + 上报 ═══════════════════

    @Test
    @DisplayName("要点5/X-35: 摘要丢引用/不可解析 ⇒ TRUNCATE_FALLBACK 打标 + 事件上报 + 不插摘要行")
    void summaryFailureFallsBackToTruncateWithMarker() {
        SessionCompressionService service = wiredService(6);
        List<AgentSessionService.AgentMessage> active = longHeavySession();
        active.get(1).setContent("forecastRunId=FR-AAAA11 " + active.get(1).getContent());
        active.get(3).setContent("forecastRunId=FR-BBBB22 " + active.get(3).getContent());
        when(sessionDao.findActiveMessages(SESSION, "main")).thenReturn(active);
        when(sessionDao.archiveMessagesByIds(eq(SESSION), anyList(), anyString()))
                .thenAnswer(inv -> ((List<?>) inv.getArgument(1)).size());
        // 摘要只回一枚 runId ⇒ 引用完整性硬校验失败
        when(llmGateway.call(any())).thenReturn(ChatResponse.ok(
                summaryJson("{\"type\":\"FORECAST_RUN\",\"id\":\"FR-AAAA11\"}"), 5, 5, MODEL));

        SessionCompressionService.Outcome outcome = service.compress(SESSION, MODEL, TRACE);

        assertEquals(SessionCompressionService.Status.TRUNCATE_FALLBACK, outcome.status());
        assertEquals(SessionCompressionService.MODE_TRUNCATE_FALLBACK, outcome.compressionMode());
        assertEquals("reference_check_failed", outcome.reason());
        assertFalse(outcome.compressSkipped());
        verify(sessionDao, never()).insertSummaryMessage(anyString(), anyString(), anyString(),
                anyString(), anyInt(), anyLong());
        verify(sessionDao).updateSessionCompressionMode(SESSION,
                SessionCompressionService.MODE_TRUNCATE_FALLBACK);
        verify(batchDao).insertBatch(anyString(), eq(SESSION),
                eq(SessionCompressionService.MODE_TRUNCATE_FALLBACK), any(), any(),
                any(), any(), anyString(), eq("FR-BBBB22"), eq("reference_check_failed"),
                eq(TRACE), anyString());

        ArgumentCaptor<Object> eventCap = ArgumentCaptor.forClass(Object.class);
        verify(eventBus).publish(eq(KafkaTopics.AUDIT), eventCap.capture());
        Map<?, ?> event = (Map<?, ?>) eventCap.getValue();
        assertEquals("SESSION_COMPRESSION_FALLBACK", event.get("eventType"));
        assertEquals(SessionCompressionService.MODE_TRUNCATE_FALLBACK, event.get("compressionMode"));
        assertEquals(TRACE, event.get("traceId"));

        // 摘要调用直接失败（provider 不可用）同样降级打标
        when(llmGateway.call(any())).thenReturn(ChatResponse.fail("upstream 503"));
        SessionCompressionService.Outcome second = service.compress(SESSION, MODEL, TRACE);
        assertEquals(SessionCompressionService.Status.TRUNCATE_FALLBACK, second.status());
        assertEquals("llm_call_failed", second.reason());
    }

    // ═══════════════════ 摘要 JSON 组装 ═══════════════════

    private static String summaryJson(String... keyRefs) {
        StringBuilder sb = new StringBuilder();
        sb.append("```json\n{\"confirmedFacts\":[\"已确认Q3毛利率口径\"],")
                .append("\"userPreferences\":[\"偏好表格\"],")
                .append("\"openQuestions\":[\"是否复算\"],")
                .append("\"keyRefs\":[");
        for (int i = 0; i < keyRefs.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(keyRefs[i]);
        }
        sb.append("]}\n```");
        return sb.toString();
    }
}
