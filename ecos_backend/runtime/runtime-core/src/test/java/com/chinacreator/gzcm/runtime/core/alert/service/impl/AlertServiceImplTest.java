package com.chinacreator.gzcm.runtime.core.alert.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.chinacreator.gzcm.runtime.core.alert.AlertRecord;
import com.chinacreator.gzcm.runtime.core.alert.AlertRule;
import com.chinacreator.gzcm.runtime.core.alert.IAlertService;

/**
 * AlertServiceImpl 单元测试
 */
@DisplayName("告警服务测试")
class AlertServiceImplTest {

    private AlertServiceImpl alertService;

    @BeforeEach
    void setUp() {
        alertService = new AlertServiceImpl();
    }

    @Test
    @DisplayName("创建告警规则")
    void testCreateAlertRule() throws IAlertService.AlertException {
        AlertRule rule = new AlertRule();
        rule.setRuleId("rule-001");
        rule.setRuleName("测试规则");
        rule.setMetricType("TASK_FAILURE");
        rule.setOperator(">");
        rule.setThreshold(100.0);
        rule.setAlertLevel("P1");
        rule.setEnabled("1");

        String ruleId = alertService.createAlertRule(rule);
        assertNotNull(ruleId);
        assertEquals("rule-001", ruleId);
        
        AlertRule found = alertService.getAlertRuleById(ruleId);
        assertNotNull(found);
        assertEquals("测试规则", found.getRuleName());
    }

    @Test
    @DisplayName("创建null告警规则应抛出异常")
    void testCreateAlertRuleWithNull() {
        assertThrows(IAlertService.AlertException.class, () -> {
            alertService.createAlertRule(null);
        });
    }

    @Test
    @DisplayName("更新告警规则")
    void testUpdateAlertRule() throws IAlertService.AlertException {
        AlertRule rule = new AlertRule();
        rule.setRuleId("rule-001");
        rule.setRuleName("测试规则");
        rule.setEnabled("1");
        alertService.createAlertRule(rule);

        rule.setRuleName("更新后的规则");
        alertService.updateAlertRule(rule);

        AlertRule updated = alertService.getAlertRuleById("rule-001");
        assertNotNull(updated);
        assertEquals("更新后的规则", updated.getRuleName());
    }

    @Test
    @DisplayName("根据ID获取告警规则")
    void testGetAlertRuleById() throws IAlertService.AlertException {
        AlertRule rule = new AlertRule();
        rule.setRuleId("rule-001");
        rule.setRuleName("测试规则");
        rule.setEnabled("1");
        alertService.createAlertRule(rule);

        AlertRule found = alertService.getAlertRuleById("rule-001");
        assertNotNull(found);
        assertEquals("rule-001", found.getRuleId());
    }

    @Test
    @DisplayName("删除告警规则")
    void testDeleteAlertRule() throws IAlertService.AlertException {
        AlertRule rule = new AlertRule();
        rule.setRuleId("rule-001");
        rule.setEnabled("1");
        alertService.createAlertRule(rule);

        alertService.deleteAlertRule("rule-001");

        AlertRule found = alertService.getAlertRuleById("rule-001");
        assertNull(found);
    }

    @Test
    @DisplayName("查询告警规则")
    void testQueryAlertRules() throws IAlertService.AlertException {
        for (int i = 1; i <= 5; i++) {
            AlertRule rule = new AlertRule();
            rule.setRuleId("rule-" + i);
            rule.setRuleName("规则" + i);
            rule.setEnabled(i % 2 == 0 ? "1" : "0");
            alertService.createAlertRule(rule);
        }

        List<AlertRule> enabledRules = alertService.queryAlertRules("1");
        assertNotNull(enabledRules);
        assertTrue(enabledRules.size() >= 2);

        List<AlertRule> allRules = alertService.queryAlertRules(null);
        assertNotNull(allRules);
        assertTrue(allRules.size() >= 5);
    }

    @Test
    @DisplayName("触发告警")
    void testTriggerAlert() throws IAlertService.AlertException {
        AlertRule rule = new AlertRule();
        rule.setRuleId("rule-001");
        rule.setRuleName("测试规则");
        rule.setEnabled("1");
        alertService.createAlertRule(rule);

        alertService.triggerAlert("rule-001", "TASK_FAILURE", "node-001", "task-001", "测试告警消息");

        List<AlertRecord> records = alertService.queryAlertRecords("rule-001", null, null, null, null);
        assertNotNull(records);
        assertTrue(records.size() >= 1);
    }

    @Test
    @DisplayName("查询告警记录")
    void testQueryAlertRecords() throws IAlertService.AlertException {
        AlertRule rule = new AlertRule();
        rule.setRuleId("rule-001");
        rule.setEnabled("1");
        alertService.createAlertRule(rule);

        for (int i = 1; i <= 3; i++) {
            alertService.triggerAlert("rule-001", "TASK_FAILURE", "node-001", "task-" + i, "告警消息" + i);
        }

        List<AlertRecord> records = alertService.queryAlertRecords("rule-001", null, null, null, null);
        assertNotNull(records);
        assertTrue(records.size() >= 3);
    }

    @Test
    @DisplayName("标记告警为已解决")
    void testResolveAlert() throws IAlertService.AlertException {
        AlertRule rule = new AlertRule();
        rule.setRuleId("rule-001");
        rule.setEnabled("1");
        alertService.createAlertRule(rule);

        alertService.triggerAlert("rule-001", "TASK_FAILURE", "node-001", "task-001", "测试告警");

        List<AlertRecord> records = alertService.queryAlertRecords("rule-001", null, null, null, null);
        assertNotNull(records);
        if (!records.isEmpty()) {
            String alertId = records.get(0).getAlertId();
            alertService.resolveAlert(alertId, "admin", "已解决");
            
            records = alertService.queryAlertRecords("rule-001", "RESOLVED", null, null, null);
            assertNotNull(records);
            assertTrue(records.size() >= 1);
        }
    }
}

