package com.chinacreator.gzcm.runtime.eventbus.dlq;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.chinacreator.gzcm.runtime.eventbus.dlq.dao.EcosRuntimeEventDlqDao;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 详细设计-00 C.5.2 / W13 — DLQ 单一消费者：持久化 + lag 告警 + 重放标记。
 * R2 收口后 SQL 全部下沉 {@link EcosRuntimeEventDlqDao}；本测试用真实 DAO +
 * Mockito.mock(JdbcTemplate)（经 ReflectionTestUtils 注入 DAO 的 jdbcTemplate 字段）
 * 继续逐字节断言 SQL，不连 broker / DB。
 */
class DltConsumerTest {

    private static JdbcTemplate jdbc() {
        return Mockito.mock(JdbcTemplate.class);
    }

    /** 真实 DAO + mock 的 jdbcTemplate（收口后 DltConsumer 只持有 DAO）。 */
    private static EcosRuntimeEventDlqDao dao(JdbcTemplate db) {
        EcosRuntimeEventDlqDao dao = new EcosRuntimeEventDlqDao();
        ReflectionTestUtils.setField(dao, "jdbcTemplate", db);
        return dao;
    }

    @Test
    void consume_persistsToEcosEventDlq_stripsBaseTopic() {
        JdbcTemplate db = jdbc();
        DltConsumer c = new DltConsumer(dao(db));
        String id = c.consume("ecos.audit.DLT", "{\"op\":\"grant\"}", new RuntimeException("denied"));
        assertNotNull(id);

        ArgumentCaptor<String> sqlC = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsC = ArgumentCaptor.forClass(Object[].class);
        verify(db).update(sqlC.capture(), argsC.capture());
        assertTrue(sqlC.getValue().contains("ecos_runtime_event_dlq"), sqlC.getValue());
        assertEquals("ecos.audit", argsC.getValue()[1]);
        assertEquals("ecos.audit.DLT", argsC.getValue()[2]);
        assertEquals("{\"op\":\"grant\"}", argsC.getValue()[3]);
        assertNotNull(argsC.getValue()[4]);
    }

    @Test
    void reportLag_belowOrEqualThreshold_doesNotAlert() {
        List<String> fired = new CopyOnWriteArrayList<>();
        DltConsumer c = new DltConsumer(dao(jdbc()), (sev, msg) -> fired.add(msg));
        c.reportLag("t", 10);
        c.reportLag("t", DltConsumer.LAG_THRESHOLD);
        assertTrue(fired.isEmpty(), "lag <= 阈值 不告警（严格 >）");
    }

    @Test
    void baseTopic_stripsDltSuffix() {
        assertEquals("ecos.audit", DltConsumer.baseTopic("ecos.audit.DLT"));
        assertEquals("x", DltConsumer.baseTopic("x"));
    }

    @Test
    void dlqCount_readsFromEcosTable() {
        JdbcTemplate db = jdbc();
        when(db.queryForObject(anyString(), any(Class.class))).thenReturn(4L);
        assertEquals(4L, new DltConsumer(dao(db)).dlqCount());
        ArgumentCaptor<String> sqlC = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Class<?>> classC = ArgumentCaptor.forClass(Class.class);
        verify(db).queryForObject(sqlC.capture(), classC.capture());
        assertTrue(sqlC.getValue().contains("COUNT(*)"));
        assertTrue(sqlC.getValue().contains("ecos_runtime_event_dlq"));
    }

    @Test
    void markReplayed_updatesStatusAndTime() {
        JdbcTemplate db = jdbc();
        when(db.update(anyString(), any(String.class))).thenReturn(1);
        DltConsumer c = new DltConsumer(dao(db), (s, m) -> {});
        assertTrue(c.markReplayed("A1"));
        ArgumentCaptor<String> sqlC = ArgumentCaptor.forClass(String.class);
        verify(db).update(sqlC.capture(), eq("A1"));
        assertTrue(sqlC.getValue().contains("status = 'replayed'"));
        assertTrue(sqlC.getValue().contains("replayed_at = NOW()"));
    }
}
