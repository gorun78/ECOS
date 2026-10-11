package com.chinacreator.gzcm.engine.data.fact.period;

import com.chinacreator.gzcm.engine.data.fact.dao.PeriodStatusMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DB-backed PeriodLock 判定（H-3 冻结测试类②）：
 * <ul>
 *   <li>DB 态 CLOSED 期间命中拒绝、OPEN/未登记期间放行；</li>
 *   <li>缓存生效：多次 isClosed 只查库一次（禁循环查库红线）；</li>
 *   <li>forceRefresh 后重查（关账即时失效路径）；</li>
 *   <li>DB 异常 → 降级 OpenPeriodsPeriodLock 配置 CSV 判定（不抛、不把迁移进度变全量 500）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DatabasePeriodLockTest {

    @Mock private PeriodStatusMapper periodStatusMapper;

    private OpenPeriodsPeriodLock configFallback;

    @BeforeEach
    void setUp() {
        configFallback = new OpenPeriodsPeriodLock("2024-12");   // 降级形态的既有运维配置
    }

    private DatabasePeriodLock lock() {
        return new DatabasePeriodLock(periodStatusMapper, configFallback, 60L);
    }

    @Test
    @DisplayName("DB CLOSED 期间 → isClosed=true；OPEN/未登记 → false")
    void dbBackedDecision() {
        when(periodStatusMapper.selectClosedPeriods()).thenReturn(List.of("2025-01", "2025-03"));
        DatabasePeriodLock lock = lock();

        assertTrue(lock.isClosed("2025-01"));
        assertFalse(lock.isClosed("2025-02"));
        assertFalse(lock.isClosed("1999-12"));
    }

    @Test
    @DisplayName("缓存：多次判定只查库一次（禁循环查库）；null/空期间不触发查询")
    void cachedNoPerCallQuery() {
        when(periodStatusMapper.selectClosedPeriods()).thenReturn(List.of("2025-01"));
        DatabasePeriodLock lock = lock();

        for (int i = 0; i < 50; i++) {
            lock.isClosed("2025-01");
            lock.isClosed("2025-02");
        }
        lock.isClosed(null);
        lock.isClosed("");

        verify(periodStatusMapper, times(1)).selectClosedPeriods();
    }

    @Test
    @DisplayName("forceRefresh → 下一判定重查库（关账成功后即时生效）")
    void forceRefreshRereads() {
        when(periodStatusMapper.selectClosedPeriods())
                .thenReturn(List.of())
                .thenReturn(List.of("2025-06"));
        DatabasePeriodLock lock = lock();

        assertFalse(lock.isClosed("2025-06"));
        lock.forceRefresh();
        assertTrue(lock.isClosed("2025-06"));
        verify(periodStatusMapper, times(2)).selectClosedPeriods();
        assertEquals(1, lock.cachedClosedPeriods().size());
    }

    @Test
    @DisplayName("DB 异常（V278 未应用等）→ 降级配置期锁：CSV 命中=true，其余=false，不抛")
    void dbFailureDegradesToConfigLock() {
        when(periodStatusMapper.selectClosedPeriods()).thenThrow(new RuntimeException("relation ecos_data_period_status does not exist"));
        DatabasePeriodLock lock = lock();

        assertTrue(lock.isClosed("2024-12"));    // 配置 CSV（ecos.data.facts.closed-periods）仍生效
        assertFalse(lock.isClosed("2025-01"));   // DB 里的 CLOSED 在降级态按配置口径放行
        assertFalse(lock.isClosed("2099-01"));
    }

    @Test
    @DisplayName("降级恢复：DB 重新可达后回到 DB 判定（forceRefresh 越过失败退避窗口重查）")
    void recoveryAfterDbDown() {
        when(periodStatusMapper.selectClosedPeriods())
                .thenThrow(new RuntimeException("db down"))
                .thenReturn(List.of("2025-07"));
        DatabasePeriodLock lock = lock();

        assertFalse(lock.isClosed("2025-07"));   // 第一次：异常 → 配置降级（2025-07 不在 CSV）
        lock.forceRefresh();                     // 恢复探测（运维面等价动作：退避窗口后自动重试）
        assertTrue(lock.isClosed("2025-07"));    // DB 恢复 → 回到 DB 判定
        verify(periodStatusMapper, times(2)).selectClosedPeriods();
    }

    @Test
    @DisplayName("失败退避：从未成功时窗口内不反复打库（禁循环查库/防空转）")
    void failureBackoffAvoidsQueryStorm() {
        when(periodStatusMapper.selectClosedPeriods()).thenThrow(new RuntimeException("db down"));
        DatabasePeriodLock lock = lock();

        for (int i = 0; i < 30; i++) {
            assertFalse(lock.isClosed("2025-01"));
        }
        verify(periodStatusMapper, times(1)).selectClosedPeriods();
    }
}
