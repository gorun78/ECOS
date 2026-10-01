package com.chinacreator.gzcm.runtime.core.alert.persist;

import com.chinacreator.gzcm.runtime.core.alert.AlertRecord;
import com.chinacreator.gzcm.runtime.core.alert.INotificationService;
import com.chinacreator.gzcm.runtime.core.alert.dao.AlertDao;
import com.chinacreator.gzcm.runtime.core.alert.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 详细设计-00 F00-09 / W07 — 告警持久化 + 通知通道（控制域 ecos_runtime_alert_*）。
 *
 * R2-B4c 收口：raw-SQL 已下沉 {@link AlertDao}（..alert.dao..，继承 runtime-core
 * {@code JdbcTemplateDaoSupport}，SQL 逐字节不变）。本测试改以 {@code JdbcTemplate}
 * mock 经 {@link ReflectionTestUtils} 注入 DAO 的 {@code jdbcTemplate} 通道，
 * 不依赖 PG/H2（本地无 H2 驱动）。验收：
 * <ul>
 *   <li>insertRecord 落控制域表、全 slot 参数化（列数 12）</li>
 *   <li>ack/close 状态机 SQL 命中正确表与状态迁移</li>
 *   <li>无 webhook 目标时 webhook 通道降级为 log（返回 success 而非 fail）</li>
 * </ul>
 */
class AlertPersistenceTest {

    private JdbcTemplate jdbc;
    private AlertDao repo;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        repo = new AlertDao();
        ReflectionTestUtils.setField(repo, "jdbcTemplate", jdbc);
    }

    @SuppressWarnings("unchecked")
    @Test
    void insertRecord_targetsEcosAlertRecord_withParamSlots() {
        String id = repo.insertRecord("MQ-ROGUE-001", "P0", "Kafka DLQ 增量告警",
                Map.of("lag", 101, "topic", "ecos.audit.DLT"),
                "runtime-monitor", "T1", "1");
        assertNotNull(id);

        ArgumentCaptor<String> sqlC = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsC = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sqlC.capture(), argsC.capture());

        assertTrue(sqlC.getValue().contains("ecos_runtime_alert_record"), sqlC.getValue());
        assertTrue(sqlC.getValue().contains("detail_json"), sqlC.getValue());
        // 9 个 slot 参数（status=NOW()/is_deleted/domain 为常量不入 args）
        assertEquals(9, argsC.getValue().length);
        assertEquals(id, argsC.getValue()[0]);
        assertEquals("MQ-ROGUE-001", argsC.getValue()[1]);
    }

    @Test
    void ackRecord_setsackedStatusAndAckBy_withNoteJsonMerged() {
        when(jdbc.queryForObject(
                org.mockito.ArgumentMatchers.contains("SELECT detail_json"), eq(String.class), eq("A1")))
                .thenReturn("{\"lag\":101}");
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        assertTrue(repo.ackRecord("A1", "admin", "reviewed"));

        ArgumentCaptor<String> sqlC = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sqlC.capture(), any(Object[].class));
        String sql = sqlC.getValue();
        assertTrue(sql.contains("ecos_runtime_alert_record"));
        assertTrue(sql.contains("status = 'acked'"));
        assertTrue(sql.contains("ack_by"));
        assertTrue(sql.contains("ack_time = NOW()"));
    }

    @Test
    void closeRecord_migratesOnlyOpenOrAcked() {
        when(jdbc.update(anyString(), eq("X"))).thenReturn(1);
        assertTrue(repo.closeRecord("X"));
        ArgumentCaptor<String> sqlC = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sqlC.capture(), eq("X"));
        assertTrue(sqlC.getValue().contains("status = 'closed'"));
        assertTrue(sqlC.getValue().contains("close_time = NOW()"));
        assertTrue(sqlC.getValue().contains("status IN ('open','acked')"));
    }

    @Test
    void listRecords_ordersByOccurredAtDesc_andTargetsEcosTable() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        repo.listRecords("open", null, 1, 20);

        ArgumentCaptor<String> sqlC = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<RowMapper> rmC = ArgumentCaptor.forClass(RowMapper.class);
        ArgumentCaptor<Object[]> argsC = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sqlC.capture(), rmC.capture(), argsC.capture());
        assertTrue(sqlC.getValue().contains("ecos_runtime_alert_record"));
        assertTrue(sqlC.getValue().contains("occurred_at DESC"));
        assertTrue(sqlC.getValue().contains("status = ?"));
        assertEquals("open", argsC.getValue()[0]);
        assertEquals(20, argsC.getValue()[1]);
        assertEquals(0, argsC.getValue()[2]);
    }

    @Test
    void queryRecords_countTargetsEcosTable() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(7L);
        assertEquals(7L, repo.countRecords("open", null));
        ArgumentCaptor<String> sqlC = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sqlC.capture(), eq(Long.class), any());
        assertTrue(sqlC.getValue().contains("COUNT(*)"));
        assertTrue(sqlC.getValue().contains("ecos_runtime_alert_record"));
    }

    // ===================== 通知通道 (F00-09) =====================

    @Test
    void webhookChannel_withoutUrl_degradesToLog() {
        NotificationServiceImpl svc = new NotificationServiceImpl();
        AlertRecord r = new AlertRecord();
        r.setRuleId("R1");
        r.setAlertType("DLQ_LAG");
        r.setAlertLevel("P1");
        r.setAlertStatus("PENDING");
        r.setAlertMessage("no url here");
        INotificationService.NotificationResult res = svc.sendWebhook(r);
        // 无 URL → 降级 log 通道 → success
        assertTrue(res.isSuccess());
        assertEquals("log", res.getChannel());
    }

    @Test
    void logChannel_returnsSuccessAndStructuredLog() {
        NotificationServiceImpl svc = new NotificationServiceImpl();
        AlertRecord r = new AlertRecord();
        r.setRuleId("R2");
        r.setAlertLevel("P2");
        r.setAlertMessage("hello");
        INotificationService.NotificationResult res = svc.logChannel(r);
        assertTrue(res.isSuccess());
        assertEquals("log", res.getChannel());
    }

    @Test
    void notify_logChannel_inMultichannelList() {
        NotificationServiceImpl svc = new NotificationServiceImpl();
        AlertRecord r = new AlertRecord();
        r.setAlertMessage("multi");
        INotificationService.NotificationResult res =
                svc.sendNotification(java.util.List.of("log", "EMAIL"), r);
        assertTrue(res.isSuccess());
    }
}
