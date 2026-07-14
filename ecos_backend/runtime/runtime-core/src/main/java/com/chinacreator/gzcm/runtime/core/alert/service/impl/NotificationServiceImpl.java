package com.chinacreator.gzcm.runtime.core.alert.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.chinacreator.gzcm.runtime.core.alert.AlertRecord;
import com.chinacreator.gzcm.runtime.core.alert.INotificationService;

/**
 * 通知服务实现
 * 提供多渠道通知发送功能
 * 
 * @author CDRC Runtime Team
 */
public class NotificationServiceImpl implements INotificationService {
    
    // 通知发送历史记录
    private final ConcurrentMap<String, INotificationService.NotificationResult> notificationHistory = new ConcurrentHashMap<>();
    
    // 通知配置（可以从配置文件读取）
    private final ConcurrentMap<String, String> notificationConfig = new ConcurrentHashMap<>();

    @Override
    public INotificationService.NotificationResult sendNotification(List<String> channels, AlertRecord alertRecord) {
        if (channels == null || channels.isEmpty()) {
            return new INotificationService.NotificationResult(false, "No channels specified", "");
        }
        if (alertRecord == null) {
            return new INotificationService.NotificationResult(false, "Alert record cannot be null", "");
        }
        
        List<String> successChannels = new ArrayList<>();
        List<String> failedChannels = new ArrayList<>();
        StringBuilder errorMessages = new StringBuilder();
        
        for (String channel : channels) {
            INotificationService.NotificationResult result = null;
            try {
                switch (channel.toUpperCase()) {
                    case "EMAIL":
                        result = sendEmail(alertRecord);
                        break;
                    case "SMS":
                        result = sendSms(alertRecord);
                        break;
                    case "WECHAT_WORK":
                    case "WECHAT":
                        result = sendWechatWork(alertRecord);
                        break;
                    case "DINGTALK":
                    case "DING":
                        result = sendDingtalk(alertRecord);
                        break;
                    case "WEBHOOK":
                        result = sendWebhook(alertRecord);
                        break;
                    default:
                        result = new INotificationService.NotificationResult(false, "Unknown channel: " + channel, channel);
                }
                
                if (result.isSuccess()) {
                    successChannels.add(channel);
                } else {
                    failedChannels.add(channel);
                    if (errorMessages.length() > 0) {
                        errorMessages.append("; ");
                    }
                    errorMessages.append(channel).append(": ").append(result.getMessage());
                }
            } catch (Exception e) {
                failedChannels.add(channel);
                if (errorMessages.length() > 0) {
                    errorMessages.append("; ");
                }
                errorMessages.append(channel).append(": ").append(e.getMessage());
            }
        }
        
        boolean overallSuccess = !successChannels.isEmpty();
        String message = overallSuccess ? 
            "Sent to " + successChannels.size() + " channel(s)" : 
            "Failed to send to all channels";
        if (!failedChannels.isEmpty()) {
            message += ". Failed: " + String.join(", ", failedChannels);
        }
        
        return new INotificationService.NotificationResult(overallSuccess, message, String.join(",", successChannels));
    }

    @Override
    public INotificationService.NotificationResult sendEmail(AlertRecord alertRecord) {
        // 占位实现：实际应调用邮件服务（如JavaMail）
        // 这里记录通知历史
        String notificationId = "email_" + System.currentTimeMillis();
        INotificationService.NotificationResult result = new INotificationService.NotificationResult(true, "Email notification sent", "email");
        notificationHistory.put(notificationId, result);
        return result;
    }

    @Override
    public INotificationService.NotificationResult sendSms(AlertRecord alertRecord) {
        // 占位实现：实际应调用短信服务
        String notificationId = "sms_" + System.currentTimeMillis();
        INotificationService.NotificationResult result = new INotificationService.NotificationResult(true, "SMS notification sent", "sms");
        notificationHistory.put(notificationId, result);
        return result;
    }

    @Override
    public INotificationService.NotificationResult sendWechatWork(AlertRecord alertRecord) {
        // 占位实现：实际应调用企业微信API
        String notificationId = "wechat_" + System.currentTimeMillis();
        INotificationService.NotificationResult result = new INotificationService.NotificationResult(true, "WeChat Work notification sent", "wechat_work");
        notificationHistory.put(notificationId, result);
        return result;
    }

    @Override
    public INotificationService.NotificationResult sendDingtalk(AlertRecord alertRecord) {
        // 占位实现：实际应调用钉钉API
        String notificationId = "dingtalk_" + System.currentTimeMillis();
        INotificationService.NotificationResult result = new INotificationService.NotificationResult(true, "DingTalk notification sent", "dingtalk");
        notificationHistory.put(notificationId, result);
        return result;
    }

    @Override
    public INotificationService.NotificationResult sendWebhook(AlertRecord alertRecord) {
        // 占位实现：实际应发送HTTP POST请求到webhook URL
        String notificationId = "webhook_" + System.currentTimeMillis();
        INotificationService.NotificationResult result = new INotificationService.NotificationResult(true, "Webhook notification sent", "webhook");
        notificationHistory.put(notificationId, result);
        return result;
    }
    
    /**
     * 获取通知历史记录
     */
    public ConcurrentMap<String, INotificationService.NotificationResult> getNotificationHistory() {
        return new ConcurrentHashMap<>(notificationHistory);
    }
}

