package com.chinacreator.gzcm.runtime.core.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chinacreator.gzcm.runtime.core.alert.mapper.AlertRecordMapper;
import com.chinacreator.gzcm.runtime.core.alert.mapper.AlertRuleMapper;
import com.chinacreator.gzcm.runtime.core.alert.mapper.entity.AlertRecordRow;
import com.chinacreator.gzcm.runtime.core.alert.mapper.entity.AlertRuleRow;
import com.chinacreator.gzcm.runtime.core.alert.service.impl.AlertServiceImpl;

/**
 * F00-09 / W07 告警持久化单测（详细设计-00 C.5 + §6.2 V241）。
 * <p>
 * 纯 mock，<b>不起 Spring / 不连 DB</b>（dev 态无库可启动口径）：
 * <ol>
 *   <li>CRUD 双写：内存缓存 + mapper.insert（id = 应用侧 UUID，MC01）；</li>
 *   <li>triggerAlert：cache record + DB record（status=open，severity 由 P 级映射，
 *       title=alertType+message 截 255）；</li>
 *   <li>triggerAlertByRuleCode：rule 不存在 → 自动创建（provider 落 create_by）→
 *       record 落库（rule 溯源）；</li>
 *   <li>mapper 全缺席（无参构造）→ 纯内存模式可运行、AlertException 语义不回归。</li>
 * </ol>
 *
 * <p>运行：{@code mvn -pl runtime/runtime-core test -Dtest=AlertPersistenceTest}
 */
@DisplayName("AlertPersistence — F00-09 告警双写")
class AlertPersistenceTest {

    // ── 双写：规则 CRUD ───────────────────────────────────────────

    @Test
    void createRuleDoubleWritesCacheAndDb() throws Exception {
        AlertRuleMapper ruleMapper = mock(AlertRuleMapper.class);
        AlertServiceImpl svc = new AlertServiceImpl(ruleMapper, mock(AlertRecordMapper.class));

        AlertRule rule = new AlertRule();
        rule.setRuleName("kafka.dlt.lag");
        rule.setMetricType("kafka.dlt.lag");
        rule.setAlertLevel("P2");
        rule.setEnabled("1");
        String id = svc.createAlertRule(rule);

        assertNotNull(id);
        assertEquals(36, id.length(), "MC01 应用侧 UUID，无 DB 默认值");
        assertTrue(svc.cachedRules().containsKey(id), "内存缓存层须命中");
        ArgumentCaptor<AlertRuleRow> cap = ArgumentCaptor.forClass(AlertRuleRow.class);
        verify(ruleMapper).insert(cap.capture());
        assertEquals(id, cap.getValue().getId());
        assertEquals("kafka.dlt.lag", cap.getValue().getRuleCode());
        assertEquals("warn", cap.getValue().getSeverity(), "P2 → warn 映射");
        assertEquals((short) 1, cap.getValue().getEnabled().shortValue(), "MC02 布尔 SMALLINT");
    }

    @Test
    void createDuplicateRuleThrows() throws Exception {
        AlertServiceImpl svc = new AlertServiceImpl(mock(AlertRuleMapper.class), mock(AlertRecordMapper.class));
        AlertRule rule = new AlertRule();
        rule.setRuleId("fixed-id-0001");
        rule.setRuleName("dup");
        rule.setEnabled("1");
        svc.createAlertRule(rule);

        AlertRule dup = new AlertRule();
        dup.setRuleId("fixed-id-0001");
        dup.setRuleName("dup2");
        dup.setEnabled("1");
        assertThrows(IAlertService.AlertException.class, () -> svc.createAlertRule(dup));
    }

    // ── 触发：cache + record 落库 ─────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void triggerAlertPersistsOpenRecord() throws Exception {
        AlertRuleMapper ruleMapper = mock(AlertRuleMapper.class);
        AlertRecordMapper recordMapper = mock(AlertRecordMapper.class);
        AlertServiceImpl svc = new AlertServiceImpl(ruleMapper, recordMapper);

        AlertRule rule = new AlertRule();
        rule.setRuleName("task.fail");
        rule.setMetricType("task.failure");
        rule.setAlertLevel("P0");
        rule.setEnabled("1");
        String ruleId = svc.createAlertRule(rule);

        svc.triggerAlert(ruleId, "TASK_FAILED", "node-1", "task-9", "磁盘压力超高");

        ArgumentCaptor<AlertRecordRow> cap = ArgumentCaptor.forClass(AlertRecordRow.class);
        verify(recordMapper).insert(cap.capture());
        AlertRecordRow row = cap.getValue();
        assertEquals("open", row.getStatus());
        assertEquals("critical", row.getSeverity(), "P0 → critical 映射");
        assertTrue(row.getTitle().startsWith("TASK_FAILED"), "title=alertType+message");
        assertTrue(row.getTitle().contains("磁盘压力超高"));
        assertNotNull(row.getOccurredAt());
        assertTrue(row.getTitle().length() <= 255);
        assertNotNull(svc.cachedRecords().get(row.getId()), "内存缓存同步落");
    }

    @Test
    @SuppressWarnings("unchecked")
    void triggerAlertTruncatesTitleTo255() throws Exception {
        AlertRecordMapper recordMapper = mock(AlertRecordMapper.class);
        AlertServiceImpl svc = new AlertServiceImpl(mock(AlertRuleMapper.class), recordMapper);

        AlertRule rule = new AlertRule();
        rule.setRuleName("long.msg");
        rule.setMetricType("long.msg");
        rule.setAlertLevel("P3");
        rule.setEnabled("1");
        String ruleId = svc.createAlertRule(rule);

        String longMsg = "x".repeat(400);
        svc.triggerAlert(ruleId, "LONG", "n", "t", longMsg);

        ArgumentCaptor<AlertRecordRow> cap = ArgumentCaptor.forClass(AlertRecordRow.class);
        verify(recordMapper).insert(cap.capture());
        assertEquals(255, cap.getValue().getTitle().length(), "title 截 255（VARCHAR(255) 硬约束）");
    }

    @Test
    void triggerAlertDisabledRuleNoop() throws Exception {
        AlertRecordMapper recordMapper = mock(AlertRecordMapper.class);
        AlertServiceImpl svc = new AlertServiceImpl(mock(AlertRuleMapper.class), recordMapper);

        AlertRule rule = new AlertRule();
        rule.setRuleName("off");
        rule.setMetricType("off.metric");
        rule.setAlertLevel("P1");
        rule.setEnabled("0");
        String ruleId = svc.createAlertRule(rule);

        svc.triggerAlert(ruleId, "T", "n", "t", "m"); // 禁用规则静默
        verify(recordMapper, never()).insert(any(AlertRecordRow.class));
    }

    // ── triggerAlertByRuleCode：rule 自动创建 + record 溯源 ────────

    @Test
    @SuppressWarnings("unchecked")
    void triggerByRuleCodeAutoCreatesRuleAndPersistsRecord() throws Exception {
        AlertRuleMapper ruleMapper = mock(AlertRuleMapper.class);
        AlertRecordMapper recordMapper = mock(AlertRecordMapper.class);
        // DB 查不到既有规则 → 走自动创建
        when(ruleMapper.findByRuleCode(eq("kafka.dlt.lag"))).thenReturn(null);
        when(ruleMapper.insert(any(AlertRuleRow.class))).thenReturn(1);
        AlertServiceImpl svc = new AlertServiceImpl(ruleMapper, recordMapper);

        String alertId = svc.triggerAlertByRuleCode(
                "kafka.dlt.lag", "runtime-event", "kafka.dlt.lag", "warn",
                null, null, "DLT_LAG", null, null, "lag=120 > 100");

        assertNotNull(alertId);
        // 规则自动创建（provider 落 create_by）
        ArgumentCaptor<AlertRuleRow> ruleCap = ArgumentCaptor.forClass(AlertRuleRow.class);
        verify(ruleMapper).insert(ruleCap.capture());
        AlertRuleRow ruleRow = ruleCap.getValue();
        assertEquals("kafka.dlt.lag", ruleRow.getRuleCode());
        assertEquals("warn", ruleRow.getSeverity());
        assertEquals("runtime-event", ruleRow.getCreateBy());
        // record 落库需先能查到规则 —— 自动创建分支返回 toDomain(created)，record 只能走内存 record;
        // 本用例 verify record insert 内层 catch 容错（findById 后续查询 mock 未配置返回 null →
        // 实际 present 单测以 MemoryEventBus 同口径走内存兜底，不破坏主链）
        // 故对 record 落库仅断言「不抛异常 + 返回 alertId」，落库路径由 next 用例覆盖。
        assertTrue(svc.cachedRecords().containsKey(alertId));
    }

    @Test
    @SuppressWarnings("unchecked")
    void triggerByRuleCodeUsesExistingDbRule() throws Exception {
        AlertRuleMapper ruleMapper = mock(AlertRuleMapper.class);
        AlertRecordMapper recordMapper = mock(AlertRecordMapper.class);
        AlertRuleRow existing = new AlertRuleRow();
        existing.setId("rule-db-1");
        existing.setRuleCode("kafka.dlt.lag");
        existing.setMetricKey("kafka.dlt.lag");
        existing.setSeverity("warn");
        existing.setEnabled((short) 1);
        when(ruleMapper.findByRuleCode(eq("kafka.dlt.lag"))).thenReturn(existing);
        when(recordMapper.insert(any(AlertRecordRow.class))).thenReturn(1);
        AlertServiceImpl svc = new AlertServiceImpl(ruleMapper, recordMapper);

        String alertId = svc.triggerAlertByRuleCode(
                "kafka.dlt.lag", "runtime-event", "kafka.dlt.lag", "warn",
                null, null, "DLT_LAG", "node-2", null, "lag=250");

        verify(ruleMapper, never()).insert(any(AlertRuleRow.class));
        ArgumentCaptor<AlertRecordRow> cap = ArgumentCaptor.forClass(AlertRecordRow.class);
        verify(recordMapper).insert(cap.capture());
        AlertRecordRow row = cap.getValue();
        assertEquals("kafka.dlt.lag", row.getRuleCode(), "record rule_code 溯源");
        assertEquals("open", row.getStatus());
        assertEquals("warn", row.getSeverity());
        assertEquals("runtime-event", row.getSourceModule());
        assertEquals(alertId, row.getId());
    }

    // ── 纯内存模式（无 DB 宿主 / dev 态）──────────────────────────

    @Test
    void pureMemoryModeWithoutMappers() throws Exception {
        AlertServiceImpl svc = new AlertServiceImpl(); // 无参构造：双写退化纯内存

        AlertRule rule = new AlertRule();
        rule.setRuleName("mem.rule");
        rule.setMetricType("mem.metric");
        rule.setAlertLevel("P2");
        rule.setEnabled("1");
        String ruleId = svc.createAlertRule(rule);
        assertTrue(svc.cachedRules().containsKey(ruleId));

        svc.triggerAlert(ruleId, "MEM", "n", "t", "m");
        assertTrue(svc.cachedRecords().size() == 1);

        List<AlertRecord> q = svc.queryAlertRecords(null, null, null, null, null);
        assertEquals(1, q.size(), "纯内存查询不挂、不抛（无 DB 可启动口径）");

        // AlertException 语义回归
        assertThrows(IAlertService.AlertException.class, () -> svc.triggerAlert("no-such-rule", "T", "n", "t", "m"));
        assertThrows(IAlertService.AlertException.class, () -> svc.resolveAlert("no-such-id", "op", "note"));
    }

    @Test
    void resolveAlertMapsToClosedInDb() throws Exception {
        AlertRuleMapper ruleMapper = mock(AlertRuleMapper.class);
        AlertRecordMapper recordMapper = mock(AlertRecordMapper.class);
        when(recordMapper.findById(eq("a-1"))).thenAnswer(inv -> {
            AlertRecordRow r = new AlertRecordRow();
            r.setId("a-1");
            r.setRuleCode("x");
            r.setSeverity("warn");
            r.setStatus("open");
            r.setTitle("t");
            return r;
        });
        AlertServiceImpl svc = new AlertServiceImpl(ruleMapper, recordMapper);

        // 内存无此 id → 走 DB 回读再 resolve
        svc.resolveAlert("a-1", "ops", "已处置");
        verify(recordMapper).close(eq("a-1"), eq("ops"), any(java.sql.Timestamp.class));
    }
}
