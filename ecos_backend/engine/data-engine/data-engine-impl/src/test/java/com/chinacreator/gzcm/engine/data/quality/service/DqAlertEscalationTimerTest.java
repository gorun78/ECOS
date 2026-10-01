package com.chinacreator.gzcm.engine.data.quality.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.engine.data.quality.dao.DqAlertEscalationDao;
import com.chinacreator.gzcm.runtime.core.alert.IAlertService;

/**
 * F02-10 DQ 告警升级时限状态机（W62） — 验收测试：
 * P2 未 acknowledge 超 5 min → P1；P1 未 acknowledge 超 15 min → P0。
 *
 * <p>可控时钟：直接注入 {@link LocalDateTime} 到
 * {@link DqAlertEscalationService#executeEscalationScan(LocalDateTime)}；
 * DqAlertEscalationDao 用 mock 提供候选行 / 观察 UPDATE 参数；
 * IAlertService 用 mock 断言 runtime 推送调用 1 次。</p>
 *
 * @author ECOS Data Engine Team / F02-10
 */
@DisplayName("F02-10 DQ 告警升级时限状态机（P2→P1 5min / P1→P0 15min）")
class DqAlertEscalationTimerTest {

    private static final LocalDateTime NOW =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(1_700_000_000_000L), ZoneId.systemDefault());

    private DqAlertEscalationService wire(DqAlertEscalationDao dao, IAlertService alertSvc,
                                          DqSecurityService sec) {
        ObjectProvider<IAlertService> ap = mock(ObjectProvider.class);
        when(ap.getIfAvailable()).thenReturn(alertSvc);
        ObjectProvider<DqSecurityService> sp = mock(ObjectProvider.class);
        when(sp.getIfAvailable()).thenReturn(sec);
        return new DqAlertEscalationService(ap, sp, dao);
    }

    /** 依次返回值：P2 轮 → P1 轮（扫描内部先 P2 后 P1，固定两次 findUnacknowledged）。 */
    private static void stubCandidates(DqAlertEscalationDao dao,
                                       List<Map<String, Object>> p2Candidates,
                                       List<Map<String, Object>> p1Candidates) {
        when(dao.findUnacknowledged(eq("P2"), any(Timestamp.class))).thenReturn(p2Candidates);
        when(dao.findUnacknowledged(eq("P1"), any(Timestamp.class))).thenReturn(p1Candidates);
    }

    private static Map<String, Object> row(String id, String ruleId, String level,
                                           int elapsedMinutes) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("ruleId", ruleId);
        r.put("alertLevel", level);
        r.put("assetId", "a-" + id);
        r.put("ruleName", "rule for " + id);
        r.put("message", "message for " + id);
        r.put("createdAt", Timestamp.valueOf(NOW.minusMinutes(elapsedMinutes)));
        return r;
    }

    @Test
    @DisplayName("P2 未 ack 超 5 min → 升级 P1；runtime 推送恰好 1 次；escalated_to=P1 + update 1 次")
    void p2FiveMinUnacked_escalatesToP1AndPushesOnce() throws Exception {
        DqAlertEscalationDao dao = mock(DqAlertEscalationDao.class);
        IAlertService alertSvc = mock(IAlertService.class);
        DqSecurityService sec = mock(DqSecurityService.class);
        DqAlertEscalationService svc = wire(dao, alertSvc, sec);

        stubCandidates(dao, List.of(row("p2-1", "r-1", "P2", 6)), Collections.emptyList());
        when(dao.markEscalated(eq("P1"), eq("p2-1"))).thenReturn(1);

        int escalated = svc.executeEscalationScan(NOW);

        assertEquals(1, escalated, "本批应恰好 1 条升级（P2→P1）");
        // markEscalated 参数正是 (toLevel="P1", alertId="p2-1")。
        verify(dao, times(1)).markEscalated(eq("P1"), eq("p2-1"));

        verify(alertSvc, times(1)).triggerAlert(eq("r-1"), eq("DQ_ALERT_ESCALATION"),
                anyString(), anyString(), anyString());

        verify(sec, times(1)).auditWrite(
                eq("DQ_ALERT_ESCALATION"), eq("p2-1"), anyString());
    }

    @Test
    @DisplayName("P1 未 ack 超 15 min → 升级 P0；P1 但只 5 min 前的不应该升级（SLA 15）")
    void p1FifteenMinUnacked_escalatesToP0() throws Exception {
        DqAlertEscalationDao dao = mock(DqAlertEscalationDao.class);
        IAlertService alertSvc = mock(IAlertService.class);
        DqSecurityService sec = mock(DqSecurityService.class);
        DqAlertEscalationService svc = wire(dao, alertSvc, sec);

        // P2 候选空；P1 候选一条 20 min（超 15 min SLA）
        stubCandidates(dao, Collections.emptyList(), List.of(row("p1-1", "r-2", "P1", 20)));
        when(dao.markEscalated(eq("P0"), eq("p1-1"))).thenReturn(1);

        int escalated = svc.executeEscalationScan(NOW);
        assertEquals(1, escalated);

        verify(dao, times(1)).markEscalated(eq("P0"), eq("p1-1"));
        verify(alertSvc, times(1)).triggerAlert(eq("r-2"), eq("DQ_ALERT_ESCALATION"),
                anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("无候选（未超 SLA 或已 ack 全部过滤）→ 0 升级，且 UPDATE 未发生（SLA 守门）")
    void noSlashCandidate_noUpdateNoPush() throws Exception {
        DqAlertEscalationDao dao = mock(DqAlertEscalationDao.class);
        IAlertService alertSvc = mock(IAlertService.class);
        DqSecurityService sec = mock(DqSecurityService.class);
        DqAlertEscalationService svc = wire(dao, alertSvc, sec);

        stubCandidates(dao, Collections.emptyList(), Collections.emptyList());

        int escalated = svc.executeEscalationScan(NOW);
        assertEquals(0, escalated, "两个级别候选均空时应 0");
        verify(dao, never()).markEscalated(anyString(), anyString());
        verify(alertSvc, never()).triggerAlert(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("乐观锁 miss（UPDATE 0 行）→ 不计 escalated 且不触发推送（并发 ack 后 stop）")
    void optimisticLockMiss_isNotCountedNorPushed() throws Exception {
        DqAlertEscalationDao dao = mock(DqAlertEscalationDao.class);
        IAlertService alertSvc = mock(IAlertService.class);
        DqSecurityService sec = mock(DqSecurityService.class);
        DqAlertEscalationService svc = wire(dao, alertSvc, sec);

        stubCandidates(dao, List.of(row("p2-miss", "r-3", "P2", 42)), Collections.emptyList());
        when(dao.markEscalated(anyString(), anyString())).thenReturn(0); // 状态已被并发 ack

        int escalated = svc.executeEscalationScan(NOW);
        assertEquals(0, escalated, "UPDATE n=0 时应不计数 escalated");
        verify(dao, times(1)).markEscalated(anyString(), anyString());
        verify(alertSvc, never()).triggerAlert(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("SLA 矩阵对外导出：P2_ACK=5、P1_ACK=15（保持状态机校验阈值一致性守护）")
    void slaMatrixExposesBothThresholds() {
        Map<String, Long> m = DqAlertEscalationService.currentSlaMatrix();
        assertEquals(Long.valueOf(5), m.get("P2_ACK_SLA_MINUTES"), "P2 SLA=5min");
        assertEquals(Long.valueOf(15), m.get("P1_ACK_SLA_MINUTES"), "P1 SLA=15min");
    }
}
