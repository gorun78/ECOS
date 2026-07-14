package com.chinacreator.gzcm.runtime.core.alert;

import java.util.List;

/**
 * 閫氱煡鏈嶅姟鎺ュ彛
 * 鏀寔澶氱閫氱煡娓犻亾锛氶偖浠躲€佺煭淇°€佷紒涓氬井淇°€侀拤閽夈€乄ebhook
 * 
 * @author CDRC Runtime Team
 */
public interface INotificationService {
    
    /**
     * 鍙戦€侀€氱煡
     * 
     * @param channels 閫氱煡娓犻亾鍒楄〃
     * @param alertRecord 鍛婅璁板綍
     * @return 鍙戦€佺粨鏋?
     */
    NotificationResult sendNotification(List<String> channels, AlertRecord alertRecord);
    
    /**
     * 鍙戦€侀偖浠堕€氱煡
     * 
     * @param alertRecord 鍛婅璁板綍
     * @return 鍙戦€佺粨鏋?
     */
    NotificationResult sendEmail(AlertRecord alertRecord);
    
    /**
     * 鍙戦€佺煭淇￠€氱煡
     * 
     * @param alertRecord 鍛婅璁板綍
     * @return 鍙戦€佺粨鏋?
     */
    NotificationResult sendSms(AlertRecord alertRecord);
    
    /**
     * 鍙戦€佷紒涓氬井淇￠€氱煡
     * 
     * @param alertRecord 鍛婅璁板綍
     * @return 鍙戦€佺粨鏋?
     */
    NotificationResult sendWechatWork(AlertRecord alertRecord);
    
    /**
     * 鍙戦€侀拤閽夐€氱煡
     * 
     * @param alertRecord 鍛婅璁板綍
     * @return 鍙戦€佺粨鏋?
     */
    NotificationResult sendDingtalk(AlertRecord alertRecord);
    
    /**
     * 鍙戦€乄ebhook閫氱煡
     * 
     * @param alertRecord 鍛婅璁板綍
     * @return 鍙戦€佺粨鏋?
     */
    NotificationResult sendWebhook(AlertRecord alertRecord);
    
    /**
     * 閫氱煡缁撴灉
     */
    class NotificationResult {
        private boolean success;
        private String message;
        private String channel;
        
        public NotificationResult(boolean success, String message, String channel) {
            this.success = success;
            this.message = message;
            this.channel = channel;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getChannel() {
            return channel;
        }
    }
}

