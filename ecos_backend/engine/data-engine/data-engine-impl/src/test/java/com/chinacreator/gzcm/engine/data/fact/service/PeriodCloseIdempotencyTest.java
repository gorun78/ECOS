package com.chinacreator.gzcm.engine.data.fact.service;

import com.chinacreator.gzcm.engine.data.fact.dao.PeriodStatusMapper;
import com.chinacreator.gzcm.engine.data.fact.period.OpenPeriodsPeriodLock;
import com.chinacreator.gzcm.engine.data.model.PeriodCloseResultVO;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineSecurityService;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 关账幂等（H-3 冻结测试类①）：
 * <ul>
 *   <li>无登记行 → INSERT CLOSED + ecos.audit 事件一条；</li>
 *   <li>重复 close 已 CLOSED 期间 → alreadyClosed=true，零写入、零第二次审计事件（首写留痕不改）；</li>
 *   <li>OPEN 行 → UPDATE→CLOSED（version_no 进位）；</li>
 *   <li>并发双写（UPDATE affected=0）→ 按回放处置，不报错不重复。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PeriodCloseIdempotencyTest {

    @Mock private PeriodStatusMapper periodStatusMapper;
    @Mock private PipelineSecurityService securityService;
    @Mock private EventBusService eventBus;

    private PeriodCloseService service;

    @BeforeEach
    void setUp() {
        service = new PeriodCloseService(periodStatusMapper, securityService,
                new OpenPeriodsPeriodLock(""), Optional.empty(), Optional.of(eventBus));
    }

    private static Map<String, Object> closedRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "ps-uuid-1");
        row.put("period", "2025-01");
        row.put("status", "CLOSED");
        row.put("closed_by", "finance-user");
        row.put("closed_at", Timestamp.valueOf(LocalDateTime.of(2025, 2, 1, 10, 0)));
        row.put("close_reason", "月结审批单 A-001");
        row.put("version_no", "1");
        return row;
    }

    private void allow() {
        when(securityService.evaluate(eq("data.period.close"), anyString(), any()))
                .thenReturn(new PipelineSecurityService.AllowedResult(true, "RBAC_ALLOW"));
    }

    @Test
    @DisplayName("首次关账（无登记行）→ INSERT CLOSED + 审计事件恰一条")
    void firstCloseInsertsAndAuditsOnce() {
        allow();
        when(periodStatusMapper.selectByPeriod("2025-01")).thenReturn(null);

        PeriodCloseResultVO vo = service.close("2025-01", null);

        assertEquals("2025-01", vo.getPeriod());
        assertEquals("CLOSED", vo.getStatus());
        assertFalse(vo.isAlreadyClosed());
        assertNotNull(vo.getClosedAt());
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.captor();
        verify(periodStatusMapper, times(1)).insertRow(captor.capture());
        Map<String, Object> row = captor.getValue();
        assertEquals("CLOSED", row.get("status"));
        assertEquals("2025-01", row.get("period"));
        assertNotNull(row.get("id"));                    // MC01 应用侧 UUID
        assertEquals("1", row.get("versionNo"));         // DR07
        assertEquals(0, row.get("isDeleted"));           // DR06
        assertEquals("default", row.get("domain"));      // DR08
        assertEquals("system", row.get("createBy"));     // 无 UserContext 环境的缺省口径
        verify(eventBus, times(1)).publish(eq("ecos.audit"), any(Object.class));
        verify(periodStatusMapper, never()).updateOpenToClosed(anyString(), anyString(),
                any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("重复关账已 CLOSED 期间 → alreadyClosed=true，零写入零第二次审计（幂等）")
    void repeatCloseIsIdempotentNoWriteNoAudit() {
        allow();
        when(periodStatusMapper.selectByPeriod("2025-01")).thenReturn(closedRow());

        PeriodCloseResultVO vo = service.close("2025-01", null);

        assertTrue(vo.isAlreadyClosed());
        assertEquals("CLOSED", vo.getStatus());
        assertEquals("finance-user", vo.getClosedBy());  // 首写留痕不改
        verify(periodStatusMapper, never()).insertRow(any());
        verify(periodStatusMapper, never()).updateOpenToClosed(anyString(), anyString(),
                any(), any(), anyString(), anyString());
        verify(eventBus, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("OPEN 行关账 → UPDATE→CLOSED，version_no 进位；审计一条")
    void openRowCloseUpdatesWithVersionBump() {
        allow();
        Map<String, Object> open = new LinkedHashMap<>();
        open.put("id", "ps-uuid-2");
        open.put("period", "2025-02");
        open.put("status", "OPEN");
        open.put("version_no", "3");
        when(periodStatusMapper.selectByPeriod("2025-02")).thenReturn(open);
        when(periodStatusMapper.updateOpenToClosed(eq("2025-02"), anyString(), any(), any(),
                eq("4"), anyString())).thenReturn(1);

        PeriodCloseResultVO vo = service.close("202502", null);   // yyyMM 归一亦覆盖本用例

        assertEquals("2025-02", vo.getPeriod());
        assertFalse(vo.isAlreadyClosed());
        verify(periodStatusMapper, never()).insertRow(any());
        verify(eventBus, times(1)).publish(eq("ecos.audit"), any(Object.class));
    }

    @Test
    @DisplayName("并发双写（UPDATE affected=0）→ 按回放处置，不报错不重复审计")
    void concurrentUpdateLossFallsBackToReplay() {
        allow();
        Map<String, Object> open = new LinkedHashMap<>();
        open.put("id", "ps-uuid-3");
        open.put("period", "2025-03");
        open.put("status", "OPEN");
        open.put("version_no", "1");
        when(periodStatusMapper.selectByPeriod("2025-03")).thenReturn(open, closedRow202503());
        when(periodStatusMapper.updateOpenToClosed(eq("2025-03"), anyString(), any(), any(),
                anyString(), anyString())).thenReturn(0);

        PeriodCloseResultVO vo = service.close("2025-03", null);

        assertTrue(vo.isAlreadyClosed());
        assertEquals("other-finance", vo.getClosedBy());
        verify(eventBus, never()).publish(anyString(), any());
    }

    private static Map<String, Object> closedRow202503() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "ps-uuid-3");
        row.put("period", "2025-03");
        row.put("status", "CLOSED");
        row.put("closed_by", "other-finance");
        row.put("closed_at", Timestamp.valueOf(LocalDateTime.of(2025, 4, 1, 9, 0)));
        row.put("version_no", "2");
        return row;
    }
}
