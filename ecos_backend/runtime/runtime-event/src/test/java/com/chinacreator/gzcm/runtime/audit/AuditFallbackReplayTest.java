package com.chinacreator.gzcm.runtime.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.runtime.audit.entity.AuditRetryRow;
import com.chinacreator.gzcm.runtime.audit.mapper.AuditRetryMapper;
import com.chinacreator.gzcm.runtime.core.alert.IAlertService;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;

/**
 * C.5.4 审计兜底单测（W12 / 详细设计-00）。
 * <p>
 * 全 mock，不起 Kafka/不连 DB：
 * <ol>
 *   <li>{@link AuditRetrySink#tryPublish} publish 抛异常 → 落兜底行
 *       （pending / attempts=0 / next_retry_at≈now+60s / 合法 payload）；</li>
 *   <li>{@link AuditRetryTask#replayOne} 重投失败 → attempts+1（markRetryFailed）；</li>
 *   <li>attempts 达 3（含本次）仍失败 → status=alerted + critical 告警
 *       {@code kafka.audit.retry.exhausted}（provider=runtime-event；ack 必须人工）。</li>
 * </ol>
 *
 * <p>运行：{@code mvn -pl runtime/runtime-event test -Dtest=AuditFallbackReplayTest}
 */
@DisplayName("AuditFallback — C.5.4 审计兜底链")
class AuditFallbackReplayTest {

    // ── Sink：publish 失败 → 兜底行 ───────────────────────────────

    @Test
    void tryPublishFailsThenWritesRetryRow() {
        EventBusService bus = mock(EventBusService.class);
        org.mockito.Mockito.doThrow(new RuntimeException("kafka down")).when(bus).publish(anyString(), any());
        AuditRetryMapper mapper = mock(AuditRetryMapper.class);
        when(mapper.insert(any(AuditRetryRow.class))).thenReturn(1);

        AuditRetrySink sink = new AuditRetrySink(bus, mapper);
        boolean ok = sink.tryPublish("MONITOR_ALERT_ACK", "{\"id\":\"a-1\"}");

        // 投递失败 → 转兜底表：mapper.insert 成功 → sink 返回 true（已接管）
        assertTrue(ok, "publish 失败但已转兜底表 → sink 返回 true（已接管，不再抛给业务方）");
        ArgumentCaptor<AuditRetryRow> cap = ArgumentCaptor.forClass(AuditRetryRow.class);
        verify(mapper).insert(cap.capture());
        AuditRetryRow row = cap.getValue();
        assertNotNull(row.getId());
        assertEquals(36, row.getId().length(), "MC01 应用侧 UUID");
        assertEquals("MONITOR_ALERT_ACK", row.getEventType());
        assertEquals("{\"id\":\"a-1\"}", row.getPayload());
        assertEquals("pending", row.getStatus());
        assertNotNull(row.getNextRetryAt(), "next_retry_at 须落 now+60s 窗");
        // 首重试基础窗 60s（±30% 抖动 → [42s,78s]）
        long delta = row.getNextRetryAt().getTime() - System.currentTimeMillis();
        assertTrue(delta > 40_000L && delta < 80_000L, "next_retry_at 应在 60s±30% 窗内，实际 " + delta + "ms");
    }

    @Test
    void tryPublishSucceedsNoRetryRow() {
        EventBusService bus = mock(EventBusService.class);
        AuditRetryMapper mapper = mock(AuditRetryMapper.class);
        AuditRetrySink sink = new AuditRetrySink(bus, mapper);

        boolean ok = sink.tryPublish("EVT", "{}");

        assertTrue(ok, "publish 成功应返回 true");
        verify(mapper, times(0)).insert(any(AuditRetryRow.class));
    }

    // ── Task：到期行重放，attempts+1，达 3 → alerted + critical 告警 ──

    private AuditRetryRow row(int attempts) {
        AuditRetryRow r = new AuditRetryRow();
        r.setId("r-" + attempts);
        r.setEventType("MONITOR_ALERT_ACK");
        r.setPayload("{}");
        r.setAttempts((short) attempts);
        r.setStatus("pending");
        return r;
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(bean);
        return p;
    }

    @Test
    void replayFailsAttemptsBelowMaxBacksOff() {
        EventBusService bus = mock(EventBusService.class);
        org.mockito.Mockito.doThrow(new RuntimeException("still down")).when(bus).publish(anyString(), any());
        AuditRetryMapper mapper = mock(AuditRetryMapper.class);
        AuditRetryTask task = new AuditRetryTask(mapper, bus, provider(mock(IAlertService.class)));

        task.replayOne(row(1)); // attempts=1 → 重投失败 → attempts 2 (<3) 退避

        // attempts+1（markRetryFailed），status 仍 pending（未达上限 → 不 alerted）
        ArgumentCaptor<java.sql.Timestamp> ts = ArgumentCaptor.forClass(java.sql.Timestamp.class);
        verify(mapper).markRetryFailed(eq("r-1"), ts.capture(), anyString());
        verify(mapper, times(0)).markAlerted(anyString());
        // 二次退避窗 60s*2^1=120s±30% → [84s,156s]
        long delta = ts.getValue().getTime() - System.currentTimeMillis();
        assertTrue(delta > 80_000L && delta < 160_000L, "attempts=1 退避应 120s±30%，实际 " + delta);
    }

    @Test
    void replayExhaustedAtThreeMarksAlertedAndRaisesCritical() throws Exception {
        EventBusService bus = mock(EventBusService.class);
        org.mockito.Mockito.doThrow(new RuntimeException("eof")).when(bus).publish(anyString(), any());
        AuditRetryMapper mapper = mock(AuditRetryMapper.class);
        IAlertService alert = mock(IAlertService.class);
        when(mapper.markAlerted(anyString())).thenReturn(1);
        AuditRetryTask task = new AuditRetryTask(mapper, bus, provider(alert));

        task.replayOne(row(2)); // attempts=2 → 本次失败后=3 → 达上限

        verify(mapper, times(1)).markAlerted(eq("r-2"));
        verify(alert).triggerAlertByRuleCode(
                eq(AuditRetryTask.RULE_RETRY_EXHAUSTED),
                eq("runtime-event"),
                eq(AuditRetryTask.RULE_RETRY_EXHAUSTED),
                eq("critical"),
                isNull(), isNull(),
                eq("AUDIT_RETRY_EXHAUSTED"),
                isNull(),
                eq("r-2"),
                anyString());
    }

    @Test
    void replaySuccessMarksReplayed() {
        EventBusService bus = mock(EventBusService.class);
        AuditRetryMapper mapper = mock(AuditRetryMapper.class);
        AuditRetryTask task = new AuditRetryTask(mapper, bus, provider(mock(IAlertService.class)));

        task.replayOne(row(0));

        verify(mapper).markReplayed(eq("r-0"));
        verify(mapper, times(0)).markRetryFailed(anyString(), any(), anyString());
    }

    @Test
    void envelopeCarriesRetryId() {
        EventBusService bus = mock(EventBusService.class);
        AuditRetryMapper mapper = mock(AuditRetryMapper.class);
        AuditRetryTask task = new AuditRetryTask(mapper, bus, provider(mock(IAlertService.class)));

        task.replayOne(row(0));

        org.mockito.ArgumentCaptor<Object> cap = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(bus).publish(eq("ecos.audit"), cap.capture());
        assertTrue(cap.getValue() instanceof Map, "重投应为事件总线 envelope(Map)");
        Map<?, ?> env = (Map<?, ?>) cap.getValue();
        assertEquals("r-0", env.get("retryId"), "envelope 应携带 retryId 溯源");
    }
}
