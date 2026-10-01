package com.chinacreator.gzcm.runtime.core.audit.retry;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.chinacreator.gzcm.runtime.core.audit.retry.dao.AuditRetryDao;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 详细设计-00 F00-12 / C.5.4 — 审计兜底 SLA：Kafka 失败暂存 + 定时重放（指数退避 ≤3）+ 3 次失败转告警。
 * R2 收口后 SQL 全部下沉 {@link AuditRetryDao}；本测试用真实 DAO + Mockito.mock(JdbcTemplate)
 * （经 ReflectionTestUtils 注入 DAO 的 jdbcTemplate 字段）继续逐字节断言 SQL，不依赖 PG/Kafka。
 */
class AuditFallbackReplayTest {

    private static JdbcTemplate db() { return Mockito.mock(JdbcTemplate.class); }

    /** 真实 DAO + mock 的 jdbcTemplate（收口后 AuditRetryTask 只持有 DAO）。 */
    private static AuditRetryDao dao(JdbcTemplate j) {
        AuditRetryDao dao = new AuditRetryDao();
        ReflectionTestUtils.setField(dao, "jdbcTemplate", j);
        return dao;
    }

    private static Map<String, Object> row(String id, int attempts, String payload) {
        return java.util.Map.of("id", id, "eventType", "bos", "payload", payload, "attempts", attempts);
    }

    private static org.mockito.ArgumentMatcher<String> sql(String needle) {
        return s -> s != null && s.contains(needle);
    }

    @Test
    void enqueue_targetsEcosTable_attemptsStartZero() {
        JdbcTemplate j = db();
        AuditRetryTask t = new AuditRetryTask(dao(j), (topic, p) -> true, (s, m) -> {});
        String id = t.enqueue("bos", "{\"u\":1}", "T-1");
        ArgumentCaptor<String> sqlC = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsC = ArgumentCaptor.forClass(Object[].class);
        verify(j).update(sqlC.capture(), argsC.capture());
        assertTrue(sqlC.getValue().contains("ecos_runtime_audit_retry"), sqlC.getValue());
        assertEquals(id, argsC.getValue()[0]);
        assertEquals("bos", argsC.getValue()[1]);
        assertEquals("{\"u\":1}", argsC.getValue()[2]);
    }

    @Test
    void replayDue_replayerSuccess_closesRow() {
        JdbcTemplate j = db();
        when(j.queryForList(anyString(), eq(String.class), any())).thenReturn(List.of("idY"));
        when(j.queryForMap(anyString(), any())).thenReturn(row("idY", 0, "{\"ok\":1}"));
        when(j.update(anyString(), any(String.class))).thenReturn(1);

        AtomicBoolean replayed = new AtomicBoolean(false);
        AuditRetryTask t = new AuditRetryTask(dao(j),
                (topic, p) -> { replayed.set(true); return true; }, (s, m) -> {});
        t.replayDue();

        assertTrue(replayed.get());
        verify(j).update(argThat(sql("status = 'closed'")), any(String.class));
    }

    @Test
    void replayDue_replayerFailsAtFinalAttempt_markExhaustedAndFireCritical() {
        JdbcTemplate j = db();
        when(j.queryForList(anyString(), eq(String.class), any())).thenReturn(List.of("idX"));
        when(j.queryForMap(anyString(), any())).thenReturn(row("idX", 2, "{\"e\":1}"));
        // 失败路径：bumpAttempt（update 返回 0 → 视为无法继续）→ markExhausted + 告警
        when(j.update(anyString(), any(String.class))).thenReturn(0);

        CopyOnWriteArrayList<String> fired = new CopyOnWriteArrayList<>();
        AuditRetryTask t = new AuditRetryTask(dao(j), (topic, p) -> false, (s, m) -> fired.add(s));
        t.replayDue();

        assertTrue(fired.contains("critical"), "3 次失败应转 critical，got=" + fired);
        verify(j).update(argThat(sql("status = 'exhausted'")), any(String.class));
    }

    @Test
    void replayDue_fetchMissing_skipsRow() {
        JdbcTemplate j = db();
        when(j.queryForList(anyString(), eq(String.class), any())).thenReturn(List.of("gone"));
        when(j.queryForMap(anyString(), any())).thenThrow(new EmptyResultDataAccessException(1));
        when(j.update(anyString(), any(String.class))).thenReturn(1);

        AuditRetryTask t = new AuditRetryTask(dao(j), (topic, p) -> true, (s, m) -> {});
        t.replayDue();
        verify(j, never()).update(argThat(sql("status = 'closed'")), any(String.class));
    }

    @Test
    void constants_matchDesignSla() {
        assertEquals(60_000L, AuditRetryTask.INTERVAL_MS);
        assertEquals(3, AuditRetryTask.MAX_ATTEMPTS);
    }
}
