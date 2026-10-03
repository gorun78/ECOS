package com.chinacreator.gzcm.engine.data.quality.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chinacreator.gzcm.runtime.core.alert.IAlertService;

/**
 * F02-10 验收 — {@code DqAlertEscalationTimerTest}。
 * <p>
 * 可控时钟推进断言升级状态机：P2 超 5min 未 ack → P1 且恰推送 1 次；
 * 未到窗口的告警不升级、不推送。覆盖 P1→P0、P3 无升级、DB 降级三条路径。
 */
@DisplayName("F02-10 DQ 告警升级时限状态机")
class DqAlertEscalationTimerTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final Instant NOW = Instant.parse("2026-10-03T02:00:00Z");

    private JdbcTemplate jdbc;
    private IAlertService alertService;
    private DqAlertEscalationService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        alertService = mock(IAlertService.class);
        service = new DqAlertEscalationService(jdbc, alertService, Clock.fixed(NOW, ZONE));
    }

    private Map<String, Object> row(String id, String ruleId, String level, long ageMinutes) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", id);
        r.put("rule_id", ruleId);
        r.put("alert_level", level);
        r.put("asset_id", "A1");
        r.put("rule_name", "not-null-gate");
        r.put("created_at", Timestamp.from(NOW.minus(Duration.ofMinutes(ageMinutes))));
        return r;
    }

    @Test
    @DisplayName("P2 超 5min 未 ack → P1，且推送 1 次")
    void p2EscalatesToP1After6Minutes() throws Exception {
        when(jdbc.queryForList(anyString()))
            .thenReturn(List.of(row("ALERT-1", "RULE-1", "P2", 6)));   // 6min > 5min 窗口

        int escalated = service.runEscalation();

        assertEquals(1, escalated);
        // 落库升级：SET alert_level='P1', escalated_to='P1'
        verify(jdbc).update(anyString(), eq("P1"), eq("P1"), eq("ALERT-1"));
        // 推送 1 次，rule_id 从规则表取
        verify(alertService, times(1)).triggerAlert(
                eq("RULE-1"), eq("DQ_ESCALATION"), eq("A1"), any(), anyString());
    }

    @Test
    @DisplayName("P2 未超窗口（<5min）不升级、不推送")
    void p2WithinWindowNotEscalated() throws Exception {
        when(jdbc.queryForList(anyString()))
            .thenReturn(List.of(row("ALERT-2", "RULE-2", "P2", 3)));   // 3min < 5min

        int escalated = service.runEscalation();

        assertEquals(0, escalated);
        verify(jdbc, times(0)).update(anyString(), anyString(), anyString(), anyString());
        verify(alertService, times(0)).triggerAlert(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("P1 超 15min 未 ack → P0；P3 无升级")
    void p1EscalatesToP0After20Minutes() throws Exception {
        when(jdbc.queryForList(anyString()))
            .thenReturn(List.of(
                    row("ALERT-3", "RULE-3", "P1", 20),     // 20min > 15min → P0
                    row("ALERT-4", "RULE-4", "P3", 240)));  // P3 无升级

        int escalated = service.runEscalation();

        assertEquals(1, escalated);
        verify(jdbc).update(anyString(), eq("P0"), eq("P0"), eq("ALERT-3"));
        verify(jdbc, times(0)).update(anyString(), anyString(), anyString(), eq("ALERT-4"));
    }

    @Test
    @DisplayName("纯决策函数：确界与时限窗口一致")
    void decideEscalationPure() {
        assertEquals("P1", service.decideEscalation("P2", Duration.ofMinutes(5)));   // 恰 5min
        assertNull(  service.decideEscalation("P2", Duration.ofMinutes(4)));         // 未达
        assertEquals("P0", service.decideEscalation("P1", Duration.ofMinutes(15)));  // 恰 15min
        assertNull(  service.decideEscalation("P1", Duration.ofMinutes(14)));
        assertNull(  service.decideEscalation("P0", Duration.ofMinutes(999)));       // 最高级不升级
        assertNull(  service.decideEscalation("P3", Duration.ofMinutes(999)));       // P3 无规则
        assertNull(  service.decideEscalation(null, Duration.ofMinutes(99)));
        assertEquals("P1", service.decideEscalation("p2", Duration.ofMinutes(6)));   // 大小写不敏感
    }

    @Test
    @DisplayName("DB 不可用（查询异常）→ 降级返回 0，不上抛")
    void dbUnavailableDegradesGracefully() throws Exception {
        when(jdbc.queryForList(anyString())).thenThrow(new RuntimeException("no table"));

        int escalated = service.runEscalation();

        assertEquals(0, escalated);
        verify(alertService, times(0)).triggerAlert(anyString(), anyString(), anyString(), any(), anyString());
    }
}
