package com.chinacreator.gzcm.runtime.core.alert.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.chinacreator.gzcm.runtime.core.alert.AlertRecord;
import com.chinacreator.gzcm.runtime.core.alert.INotificationService;

/**
 * NotificationServiceImpl 单元测试
 */
@DisplayName("通知服务测试")
class NotificationServiceImplTest {

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl();
    }

    @Test
    @DisplayName("发送多渠道通知")
    void testSendNotification() {
        AlertRecord alertRecord = createTestAlertRecord();
        List<String> channels = Arrays.asList("email", "sms");

        INotificationService.NotificationResult result = 
            notificationService.sendNotification(channels, alertRecord);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getMessage());
    }

    @Test
    @DisplayName("发送空渠道列表应返回失败")
    void testSendNotificationWithEmptyChannels() {
        AlertRecord alertRecord = createTestAlertRecord();
        List<String> channels = Arrays.asList();

        INotificationService.NotificationResult result = 
            notificationService.sendNotification(channels, alertRecord);

        assertNotNull(result);
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("发送null告警记录应返回失败")
    void testSendNotificationWithNullRecord() {
        List<String> channels = Arrays.asList("email");

        INotificationService.NotificationResult result = 
            notificationService.sendNotification(channels, null);

        assertNotNull(result);
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("发送邮件通知")
    void testSendEmail() {
        AlertRecord alertRecord = createTestAlertRecord();

        INotificationService.NotificationResult result = 
            notificationService.sendEmail(alertRecord);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("email", result.getChannel());
    }

    @Test
    @DisplayName("发送短信通知")
    void testSendSms() {
        AlertRecord alertRecord = createTestAlertRecord();

        INotificationService.NotificationResult result = 
            notificationService.sendSms(alertRecord);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("sms", result.getChannel());
    }

    @Test
    @DisplayName("发送企业微信通知")
    void testSendWechatWork() {
        AlertRecord alertRecord = createTestAlertRecord();

        INotificationService.NotificationResult result = 
            notificationService.sendWechatWork(alertRecord);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("wechat_work", result.getChannel());
    }

    @Test
    @DisplayName("发送钉钉通知")
    void testSendDingtalk() {
        AlertRecord alertRecord = createTestAlertRecord();

        INotificationService.NotificationResult result = 
            notificationService.sendDingtalk(alertRecord);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("dingtalk", result.getChannel());
    }

    @Test
    @DisplayName("发送Webhook通知")
    void testSendWebhook() {
        AlertRecord alertRecord = createTestAlertRecord();

        INotificationService.NotificationResult result = 
            notificationService.sendWebhook(alertRecord);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("webhook", result.getChannel());
    }

    @Test
    @DisplayName("获取通知历史")
    void testGetNotificationHistory() {
        AlertRecord alertRecord = createTestAlertRecord();
        notificationService.sendEmail(alertRecord);

        var history = notificationService.getNotificationHistory();
        assertNotNull(history);
    }

    private AlertRecord createTestAlertRecord() {
        AlertRecord record = new AlertRecord();
        record.setAlertId("test-alert-001");
        record.setRuleId("test-rule-001");
        record.setAlertLevel("P1");
        record.setAlertMessage("测试告警消息");
        record.setAlertTime(new Timestamp(System.currentTimeMillis()));
        return record;
    }
}

