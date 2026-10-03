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
     * 持久化告警编程入口（C.5.2 / C.5.4 DLQ 与审计兜底消费方复用）— 增量接口（API 只增不改）。
     * <p>
     * 语义 = {@code triggerAlert}，但规则溯源走 <b>ruleCode</b>（DB 权威）：
     * 不存在时按 (provider, metricKey, severity, threshold, upgradeMin) 自动创建
     * （provider 落 create_by，如 {@code runtime-event}）；规则启用才落 record。
     * DB 不可用时降级纯内存，{@link AlertException} 语义同 {@link #triggerAlert}。
     *
     * @param ruleCode   规则编码（V241 rule_code，如 kafka.dlt.lag / kafka.audit.retry.exhausted）
     * @param provider   触发方模块标识（rule 不存在时自动创建，落 create_by）
     * @param metricKey  指标键（V241 metric_key）
     * @param severity   规则严重度 info|warn|error|critical（rule 不存在时用于创建）
     * @param threshold  阈值（rule 不存在时用于创建，可 null）
     * @param upgradeMin 升级 SLA 分钟（rule 不存在时用于创建，可 null）
     * @param alertType  告警类型（进 record.title）
     * @param nodeId     节点 ID（可 null）
     * @param taskId     任务 ID（可 null）
     * @param message    告警消息（进 record.title / detail）
     * @return 落库的 alertId（纯内存降级时同返内存记录 id）
     */
    String triggerAlertByRuleCode(String ruleCode, String provider, String metricKey, String severity,
                                  Double threshold, Integer upgradeMin,
                                  String alertType, String nodeId, String taskId, String message)
            throws AlertException;
    
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

