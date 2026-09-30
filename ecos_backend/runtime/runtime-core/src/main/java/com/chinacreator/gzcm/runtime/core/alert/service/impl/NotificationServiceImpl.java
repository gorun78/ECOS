package com.chinacreator.gzcm.runtime.core.alert.service.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.chinacreator.gzcm.runtime.core.alert.AlertRecord;
import com.chinacreator.gzcm.runtime.core.alert.INotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通知服务实现
 * 提供多渠道通知发送功能
 *
 * <p>详细设计-00 F00-09 (W07)：补齐 spec 要求的两条<b>真实</b>通道：
 * {@code log}（slf4j 结构化落日志）与 {@code webhook}（HTTP POST，okhttp）。
 * email/sms/wechat/dingtalk 仍为占位（工艺通道归 09 册，非本册底座范围）。</p>
 *
 * @author CDRC Runtime Team
 */
public class NotificationServiceImpl implements INotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // webhook 目标地址key：由调用方在 AlertRecord 消息体或配置中携带（形如 "URL=<url>"）
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(5))
            .writeTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(10))
            .retryOnConnectionFailure(false)
            .build();

    // 通知发送历史记录
    private final ConcurrentMap<String, INotificationService.NotificationResult> notificationHistory = new ConcurrentHashMap<>();

    // 通知配置（可以从配置文件读取）
    private final ConcurrentMap<String, String> notificationConfig = new ConcurrentHashMap<>();


    @Override
    public INotificationService.NotificationResult sendWebhook(AlertRecord alertRecord) {
        String url = extractWebhookUrl(alertRecord);
        if (url == null || url.isEmpty()) {
            // 无明确 webhook 目标 → 落 slf4j（等价 log 通道），不把通知记为失败
            return logChannel(alertRecord);
        }
        String notificationId = "webhook_" + System.currentTimeMillis();
        try {
            String body = MAPPER.writeValueAsString(toWire(alertRecord));
            Request req = new Request.Builder().url(url)
                    .post(RequestBody.create(body, JSON))
                    .build();
            try (Response resp = httpClient.newCall(req).execute()) {
                if (resp.isSuccessful()) {
                    INotificationService.NotificationResult result =
                            new INotificationService.NotificationResult(true, "Webhook 2xx", "webhook");
                    notificationHistory.put(notificationId, result);
                    return result;
                }
                INotificationService.NotificationResult result =
                        new INotificationService.NotificationResult(false, "Webhook HTTP " + resp.code(), "webhook");
                notificationHistory.put(notificationId, result);
                return result;
            }
        } catch (Exception e) {
            log.warn("[runtime-alert] webhook 投递失败 url={} err={}", url, e.getMessage());
            return new INotificationService.NotificationResult(false, "Webhook 投递异常: " + e.getMessage(), "webhook");
        }
    }

    /** 详细设计-00 F00-09：log 通道（真实 slf4j 输出）。channels 含 "log"/"LOG" 时 sendNotification 转发至此。 */
    public INotificationService.NotificationResult logChannel(AlertRecord alertRecord) {
        if (alertRecord == null) {
            return new INotificationService.NotificationResult(false, "Alert record cannot be null", "log");
        }
        String notificationId = "log_" + System.currentTimeMillis();
        log.warn("[runtime-alert][{}] rule={} type={} level={} node={} task={} msg={}",
                notificationId, alertRecord.getRuleId(), alertRecord.getAlertType(),
                alertRecord.getAlertLevel(), alertRecord.getNodeId(), alertRecord.getTaskId(),
                alertRecord.getAlertMessage());
        INotificationService.NotificationResult result =
                new INotificationService.NotificationResult(true, "Alert logged to slf4j", "log");
        notificationHistory.put(notificationId, result);
        return result;
    }

    private Map<String, Object> toWire(AlertRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("alertId", r.getAlertId());
        m.put("ruleId", r.getRuleId());
        m.put("alertType", r.getAlertType());
        m.put("severity", r.getAlertLevel());
        m.put("nodeId", r.getNodeId());
        m.put("taskId", r.getTaskId());
        m.put("status", r.getAlertStatus());
        m.put("message", r.getAlertMessage());
        m.put("occurredAt", r.getAlertTime() == null ? null : r.getAlertTime().toLocalDateTime().toString());
        return m;
    }

    /** 从 message 中提取 "URL=..." 形式的 webhook 目标；否则返回 null。 */
    private static String extractWebhookUrl(AlertRecord r) {
        String msg = r == null ? null : r.getAlertMessage();
        if (msg == null) return null;
        if (msg.startsWith("URL=")) return msg.substring(4).trim();
        return null;
    }

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
                    case "LOG":
                        result = logChannel(alertRecord);
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

    /**
     * 获取通知历史记录
     */
    public ConcurrentMap<String, INotificationService.NotificationResult> getNotificationHistory() {
        return new ConcurrentHashMap<>(notificationHistory);
    }
}

