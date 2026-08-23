package com.chinacreator.gzcm.runtime.core.alert;

import java.util.List;
import java.util.Map;

/**
 * 鍛婅鏈嶅姟鎺ュ彛
 * 
 * @author CDRC Runtime Team
 */
public interface IAlertService {
    
    /**
     * 鍒涘缓鍛婅瑙勫垯
     */
    String createAlertRule(AlertRule rule) throws AlertException;
    
    /**
     * 鏇存柊鍛婅瑙勫垯
     */
    void updateAlertRule(AlertRule rule) throws AlertException;
    
    /**
     * 鍒犻櫎鍛婅瑙勫垯
     */
    void deleteAlertRule(String ruleId) throws AlertException;
    
    /**
     * 鏌ヨ鍛婅瑙勫垯
     */
    AlertRule getAlertRuleById(String ruleId) throws AlertException;
    
    /**
     * 鏌ヨ鍛婅瑙勫垯鍒楄〃
     */
    List<AlertRule> queryAlertRules(String enabled) throws AlertException;
    
    /**
     * 瑙﹀彂鍛婅
     */
    void triggerAlert(String ruleId, String alertType, String nodeId, String taskId, String message) throws AlertException;
    
    /**
     * 鏌ヨ鍛婅璁板綍鍒楄〃
     */
    List<AlertRecord> queryAlertRecords(String ruleId, String alertStatus, String alertLevel, 
                                        Integer page, Integer size) throws AlertException;
    
    /**
     * 鏍囪鍛婅涓哄凡瑙ｅ喅
     */
    void resolveAlert(String alertId, String resolveBy, String resolveNote) throws AlertException;
    
    /**
     * 鍛婅寮傚父
     */
    class AlertException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public AlertException(String message) {
            super(message);
        }
        
        public AlertException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

